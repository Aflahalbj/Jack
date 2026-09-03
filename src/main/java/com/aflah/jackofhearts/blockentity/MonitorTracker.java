package com.aflah.jackofhearts.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Posisi Papan Peserta yang sedang termuat di server, per dimensi.
 *
 * <p>Dipakai untuk menempatkan kamera yang mengarahkan semua orang ke papan setelah
 * eksekusi. Server tidak punya cara murah untuk mencari satu blok di dunia yang
 * luasnya tak terbatas, jadi papannya mencatatkan diri saat dimuat.</p>
 *
 * <p>Batasnya jelas dan disengaja: papan yang chunk-nya tidak termuat tidak ada di
 * sini. Untuk keperluan ini justru benar — papan di ruangan yang tidak ditempati
 * siapa pun memang tidak perlu dijadikan sasaran kamera.</p>
 */
public final class MonitorTracker {

    private MonitorTracker() {
    }

    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    public static void add(ResourceKey<Level> dimension, BlockPos pos) {
        LOADED.computeIfAbsent(dimension, key -> new LinkedHashSet<>()).add(pos.immutable());
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

    /** Papan terdekat dari satu titik di dimensi yang sama, atau null kalau tidak ada. */
    @Nullable
    public static BlockPos nearest(ResourceKey<Level> dimension, Vec3 from) {
        Set<BlockPos> set = LOADED.get(dimension);
        if (set == null || set.isEmpty()) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : set) {
            double d = from.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (d < bestDistance) {
                bestDistance = d;
                best = pos;
            }
        }
        return best;
    }
}
