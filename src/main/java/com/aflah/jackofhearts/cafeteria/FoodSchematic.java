package com.aflah.jackofhearts.cafeteria;

import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.game.Schematic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Meja makanan yang dipasang ulang dari schematic.
 *
 * <p>Kafetaria memulihkan mejanya sendiri dari potret yang dibaca berkala, dan itu cukup
 * untuk pemakaian biasa. Tapi potret cuma seakurat keadaan terakhir yang sempat terbaca:
 * meja yang telanjur diobrak-abrik sebelum potretnya sempat diperbarui akan dipulihkan ke
 * keadaan yang sama-sama berantakan. Schematic tidak punya masalah itu — isinya ditentukan
 * sekali oleh yang membangun mapnya, dan tidak pernah berubah lagi.</p>
 *
 * <p>Pemasangannya sendiri diurus {@link Schematic}; di sini cuma setelan yang mana dan
 * di mana.</p>
 */
public final class FoodSchematic {

    private FoodSchematic() {
    }

    /**
     * Batas ukuran schematic meja.
     *
     * <p>Sengaja ketat: yang dipasang cuma meja kafetaria, dan berkas yang jauh lebih
     * besar dari itu hampir pasti berkas yang salah — lebih baik ditolak daripada
     * membekukan server beberapa detik tiap kali permainan dimulai.</p>
     */
    private static final int MAX_BLOCKS = 200_000;

    public static Path folder() {
        return Schematic.folder();
    }

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

    /** @return jumlah blok yang dipasang, atau -1 kalau tidak ada yang dipasang. */
    public static int paste(ServerLevel level) {
        BlockPos origin = origin();
        if (origin == null) {
            return -1;
        }
        return Schematic.paste(level, JackConfig.CONFIG.foodSchematic.get(), origin, MAX_BLOCKS);
    }
}
