package com.aflah.jackofhearts.client;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Posisi semua Papan Peserta yang sedang termuat di client.
 *
 * <p>Dipakai untuk satu hal saja: menentukan dari mana bunyi panel padam keluar.
 * Tanpa daftar ini, satu-satunya cara client tahu di mana papan berada adalah
 * menyisir seluruh block entity yang termuat setiap kali ada yang gugur, dan itu
 * pemborosan untuk informasi yang bisa dicatat sekali saat papannya dimuat.</p>
 *
 * <p>Isinya diurus {@link com.aflah.jackofhearts.blockentity.MonitorBlockEntity}
 * sendiri saat dimuat dan dibuang, jadi papan yang chunk-nya keluar dari jangkauan
 * ikut hilang dari sini dan tidak lagi berbunyi.</p>
 */
public final class ClientMonitors {

    private ClientMonitors() {
    }

    private static final Set<BlockPos> LOADED = new LinkedHashSet<>();

    public static void add(BlockPos pos) {
        LOADED.add(pos.immutable());
    }

    public static void remove(BlockPos pos) {
        LOADED.remove(pos);
    }

    public static void clear() {
        LOADED.clear();
    }

    public static Set<BlockPos> loaded() {
        return LOADED;
    }
}
