package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.config.JackConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Dimensi arena: dunia tersendiri tempat permainan berlangsung.
 *
 * <h2>Kenapa dimensi sendiri, bukan sepetak tanah di overworld</h2>
 *
 * <p>Map permainan ini punya aturan yang bertabrakan dengan dunia biasa: pintu yang
 * mengunci sendiri, wadah yang cuma bisa dibuka di fase tertentu, dan semua peti yang
 * ditandai jadi tempat susu. Menaruhnya di overworld berarti aturan-aturan itu harus
 * dijaga supaya tidak merembet ke bangunan pemain di luar arena. Dimensi terpisah
 * membuat batasnya jadi hal yang tidak perlu dijaga sama sekali.</p>
 *
 * <h2>Isinya dari mana</h2>
 *
 * <p>Dimensinya sendiri kosong melompong — generatornya {@code minecraft:flat} tanpa satu
 * lapis pun, alias void. Map-nya dipasang dari schematic yang ikut di dalam jar mod, dan
 * itu satu-satunya cara yang masuk akal untuk membungkus map ke dalam mod: berkas region
 * tidak bisa dijadikan dimensi tanpa generator buatan sendiri, sementara schematic sudah
 * punya jalur pemasangan yang sama dengan meja kafetaria.</p>
 */
public final class Arena {

    private Arena() {
    }

    /**
     * Batas ukuran schematic arena.
     *
     * <p>Jauh lebih longgar daripada batas meja kafetaria, karena isinya memang seluruh
     * map. Pemasangannya berjalan di satu tick, jadi map sebesar ini akan membekukan
     * server beberapa detik — itu sebabnya pemasangannya perintah tersendiri yang
     * dijalankan sekali saat menyiapkan dunia, bukan sesuatu yang jalan tiap permainan.
     */
    public static final int MAX_BLOCKS = 5_000_000;

    /** Kunci dimensi arena, dibaca dari config supaya map buatan sendiri tetap bisa dipakai. */
    public static ResourceKey<Level> key() {
        ResourceLocation id = ResourceLocation.tryParse(JackConfig.CONFIG.arenaDimension.get());
        return ResourceKey.create(Registries.DIMENSION,
                id == null ? new ResourceLocation(JackOfHeartsMod.MOD_ID, "arena") : id);
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(key());
    }

    /** Titik masuk arena, dan tempat semua orang dikumpulkan saat permainan dimulai. */
    public static BlockPos spawn() {
        String[] parts = JackConfig.CONFIG.arenaSpawn.get().trim().split("\\s+");
        if (parts.length != 3) {
            return BlockPos.ZERO;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return BlockPos.ZERO;
        }
    }

    /**
     * Pindahkan satu pemain ke arena.
     *
     * <p>Titik tengah blok, bukan sudutnya: pemain yang mendarat di sudut blok bisa
     * tersangkut di dinding kalau titiknya kebetulan menempel ke tembok.</p>
     *
     * @return false kalau dimensinya belum ada.
     */
    public static boolean send(ServerPlayer player) {
        ServerLevel level = level(player.server);
        if (level == null) {
            return false;
        }
        BlockPos spawn = spawn();
        player.teleportTo(level, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        return true;
    }

    /** Pasang map arena dari schematic. @return jumlah blok, atau -1 kalau gagal. */
    public static int paste(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) {
            return -1;
        }
        return Schematic.paste(level, JackConfig.CONFIG.arenaSchematic.get(),
                arenaOrigin(), MAX_BLOCKS);
    }

    /** Sudut schematic arena dengan koordinat paling kecil. */
    public static BlockPos arenaOrigin() {
        String[] parts = JackConfig.CONFIG.arenaSchematicOrigin.get().trim().split("\\s+");
        if (parts.length != 3) {
            return BlockPos.ZERO;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return BlockPos.ZERO;
        }
    }
}
