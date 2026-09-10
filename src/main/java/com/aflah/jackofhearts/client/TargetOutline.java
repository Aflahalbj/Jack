package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.game.Skill;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Kotak di sekeliling orang yang akan kena skill, digambar cuma di layar Jack.
 *
 * <p>Ini bukan hiasan. Skill mengenai orang terdekat, dan di ruangan yang ramai
 * "terdekat" bisa berpindah dari satu orang ke orang lain hanya karena bahu yang
 * bergeser sedikit. Tanpa kotak ini Jack menekan tombolnya sambil menebak, dan jatah
 * yang cuma dua kali itu habis ke orang yang salah.</p>
 *
 * <p>Sasarannya dipilih client (lihat {@link ClientSkills}), dan id yang sama itu yang
 * dikirim saat tombolnya ditekan — jadi yang dikotaki dan yang kena selalu orang yang
 * sama.</p>
 *
 * <p>Piring yang akan diracuni ikut digaris, ungu. Perlu, bukan pelengkap: selama
 * permainan semua peserta di mode adventure, dan di mode itu Minecraft tidak menggambar
 * garis blok yang ditatap sama sekali untuk blok yang tidak bisa dihancurkan — tanpa
 * garis ini Jack tidak pernah tahu piring mana yang sedang ditunjuk crosshair-nya.</p>
 */
@Mod.EventBusSubscriber(modid = JackOfHeartsMod.MOD_ID, value = Dist.CLIENT)
public final class TargetOutline {

    private TargetOutline() {
    }

    /** Sedikit lebih besar dari badan sasaran, supaya garisnya tidak menempel di kulit. */
    private static final double INFLATE = 0.06D;
    /** Garis piring lebih rapat: piringnya kecil, dan garis yang renggang terbaca sebagai kotak kosong. */
    private static final double FOOD_INFLATE = 0.02D;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !ClientSkills.active()) {
            return;
        }
        Player target = ClientSkills.target();
        BlockPos food = ClientSkills.foodTarget();
        if (target == null && food == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        Vec3 camera = event.getCamera().getPosition();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // Kerangka di tahap ini masih berpusat di kamera, jadi kotaknya digeser sendiri.
        pose.translate(-camera.x, -camera.y, -camera.z);

        if (target != null) {
            // Kotaknya digambar di posisi frame ini, bukan posisi tick terakhir: sasaran
            // yang sedang berjalan akan terlihat tertinggal dari badannya sendiri kalau
            // posisinya tidak diinterpolasi.
            float partialTick = event.getPartialTick();
            Vec3 at = new Vec3(
                    lerp(target.xOld, target.getX(), partialTick),
                    lerp(target.yOld, target.getY(), partialTick),
                    lerp(target.zOld, target.getZ(), partialTick));
            AABB box = target.getBoundingBox().move(at.subtract(target.position())).inflate(INFLATE);
            LevelRenderer.renderLineBox(pose, lines, box, 1.0F, 0.25F, 0.25F, 0.9F);
        }

        if (food != null && minecraft.level != null) {
            // Ungu terang - warna botol di ikon racun - kalau racunnya bisa dipakai
            // sekarang juga; kalau jatahnya habis atau sudah dipakai ronde ini, garisnya
            // tetap ada tapi kelabu. Piringnya tetap perlu ditunjukkan, cuma bukan
            // sebagai undangan.
            boolean ready = ClientSkills.ready(Skill.POISON, false);
            float r = ready ? 0.8F : 0.55F;
            float g = ready ? 0.35F : 0.55F;
            float b = ready ? 1.0F : 0.55F;
            BlockState state = minecraft.level.getBlockState(food);
            VoxelShape shape = state.getShape(minecraft.level, food,
                    minecraft.player == null ? CollisionContext.empty() : CollisionContext.of(minecraft.player));
            // Satu kotak pembungkus, bukan tiap potongan bentuknya: piring bertumpuk
            // tersusun dari banyak kotak kecil, dan menggaris semuanya jadi kusut.
            AABB box = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
            LevelRenderer.renderLineBox(pose, lines, box.move(food).inflate(FOOD_INFLATE), r, g, b, 1.0F);
        }

        pose.popPose();
        buffer.endBatch(RenderType.lines());
    }

    private static double lerp(double from, double to, float partialTick) {
        return from + (to - from) * partialTick;
    }
}
