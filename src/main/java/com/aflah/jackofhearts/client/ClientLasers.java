package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.network.LaserPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Menyimpan dan menggambar laser eksekusi.
 *
 * <p>Lasernya <b>satu tembakan</b>, bukan sinar yang menyala terus. Selama mengunci
 * tidak ada garis sama sekali — yang terlihat hanya bola energi menumpuk di ujung
 * Terminal Tebakan. Begitu menembak, sinarnya meluncur: ujungnya memanjang cepat dari
 * terminal ke korban, menyala sesaat, lalu padam. Garis yang bertahan lama akan
 * terbaca sebagai beam penyembuh, bukan laser.</p>
 *
 * <p>Sinarnya quad pipih yang selalu menghadap kamera, bukan beam beacon: beam beacon
 * selalu kolom kotak yang jauh terlalu besar, dan tebalnya berubah tergantung sudut
 * pandang.</p>
 */
@Mod.EventBusSubscriber(modid = JackOfHeartsMod.MOD_ID, value = Dist.CLIENT)
public class ClientLasers {

    /** Lamanya ujung sinar menempuh jarak dari terminal ke korban. */
    private static final float TRAVEL_TICKS = 2.0F;
    /** Lamanya sinar penuh menyala setelah sampai. */
    private static final float HOLD_TICKS = 3.0F;
    /** Lamanya sinar padam setelah itu. */
    private static final float FADE_TICKS = 5.0F;

    /**
     * Setengah lebar inti sinar saat menembak, dalam blok. Satu piksel Minecraft =
     * 0.0625 blok, jadi 0.035 menghasilkan inti selebar sekitar satu piksel.
     */
    private static final float BEAM_WIDTH = 0.035F;
    /** Selubung cahaya digambar selebar ini kali inti. */
    private static final float GLOW_SCALE = 2.5F;
    /** Setengah ukuran bola energi di terminal pada puncak pengisian. */
    private static final float CHARGE_SIZE = 0.16F;
    /** Setengah ukuran kilatan di titik tumbukan. */
    private static final float IMPACT_SIZE = 0.40F;

    private static final float[] CORE_COLOR = {1.0F, 0.75F, 0.72F};
    private static final float[] GLOW_COLOR = {1.0F, 0.09F, 0.09F};

    private static final List<Laser> ACTIVE = new ArrayList<>();

    private static class Laser {
        final Vec3 from;
        final Vec3 fallback;
        final int victimId;
        final int chargeTicks;
        final int totalTicks;
        int age;

        Laser(LaserPacket packet) {
            this.from = packet.from();
            this.fallback = packet.fallback();
            this.victimId = packet.victimId();
            this.chargeTicks = packet.chargeTicks();
            this.totalTicks = packet.totalTicks();
        }

        /** Kepala korban pada frame ini; jatuh ke titik cadangan kalau entitasnya tidak ada. */
        Vec3 target(float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null && victimId >= 0) {
                Entity victim = minecraft.level.getEntity(victimId);
                if (victim != null && victim.isAlive()) {
                    return victim.getEyePosition(partialTick);
                }
            }
            return fallback;
        }
    }

    public static void add(LaserPacket packet) {
        ACTIVE.add(new Laser(packet));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            ACTIVE.clear();
            return;
        }
        ACTIVE.removeIf(laser -> ++laser.age > laser.totalTicks);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ACTIVE.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        float partialTick = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());

        pose.pushPose();
        // PoseStack di tahap ini masih berpusat di kamera, jadi posisi dunia
        // harus digeser sendiri.
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();

        for (Laser laser : ACTIVE) {
            float t = laser.age + partialTick;
            if (t < laser.chargeTicks) {
                renderCharging(consumer, matrix, camera, laser, t);
            } else {
                renderShot(consumer, matrix, camera, laser, t - laser.chargeTicks, partialTick);
            }
        }

        pose.popPose();
        buffer.endBatch(RenderType.lightning());
    }

    /** Bola energi menumpuk di ujung terminal. Belum ada sinar sama sekali di sini. */
    private static void renderCharging(VertexConsumer consumer, Matrix4f matrix, Vec3 camera,
                                       Laser laser, float t) {
        float progress = Mth.clamp(t / laser.chargeTicks, 0.0F, 1.0F);
        // Kedipnya makin rapat menjelang tembakan, jadi terasa menuju puncak.
        float flicker = 0.82F + 0.18F * Mth.sin(t * (0.35F + progress * 0.9F));
        float size = CHARGE_SIZE * progress * flicker;
        if (size <= 0.001F) {
            return;
        }
        drawGlow(consumer, matrix, camera, laser.from, size * 1.9F, GLOW_COLOR, 0.5F * progress);
        drawGlow(consumer, matrix, camera, laser.from, size, CORE_COLOR, 0.9F * progress);
    }

    /** Sinar meluncur dari terminal ke korban, menyala sesaat, lalu padam. */
    private static void renderShot(VertexConsumer consumer, Matrix4f matrix, Vec3 camera,
                                   Laser laser, float f, float partialTick) {
        if (f > TRAVEL_TICKS + HOLD_TICKS + FADE_TICKS) {
            return;
        }
        Vec3 to = laser.target(partialTick);

        // Ujung sinar memanjang cepat: inilah yang bikin terbaca sebagai tembakan,
        // bukan garis yang tiba-tiba sudah utuh.
        float reach = Mth.clamp(f / TRAVEL_TICKS, 0.0F, 1.0F);
        Vec3 tip = laser.from.add(to.subtract(laser.from).scale(reach));

        float alpha = 1.0F;
        if (f > TRAVEL_TICKS + HOLD_TICKS) {
            alpha = Mth.clamp(1.0F - (f - TRAVEL_TICKS - HOLD_TICKS) / FADE_TICKS, 0.0F, 1.0F);
        }
        if (alpha <= 0.0F) {
            return;
        }

        float width = BEAM_WIDTH * (0.55F + 0.45F * alpha);
        drawBeam(consumer, matrix, camera, laser.from, tip, width * GLOW_SCALE, GLOW_COLOR, alpha * 0.45F);
        drawBeam(consumer, matrix, camera, laser.from, tip, width, CORE_COLOR, alpha);

        // Kilatan di titik tumbukan, membesar sambil memudar.
        if (reach >= 1.0F) {
            float burst = Mth.clamp((f - TRAVEL_TICKS) / (HOLD_TICKS + FADE_TICKS), 0.0F, 1.0F);
            float size = IMPACT_SIZE * (0.35F + burst);
            drawGlow(consumer, matrix, camera, to, size, GLOW_COLOR, alpha * 0.5F);
            drawGlow(consumer, matrix, camera, to, size * 0.45F, CORE_COLOR, alpha * 0.9F);
        }
    }

    /**
     * Satu quad pipih yang menghadap kamera. Arah lebarnya dihitung dari perkalian
     * silang antara arah sinar dan arah ke kamera, jadi sinarnya tidak pernah
     * terlihat menipis jadi garis saat dilihat dari sudut tertentu.
     */
    private static void drawBeam(VertexConsumer consumer, Matrix4f matrix, Vec3 camera,
                                 Vec3 from, Vec3 to, float halfWidth, float[] color, float alpha) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0E-4) {
            return;
        }
        Vec3 dir = delta.scale(1.0 / length);

        Vec3 toCamera = camera.subtract(from.add(delta.scale(0.5)));
        Vec3 side = dir.cross(toCamera);
        if (side.lengthSqr() < 1.0E-8) {
            // Sinar menunjuk tepat ke kamera: arah lebarnya bebas, ambil sembarang tegak lurus.
            side = dir.cross(new Vec3(0.0, 1.0, 0.0));
            if (side.lengthSqr() < 1.0E-8) {
                side = new Vec3(1.0, 0.0, 0.0);
            }
        }
        side = side.normalize().scale(halfWidth);

        Vec3 a1 = from.subtract(side);
        Vec3 a2 = from.add(side);
        Vec3 b1 = to.subtract(side);
        Vec3 b2 = to.add(side);

        // Digambar dua kali dengan urutan terbalik supaya terlihat dari kedua sisi.
        quad(consumer, matrix, a1, b1, b2, a2, color, alpha);
        quad(consumer, matrix, a2, b2, b1, a1, color, alpha);
    }

    /** Kotak menghadap kamera, dipakai untuk bola pengisian dan kilatan tumbukan. */
    private static void drawGlow(VertexConsumer consumer, Matrix4f matrix, Vec3 camera,
                                 Vec3 center, float halfSize, float[] color, float alpha) {
        if (halfSize <= 0.001F || alpha <= 0.0F) {
            return;
        }
        Vec3 look = camera.subtract(center);
        if (look.lengthSqr() < 1.0E-8) {
            return;
        }
        look = look.normalize();

        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-8) {
            right = new Vec3(1.0, 0.0, 0.0);
        }
        right = right.normalize().scale(halfSize);
        Vec3 up = right.normalize().cross(look).normalize().scale(halfSize);

        Vec3 c1 = center.subtract(right).subtract(up);
        Vec3 c2 = center.subtract(right).add(up);
        Vec3 c3 = center.add(right).add(up);
        Vec3 c4 = center.add(right).subtract(up);

        quad(consumer, matrix, c1, c2, c3, c4, color, alpha);
        quad(consumer, matrix, c4, c3, c2, c1, color, alpha);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, float[] color, float alpha) {
        vertex(consumer, matrix, p1, color, alpha);
        vertex(consumer, matrix, p2, color, alpha);
        vertex(consumer, matrix, p3, color, alpha);
        vertex(consumer, matrix, p4, color, alpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 pos,
                               float[] color, float alpha) {
        consumer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(color[0], color[1], color[2], alpha)
                .endVertex();
    }
}
