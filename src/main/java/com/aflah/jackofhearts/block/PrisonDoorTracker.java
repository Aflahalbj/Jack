package com.aflah.jackofhearts.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Posisi Pintu Sel yang sedang termuat di server, per dimensi.
 *
 * <p>Mesin game perlu mengunci <i>semua</i> pintu sel begitu fase berganti, dan
 * server tidak punya cara murah untuk mencari satu blok di dunia yang luasnya tak
 * terbatas. Jadi pintunya yang mencatatkan diri: saat dipasang, dan saat chunk-nya
 * dimuat. Polanya sama dengan {@link com.aflah.jackofhearts.blockentity.MonitorTracker},
 * cuma sumber pencatatannya beda — pintu tidak punya block entity.</p>
 *
 * <p>Yang dicatat cuma <b>separuh bawah</b> pintu. Separuh atas selalu ikut lewat
 * penulisan dua separuh di {@link PrisonDoorBlock}, jadi mencatat keduanya cuma
 * bikin setiap pintu diproses dua kali.</p>
 */
public final class PrisonDoorTracker {

    private PrisonDoorTracker() {
    }

    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    /**
     * Pintu yang baru saja tercatat dan belum pernah disetel mengikuti fase.
     *
     * <p>Ada antrean terpisah karena pintu bisa muncul kapan saja — dipasang orang,
     * atau chunk-nya baru dimuat — sementara penyetelannya harus dikerjakan dari
     * tick server, bukan dari tengah proses pemuatan chunk.</p>
     *
     * <p>Isinya sengaja <b>cuma pintu yang benar-benar baru</b>. Kalau setiap
     * perubahan blok ikut masuk antrean, pintu yang barusan membanting dan mengunci
     * sendiri akan dibuka lagi sedetik kemudian oleh penyetelan fase.</p>
     */
    private static final List<PendingDoor> PENDING = new ArrayList<>();

    private record PendingDoor(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public static void add(ResourceKey<Level> dimension, BlockPos pos) {
        if (LOADED.computeIfAbsent(dimension, key -> new LinkedHashSet<>()).add(pos.immutable())) {
            PENDING.add(new PendingDoor(dimension, pos.immutable()));
        }
    }

    public static void remove(ResourceKey<Level> dimension, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(dimension);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) {
                LOADED.remove(dimension);
            }
        }
    }

    /** Chunk yang dilepas tidak lagi punya pintu yang bisa disentuh. */
    public static void forgetChunk(ResourceKey<Level> dimension, ChunkPos chunkPos) {
        Set<BlockPos> set = LOADED.get(dimension);
        if (set == null) {
            return;
        }
        set.removeIf(pos -> SectionPos.blockToSectionCoord(pos.getX()) == chunkPos.x
                && SectionPos.blockToSectionCoord(pos.getZ()) == chunkPos.z);
        if (set.isEmpty()) {
            LOADED.remove(dimension);
        }
    }

    /**
     * Catat pintu yang sudah ada di chunk yang baru dimuat.
     *
     * <p>Section yang tidak mungkin berisi pintu dilewati lewat {@code maybeHas},
     * yang membaca palet section — jadi chunk biasa tidak pernah benar-benar
     * ditelusuri per blok.</p>
     */
    public static void scanChunk(ServerLevel level, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(PrisonDoorTracker::isLowerHalf)) {
                continue;
            }
            int baseX = chunk.getPos().getMinBlockX();
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            int baseZ = chunk.getPos().getMinBlockZ();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (isLowerHalf(section.getBlockState(x, y, z))) {
                            add(level.dimension(), new BlockPos(baseX + x, baseY + y, baseZ + z));
                        }
                    }
                }
            }
        }
    }

    private static boolean isLowerHalf(BlockState state) {
        return state.getBlock() instanceof PrisonDoorBlock
                && state.getValue(PrisonDoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * Setel semua pintu yang termuat.
     *
     * @param locked keadaan yang diinginkan; dikirim apa adanya, bukan diturunkan dari
     *               fase. Yang berhak memutuskan boleh-tidaknya pintu terkunci cuma
     *               mesin game, dan aturannya bukan cuma "sedang fase menebak atau
     *               tidak" — sesudah fase menebak pun pintunya masih dibiarkan terbuka
     *               sampai ruangannya kosong.
     */
    public static void applyToAll(MinecraftServer server, boolean locked) {
        PENDING.clear();
        for (Map.Entry<ResourceKey<Level>, Set<BlockPos>> entry : LOADED.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            for (BlockPos pos : List.copyOf(entry.getValue())) {
                apply(level, pos, locked);
            }
        }
    }

    /** Setel pintu yang baru tercatat saja; tidak menyentuh pintu yang sudah ada. */
    public static void applyToNew(MinecraftServer server, boolean locked) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<PendingDoor> batch = List.copyOf(PENDING);
        PENDING.clear();
        for (PendingDoor door : batch) {
            ServerLevel level = server.getLevel(door.dimension());
            if (level != null) {
                apply(level, door.pos(), locked);
            }
        }
    }

    private static void apply(ServerLevel level, BlockPos pos, boolean locked) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PrisonDoorBlock door) {
            door.setLockedByGame(level, pos, state, locked);
        } else {
            remove(level.dimension(), pos);
        }
    }
}
