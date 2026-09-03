package com.aflah.jackofhearts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Mengganti sudut pandang lokal tanpa memindahkan pemain.
 *
 * <p>{@code Minecraft.setCameraEntity} hanya menyetel satu field di client, jadi
 * badan pemain tidak tersentuh sama sekali.</p>
 */
public final class ClientCameraOverride {

    /** Berapa lama menunggu entitas kameranya sampai di client sebelum menyerah. */
    private static final int PENDING_TIMEOUT = 60;

    private static int pendingId = -1;
    private static int pendingTicks;

    private ClientCameraOverride() {
    }

    public static void set(int entityId) {
        if (entityId < 0) {
            pendingId = -1;
            restore();
            return;
        }
        // Paket ini bisa tiba sebelum entitas kameranya sendiri: server mengirim
        // paket mod segera, sedangkan spawn entitas baru dikirim di akhir tick.
        // Jadi kalau belum ada, dicoba lagi tiap tick.
        pendingId = entityId;
        pendingTicks = PENDING_TIMEOUT;
        tryApply();
    }

    public static void tick() {
        if (pendingId >= 0) {
            if (--pendingTicks <= 0) {
                pendingId = -1;
            } else {
                tryApply();
            }
        }

        // Jaring pengaman: kalau entitas kameranya hilang mendadak, jangan sampai
        // pemain terjebak melihat dari titik yang sudah tidak ada.
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        if (minecraft.player != null && camera != null && camera != minecraft.player
                && (camera.isRemoved() || !camera.isAlive())) {
            restore();
        }
    }

    private static void tryApply() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Entity camera = minecraft.level.getEntity(pendingId);
        if (camera != null) {
            minecraft.setCameraEntity(camera);
            pendingId = -1;
        }
    }

    public static void restore() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        }
    }
}
