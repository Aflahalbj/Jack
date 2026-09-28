package com.aflah.jackofhearts.game;

import net.minecraft.world.phys.Vec3;

/**
 * Jadwal cutscene pembuka: titik-titik kamera dan panjang tiap babaknya.
 *
 * <p>Dipakai dua sisi. Client menggerakkan kameranya sendiri menurut angka ini; server
 * cuma perlu tahu kapan cutscene-nya selesai supaya permainannya dimulai tepat sesudah
 * itu. Karena itu kelas ini tidak boleh menyentuh apa pun yang khusus client.</p>
 *
 * <p>Semua koordinat ada di dimensi arena.</p>
 *
 * <ol>
 *   <li><b>Naik</b> — dari dekat tanah naik dan mundur ke atas arena, lewat jalur yang
 *       melengkung, bukan garis lurus.</li>
 *   <li><b>Logo</b> — kamera nyaris diam di atas, logo muncul di tengah layar.</li>
 *   <li><b>Maju</b> — kamera maju perlahan ke arah arena sambil petir menyambar, lalu
 *       layarnya menggelap dan permainan dimulai.</li>
 * </ol>
 */
public final class CutsceneTimeline {

    private CutsceneTimeline() {
    }

    /** Titik mata kamera di awal. */
    public static final Vec3 START = new Vec3(0.0D, -2.270D, 115.166D);
    public static final float START_YAW = -179.6F;
    public static final float START_PITCH = -7.7F;

    /** Titik mata kamera di akhir babak naik, tempat logonya muncul. */
    public static final Vec3 TOP = new Vec3(0.0D, 35.0D, 145.0D);
    public static final float TOP_YAW = -179.6F;
    public static final float TOP_PITCH = 7.2F;

    /** Babak naik. */
    public static final int RISE_TICKS = 120;
    /** Babak logo. */
    public static final int HOLD_TICKS = 70;
    /** Babak maju dengan petir, termasuk layar yang menggelap di ujungnya. */
    public static final int PUSH_TICKS = 120;
    /** Layar hitam yang memudar di awal cutscene. */
    public static final int FADE_IN_TICKS = 20;
    /** Layar yang menggelap di ujung babak maju. */
    public static final int FADE_OUT_TICKS = 18;

    public static final int TOTAL_TICKS = RISE_TICKS + HOLD_TICKS + PUSH_TICKS;

    /**
     * Jeda setelah pemain dipindahkan ke arena sebelum kameranya mulai jalan.
     *
     * <p>Pindah dimensi membuat client memuat ulang dunianya. Kalau cutscene langsung
     * jalan, detik-detik pertamanya habis tertutup layar "memuat". Selama jeda ini layar
     * pemain dibuat hitam, jadi yang terlihat cuma satu gelap yang kemudian memudar.</p>
     */
    public static final int WARMUP_TICKS = 40;

    /**
     * Tempat badan pemain dititipkan selama cutscene: tengah-tengah jalur kamera.
     *
     * <p>Server cuma mengirim chunk di sekitar <b>badan</b> pemain, bukan di sekitar
     * kameranya. Badan yang tertinggal jauh dari kamera membuat kameranya melayang di atas
     * dunia yang belum termuat.</p>
     */
    public static final Vec3 BODY = new Vec3(0.0D, 16.0D, 131.0D);

    /**
     * Jam dunia selama cutscene: sama dengan {@code /time set night}. Petir di langit
     * terang cuma garis pucat; di langit malam dia yang menerangi arena.
     */
    public static final long NIGHT_TIME = 18000L;
}
