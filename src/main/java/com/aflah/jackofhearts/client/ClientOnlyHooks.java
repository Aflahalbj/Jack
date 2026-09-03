package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.game.Symbol;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Hanya dimuat di sisi client. Dipakai lewat guard {@code FMLEnvironment.dist == Dist.CLIENT}
 * dari kode common, jadi kelasnya tidak pernah di-load di dedicated server.
 */
public final class ClientOnlyHooks {

    private ClientOnlyHooks() {
    }

    /** True kalau entity ini adalah pemain lokal, yaitu orang yang tidak boleh tahu simbolnya. */
    public static boolean isLocalPlayer(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity != null && minecraft.player != null && entity.getUUID().equals(minecraft.player.getUUID());
    }

    /**
     * Simbol yang sedang ditampilkan selama animasi kocok di awal ronde, atau null
     * kalau tidak sedang mengocok. Bergantian tiap 2 tick supaya terbaca sebagai
     * simbol yang sedang diputar, bukan sekadar berkedip.
     */
    @Nullable
    public static String shufflingSymbolId() {
        if (ClientGameState.symbolShuffleTicks <= 0) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        long step = minecraft.level.getGameTime() / 2L;
        return Symbol.VALUES[(int) Math.floorMod(step, Symbol.VALUES.length)].getId();
    }
}
