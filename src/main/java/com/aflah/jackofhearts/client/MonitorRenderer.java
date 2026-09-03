package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.block.MonitorBlock;
import com.aflah.jackofhearts.blockentity.MonitorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Menggambar Papan Peserta: deretan potret semua peserta, yang gugur padam.
 *
 * <p>Papannya satu blok yang memproyeksikan bidang
 * {@value #BOARD_WIDTH}x{@value #BOARD_HEIGHT} di depannya. Blok itu sendiri adalah
 * sudut kiri-bawah bidang tersebut dilihat dari depan.</p>
 *
 * <p>Seluruh gambar dikerjakan di dalam satu kerangka koordinat papan: begitu
 * {@code PoseStack} diputar sekali di awal, sumbu X jadi arah kanan papan, Y arah
 * atas, dan Z keluar dari layar. Sesudah itu semua penempatan cuma soal dua angka,
 * dan tidak ada lagi urusan menghadap ke utara atau ke barat.</p>
 */
public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {

    private static final int BOARD_WIDTH = MonitorBlock.BOARD_WIDTH;
    private static final int BOARD_HEIGHT = MonitorBlock.BOARD_HEIGHT;

    /** Bahan quad panel: putih polos, warnanya sepenuhnya dari vertex color. */
    private static final ResourceLocation WHITE =
            new ResourceLocation(JackOfHeartsMod.MOD_ID, "textures/misc/white.png");

    /** Jarak bidang gambar dari muka blok, supaya tidak berkedip melawan permukaannya. */
    private static final float PLANE_OFFSET = 0.02F;
    // Ditumpuk dari belakang ke depan. Jaraknya kecil tapi cukup memisahkan lapisan
    // di depth buffer pada jarak pandang wajar.
    private static final float BACKDROP_Z = 0.0F;
    private static final float BORDER_Z = 0.004F;
    private static final float PANEL_Z = 0.008F;
    private static final float TEXT_Z = 0.026F;
    /**
     * Seberapa gepeng potretnya, sebagai pecahan dari tebal aslinya.
     *
     * <p>Yang diminta adalah foto, bukan patung. Modelnya tetap model tiga dimensi
     * dengan rig yang sama — cuma sumbu tebalnya yang dikempiskan, sehingga hasilnya
     * menempel rata di panel seperti kertas guntingan. Tidak perlu framebuffer, tidak
     * perlu layanan render dari internet, dan posenya tetap bisa diatur per sendi.</p>
     *
     * <p>Sengaja tidak nol. Skala nol membuat matriks normalnya tidak bisa dibalik,
     * dan muka depan dengan muka belakang jadi sebidang persis sehingga berkedip
     * berebut kedalaman. Pada 0,06 tebal sisanya di bawah seperempat piksel blok —
     * terbaca rata — tapi masih cukup untuk memisahkan keduanya di depth buffer.</p>
     */
    private static final float FLATTEN = 0.06F;
    /** Jarak potret dari bidang papan. Tetap, karena setelah digepengkan tebalnya dapat diabaikan. */
    private static final float PORTRAIT_Z = 0.014F;

    /**
     * Bentuk sel yang dituju saat menyusun kisi. Bukan batas keras — muat-tidaknya
     * potret dihitung ulang per sel — tapi angka inilah yang menentukan kisi mana yang
     * dipilih, dan potret dada ke atas plus satu baris nama memang lebih tinggi
     * daripada lebar.
     */
    private static final float ASPECT_W = 1.0F;
    private static final float ASPECT_H = 1.45F;
    /**
     * Batas atas ukuran sel.
     *
     * <p>Tanpa ini, dua peserta akan mendapat sel setinggi papan penuh dan potretnya
     * jadi setinggi empat setengah blok — wajah setinggi dua orang, yang terbaca
     * sebagai kesalahan, bukan sebagai papan yang lapang. Dengan batas ini papan yang
     * isinya sedikit tampil sebagai deretan kecil yang dipusatkan, dan itu memang
     * kenyataannya.</p>
     */
    private static final float MAX_SCALE = 1.30F;
    /**
     * Sisa ruang yang disengaja antara potret dan tepi panel.
     *
     * <p>Perhitungan muat-tidaknya memakai jangkauan model saat berdiri tegak, padahal
     * kepala yang dimiringkan dan lengan yang dibuka menjulur sedikit lebih jauh dari
     * itu. Menyusutkan hasilnya sedikit lebih murah dan lebih aman daripada menghitung
     * ulang jangkauan tiap pose — dan sisa ruangnya sendiri memang membuat potretnya
     * terlihat lebih rapi di dalam bingkai.</p>
     */
    private static final float PORTRAIT_FIT = 0.88F;

    /** Bagian bawah sel yang disisihkan untuk nama. */
    private static final float NAME_BAND = 0.24F;
    /** Tebal bingkai sel, sebagai pecahan dari lebar sel. */
    private static final float BORDER = 0.05F;
    /** Jarak antar sel, sebagai pecahan dari lebar sel. */
    private static final float GAP = 0.06F;

    /** Latar seluruh bidang papan, di belakang semua sel. */
    private static final float[] BACKDROP = {0.045F, 0.05F, 0.06F};
    private static final float[] PANEL_LIT = {0.10F, 0.13F, 0.17F};
    private static final float[] PANEL_DARK = {0.025F, 0.03F, 0.035F};
    private static final float[] BORDER_LIT = {0.25F, 0.31F, 0.38F};
    private static final float[] BORDER_DARK = {0.07F, 0.08F, 0.09F};
    /** Potret yang sudah padam masih tergambar, tapi tinggal bayangan. */
    private static final float PORTRAIT_DARK = 0.22F;
    private static final int NAME_LIT = 0xFFE8ECF2;
    private static final int NAME_DARK = 0xFF3B4249;

    private final PlayerModel<LivingEntity> wideModel;
    private final PlayerModel<LivingEntity> slimModel;
    private final Font font;

    public MonitorRenderer(BlockEntityRendererProvider.Context context) {
        this.wideModel = new PlayerModel<>(context.getModelSet().bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel<>(context.getModelSet().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.font = context.getFont();
    }

    /**
     * Papan menjulur sampai 13 blok ke samping dari bloknya. Tanpa ini papannya
     * menghilang begitu blok pengendalinya sendiri keluar layar, padahal separuh
     * papannya masih terlihat jelas.
     */
    @Override
    public boolean shouldRenderOffScreen(MonitorBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public void render(MonitorBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        List<ClientRoster.Slot> slots = ClientRoster.slots();

        BlockState state = be.getBlockState();
        if (!state.hasProperty(MonitorBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(MonitorBlock.FACING);
        Direction right = facing.getCounterClockWise();

        pose.pushPose();
        // Pindah ke sudut kiri-bawah papan, di muka depan blok.
        pose.translate(
                0.5D - right.getStepX() * 0.5D + facing.getStepX() * (0.5D + PLANE_OFFSET),
                0.0D,
                0.5D - right.getStepZ() * 0.5D + facing.getStepZ() * (0.5D + PLANE_OFFSET));
        // Setelah putaran ini: +X kanan papan, +Y atas papan, +Z keluar layar.
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));

        // Tiga lintasan terpisah, bukan satu lintasan per sel.
        //
        // MultiBufferSource menutup batch yang sedang berjalan begitu render type yang
        // diminta berganti. Kalau panel dan potret digambar berselang-seling, consumer
        // panel yang sudah diambil menjadi mati begitu potret pertama meminta tekstur
        // skin — dan sisa panelnya hilang tanpa error apa pun. Memisahkan lintasannya
        // membuat setiap render type dibuka satu kali saja.
        VertexConsumer panels = buffer.getBuffer(RenderType.entitySolid(WHITE));
        // Latar seluruh papan: menutupi dinding yang dibangun di belakangnya, sehingga
        // yang terlihat bidang layar utuh, bukan blok-blok yang kebetulan ada di situ.
        //
        // Digambar juga saat rosternya kosong. Papan yang menyala tanpa isi terbaca
        // sebagai mesin yang siap dan menunggu — dan itu memang keadaannya sebelum ada
        // yang mendaftar.
        rect(panels, pose.last(), 0.0F, 0.0F, BOARD_WIDTH, BOARD_HEIGHT, BACKDROP_Z, BACKDROP, light);

        if (!slots.isEmpty()) {
            Grid grid = Grid.forCount(slots.size());
            for (int i = 0; i < slots.size(); i++) {
                drawPanel(panels, pose.last(), cellOf(grid, i), slots.get(i).dark(), light);
            }
            for (int i = 0; i < slots.size(); i++) {
                drawPortrait(pose, buffer, slots.get(i), cellOf(grid, i), light);
            }
            for (int i = 0; i < slots.size(); i++) {
                drawName(pose, buffer, slots.get(i), cellOf(grid, i), light);
            }
        }

        pose.popPose();
    }

    /** Batas satu sel di kerangka papan, sudah termasuk jarak antar sel. */
    private record Cell(float x0, float y0, float x1, float y1, float border) {
        float ix0() {
            return x0 + border;
        }

        float iy0() {
            return y0 + border;
        }

        float ix1() {
            return x1 - border;
        }

        float iy1() {
            return y1 - border;
        }

        /** Batas atas pita nama, sekaligus dasar area potret. */
        float nameTop() {
            return iy0() + (iy1() - iy0()) * NAME_BAND;
        }
    }

    private static Cell cellOf(Grid grid, int index) {
        int row = index / grid.cols;
        int col = index % grid.cols;

        // Baris terakhir yang tidak penuh dipusatkan, bukan dibiarkan menggantung di
        // kiri — deretan yang rata tengah terbaca sebagai susunan, bukan sisa.
        int inRow = Math.min(grid.cols, grid.count - row * grid.cols);
        float rowShift = (grid.cols - inRow) * grid.cellW * 0.5F;

        float x0 = grid.originU + rowShift + col * grid.cellW;
        float yTop = grid.originV + grid.rows * grid.cellH - row * grid.cellH;

        float gap = grid.cellW * GAP;
        return new Cell(x0 + gap, yTop - grid.cellH + gap, x0 + grid.cellW - gap, yTop - gap,
                grid.cellW * BORDER);
    }

    private static void drawPanel(VertexConsumer panels, PoseStack.Pose pose, Cell cell,
                                  boolean dark, int light) {
        int panelLight = dark ? light : LightTexture.FULL_BRIGHT;
        rect(panels, pose, cell.x0(), cell.y0(), cell.x1(), cell.y1(), BORDER_Z,
                dark ? BORDER_DARK : BORDER_LIT, panelLight);
        rect(panels, pose, cell.ix0(), cell.iy0(), cell.ix1(), cell.iy1(), PANEL_Z,
                dark ? PANEL_DARK : PANEL_LIT, panelLight);
    }

    private void drawPortrait(PoseStack pose, MultiBufferSource buffer, ClientRoster.Slot slot,
                              Cell cell, int light) {
        float x0 = cell.ix0(), x1 = cell.ix1();
        float y0 = cell.nameTop(), y1 = cell.iy1();
        float availW = x1 - x0;
        float availH = y1 - y0;
        // Dua batas sekaligus: potret harus muat lebar maupun tinggi. Mengambil yang
        // terkecil berarti sel yang gepeng menyusutkan potretnya, bukan membiarkannya
        // menjulur ke panel sebelah.
        float scale = Math.min(availW / PortraitPose.BUST_WIDTH, availH / PortraitPose.BUST_HEIGHT)
                * PORTRAIT_FIT;
        if (scale <= 0.0F) {
            return;
        }

        PlayerModel<LivingEntity> model = PlayerSkins.isSlim(slot.uuid()) ? slimModel : wideModel;
        PortraitPose.pose(model, slot.uuid());
        model.young = false;
        model.riding = false;
        model.crouching = false;
        model.attackTime = 0.0F;

        float cx = (x0 + x1) * 0.5F;
        // Titik nol model ada di leher, dan sumbu Y-nya terbalik setelah diskalakan
        // negatif: ujung bawah potret mendarat di cy dikurangi BUST_BOTTOM kali skala.
        // Jadi untuk menaruh ujung bawah itu tepat di dasar area, cy harus digeser ke
        // atas sebanyak BUST_BOTTOM — bukan BUST_TOP, yang justru menaruhnya terlalu
        // rendah sehingga badannya menutupi nama.
        float cy = y0 + (availH - PortraitPose.BUST_HEIGHT * scale) * 0.5F
                + PortraitPose.BUST_BOTTOM * scale;

        pose.pushPose();
        pose.translate(cx, cy, PORTRAIT_Z);
        // 180 derajat: model menghadap -Z pada kerangkanya sendiri, dan papan ini
        // menghadap +Z. Tidak ada sudut serong — lihat FLATTEN.
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(-scale, -scale, scale * FLATTEN);

        float shade = slot.dark() ? PORTRAIT_DARK : 1.0F;
        model.renderToBuffer(pose,
                buffer.getBuffer(model.renderType(PlayerSkins.texture(slot.uuid()))),
                slot.dark() ? light : LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, shade, shade, shade, 1.0F);
        pose.popPose();
    }

    private void drawName(PoseStack pose, MultiBufferSource buffer, ClientRoster.Slot slot,
                          Cell cell, int light) {
        String name = slot.name();
        int width = font.width(name);
        if (width <= 0) {
            return;
        }
        float cx = (cell.ix0() + cell.ix1()) * 0.5F;
        float top = cell.nameTop();
        float availW = cell.ix1() - cell.ix0();
        float bandH = (cell.iy1() - cell.iy0()) * NAME_BAND;
        boolean dark = slot.dark();
        int nameLight = dark ? light : LightTexture.FULL_BRIGHT;
        // Tinggi baris font 9 piksel. Skala diambil dari tinggi pita nama, lalu
        // dikecilkan lagi kalau namanya terlalu panjang — dikecilkan, bukan dipotong,
        // supaya nama panjang tetap terbaca utuh.
        float scale = Math.min(bandH * 0.62F / 9.0F, availW * 0.92F / width);

        pose.pushPose();
        pose.translate(cx, top - bandH * 0.22F, TEXT_Z);
        // Sumbu Y font menghadap ke bawah; tanda minus membalikkannya ke kerangka papan.
        pose.scale(scale, -scale, scale);
        font.drawInBatch(name, -width * 0.5F, 0.0F, dark ? NAME_DARK : NAME_LIT, false,
                pose.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, nameLight);
        pose.popPose();
    }

    /** Satu quad menghadap keluar papan, digambar dua sisi supaya tidak bergantung culling. */
    private static void rect(VertexConsumer consumer, PoseStack.Pose pose,
                             float x0, float y0, float x1, float y1, float z,
                             float[] color, int light) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vertex(consumer, m, n, x0, y0, z, color, light);
        vertex(consumer, m, n, x1, y0, z, color, light);
        vertex(consumer, m, n, x1, y1, z, color, light);
        vertex(consumer, m, n, x0, y1, z, color, light);

        vertex(consumer, m, n, x0, y1, z, color, light);
        vertex(consumer, m, n, x1, y1, z, color, light);
        vertex(consumer, m, n, x1, y0, z, color, light);
        vertex(consumer, m, n, x0, y0, z, color, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f m, Matrix3f n,
                               float x, float y, float z, float[] color, int light) {
        consumer.vertex(m, x, y, z)
                .color(color[0], color[1], color[2], 1.0F)
                .uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    /**
     * Susunan kisi untuk sejumlah peserta.
     *
     * <p>Kolomnya dipilih dengan mencoba semua kemungkinan dan mengambil yang
     * menghasilkan potret terbesar — bukan dengan rumus akar kuadrat, karena papan ini
     * jauh lebih lebar daripada tinggi dan pembagian yang seimbang justru membuang
     * ruang. Untuk 25 orang hasilnya 9 kolom 3 baris, kebetulan persis seperti papan
     * di filmnya.</p>
     */
    private record Grid(int count, int cols, int rows, float cellW, float cellH,
                        float originU, float originV) {

        static Grid forCount(int count) {
            int bestCols = 1;
            float bestScale = 0.0F;
            for (int cols = 1; cols <= count; cols++) {
                int rows = (count + cols - 1) / cols;
                float scale = Math.min((float) BOARD_WIDTH / cols / ASPECT_W,
                        (float) BOARD_HEIGHT / rows / ASPECT_H);
                if (scale > bestScale) {
                    bestScale = scale;
                    bestCols = cols;
                }
            }
            int rows = (count + bestCols - 1) / bestCols;
            bestScale = Math.min(bestScale, MAX_SCALE);
            float cellW = bestScale * ASPECT_W;
            float cellH = bestScale * ASPECT_H;
            // Kisinya dipusatkan di papan, jadi sisa ruangnya jatuh jadi pinggiran yang
            // rata di keempat sisi.
            return new Grid(count, bestCols, rows, cellW, cellH,
                    (BOARD_WIDTH - bestCols * cellW) * 0.5F,
                    (BOARD_HEIGHT - rows * cellH) * 0.5F);
        }
    }
}
