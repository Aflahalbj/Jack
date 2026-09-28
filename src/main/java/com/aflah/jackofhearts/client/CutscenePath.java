package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.game.CutsceneTimeline;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Posisi dan arah kamera cutscene di satu titik waktu.
 *
 * <p>Waktunya kontinu (tick plus sisa frame), bukan per tick, supaya kameranya bisa
 * digambar di posisi yang tepat di tiap frame dan tidak tersendat 20 kali sedetik.</p>
 */
final class CutscenePath {

    private CutscenePath() {
    }

    record Pose(double x, double y, double z, float yaw, float pitch, float roll) {
        Vec3 position() {
            return new Vec3(x, y, z);
        }
    }

    /**
     * Titik kendali lengkungan babak naik.
     *
     * <p>Garis lurus dari bawah ke atas terbaca seperti lift, bukan seperti kamera. Dua
     * titik ini membuatnya naik lebih dulu, berayun sedikit ke samping, lalu mundur
     * menjauh dari arena — busur, bukan garis miring.</p>
     */
    private static final Vec3 RISE_C1 = CutsceneTimeline.START.add(9.0D, 13.0D, -4.0D);
    private static final Vec3 RISE_C2 = CutsceneTimeline.TOP.add(9.0D, 2.0D, -10.0D);

    /**
     * Seberapa jauh titik yang ditatap kamera. Selama kamera berayun ke samping,
     * arahnya ikut dikoreksi sedikit supaya arena tetap di tengah bingkai.
     */
    private static final double AIM_DISTANCE = 120.0D;
    /** Seberapa miring kamera saat berayun ke samping, per blok per tick. */
    private static final double BANK = 16.0D;

    /** Kamera tidak benar-benar diam selama logo tampil, cuma bergeser sangat pelan. */
    private static final double HOLD_DRIFT = 1.5D;
    /** Jarak maju di babak petir. */
    private static final double PUSH_DISTANCE = 24.0D;
    /** Kamera menunduk sedikit selama maju, seperti mendekat ke arena. */
    private static final float PUSH_END_PITCH = 13.0F;

    static Pose at(double t) {
        t = Mth.clamp(t, 0.0D, CutsceneTimeline.TOTAL_TICKS);
        int rise = CutsceneTimeline.RISE_TICKS;
        int hold = CutsceneTimeline.HOLD_TICKS;

        if (t <= rise) {
            double u = smoothstep(t / rise);
            Vec3 p = bezier(u);
            // Kemiringan dihitung dari kecepatan menyamping, jadi kamera "menikung"
            // mengikuti lengkungannya sendiri dan tegak lagi di ujungnya.
            double dx = bezier(smoothstep(Math.min(t + 0.5D, rise) / rise)).x
                    - bezier(smoothstep(Math.max(t - 0.5D, 0.0D) / rise)).x;
            float yaw = Mth.lerp((float) u, CutsceneTimeline.START_YAW, CutsceneTimeline.TOP_YAW)
                    - (float) Math.toDegrees(Math.atan2(p.x, AIM_DISTANCE));
            float pitch = Mth.lerp((float) u, CutsceneTimeline.START_PITCH, CutsceneTimeline.TOP_PITCH);
            return new Pose(p.x, p.y, p.z, yaw, pitch, (float) (dx * BANK));
        }

        Vec3 forward = forward(CutsceneTimeline.TOP_YAW, CutsceneTimeline.TOP_PITCH);
        if (t <= rise + hold) {
            double v = smoothstep((t - rise) / hold);
            Vec3 p = CutsceneTimeline.TOP.add(forward.scale(HOLD_DRIFT * v));
            return new Pose(p.x, p.y, p.z, CutsceneTimeline.TOP_YAW, CutsceneTimeline.TOP_PITCH, 0.0F);
        }

        // Mulai dari diam lalu makin cepat: "maju perlahan" di awal, dan di ujungnya
        // cukup cepat untuk terasa seperti ditarik masuk ke arena saat layarnya gelap.
        double v = (t - rise - hold) / CutsceneTimeline.PUSH_TICKS;
        Vec3 p = CutsceneTimeline.TOP.add(forward.scale(HOLD_DRIFT + PUSH_DISTANCE * v * v));
        float pitch = Mth.lerp((float) smoothstep(v), CutsceneTimeline.TOP_PITCH, PUSH_END_PITCH);
        return new Pose(p.x, p.y, p.z, CutsceneTimeline.TOP_YAW, pitch, 0.0F);
    }

    /** Arah pandang dari sepasang sudut, memakai rumus yang sama dengan vanilla. */
    static Vec3 forward(float yaw, float pitch) {
        float x = pitch * Mth.DEG_TO_RAD;
        float y = -yaw * Mth.DEG_TO_RAD;
        float cos = Mth.cos(x);
        return new Vec3(Mth.sin(y) * cos, -Mth.sin(x), Mth.cos(y) * cos);
    }

    private static Vec3 bezier(double u) {
        double a = (1.0D - u) * (1.0D - u) * (1.0D - u);
        double b = 3.0D * (1.0D - u) * (1.0D - u) * u;
        double c = 3.0D * (1.0D - u) * u * u;
        double d = u * u * u;
        Vec3 p0 = CutsceneTimeline.START;
        Vec3 p3 = CutsceneTimeline.TOP;
        return new Vec3(
                a * p0.x + b * RISE_C1.x + c * RISE_C2.x + d * p3.x,
                a * p0.y + b * RISE_C1.y + c * RISE_C2.y + d * p3.y,
                a * p0.z + b * RISE_C1.z + c * RISE_C2.z + d * p3.z);
    }

    static double smoothstep(double s) {
        s = Mth.clamp(s, 0.0D, 1.0D);
        return s * s * (3.0D - 2.0D * s);
    }
}
