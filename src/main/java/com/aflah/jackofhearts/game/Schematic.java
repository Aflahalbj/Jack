package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
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
 * Pemasang schematic: satu jalur untuk meja kafetaria maupun seluruh arena.
 *
 * <h2>Format yang dibaca</h2>
 *
 * <p>Sponge Schematic (berkas {@code .schem} keluaran WorldEdit), versi 2 maupun 3.
 * Format lama {@code .schematic} bawaan MCEdit tidak dibaca: isinya id blok berupa angka
 * dari zaman sebelum blockstate, dan tidak ada lagi tabel yang bisa menerjemahkannya di
 * 1.20.</p>
 *
 * <h2>Dari mana berkasnya dibaca</h2>
 *
 * <p>Dari {@code config/jackofhearts/} kalau ada di situ, kalau tidak dari dalam jar mod
 * ({@code data/jackofhearts/schematics/}). Urutannya sengaja begitu: yang dibawa mod
 * adalah map yang benar dan selalu ada, sementara berkas di config adalah cara
 * menggantinya tanpa membongkar jar.</p>
 *
 * <p>Blok udara di dalam schematic <b>ikut dipasang</b>, persis seperti {@code //paste}
 * biasa. Itu yang membuat pemasangan ulang bisa diandalkan: apa pun yang tertinggal di
 * dalam bidangnya ikut tersapu, bukan menumpuk tiap kali dipasang lagi.</p>
 */
public final class Schematic {

    private Schematic() {
    }

    /** Letak schematic bawaan di dalam jar mod. */
    private static final String BUNDLED = "/data/" + JackOfHeartsMod.MOD_ID + "/schematics/";

    /** Folder tempat berkas pengganti dicari. */
    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(JackOfHeartsMod.MOD_ID);
    }

    /**
     * Pasang satu schematic.
     *
     * @param name     nama berkasnya, mis. {@code food.schem}.
     * @param origin   tempat mendaratnya sudut dengan koordinat paling kecil.
     * @param maxBlocks batas jumlah blok yang mau dipasang sekali jalan. Pemasangannya
     *                  berjalan di thread server, jadi berkas sebesar satu benua akan
     *                  membekukan seluruh server sampai selesai; batas ini menolak berkas
     *                  yang jelas bukan yang dimaksud daripada membiarkan servernya
     *                  menggantung tanpa penjelasan.
     * @return jumlah blok yang dipasang, atau -1 kalau tidak ada yang dipasang sama
     *         sekali (berkasnya tidak ada, atau isinya tidak terbaca).
     */
    public static int paste(ServerLevel level, String name, BlockPos origin, int maxBlocks) {
        if (name.isBlank()) {
            return -1;
        }
        CompoundTag root = read(folder().resolve(name), name);
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
            JackOfHeartsMod.LOGGER.error("[JackOfHearts] Schematic {} tidak punya ukuran.", name);
            return -1;
        }
        long cells = (long) width * height * length;
        if (cells > maxBlocks) {
            JackOfHeartsMod.LOGGER.error("[JackOfHearts] Schematic {} kebesaran ({} blok, batas {}).",
                    name, cells, maxBlocks);
            return -1;
        }

        CompoundTag palette = blocks.getCompound(blocks.contains("Palette", Tag.TAG_COMPOUND)
                ? "Palette" : "palette");
        byte[] data = blocks.contains("Data", Tag.TAG_BYTE_ARRAY)
                ? blocks.getByteArray("Data")
                : blocks.getByteArray("BlockData");
        if (palette.isEmpty() || data.length == 0) {
            JackOfHeartsMod.LOGGER.error("[JackOfHearts] Schematic {} tidak punya isi blok.", name);
            return -1;
        }

        BlockState[] states = readPalette(palette, level.holderLookup(Registries.BLOCK));
        int placed = place(level, origin, width, height, length, data, states);
        JackOfHeartsMod.LOGGER.info("[JackOfHearts] Schematic {}: {} blok, {} .. {}",
                name, placed, origin.toShortString(),
                origin.offset(width - 1, height - 1, length - 1).toShortString());
        return placed;
    }

    @Nullable
    private static CompoundTag read(Path path, String name) {
        File file = path.toFile();
        if (file.isFile()) {
            try {
                return NbtIo.readCompressed(file);
            } catch (Exception e) {
                JackOfHeartsMod.LOGGER.error("[JackOfHearts] Schematic gagal dibaca: {}", path, e);
                return null;
            }
        }

        String resource = BUNDLED + name;
        try (InputStream stream = Schematic.class.getResourceAsStream(resource)) {
            if (stream == null) {
                JackOfHeartsMod.LOGGER.warn(
                        "[JackOfHearts] Schematic tidak ada, baik di {} maupun di dalam mod ({}).",
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
     * persis, termasuk properti milik mod lain.</p>
     */
    private static BlockState parseState(String description, HolderLookup<Block> lookup) {
        try {
            return BlockStateParser.parseForBlock(lookup, description, false).blockState();
        } catch (Exception e) {
            JackOfHeartsMod.LOGGER.warn("[JackOfHearts] Blok '{}' di schematic tidak dikenal; dilewati.",
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
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

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
                    JackOfHeartsMod.LOGGER.error("[JackOfHearts] Schematic rusak: varint kepanjangan.");
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
            at.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            // Bendera 2|16: kirim ke client, tapi jangan picu pembaruan bentuk tetangga.
            // Tanpa 16, blok yang butuh sandaran bisa langsung copot sendiri karena
            // tetangganya belum sempat terpasang saat giliran dia tiba.
            level.setBlock(at, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            placed++;
        }
        return placed;
    }
}
