package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.locating.IModFile;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Map arena yang dibungkus ke dalam mod sebagai <b>dunia jadi</b>, bukan schematic.
 *
 * <h2>Kenapa bukan schematic</h2>
 *
 * <p>Schematic harus dipasang blok demi blok di satu tick, dan untuk map seukuran arena
 * itu berarti server diam beberapa detik, lampu dihitung ulang dari nol, dan tiap block
 * entity dibangun ulang dari palet teks. Berkas region tidak melewati satu pun dari itu:
 * chunk-nya sudah jadi, lengkap dengan cahaya, block entity, dan blok milik mod lain
 * apa adanya. Yang dikerjakan cuma menyalin berkas.</p>
 *
 * <h2>Cara kerjanya</h2>
 *
 * <p>Berkas region-nya ikut di dalam jar, di {@code data/jackofhearts/arena_world/}.
 * Sebelum server memuat dunia mana pun, isinya disalin ke folder dimensi arena di dunia
 * yang sedang dibuka — <b>hanya kalau folder itu masih kosong</b>. Sesudah itu mod tidak
 * pernah menyentuhnya lagi: bangunan yang diubah di server tetap milik server itu, tidak
 * ditimpa tiap kali dinyalakan.</p>
 *
 * <p>Dimensinya sendiri tetap dideklarasikan lewat datapack biasa
 * ({@code data/jackofhearts/dimension/arena.json}) dengan generator void. Generator itu
 * cuma dipakai untuk chunk yang <i>tidak ada</i> di berkas region — yaitu daerah di luar
 * map, yang memang seharusnya kosong.</p>
 */
public final class ArenaWorld {

    private ArenaWorld() {
    }

    /** Letak berkas dunia bawaan di dalam jar mod. */
    private static final String BUNDLED = "data/" + JackOfHeartsMod.MOD_ID + "/arena_world";

    /**
     * Folder isi map: chunk beserta apa yang tersimpan di dalamnya.
     *
     * <p>Dipasang sebagai satu kesatuan, dan cuma kalau dimensinya masih benar-benar
     * kosong. Menyalin sebagian berarti map bawaan bercampur dengan chunk yang sudah ada
     * di dunia itu.</p>
     */
    private static final List<String> WORLD_FOLDERS = List.of("region", "entities", "poi");

    /**
     * Folder data dimensi — di antaranya daftar wadah susu.
     *
     * <p>Aturannya berbeda dari isi map: berkas yang belum ada di dunia tujuan tetap
     * disalin walau chunk-nya sudah terlanjur dibuat. Dunia yang arenanya pernah dikunjungi
     * sekali sudah punya berkas region berisi kekosongan, dan aturan "semua atau tidak sama
     * sekali" akan membuat daftar wadah susu bawaan tidak pernah ikut terpasang.</p>
     */
    private static final List<String> DATA_FOLDERS = List.of("data");

    /**
     * Folder dimensi arena di dalam dunia yang sedang dibuka:
     * {@code <dunia>/dimensions/<namespace>/<path>/}.
     */
    public static Path folder(MinecraftServer server) {
        var id = Arena.key().location();
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath());
    }

    /**
     * Pasang map bawaan kalau dimensi arenanya masih kosong.
     *
     * <p>Dipanggil sebelum server memuat dunia, jadi chunk-nya sudah ada di tempatnya
     * sebelum ada satu pun yang dibaca. Kalau dipasang belakangan, chunk yang sudah
     * terlanjur dibuat sebagai void akan menang dan map-nya tidak pernah muncul.</p>
     *
     * @return jumlah berkas yang disalin; 0 berarti tidak ada yang perlu dipasang.
     */
    public static int install(MinecraftServer server) {
        return install(server, false);
    }

    /**
     * @param force pasang walau dimensinya sudah punya isi, menimpa yang ada. Dipakai
     *              perintah {@code /jack arena install force} untuk dunia yang arenanya
     *              telanjur dikunjungi sekali dan jadi berisi kekosongan.
     */
    public static int install(MinecraftServer server, boolean force) {
        Path target = folder(server);
        Path source = bundled();
        if (source == null || !Files.isDirectory(source)) {
            JackOfHeartsMod.LOGGER.info(
                    "[JackOfHearts] Tidak ada map arena bawaan di dalam mod; arena dibiarkan kosong.");
            return 0;
        }

        int copied = 0;
        if (force || !hasChunks(target)) {
            copied += copyTree(source, target, WORLD_FOLDERS, true);
        }
        // Data dimensi selalu dilengkapi, walau chunk-nya sudah ada.
        copied += copyTree(source, target, DATA_FOLDERS, force);
        if (copied > 0) {
            JackOfHeartsMod.LOGGER.info("[JackOfHearts] Map arena dipasang: {} berkas ke {}", copied, target);
        }
        return copied;
    }

    /**
     * Menyalin dimensi arena yang sekarang ke {@code config/jackofhearts/arena_world/},
     * siap dipindahkan ke folder sumber mod.
     *
     * <p>Dunianya disimpan dulu. Chunk yang baru dibangun masih ada di memori sampai
     * server menyimpannya, dan menyalin berkas region tanpa itu menghasilkan map yang
     * tertinggal beberapa menit dari yang barusan dibangun.</p>
     *
     * @return jumlah berkas yang disalin, atau -1 kalau dimensinya tidak ada.
     */
    public static int export(MinecraftServer server) {
        ServerLevel level = Arena.level(server);
        if (level == null) {
            return -1;
        }
        level.save(null, true, false);

        Path source = folder(server);
        Path target = FMLPaths.CONFIGDIR.get().resolve(JackOfHeartsMod.MOD_ID).resolve("arena_world");
        List<String> all = new ArrayList<>(WORLD_FOLDERS);
        all.addAll(DATA_FOLDERS);
        int copied = copyTree(source, target, all, true);
        JackOfHeartsMod.LOGGER.info("[JackOfHearts] Map arena disalin: {} berkas ke {}", copied, target);
        return copied;
    }

    /** Ada berkas region di situ, berarti dimensinya sudah punya isi sendiri. */
    private static boolean hasChunks(Path folder) {
        Path region = folder.resolve("region");
        if (!Files.isDirectory(region)) {
            return false;
        }
        try (Stream<Path> files = Files.list(region)) {
            return files.anyMatch(path -> path.getFileName().toString().endsWith(".mca"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Folder dunia bawaan di dalam jar.
     *
     * <p>Dibaca lewat {@code findResource} milik Forge, bukan {@code getResourceAsStream}:
     * yang dibutuhkan seluruh isi folder, dan aliran berkas tidak bisa didaftar. Yang
     * dikembalikan {@code Path} di dalam sistem berkas jar-nya, jadi bisa ditelusuri
     * seperti folder biasa — termasuk saat dijalankan dari folder kelas di lingkungan
     * pengembangan.</p>
     */
    @Nullable
    private static Path bundled() {
        var info = ModList.get().getModFileById(JackOfHeartsMod.MOD_ID);
        if (info == null) {
            return null;
        }
        IModFile file = info.getFile();
        Path path = file.findResource(BUNDLED);
        return Files.exists(path) ? path : null;
    }

    private static int copyTree(Path source, Path target, List<String> folders, boolean overwrite) {
        int copied = 0;
        for (String name : folders) {
            Path from = source.resolve(name);
            if (!Files.isDirectory(from)) {
                continue;
            }
            copied += copyFolder(from, target.resolve(name), overwrite);
        }
        return copied;
    }

    /**
     * Menyalin satu folder apa adanya.
     *
     * <p>Sumbernya bisa berada di sistem berkas yang berbeda dengan tujuannya (di dalam
     * jar), jadi jalurnya tidak boleh dipakai bersilangan: nama berkasnya diambil sebagai
     * teks, bukan sebagai {@code Path} yang di-resolve lintas sistem berkas.</p>
     */
    private static int copyFolder(Path from, Path to, boolean overwrite) {
        int copied = 0;
        try {
            Files.createDirectories(to);
            try (Stream<Path> files = Files.walk(from)) {
                for (Path path : files.toList()) {
                    Path relative = from.relativize(path);
                    Path destination = to;
                    for (Path part : relative) {
                        destination = destination.resolve(part.toString());
                    }
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                        continue;
                    }
                    if (!overwrite && Files.exists(destination)) {
                        continue;
                    }
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
        } catch (IOException e) {
            JackOfHeartsMod.LOGGER.error("[JackOfHearts] Gagal menyalin {} ke {}", from, to, e);
        }
        return copied;
    }
}
