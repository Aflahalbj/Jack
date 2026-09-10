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
     * Simbol yang <b>terlihat</b> oleh pemain ini di punggung orang lain.
     *
     * <p>Selama mabuk, yang terbaca bukan simbol yang sebenarnya. Itulah seluruh isi
     * skill mabuk: permainan ini berputar pada satu hal — kamu tidak bisa melihat
     * kartumu sendiri, jadi kamu bergantung pada apa yang dibacakan orang lain. Orang
     * yang mabuk membacakan kartu yang salah dengan penuh percaya diri, dan racun
     * informasinya menyebar jauh lebih luas daripada satu orang yang dibuat pusing.</p>
     *
     * <p>Pergeserannya tetap, bukan diacak tiap frame: 1 sampai 3 langkah, diturunkan
     * dari UUID pemiliknya dan nomor ronde. Dua akibatnya sama-sama disengaja — simbol
     * yang salah itu <b>tidak pernah kebetulan benar</b>, dan dia <b>tidak berkedip</b>.
     * Kartu yang berganti-ganti sendiri akan langsung ketahuan palsu; kartu yang salah
     * tapi diam adalah kartu yang dipercaya.</p>
     */
    public static Symbol misread(Entity entity, Symbol real) {
        if (!ClientGameState.drunk || entity == null) {
            return real;
        }
        int shift = 1 + Math.floorMod(entity.getUUID().hashCode() * 31 + ClientGameState.round,
                Symbol.VALUES.length - 1);
        return Symbol.VALUES[(real.ordinal() + shift) % Symbol.VALUES.length];
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
