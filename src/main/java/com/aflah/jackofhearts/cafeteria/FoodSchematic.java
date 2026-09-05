package com.aflah.jackofhearts.cafeteria;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.config.JackConfig;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Memasang ulang meja makanan dari file schematic.
 *
 * <p>Kafetaria ini memulihkan mejanya sendiri dari potret yang dibaca berkala, dan itu
 * cukup untuk pemakaian biasa. Tapi potret cuma seakurat keadaan terakhir yang sempat
 * terbaca: meja yang sudah telanjur diobrak-abrik sebelum potretnya sempat diperbarui
 * akan dipulihkan ke keadaan yang sama-sama berantakan. Schematic tidak punya masalah
 * itu — isinya ditentukan sekali oleh yang membangun mapnya, dan tidak pernah berubah
 * lagi. Jadi tiap permainan baru bisa dimulai dari meja yang benar-benar sama.</p>
 *
 * <h2>Format yang dibaca</h2>
 *
 * <p>Sponge Schematic (berkas {@code .schem} keluaran WorldEdit), versi 2 maupun 3.
 * Format lama {@code .schematic} bawaan MCEdit tidak dibaca: isinya id blok berupa
 * angka dari zaman sebelum blockstate, dan tidak ada lagi tabel yang bisa
 * menerjemahkannya di 1.20.</p>
 *
 * <p>Blok udara di dalam schematic <b>ikut dipasang</b>, persis seperti {@code //paste}
 * biasa. Itu yang membuat pemasangan ulang bisa diandalkan: piring yang tertinggal di
 * luar susunan aslinya ikut tersapu, bukan menumpuk tiap kali dipasang lagi.</p>
 */
public final class FoodSchematic {

    private FoodSchematic() {
    }

    /** Folder tempat berkas pengganti dicari: {@code config/jackofhearts/}. */
    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(JackOfHeartsMod.MOD_ID);
    }

    /** Letak schematic bawaan di dalam jar mod. */
    private static final String BUNDLED = "/data/" + JackOfHeartsMod.MOD_ID + "/schematics/";

    /**
     * Batas jumlah blok yang mau dipasang sekali jalan.
     *
     * <p>Pemasangannya berjalan di thread server, jadi schematic sebesar satu kota akan
     * membekukan seluruh server sampai selesai. Batas ini menolak berkas yang jelas
     * bukan meja kafetaria daripada membiarkan servernya menggantung tanpa penjelasan.
     */
    private static final int MAX_BLOCKS = 200_000;

    /** Sudut schematic dengan koordinat terkecil, sesuai config. */
    @Nullable
    public static BlockPos origin() {
        String[] parts = JackConfig.CONFIG.foodSchematicOrigin.get().trim().split("\\s+");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    public static Path file() {
        String name = JackConfig.CONFIG.foodSchematic.get().trim();
        return name.isEmpty() ? null : folder().resolve(name);
    }

    /**
     * Pasang schematic makanannya.
     *
     * @return jumlah blok yang dipasang, atau -1 kalau tidak ada yang dipasang sama
     *         sekali (fiturnya dimatikan, berkasnya tidak ada, atau isinya tidak
     *         terbaca). Bedanya dipakai pemanggil untuk memutuskan perlu tidaknya
     *         penataan meja dibaca ulang.
     */
    public static int paste(ServerLevel level) {
        Path path = file();
        BlockPos origin = origin();
        if (path == null || origin == null) {
            return -1;
        }

        CompoundTag root = read(path);
        if (root == null) {
            return -1;
        }

        // Versi 3 membungkus semuanya di dalam "Schematic", dan memindahkan palet serta
        // datanya lagi ke dalam "Blocks". Versi 2 menaruh semuanya di akar.
        CompoundTag schematic = root.contains("Schematic", Tag.TAG_COMPOUND)
                ? root.getCompound("Schematic")
                : root;
        CompoundTag blocks = schematic.contains("Blocks", Tag.TAG_COMPOUND)
                ? schematic.getCompound("Blocks")
                : schematic;

        int width = schematic.getInt("Width");
        int height = schematic.getInt("Height");
        int length = schematic.getInt("Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            JackOfHeartsMod.LOGGER.error("[JackOfHearts] Schematic makanan tidak punya ukuran: {}", path);
            return -1;
        }
        if (width * height * length > MAX_BLOCKS) {
            JackOfHeartsMod.LOGGER.error(
                    "[JackOfHearts] Schematic makanan kebesaran ({} blok, batas {}): {}",
                    width * height * length, MAX_BLOCKS, path);
            return -1;
        }

        CompoundTag palette = blocks.getCompound(blocks.contains("Palette", Tag.TAG_COMPOUND)
                ? "Palette" : "palette");
        byte[] data = blocks.contains("Data", Tag.TAG_BYTE_ARRAY)
                ? blocks.getByteArray("Data")
                : blocks.getByteArray("BlockData");
        if (palette.isEmpty() || data.length == 0) {
            JackOfHeartsMod.LOGGER.error("[JackOfHearts] Schematic makanan tidak punya isi blok: {}", path);
            return -1;
        }

        BlockState[] states = readPalette(palette, level.holderLookup(Registries.BLOCK));
        return place(level, origin, width, height, length, data, states);
    }

    /**
     * Membaca schematic-nya: berkas di folder config kalau ada, kalau tidak yang
     * dibawa di dalam jar.
     *
     * <p>Urutannya sengaja begitu. Yang dibawa mod adalah meja yang benar dan selalu
     * ada, jadi tidak ada yang perlu disiapkan sebelum bermain. Tapi map bisa saja
     * dirombak sendiri oleh yang memakainya, dan berkas di config adalah cara
     * menggantinya tanpa harus membongkar jar.</p>
     */
    @Nullable
    private static CompoundTag read(Path path) {
        File file = path.toFile();
        if (file.isFile()) {
            try {
                return NbtIo.readCompressed(file);
            } catch (Exception e) {
                JackOfHeartsMod.LOGGER.error("[JackOfHearts] Schematic makanan gagal dibaca: {}", path, e);
                return null;
            }
        }

        String resource = BUNDLED + path.getFileName();
        try (InputStream stream = FoodSchematic.class.getResourceAsStream(resource)) {
            if (stream == null) {
                JackOfHeartsMod.LOGGER.warn(
                        "[JackOfHearts] Schematic makanan tidak ada, baik di {} maupun di dalam mod ({}).",
                        path, resource);
                return null;
            }
            return NbtIo.readCompressed(stream);
        } catch (Exception e) {
            JackOfHeartsMod.LOGGER.error("[JackOfHearts] Schematic bawaan gagal dibaca: {}", resource, e);
            return null;
        }
    }

    /**
     * Palet schematic: nama blockstate lengkap dengan propertinya, dipetakan ke nomor.
     *
     * <p>Nomornya tidak berurutan dan tidak dijamin rapat, jadi yang dibuat larik
     * sepanjang nomor terbesar — bukan sepanjang jumlah entri.</p>
     */
    private static BlockState[] readPalette(CompoundTag palette, HolderLookup<Block> lookup) {
        int max = 0;
        for (String key : palette.getAllKeys()) {
            max = Math.max(max, palette.getInt(key));
        }
        BlockState[] states = new BlockState[max + 1];
        for (String key : palette.getAllKeys()) {
            states[palette.getInt(key)] = parseState(key, lookup);
        }
        for (int i = 0; i < states.length; i++) {
            if (states[i] == null) {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }
        return states;
    }

    /**
     * Mengubah teks {@code minecraft:cooked_beef[number_of_food=3]} jadi blockstate.
     *
     * <p>Dipakai pengurai milik vanilla, yang sama dengan yang dipakai perintah
     * {@code /setblock}: propertinya sudah pasti diterjemahkan dengan aturan yang sama
     * persis, termasuk properti milik mod seperti jumlah tumpukan piring.</p>
     */
    private static BlockState parseState(String description, HolderLookup<Block> lookup) {
        try {
            return BlockStateParser.parseForBlock(lookup, description, false).blockState();
        } catch (Exception e) {
            JackOfHeartsMod.LOGGER.warn("[JackOfHearts] Blok '{}' di schematic makanan tidak dikenal; dilewati.",
                    description);
            return Blocks.AIR.defaultBlockState();
        }
    }

    /**
     * Isi {@code BlockData} adalah deretan varint, satu per blok, urut y lalu z lalu x.
     *
     * <p>Varint dipakai karena palet bisa lebih dari 128 macam blok, dan sebagian besar
     * schematic isinya jauh lebih sedikit dari itu — jadi hampir semua blok cukup satu
     * byte.</p>
     */
    private static int place(ServerLevel level, BlockPos origin, int width, int height, int length,
                             byte[] data, BlockState[] states) {
        int index = 0;
        int cursor = 0;
        int placed = 0;
        int cells = width * height * length;

        while (cursor < data.length && index < cells) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (cursor >= data.length) {
                    return placed;
                }
                byte current = data[cursor++];
                value |= (current & 0x7F) << (shift * 7);
                if (++shift > 5) {
                    JackOfHeartsMod.LOGGER.error("[JackOfHearts] Schematic makanan rusak: varint kepanjangan.");
                    return placed;
                }
                if ((current & 0x80) == 0) {
                    break;
                }
            }

            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);
            index++;

            BlockState state = value >= 0 && value < states.length
                    ? states[value]
                    : Blocks.AIR.defaultBlockState();
            // Bendera 2|16: kirim ke client, tapi jangan picu pembaruan bentuk tetangga.
            // Tanpa 16, piring yang dipasang di atas meja bisa langsung copot sendiri
            // karena tetangganya belum sempat terpasang saat giliran dia tiba.
            level.setBlock(origin.offset(x, y, z), state,
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            placed++;
        }
        return placed;
    }
}
