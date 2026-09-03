package com.aflah.jackofhearts.client;

import net.minecraft.client.model.PlayerModel;

import java.util.UUID;

/**
 * Pose potret di Papan Peserta: berdiri diam, dada ke atas, menghadap keluar layar.
 *
 * <p>Sama seperti {@link CorpseAnimation}, semua sendi diatur sendiri dan
 * {@code setupAnim} bawaan tidak pernah dipanggil — animasi vanilla akan membuat
 * potretnya ikut mengayun mengikuti jalan dan menoleh mengikuti kamera, padahal ini
 * foto, bukan orang yang sedang berdiri di ruangan.</p>
 *
 * <p>Kakinya disembunyikan, bukan cuma dibingkai keluar layar. Tidak ada pemotongan
 * gambar di render dunia — apa pun yang digambar akan terlihat, termasuk kaki yang
 * menjulur keluar dari panel ke panel di bawahnya.</p>
 *
 * <p>Setiap orang mendapat kemiringan kepala dan bukaan lengan yang sedikit berbeda,
 * diturunkan dari UUID-nya supaya tetap sama di setiap frame dan di setiap client.
 * Dua puluh lima potret dengan pose yang identik terbaca sebagai deretan kloningan,
 * bukan sebagai deretan orang.</p>
 *
 * <p><b>Semua variasinya sengaja sebidang</b> — memutar dan memiringkan di dalam
 * bidang papan, tidak pernah menoleh ke samping. Potretnya digepengkan supaya rata di
 * dinding, dan apa pun yang menoleh keluar bidang akan menampilkan sisi samping yang
 * ikut tergencet jadi garis. Mugshot memang menghadap lurus ke depan.</p>
 */
public final class PortraitPose {

    private PortraitPose() {
    }

    /** Ujung atas potret dalam satuan blok, diukur dari titik nol model (leher). */
    public static final float BUST_TOP = -0.52F;
    /** Ujung bawah potret: ujung lengan, bukan pinggang — lengannya menggantung lebih rendah. */
    public static final float BUST_BOTTOM = 0.90F;
    public static final float BUST_HEIGHT = BUST_BOTTOM - BUST_TOP;
    /** Lebar potret dari ujung lengan ke ujung lengan, disisakan sedikit untuk sudut serong. */
    public static final float BUST_WIDTH = 1.20F;

    public static void pose(PlayerModel<?> model, UUID uuid) {
        float v = variance(uuid);

        // yRot kepala sengaja dibiarkan nol: menoleh ke samping adalah satu-satunya
        // gerakan yang rusak begitu potretnya digepengkan.
        model.head.xRot = -0.05F + 0.07F * v;
        model.head.yRot = 0.0F;
        model.head.zRot = 0.07F * v;

        model.body.xRot = 0.0F;
        model.body.yRot = 0.0F;
        model.body.zRot = 0.0F;

        // Lengan menggantung, sedikit terbuka dari badan. Tanda positif dibalik antara
        // kanan dan kiri supaya keduanya membuka ke luar, bukan miring ke arah yang sama.
        model.rightArm.xRot = 0.03F;
        model.rightArm.yRot = 0.0F;
        model.rightArm.zRot = 0.08F + 0.03F * v;

        model.leftArm.xRot = 0.03F;
        model.leftArm.yRot = 0.0F;
        model.leftArm.zRot = -0.08F + 0.03F * v;

        model.rightLeg.xRot = 0.0F;
        model.leftLeg.xRot = 0.0F;

        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);

        model.rightLeg.visible = false;
        model.leftLeg.visible = false;
        model.rightPants.visible = false;
        model.leftPants.visible = false;
    }

    /** Nilai -1..1 yang tetap sama untuk UUID yang sama. */
    private static float variance(UUID uuid) {
        return ((Math.abs(uuid.hashCode()) % 2001) / 1000.0F) - 1.0F;
    }
}
