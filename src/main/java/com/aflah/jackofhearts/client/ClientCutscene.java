package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.game.CutsceneTimeline;
import com.aflah.jackofhearts.network.CutscenePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Cutscene pembuka di sisi client: kamera, logo, petir, dan layar yang menggelap.
 *
 * <p>Kameranya entitas milik client sendiri yang tidak pernah masuk ke dunia — cuma
 * dijadikan sudut pandang lewat {@code setCameraEntity}. Posisinya dihitung dari
 * {@link CutscenePath} tiap tick, dan arahnya tiap frame, jadi geraknya sehalus frame
 * rate layar, bukan 20 kali sedetik seperti kamera yang digerakkan server.</p>
 *
 * <p>Petirnya juga cuma ada di client: tiap pemain melihat sambarannya sendiri, di
 * tempat yang sedikit berbeda. Tidak ada yang terbakar, tidak ada yang tersambar —
 * {@code visualOnly}, dan entitasnya tidak pernah ada di server.</p>
 *
 * <p>Selesainya selalu atas perintah server (lihat {@link CutscenePacket}). Kalau
 * hitungan client habis lebih dulu, layarnya bertahan gelap sampai perintah itu
 * datang.</p>
 */
@Mod.EventBusSubscriber(modid = JackOfHeartsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientCutscene {

    private ClientCutscene() {
    }

    private enum Stage {
        OFF,
        /** Layar hitam: badan pemain sedang dipindahkan, kameranya belum jalan. */
        PREPARE,
        PLAYING
    }

    private static final ResourceLocation LOGO =
            new ResourceLocation(JackOfHeartsMod.MOD_ID, "textures/gui/cutscene.png");
    private static final int LOGO_W = 326;
    private static final int LOGO_H = 138;
    /** Lebar logo terhadap lebar layar. Cukup besar untuk terbaca, tanpa menutupi arena. */
    private static final float LOGO_SCREEN_FRACTION = 0.32F;
    /** Tinggi pita hitam atas-bawah terhadap tinggi layar. */
    private static final float LETTERBOX = 0.085F;

    /**
     * Kapan petir menyambar, dihitung dari awal babak maju. Sedikit dan berjarak:
     * suasananya badai di kejauhan, bukan hujan petir. Jaraknya sengaja tidak rata —
     * sambaran yang datang berirama terbaca seperti metronom.
     */
    private static final int[] STRIKES = {12, 44, 71, 96};
    /** Seterang apa kilat putih di layar saat petir menyambar. Tipis: cukup terasa, tidak menyilaukan. */
    private static final float FLASH_STRENGTH = 0.18F;
    /** Satu dentuman tepat saat logonya muncul. */
    private static final int LOGO_STRIKE = CutsceneTimeline.RISE_TICKS + 4;

    /**
     * Batas menunggu perintah selesai dari server setelah hitungan client habis. Jaring
     * pengaman supaya pemain tidak terjebak di layar hitam kalau paketnya hilang.
     */
    private static final int END_TIMEOUT = 200;

    private static Stage stage = Stage.OFF;
    private static int ticks;
    @Nullable
    private static ArmorStand camera;
    private static float flash;
    private static float flashOld;
    /** Id entitas petir, negatif supaya tidak pernah bertabrakan dengan id dari server. */
    private static int nextBoltId = -424_242;
    private static final RandomSource RANDOM = RandomSource.create();

    public static boolean active() {
        return stage != Stage.OFF;
    }

    public static void accept(int action) {
        switch (action) {
            case CutscenePacket.PREPARE -> {
                stop();
                stage = Stage.PREPARE;
            }
            case CutscenePacket.PLAY -> {
                stage = Stage.PLAYING;
                ticks = 0;
                flash = 0.0F;
                flashOld = 0.0F;
                camera = null;
            }
            default -> stop();
        }
    }

    public static void reset() {
        stop();
    }

    private static void stop() {
        stage = Stage.OFF;
        ticks = 0;
        flash = 0.0F;
        flashOld = 0.0F;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && camera != null && minecraft.getCameraEntity() == camera) {
            minecraft.setCameraEntity(minecraft.player);
        }
        camera = null;
    }

    // ------------------------------------------------------------------ kamera

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage != Stage.PLAYING) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        // Dibuat ulang kalau dunianya berganti: pindah dimensi membuat ClientLevel baru,
        // dan kamera yang masih menunjuk dunia lama menggambar dari tempat yang salah.
        if (camera == null || camera.level() != minecraft.level) {
            camera = new ArmorStand(EntityType.ARMOR_STAND, minecraft.level);
            camera.setNoGravity(true);
            camera.setInvisible(true);
            place(ticks, ticks);
        }

        ticks++;
        if (ticks == LOGO_STRIKE) {
            strike(minecraft.level, 30.0D, 40.0D);
        }
        int push = ticks - CutsceneTimeline.RISE_TICKS - CutsceneTimeline.HOLD_TICKS;
        for (int at : STRIKES) {
            if (push == at) {
                strike(minecraft.level, 26.0D, 40.0D);
            }
        }
        flashOld = flash;
        flash *= 0.6F;

        place(ticks - 1, ticks);
        if (minecraft.getCameraEntity() != camera) {
            minecraft.setCameraEntity(camera);
        }
        if (ticks > CutsceneTimeline.TOTAL_TICKS + END_TIMEOUT) {
            stop();
        }
    }

    /**
     * Kamera ditaruh di antara dua titik waktu: Minecraft menggambar posisi di antara
     * posisi lama dan posisi baru sebuah entitas menurut sisa frame, jadi dua titik yang
     * berurutan inilah yang membuat geraknya kontinu.
     */
    private static void place(int from, int to) {
        if (camera == null) {
            return;
        }
        CutscenePath.Pose a = CutscenePath.at(from);
        CutscenePath.Pose b = CutscenePath.at(to);
        // Yang dijadwalkan titik MATA, sementara posisi entitas adalah kakinya.
        double eye = camera.getEyeHeight();
        camera.xo = camera.xOld = a.x();
        camera.yo = camera.yOld = a.y() - eye;
        camera.zo = camera.zOld = a.z();
        camera.setPos(b.x(), b.y() - eye, b.z());
        camera.yRotO = a.yaw();
        camera.xRotO = a.pitch();
        camera.setYRot(b.yaw());
        camera.setXRot(b.pitch());
        camera.yHeadRotO = a.yaw();
        camera.yHeadRot = b.yaw();
    }

    /**
     * Arah kamera dihitung ulang tiap frame dari waktu yang kontinu, lengkap dengan
     * kemiringan saat menikung. Tidak ada guncangan: petirnya cuma suasana, kameranya
     * tetap tenang.
     */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (stage != Stage.PLAYING || camera == null || minecraft.getCameraEntity() != camera) {
            return;
        }
        CutscenePath.Pose pose = CutscenePath.at(ticks - 1 + event.getPartialTick());
        event.setYaw(pose.yaw());
        event.setPitch(pose.pitch());
        event.setRoll(pose.roll());
    }

    // ------------------------------------------------------------------ petir

    /**
     * Satu sambaran di depan kamera.
     *
     * <p>Jaraknya dibatasi: vanilla cuma menggambar petir sampai 64 blok dari kamera,
     * dan kamera cutscene melayang puluhan blok di atas tanah — petir yang ditaruh lebih
     * jauh dari itu berbunyi tanpa pernah terlihat.</p>
     */
    private static void strike(ClientLevel level, double minDistance, double maxDistance) {
        CutscenePath.Pose pose = CutscenePath.at(ticks);
        Vec3 forward = CutscenePath.forward(pose.yaw(), 0.0F);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double distance = minDistance + RANDOM.nextDouble() * (maxDistance - minDistance);
        double side = (RANDOM.nextDouble() * 2.0D - 1.0D) * 26.0D;
        double x = pose.x() + forward.x * distance + right.x * side;
        double z = pose.z() + forward.z * distance + right.z * side;

        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        double y = ground > level.getMinBuildHeight() + 1 ? ground : pose.y() - 30.0D;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(x, y, z);
            bolt.setVisualOnly(true);
            bolt.setId(nextBoltId--);
            level.putNonPlayerEntity(bolt.getId(), bolt);
        }
        flash = 1.0F;
    }

    // ------------------------------------------------------------------ layar

    /**
     * Pita hitam, logo, kilat petir, dan layar yang memudar — digambar di atas segalanya,
     * sesudah semua overlay lain (yang selama cutscene memang dimatikan).
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (stage == Stage.OFF) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        if (stage == Stage.PREPARE) {
            graphics.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        float partial = event.getPartialTick();
        double t = Math.max(0.0D, ticks - 1 + partial);

        int bar = Math.round((float) CutscenePath.smoothstep(t / 30.0D) * height * LETTERBOX);
        if (bar > 0) {
            graphics.fill(0, 0, width, bar, 0xFF000000);
            graphics.fill(0, height - bar, width, height, 0xFF000000);
        }

        float logo = (float) CutscenePath.smoothstep((t - (CutsceneTimeline.RISE_TICKS - 12)) / 28.0D);
        if (logo > 0.01F) {
            drawLogo(graphics, width, height, logo, t);
        }

        float white = Mth.lerp(partial, flashOld, flash);
        if (white > 0.01F) {
            graphics.fill(0, 0, width, height, argb(white * FLASH_STRENGTH, 0xFFFFFF));
        }

        float black = 0.0F;
        if (t < CutsceneTimeline.FADE_IN_TICKS) {
            black = 1.0F - (float) (t / CutsceneTimeline.FADE_IN_TICKS);
        }
        double fadeOutStart = CutsceneTimeline.TOTAL_TICKS - CutsceneTimeline.FADE_OUT_TICKS;
        if (t > fadeOutStart) {
            black = Math.max(black, (float) ((t - fadeOutStart) / CutsceneTimeline.FADE_OUT_TICKS));
        }
        if (black > 0.0F) {
            graphics.fill(0, 0, width, height, argb(Math.min(black, 1.0F), 0x000000));
        }
    }

    /**
     * Logo di tengah layar, sedikit di atas titik tengah, dan membesar pelan-pelan
     * selama tampil supaya tidak terbaca sebagai gambar yang ditempel.
     *
     * <p>Logonya putih, jadi di bawahnya digambar bayangan gelap tipis: tanpa itu logo
     * putih hilang di atas langit yang terang atau saat kilat petir menyala.</p>
     */
    private static void drawLogo(GuiGraphics graphics, int width, int height, float alpha, double t) {
        float grow = 1.0F + 0.06F * (float) Mth.clamp((t - CutsceneTimeline.RISE_TICKS)
                / (CutsceneTimeline.HOLD_TICKS + CutsceneTimeline.PUSH_TICKS), 0.0D, 1.0D);
        float scale = width * LOGO_SCREEN_FRACTION / LOGO_W * grow;
        float cx = width / 2.0F;
        float cy = height / 2.0F - height * 0.04F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        graphics.setColor(0.0F, 0.0F, 0.0F, alpha * 0.55F);
        graphics.blit(LOGO, -LOGO_W / 2 + 2, -LOGO_H / 2 + 2, 0, 0.0F, 0.0F, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(LOGO, -LOGO_W / 2, -LOGO_H / 2, 0, 0.0F, 0.0F, LOGO_W, LOGO_H, LOGO_W, LOGO_H);

        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24) | rgb;
    }

    // ------------------------------------------------------------------ tangan pemain dikunci

    /** HUD, hotbar, dan chat dimatikan selama cutscene: yang tersisa cuma gambarnya. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (stage != Stage.OFF) {
            event.setCanceled(true);
        }
    }

    /**
     * Semua pemain disembunyikan, termasuk diri sendiri. Selama cutscene badan semua
     * peserta dititipkan di satu titik yang sama di tengah jalur kamera, dan kamera
     * yang lewat di situ akan menabrak kerumunan kepala melayang.
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (stage != Stage.OFF) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (stage != Stage.OFF) {
            event.setCanceled(true);
        }
    }

    /** Badan yang dititipkan tidak boleh ikut berjalan: chunk-nya dimuat di sekitar badan itu. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (stage == Stage.OFF) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /**
     * Klik ditelan. Badan pemain sedang di mode penonton, dan di mode itu klik ke entitas
     * berarti "masuk ke sudut pandangnya" — yang akan merebut kamera dari cutscene.
     */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (stage != Stage.OFF) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
