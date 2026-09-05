package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.cafeteria.CafeteriaFood;
import com.aflah.jackofhearts.network.CafeteriaPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * Sisi client kafetaria: papan angka di atas meja, dan pencegahan tebakan client.
 *
 * <p>Papannya <b>digambar</b>, bukan dipasang sebagai entitas teks. Angkanya berubah
 * tiap kali ada yang mengambil makanan, dan entitas teks berarti ada benda sungguhan
 * yang harus dijaga tetap hidup, ikut tersimpan ke dunia, dan tertinggal di map kalau
 * server mati di tengah game.</p>
 *
 * <p>Sengaja <b>tidak</b> tembus tembok, dan cuma terlihat sampai 48 blok. Papan ini
 * perabot kafetaria, bukan penunjuk arah: kalau tembus tembok, satu angka melayang di
 * seberang map jadi memberi tahu semua orang di mana kafetarianya - termasuk Jack yang
 * sedang mencari mangsa.</p>
 */
@Mod.EventBusSubscriber(modid = JackOfHeartsMod.MOD_ID, value = Dist.CLIENT)
public class ClientCafeteria {

    /** Jarak terjauh papan masih digambar, dalam blok. */
    private static final double VIEW_RANGE = 48.0D;

    /** Skala teks angka. Satu piksel font = nilai ini blok. */
    private static final float COUNT_SCALE = 0.04F;
    private static final float LABEL_SCALE = 0.022F;
    /** Jarak baris nama di atas angka, dalam blok. */
    private static final double LABEL_OFFSET = 0.34D;
    /** Tinggi papan di atas petak teratas kotak kafetaria. */
    private static final double ANCHOR_HEIGHT = 1.4D;

    private static boolean active;
    private static int stock;
    private static int total;
    @Nullable
    private static ResourceLocation dimension;
    private static BlockPos min = BlockPos.ZERO;
    private static BlockPos max = BlockPos.ZERO;

    public static void update(CafeteriaPacket packet) {
        active = packet.active();
        stock = packet.stock();
        total = packet.total();
        dimension = packet.dimension();
        min = packet.min();
        max = packet.max();
    }

    public static void reset() {
        active = false;
        stock = 0;
        total = 0;
        dimension = null;
    }

    /**
     * true kalau klik kanan di sini harus <b>dihentikan di client juga</b>.
     *
     * <p>Ini bukan hiasan, ini inti perbaikan satu kelas bug: membatalkan interaksi
     * cuma di server tidak menghentikan tebakan client. Client tetap menjalankan
     * {@code use()} bloknya sendiri, dan mod makanan menjawab tebakan itu dengan
     * memindahkan makanan dari tangan ke tumpukan. Server tidak melakukan apa-apa, jadi
     * tidak ada satu pun paket koreksi yang dikirim - di layar barangnya hilang dari tas
     * <i>dan</i> hilang dari meja, padahal di server tidak terjadi apa pun.</p>
     *
     * <p>Yang dipakai kotak dari paket, bukan dari config client. Config ini bertipe
     * COMMON: di server terpisah, berkas config client adalah miliknya sendiri dan
     * isinya belum tentu sama.</p>
     */
    public static boolean intercepts(Level level, BlockPos pos) {
        if (!active || dimension == null || !level.dimension().location().equals(dimension)) {
            return false;
        }
        if (pos.getX() < min.getX() || pos.getX() > max.getX()
                || pos.getY() < min.getY() || pos.getY() > max.getY()
                || pos.getZ() < min.getZ() || pos.getZ() > max.getZ()) {
            return false;
        }
        return CafeteriaFood.isFoodBlock(level.getBlockState(pos));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !active || dimension == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(dimension)) {
            return;
        }

        Vec3 anchor = new Vec3(
                (min.getX() + max.getX()) / 2.0D + 0.5D,
                max.getY() + ANCHOR_HEIGHT,
                (min.getZ() + max.getZ()) / 2.0D + 0.5D);
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        if (anchor.distanceToSqr(eye) > VIEW_RANGE * VIEW_RANGE) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        String count = stock + "/" + total;
        Component label = stock > 0
                ? Component.translatable("hud.jackofhearts.cafeteria")
                : Component.translatable("hud.jackofhearts.cafeteria_empty");

        drawLine(minecraft, pose, camera, eye, anchor, 0.0D, COUNT_SCALE, count, colorOf(), buffer);
        drawLine(minecraft, pose, camera, eye, anchor, LABEL_OFFSET, LABEL_SCALE,
                label.getString(), 0xAAAAAA, buffer);
        buffer.endBatch();
    }

    /** Merah kalau tinggal sedikit: yang bikin orang bergegas ke kafetaria warnanya, bukan angkanya. */
    private static int colorOf() {
        if (stock <= 0) {
            return 0xFF5555;
        }
        float left = total <= 0 ? 0.0F : (float) stock / total;
        if (left <= 0.2F) {
            return 0xFF7043;
        }
        if (left <= 0.5F) {
            return 0xFFD24D;
        }
        return 0xFFFFFF;
    }

    private static void drawLine(Minecraft minecraft, PoseStack pose, Camera camera, Vec3 eye,
                                 Vec3 anchor, double lift, float scale, String text, int color,
                                 MultiBufferSource buffer) {
        Font font = minecraft.font;
        pose.pushPose();
        pose.translate(anchor.x - eye.x, anchor.y + lift - eye.y, anchor.z - eye.z);
        // Menghadap kamera; skalanya negatif di X dan Y karena sumbu teks terbalik
        // terhadap sumbu dunia.
        pose.mulPose(camera.rotation());
        pose.scale(-scale, -scale, scale);
        Matrix4f matrix = pose.last().pose();
        float background = minecraft.options.getBackgroundOpacity(0.25F);
        int backgroundColor = (int) (background * 255.0F) << 24;
        font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, color, false, matrix, buffer,
                Font.DisplayMode.NORMAL, backgroundColor, LightTexture.pack(15, 15));
        pose.popPose();
    }

    private ClientCafeteria() {
    }
}
