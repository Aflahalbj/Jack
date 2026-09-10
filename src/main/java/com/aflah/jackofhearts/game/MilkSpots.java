package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Daftar wadah yang boleh kejatuhan susu, dipilih sendiri oleh pembangun map.
 *
 * <p>Awalnya semua peti/tungku/dispenser/hopper/tong di dunia ikut jadi calon. Itu
 * cukup untuk map kecil, tapi salah untuk map yang luas: peti penyimpanan di luar arena
 * ikut kebagian, dan susunya mendarat di tempat yang tidak pernah didatangi siapa pun
 * selama permainan.</p>
 *
 * <p>Jadi wadahnya ditunjuk satu per satu dengan {@link
 * com.aflah.jackofhearts.item.ContainerMarkerItem}, dan daftarnya disimpan ke dunia —
 * ini penataan map, bukan keadaan satu permainan. Sekali ditunjuk, berlaku untuk semua
 * permainan berikutnya sampai dicabut.</p>
 *
 * <p>Disimpan di penyimpanan data overworld, tapi tiap titik membawa dimensinya sendiri,
 * jadi wadah di dimensi mana pun tetap tercatat utuh. Polanya sama dengan
 * {@link com.aflah.jackofhearts.cafeteria.CafeteriaLayout}.</p>
 */
public class MilkSpots extends SavedData {

    public static final String NAME = "jackofhearts_milkspots";

    public record Spot(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private final Set<Spot> spots = new LinkedHashSet<>();

    /**
     * Disimpan di penyimpanan data <b>dimensi arena</b>, bukan overworld.
     *
     * <p>Supaya daftarnya ikut terbungkus bersama map: berkasnya mendarat di
     * {@code <dunia>/dimensions/jackofhearts/arena/data/}, folder yang sama yang disalin
     * ke dalam mod saat map-nya dibungkus. Kalau disimpan di overworld, map yang dipasang
     * di dunia baru akan datang tanpa satu pun wadah susu.</p>
     *
     * <p>Kalau dimensi arenanya belum ada, jatuh ke overworld supaya perintahnya tetap
     * jalan di dunia yang belum disiapkan.</p>
     */
    /**
     * Daftarnya bisa tinggal di dua tempat, dan yang dipakai adalah yang ada isinya.
     *
     * <p>Penyimpanan <b>dimensi arena</b> lebih diutamakan, karena berkasnya mendarat di
     * dalam folder dimensi — folder yang sama yang disalin saat map dibungkus ke dalam
     * mod. Daftar yang ikut bersama map berarti map yang dipasang di dunia baru datang
     * lengkap dengan wadah susunya, tanpa satu perintah pun.</p>
     *
     * <p>Penyimpanan overworld dipakai kalau yang di arena masih kosong. Itu keadaan yang
     * wajar: map yang masih dibangun di overworld, atau dunia yang dimensi arenanya belum
     * pernah dibuat sama sekali.</p>
     *
     * <p>Yang sengaja <b>tidak</b> dilakukan: memindahkan isi dari satu ke yang lain.
     * Pemindahan berarti menebak bahwa koordinat di dunia lama juga benar di dunia baru,
     * dan tebakan itu pernah salah — semua tanda berpindah ke dimensi kosong dan
     * terdaftar rapi tanpa satu pun yang bisa diisi.</p>
     */
    public static MilkSpots get(MinecraftServer server) {
        ServerLevel arena = Arena.level(server);
        MilkSpots fromArena = arena == null ? null
                : arena.getDataStorage().computeIfAbsent(MilkSpots::load, MilkSpots::new, NAME);
        if (fromArena != null && !fromArena.spots.isEmpty()) {
            return fromArena;
        }
        MilkSpots fromOverworld = server.overworld().getDataStorage()
                .computeIfAbsent(MilkSpots::load, MilkSpots::new, NAME);
        if (!fromOverworld.spots.isEmpty()) {
            return fromOverworld;
        }
        // Dua-duanya kosong: tanda baru ditulis ke arena, supaya ikut terbungkus bersama
        // map-nya nanti.
        return fromArena != null ? fromArena : fromOverworld;
    }

    public List<Spot> all() {
        return List.copyOf(spots);
    }

    public boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
        return spots.contains(new Spot(dimension, pos.immutable()));
    }

    /** @return true kalau wadahnya baru saja ditambahkan, false kalau justru dicabut. */
    public boolean toggle(ResourceKey<Level> dimension, BlockPos pos) {
        Spot spot = new Spot(dimension, pos.immutable());
        boolean added = spots.add(spot);
        if (!added) {
            spots.remove(spot);
        }
        setDirty();
        return added;
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        if (spots.remove(new Spot(dimension, pos.immutable()))) {
            setDirty();
        }
    }

    public int clear() {
        int size = spots.size();
        spots.clear();
        setDirty();
        return size;
    }

    public int size() {
        return spots.size();
    }

    public static MilkSpots load(CompoundTag tag) {
        MilkSpots data = new MilkSpots();
        ListTag list = tag.getList("spots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(entry.getString("dimension"));
            if (dimensionId == null) {
                continue;
            }
            data.spots.add(new Spot(ResourceKey.create(Registries.DIMENSION, dimensionId),
                    NbtUtils.readBlockPos(entry.getCompound("pos"))));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Spot spot : spots) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", spot.dimension().location().toString());
            entry.put("pos", NbtUtils.writeBlockPos(spot.pos()));
            list.add(entry);
        }
        tag.put("spots", list);
        return tag;
    }

    // ------------------------------------------------------------------ berkas

    /** Nama berkas daftar wadah, dipakai untuk membungkusnya bersama map. */
    public static final String FILE = "milkspots.json";

    /** Letak daftar bawaan di dalam jar mod. */
    private static final String BUNDLED = "/data/jackofhearts/" + FILE;

    /**
     * Tulis daftarnya ke {@code config/jackofhearts/milkspots.json}.
     *
     * <p>Ada karena map dan daftar wadahnya tinggal di dua tempat yang berbeda: map
     * dibungkus ke dalam mod sebagai schematic, sementara daftar wadah adalah data dunia.
     * Berkas inilah jembatannya — salin ke dalam mod, dan map yang dipasang di dunia baru
     * datang lengkap dengan wadah susunya.</p>
     *
     * @return jumlah wadah yang ditulis, atau -1 kalau gagal.
     */
    public int export() {
        Path path = Schematic.folder().resolve(FILE);
        List<Spot> sorted = sorted();
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < sorted.size(); i++) {
            Spot spot = sorted.get(i);
            json.append("  {\"x\": ").append(spot.pos().getX())
                    .append(", \"y\": ").append(spot.pos().getY())
                    .append(", \"z\": ").append(spot.pos().getZ())
                    .append(i + 1 < sorted.size() ? "},\n" : "}\n");
        }
        json.append("]\n");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            JackOfHeartsMod.LOGGER.error("[JackOfHearts] Gagal menulis {}", path, e);
            return -1;
        }
        return sorted.size();
    }

    /**
     * Muat daftar dari berkas: dari config kalau ada, kalau tidak dari dalam jar mod.
     *
     * <p>Isinya <b>ditambahkan</b>, bukan menimpa. Wadah yang sudah ditandai dengan
     * tangan tidak hilang hanya karena daftar bawaan ikut dimuat.</p>
     *
     * @param dimension dimensi tujuan: tempat perintahnya dijalankan.
     * @return jumlah wadah yang dimuat, atau -1 kalau berkasnya tidak ada.
     */
    public int importFile(MinecraftServer server, ResourceKey<Level> dimension) {
        String raw = readFile();
        if (raw == null) {
            return -1;
        }
        int loaded = 0;
        try {
            // Semuanya masuk ke dimensi tempat perintahnya dijalankan, apa pun yang
            // tertulis di berkasnya. Yang menjalankan sedang berdiri di dalam map yang
            // baru dipasang, dan dimensi asal berkasnya belum tentu ada di dunia ini -
            // menuruti nama yang tertulis di situ adalah cara tercepat mendaftarkan
            // belasan titik ke dimensi kosong.
            JsonArray array = JsonParser.parseString(raw).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                spots.add(new Spot(dimension, new BlockPos(entry.get("x").getAsInt(),
                        entry.get("y").getAsInt(), entry.get("z").getAsInt())));
                loaded++;
            }
        } catch (Exception e) {
            JackOfHeartsMod.LOGGER.error("[JackOfHearts] Daftar wadah gagal dibaca", e);
            return -1;
        }
        setDirty();
        return loaded;
    }

    @Nullable
    private static String readFile() {
        Path path = Schematic.folder().resolve(FILE);
        if (Files.isRegularFile(path)) {
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                JackOfHeartsMod.LOGGER.error("[JackOfHearts] Gagal membaca {}", path, e);
                return null;
            }
        }
        try (InputStream stream = MilkSpots.class.getResourceAsStream(BUNDLED)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            JackOfHeartsMod.LOGGER.error("[JackOfHearts] Daftar wadah bawaan gagal dibaca", e);
            return null;
        }
    }

    /** Dipakai perintah pemeriksaan; urutannya tetap supaya daftarnya enak dibaca. */
    public List<Spot> sorted() {
        List<Spot> sorted = new ArrayList<>(spots);
        sorted.sort((a, b) -> {
            int byDimension = a.dimension().location().compareTo(b.dimension().location());
            return byDimension != 0 ? byDimension : Long.compare(a.pos().asLong(), b.pos().asLong());
        });
        return Collections.unmodifiableList(sorted);
    }
}
