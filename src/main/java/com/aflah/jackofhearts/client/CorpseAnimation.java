package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.entity.CorpseEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;

import java.util.UUID;

/**
 * Animasi ambruknya mayat, dari kena tembak sampai diam tergeletak.
 *
 * <p>Semuanya keyframe yang ditulis tangan, bukan ragdoll fisika — Minecraft tidak
 * punya itu. Tiga hal yang membedakan ini dari sekadar "memutar badan 90 derajat":</p>
 *
 * <ol>
 *   <li><b>Kurvanya bukan garis lurus.</b> Interpolasi linear itu sumber utama rasa
 *       robotik. Hentakan awalnya naik mendadak, robohnya makin cepat seperti ditarik
 *       gravitasi, dan mendaratnya kelewat sedikit lalu balik dan diam.</li>
 *   <li><b>Anggota badannya telat-telatan.</b> Tabel pose yang sama dibaca pada waktu
 *       yang berbeda per bagian — badan memimpin, kepala telat sedikit, tangan lebih
 *       telat, kaki paling telat. Keterlambatan inilah yang membuat badannya terasa
 *       punya massa, bukan boneka kayu yang semua sendinya bergerak serempak.</li>
 *   <li><b>Ambruk, bukan tumbang.</b> Ada fase lutut luntur di antara hentakan dan
 *       robohnya. Kalau langsung roboh dari berdiri tegak, yang terbaca pohon ditebang,
 *       bukan orang tertembak.</li>
 * </ol>
 */
public final class CorpseAnimation {

    private CorpseAnimation() {
    }

    // Batas fase, dalam tick sejak mayatnya muncul.
    /** Hentakan tembakan. Mentah, tanpa perlambatan — ini yang membuat "kena" terbaca. */
    private static final float T_IMPACT = 3.0F;
    /** Lutut luntur: badan merosot dan torso melipat sebelum benar-benar roboh. */
    private static final float T_BUCKLE = 9.0F;
    /** Robohnya, makin cepat sampai badan rata dengan tanah. */
    private static final float T_FALL = 21.0F;
    /**
     * Mendarat dan mengendur. Sesudah ini mayatnya diam.
     *
     * <p>Diambil dari entitasnya, bukan ditulis ulang: server berhenti menambah umur
     * animasi tepat di angka itu. Kalau keduanya berbeda, animasinya bisa terpotong
     * sebelum selesai atau berhenti di pose yang belum sempat mengendur.</p>
     */
    private static final float T_SETTLE = CorpseEntity.ANIM_TICKS;

    // Kolom tabel pose. Semua dalam radian, mengikuti aturan HumanoidModel: xRot
    // positif memutar anggota badan ke depan/bawah, negatif ke belakang/atas.
    private static final int HEAD_X = 0;
    private static final int HEAD_Y = 1;
    private static final int HEAD_Z = 2;
    private static final int BODY_X = 3;
    private static final int BODY_Z = 4;
    private static final int R_ARM_X = 5;
    private static final int R_ARM_Z = 6;
    private static final int L_ARM_X = 7;
    private static final int L_ARM_Z = 8;
    private static final int R_LEG_X = 9;
    private static final int R_LEG_Z = 10;
    private static final int L_LEG_X = 11;
    private static final int L_LEG_Z = 12;
    private static final int COLUMNS = 13;

    /** Berdiri tegak, pose pemain biasa. */
    private static final float[] STAND = new float[COLUMNS];

    /** Kena: kepala nyentak ke belakang, dada membusur, tangan terlempar ke luar. */
    private static final float[] IMPACT = {
            -0.85F, 0.10F, 0.18F,
            -0.30F, 0.06F,
            -0.60F, 0.85F, -0.48F, -0.95F,
            0.14F, 0.05F, -0.10F, -0.04F,
    };

    /** Lutut luntur: dagu jatuh ke dada, torso melipat, paha maju, tangan lemas. */
    private static final float[] BUCKLE = {
            0.35F, 0.16F, 0.26F,
            0.55F, -0.10F,
            -0.12F, 0.38F, -0.05F, -0.30F,
            -0.78F, 0.20F, -0.55F, -0.14F,
    };

    /** Saat badan sudah rata: kepala terkulai ke belakang, tangan tertinggal di udara. */
    private static final float[] TOPPLED = {
            -0.28F, 0.22F, 0.34F,
            0.22F, -0.06F,
            0.48F, 0.30F, 0.62F, -0.22F,
            -0.30F, 0.12F, -0.12F, -0.20F,
    };

    /** Diam: tumpukan yang sengaja tidak simetris kiri-kanan. */
    private static final float[] SETTLE = {
            0.10F, 0.30F, 0.52F,
            0.10F, -0.14F,
            0.20F, 0.62F, 0.85F, -0.14F,
            -0.16F, 0.26F, 0.04F, -0.06F,
    };

    /**
     * Seberapa jauh setiap bagian tertinggal dari badan, dalam tick. Badan memimpin
     * karena dialah yang didorong lasernya; sisanya menyusul.
     */
    private static final float HEAD_LAG = 1.2F;
    private static final float ARM_LAG = 2.0F;
    private static final float LEG_LAG = 3.0F;

    /**
     * Setengah tebal badan pemain dalam blok. Setelah roboh, sumbu tegak modelnya jadi
     * mendatar dan yang tegak justru sisi kiri-kanannya — tanpa diangkat sebanyak ini
     * separuh badannya tertanam di dalam lantai.
     */
    private static final float LYING_LIFT = 0.28F;
    /** Sedalam apa badan merosot saat lututnya luntur. */
    private static final float BUCKLE_SINK = 0.26F;

    /**
     * Sudut roboh dalam derajat: 0 berdiri, 90 rata dengan tanah.
     *
     * <p>Sengaja kelewat sampai 96 derajat di ujung fase jatuh lalu dipantulkan balik
     * ke 90 dengan getaran yang meredam. Berhenti tepat di 90 tanpa lewat sama sekali
     * terlihat seperti animasi yang dipotong, bukan badan yang membentur tanah.</p>
     */
    public static float fallDegrees(float t) {
        if (t <= T_IMPACT + 2.0F) {
            return 0.0F;
        }
        if (t < T_BUCKLE) {
            // Miring sedikit selama lutut luntur, jadi robohnya tidak mulai mendadak.
            float x = (t - (T_IMPACT + 2.0F)) / (T_BUCKLE - (T_IMPACT + 2.0F));
            return 8.0F * x * x;
        }
        if (t < T_FALL) {
            float x = (t - T_BUCKLE) / (T_FALL - T_BUCKLE);
            return 8.0F + 88.0F * easeIn(x);
        }
        float x = (t - T_FALL) / (T_SETTLE - T_FALL);
        return 90.0F + 6.0F * Mth.cos(x * Mth.PI * 2.2F) * (float) Math.exp(-4.0D * x);
    }

    /**
     * Naik-turunnya seluruh badan, dalam blok. Negatif saat lututnya luntur, lalu
     * berbalik positif begitu badannya rebah dan perlu diangkat dari dalam lantai.
     */
    public static float bodyOffsetY(float t) {
        float lift = LYING_LIFT * Mth.sin(fallDegrees(t) * Mth.DEG_TO_RAD);
        return lift - sink(t);
    }

    private static float sink(float t) {
        if (t <= T_IMPACT) {
            return 0.0F;
        }
        if (t < T_BUCKLE) {
            return BUCKLE_SINK * easeOut((t - T_IMPACT) / (T_BUCKLE - T_IMPACT));
        }
        if (t < T_FALL) {
            // Merosotnya larut sendiri begitu badannya berputar rebah.
            return Mth.lerp(easeIn((t - T_BUCKLE) / (T_FALL - T_BUCKLE)), BUCKLE_SINK, 0.02F);
        }
        return 0.02F;
    }

    /**
     * Pasang pose ke semua sendi model. Bagian badan dibaca pada waktu yang berbeda
     * supaya ada keterlambatan antar anggota badan.
     *
     * @param variance nilai -1..1 khas per mayat, dipakai memiringkan sisi kiri dan
     *                 kanan sedikit berbeda supaya dua mayat tidak pernah persis sama.
     */
    public static void pose(HumanoidModel<?> model, float t, float variance) {
        float[] body = evaluate(t);
        float[] head = evaluate(t - HEAD_LAG);
        float[] arms = evaluate(t - ARM_LAG);
        float[] legs = evaluate(t - LEG_LAG);

        float right = 1.0F + 0.22F * variance;
        float left = 1.0F - 0.22F * variance;

        model.head.xRot = head[HEAD_X];
        model.head.yRot = head[HEAD_Y] * (1.0F + 0.35F * variance);
        model.head.zRot = head[HEAD_Z] * right;

        model.body.xRot = body[BODY_X];
        model.body.yRot = 0.0F;
        model.body.zRot = body[BODY_Z] * right;

        model.rightArm.xRot = arms[R_ARM_X];
        model.rightArm.yRot = 0.0F;
        model.rightArm.zRot = arms[R_ARM_Z] * right;

        model.leftArm.xRot = arms[L_ARM_X];
        model.leftArm.yRot = 0.0F;
        model.leftArm.zRot = arms[L_ARM_Z] * left;

        model.rightLeg.xRot = legs[R_LEG_X];
        model.rightLeg.yRot = 0.0F;
        model.rightLeg.zRot = legs[R_LEG_Z] * right;

        model.leftLeg.xRot = legs[L_LEG_X];
        model.leftLeg.yRot = 0.0F;
        model.leftLeg.zRot = legs[L_LEG_Z] * left;
    }

    /** Nilai khas per mayat, diturunkan dari UUID pemiliknya supaya tetap sama tiap frame. */
    public static float variance(UUID uuid) {
        return ((Math.abs(uuid.hashCode()) % 2001) / 1000.0F) - 1.0F;
    }

    private static float[] evaluate(float t) {
        if (t <= 0.0F) {
            return STAND;
        }
        if (t >= T_SETTLE) {
            return SETTLE;
        }
        if (t < T_IMPACT) {
            return blend(STAND, IMPACT, snap(t / T_IMPACT));
        }
        if (t < T_BUCKLE) {
            return blend(IMPACT, BUCKLE, easeOut((t - T_IMPACT) / (T_BUCKLE - T_IMPACT)));
        }
        if (t < T_FALL) {
            return blend(BUCKLE, TOPPLED, easeIn((t - T_BUCKLE) / (T_FALL - T_BUCKLE)));
        }
        return blend(TOPPLED, SETTLE, easeOut((t - T_FALL) / (T_SETTLE - T_FALL)));
    }

    private static float[] blend(float[] from, float[] to, float x) {
        float[] out = new float[COLUMNS];
        for (int i = 0; i < COLUMNS; i++) {
            out[i] = Mth.lerp(x, from[i], to[i]);
        }
        return out;
    }

    /** Naik mendadak lalu melambat. Dipakai untuk hentakan tembakan. */
    private static float snap(float x) {
        return Mth.sin(Mth.clamp(x, 0.0F, 1.0F) * Mth.HALF_PI);
    }

    /** Mulai cepat, berhenti pelan. */
    private static float easeOut(float x) {
        float c = Mth.clamp(x, 0.0F, 1.0F);
        return 1.0F - (1.0F - c) * (1.0F - c);
    }

    /** Mulai pelan, makin cepat — inilah rasa ditarik gravitasi. */
    private static float easeIn(float x) {
        float c = Mth.clamp(x, 0.0F, 1.0F);
        return c * c;
    }
}
