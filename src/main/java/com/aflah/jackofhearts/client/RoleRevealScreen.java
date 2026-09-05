package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.game.GamePhase;
import com.aflah.jackofhearts.game.Role;
import com.aflah.jackofhearts.registry.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Animasi kocok kartu saat peran dibagikan.
 *
 * <p>Urutannya: kartu dijejer terbuka satu per satu supaya semua orang bisa
 * menghitung ada berapa Jack di game ini, kartunya menutup, menyatu jadi satu deck,
 * dikocok, lalu satu kartu teratas terangkat dan membalik memperlihatkan peranmu.</p>
 *
 * <p>Layar ini tidak menjeda dunia ({@link #isPauseScreen()} false) supaya timer fase
 * REVEAL di server tetap berjalan seirama, dan tidak bisa ditutup dengan ESC supaya
 * tidak ada yang melewati pembagian peran. Penutupnya bukan timer lokal melainkan
 * laporan fase dari server, jadi kalau game dihentikan di tengah animasi layarnya
 * ikut hilang alih-alih nyangkut.</p>
 */
public class RoleRevealScreen extends Screen {

    private static final ResourceLocation CARD_BACK =
            new ResourceLocation(JackOfHeartsMod.MOD_ID, "textures/gui/card_back.png");
    private static final ResourceLocation CARD_JACK =
            new ResourceLocation(JackOfHeartsMod.MOD_ID, "textures/gui/card_jack.png");
    private static final ResourceLocation CARD_ACE =
            new ResourceLocation(JackOfHeartsMod.MOD_ID, "textures/gui/card_ace.png");

    /** Ukuran asli tekstur kartu; digambar apa adanya lalu diperbesar lewat PoseStack. */
    private static final int CARD_W = 47;
    private static final int CARD_H = 62;

    private static final float DECK_SCALE = 1.5F;
    private static final float REVEAL_SCALE = 2.3F;

    /** Batas fase animasi dalam detik. */
    private static final float T_LINEUP_END = 2.0F;
    private static final float T_CLOSE_END = 2.9F;
    private static final float T_STACK_END = 3.7F;
    private static final float T_SHUFFLE_END = 5.9F;
    private static final float T_GATHER_END = 6.5F;
    private static final float T_FLIP_END = 7.2F;

    /**
     * Berapa banyak kartu yang ikut bergerak saat fase kocok. Barisan pembuka boleh
     * sampai 30 kartu, tapi mengocok sebanyak itu terlihat penuh sesak — jadi sisanya
     * tetap diam sebagai tumpukan deck dan hanya sebagian yang berhamburan keluar.
     */
    private static final int SHUFFLE_MAX_CARDS = 15;

    /** Berapa kali kartu menyeberang bolak-balik selama fase kocok. */
    private static final float SHUFFLE_CYCLES = 3.0F;
    /**
     * Tunda fase antar kartu dalam satu kelompok, dalam satuan putaran penuh.
     * 0 = seluruh kelompok bergerak sekaku satu papan; makin besar makin mengurai
     * jadi kipas. Jangan sampai mendekati 0.5, karena di situ kartu dalam kelompok
     * yang sama mulai berlawanan arah sendiri dan efek cerminnya buyar.
     */
    private static final float SHUFFLE_CASCADE = 0.25F;
    private static final float SHUFFLE_SPREAD = 92.0F;
    private static final float SHUFFLE_LIFT = 20.0F;
    private static final float CARD_LIFT = 30.0F;

    /** Lamanya kartu peran ditahan di layar sebelum preview menutup sendiri. */
    private static final float PREVIEW_HOLD = 2.5F;

    /** Barisan pembuka disusun sebagai grid; sisanya turun ke baris berikutnya. */
    private static final int LINEUP_COLUMNS = 10;
    private static final float LINEUP_GAP_X = 6.0F;
    private static final float LINEUP_GAP_Y = 6.0F;

    /**
     * Berapa kali bunyi kocokan dibunyikan per satu putaran penuh. Bunyinya dipicu
     * tepat saat kartu menyeberang tengah, jadi audio dan gerakannya tetap sejalan
     * kalau {@link #SHUFFLE_CYCLES} diubah. Nilai 1 menghasilkan jarak antar bunyi
     * yang sama dengan versi paling awal. Naikkan untuk kocokan yang lebih rapat.
     */
    private static final int SHUFFLE_SOUNDS_PER_CYCLE = 2;
    private static final float SHUFFLE_SOUND_VOLUME = 0.8F;

    private final Role role;
    private final boolean preview;
    private final int cardCount;
    private final int jackCards;
    /** Angka sebenarnya untuk teks, tanpa dibatasi jumlah kartu yang muat digambar. */
    private final int totalPlayers;
    private final int totalJacks;
    /** Posisi mana saja di barisan yang berisi kartu Jack. Murni kosmetik. */
    private final boolean[] isJackSlot;
    private final RandomSource random = RandomSource.create();

    private int ticks;
    private boolean playedDeal;
    private boolean playedClose;
    private boolean playedFlip;
    private boolean playedLand;
    private int nextLineupSoundSlot;
    private final boolean[] shuffleSoundPlayed =
            new boolean[Math.max(1, (int) (SHUFFLE_CYCLES * SHUFFLE_SOUNDS_PER_CYCLE))];

    public RoleRevealScreen(Role role, int cardCount, int jackCards,
                            int totalPlayers, int totalJacks, boolean preview) {
        super(Component.translatable("screen.jackofhearts.reveal"));
        this.role = role;
        this.preview = preview;
        this.cardCount = Math.max(cardCount, 1);
        this.jackCards = Mth.clamp(jackCards, 0, this.cardCount);
        this.totalPlayers = Math.max(totalPlayers, this.cardCount);
        this.totalJacks = Math.max(totalJacks, 0);
        this.isJackSlot = buildSlots(this.cardCount, this.jackCards);
    }

    /** Sebar kartu Jack ke posisi acak di barisan supaya tidak selalu menumpuk di kiri. */
    private static boolean[] buildSlots(int count, int jacks) {
        boolean[] slots = new boolean[count];
        RandomSource rng = RandomSource.create();
        int placed = 0;
        while (placed < jacks) {
            int at = rng.nextInt(count);
            if (!slots[at]) {
                slots[at] = true;
                placed++;
            }
        }
        return slots;
    }

    // ------------------------------------------------------------------ siklus hidup

    @Override
    public void tick() {
        ticks++;

        if (preview) {
            // Preview tidak terikat fase server, jadi ia yang menutup dirinya sendiri.
            if (ticks >= (T_FLIP_END + PREVIEW_HOLD) * 20) {
                this.onClose();
                return;
            }
        } else if (ClientGameState.phase != GamePhase.REVEAL) {
            // Server yang menentukan kapan selesai, bukan hitungan lokal.
            this.onClose();
            return;
        }

        float t = ticks / 20.0F;

        if (!playedDeal && ticks >= 2) {
            playedDeal = true;
            playSound(ModSounds.CARD_DEAL.get(), 1.0F, 0.7F);
        }
        // Satu bunyi tiap kartu yang mendarat di barisan.
        if (t < T_LINEUP_END && nextLineupSoundSlot < cardCount) {
            float appearAt = lineupAppearTime(nextLineupSoundSlot);
            if (t >= appearAt) {
                nextLineupSoundSlot++;
                playSound(ModSounds.CARD_DEAL.get(), 1.0F + random.nextFloat() * 0.2F, 0.6F);
            }
        }
        if (!playedClose && t >= T_LINEUP_END) {
            playedClose = true;
            playSound(ModSounds.CARD_FLIP.get(), 0.9F, 0.8F);
        }
        // Bunyi kocokan dipicu tepat di saat kartu menyeberang tengah, bukan tiap
        // sekian tick, supaya audionya sejalan dengan gerakan yang terlihat.
        for (int k = 0; k < shuffleSoundPlayed.length; k++) {
            if (shuffleSoundPlayed[k]) {
                continue;
            }
            float at = T_STACK_END + (T_SHUFFLE_END - T_STACK_END) * (k / (float) shuffleSoundPlayed.length);
            if (t >= at) {
                shuffleSoundPlayed[k] = true;
                playSound(ModSounds.CARD_SHUFFLE.get(), 0.9F + random.nextFloat() * 0.3F,
                        SHUFFLE_SOUND_VOLUME);
            }
        }
        if (!playedFlip && t >= T_GATHER_END) {
            playedFlip = true;
            playSound(ModSounds.CARD_FLIP.get(), 1.0F, 1.0F);
        }
        if (!playedLand && t >= T_FLIP_END) {
            playedLand = true;
            playSound(ModSounds.CARD_DEAL.get(), 0.85F, 0.9F);
        }
    }

    private void playSound(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Saat game sungguhan tidak boleh dilewati; preview boleh ditutup kapan saja.
        return preview;
    }

    // ------------------------------------------------------------------ tata letak barisan

    /** Jumlah kartu yang benar-benar dikocok; sisanya diam jadi tumpukan. */
    private int shuffleCount() {
        return Math.min(cardCount, SHUFFLE_MAX_CARDS);
    }

    private int lineupColumns() {
        return Math.min(cardCount, LINEUP_COLUMNS);
    }

    private int lineupRows() {
        return (cardCount + LINEUP_COLUMNS - 1) / LINEUP_COLUMNS;
    }

    /**
     * Skala grid dibatasi lebar <b>dan</b> tinggi yang tersedia. Dengan 3 baris, tinggi
     * yang jadi kendala lebih dulu di layar pendek, jadi keduanya harus dihitung.
     */
    private float lineupScale() {
        float byWidth = (this.width - 40.0F) / (lineupColumns() * (CARD_W + LINEUP_GAP_X));
        float byHeight = (this.height * 0.62F) / (lineupRows() * (CARD_H + LINEUP_GAP_Y));
        return Math.min(1.15F, Math.min(byWidth, byHeight));
    }

    /** Posisi tengah kartu ke-index di grid; terisi baris demi baris dari kiri atas. */
    private float[] lineupPos(int index) {
        float scale = lineupScale();
        float stepX = (CARD_W + LINEUP_GAP_X) * scale;
        float stepY = (CARD_H + LINEUP_GAP_Y) * scale;

        int rows = lineupRows();
        int row = index / LINEUP_COLUMNS;
        int col = index % LINEUP_COLUMNS;
        // Baris terakhir bisa tidak penuh, jadi dipusatkan sendiri agar tidak menggantung.
        int inRow = Math.min(cardCount - row * LINEUP_COLUMNS, LINEUP_COLUMNS);

        float x = this.width / 2.0F - (stepX * inRow) / 2.0F + stepX * (col + 0.5F);
        float y = lineupCenterY() - (stepY * rows) / 2.0F + stepY * (row + 0.5F);
        return new float[]{x, y};
    }

    private float lineupCenterY() {
        return this.height / 2.0F - 8.0F;
    }

    /** Tepi bawah grid, dipakai menaruh teks supaya tidak menimpa kartu. */
    private float lineupBottom() {
        float stepY = (CARD_H + LINEUP_GAP_Y) * lineupScale();
        return lineupCenterY() + (stepY * lineupRows()) / 2.0F;
    }

    /** Kartu ke-i mendarat pada detik ini; sisa waktu fase dipakai untuk menahan barisan. */
    private float lineupAppearTime(int index) {
        return T_LINEUP_END * 0.55F * (index / (float) cardCount);
    }

    // ------------------------------------------------------------------ gambar

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        float t = (ticks + partialTick) / 20.0F;
        float cx = this.width / 2.0F;
        float cy = this.height / 2.0F - 8.0F;

        if (t < T_LINEUP_END) {
            renderLineup(graphics, t, cy);
        } else if (t < T_CLOSE_END) {
            renderClosing(graphics, t, cy);
        } else if (t < T_STACK_END) {
            renderStacking(graphics, t, cx, cy);
        } else if (t < T_SHUFFLE_END) {
            renderShuffling(graphics, t, cx, cy);
        } else if (t < T_GATHER_END) {
            renderGathering(graphics, t, cx, cy);
        } else {
            renderFlip(graphics, t, cx, cy);
        }

        renderCaption(graphics, t);
    }

    /** Kartu dijejer terbuka satu per satu: inilah yang memperlihatkan jumlah Jack. */
    private void renderLineup(GuiGraphics graphics, float t, float cy) {
        float scale = lineupScale();
        for (int i = 0; i < cardCount; i++) {
            float appear = Mth.clamp((t - lineupAppearTime(i)) * 6.0F, 0.0F, 1.0F);
            if (appear <= 0.0F) {
                continue;
            }
            float eased = easeOut(appear);
            float[] at = lineupPos(i);
            // Kartu turun dari atas sambil membesar sedikit.
            float drop = (1.0F - eased) * 26.0F;
            float size = scale * Mth.lerp(eased, 0.8F, 1.0F);
            drawCard(graphics, slotTexture(i), at[0], at[1] - drop, size, size, 0.0F, appear);
        }
    }

    /** Semua kartu menutup bersamaan, dengan sedikit tunda per kartu. */
    private void renderClosing(GuiGraphics graphics, float t, float cy) {
        float scale = lineupScale();
        float base = (t - T_LINEUP_END) / (T_CLOSE_END - T_LINEUP_END);
        for (int i = 0; i < cardCount; i++) {
            float u = Mth.clamp(base * 1.6F - (i / (float) cardCount) * 0.6F, 0.0F, 1.0F);
            float squash = Math.abs(Mth.cos(u * Mth.PI));
            ResourceLocation tex = u < 0.5F ? slotTexture(i) : CARD_BACK;
            float[] at = lineupPos(i);
            drawCard(graphics, tex, at[0], at[1], scale * Math.max(squash, 0.02F), scale, 0.0F, 1.0F);
        }
    }

    /** Barisan menyatu jadi satu tumpukan di tengah. */
    private void renderStacking(GuiGraphics graphics, float t, float cx, float cy) {
        float u = easeOut((t - T_CLOSE_END) / (T_STACK_END - T_CLOSE_END));
        float scale = Mth.lerp(u, lineupScale(), DECK_SCALE);
        int moving = shuffleCount();
        for (int i = 0; i < cardCount; i++) {
            // Kartu yang tidak ikut dikocok menumpuk di slot teratas, bukan meneruskan
            // garis diagonal tumpukan — kalau tidak, 30 kartu membentuk coretan miring
            // yang melebar, bukan deck. Begitu tumpukan selesai mereka tinggal berhenti
            // digambar; karena posisinya sudah saling menimpa, tidak ada yang terlihat
            // menghilang.
            int slot = Math.min(i, moving);
            float[] from = lineupPos(i);
            float x = Mth.lerp(u, from[0], cx + slot * 0.4F);
            float y = Mth.lerp(u, from[1], cy - slot * 0.8F);
            drawCard(graphics, CARD_BACK, x, y, scale, scale, 0.0F, 1.0F);
        }
    }

    /** Kartu menyebar kiri-kanan dan menyilang beberapa kali. */
    private void renderShuffling(GuiGraphics graphics, float t, float cx, float cy) {
        float u = (t - T_STACK_END) / (T_SHUFFLE_END - T_STACK_END);
        for (int i = 0; i < shuffleCount(); i++) {
            float[] p = shufflePos(i, u, cx, cy);
            drawCard(graphics, CARD_BACK, p[0], p[1], DECK_SCALE, DECK_SCALE, p[2], 1.0F);
        }
    }

    /** Kartu kembali menyatu jadi satu tumpukan rapi. */
    private void renderGathering(GuiGraphics graphics, float t, float cx, float cy) {
        float u = easeOut((t - T_SHUFFLE_END) / (T_GATHER_END - T_SHUFFLE_END));
        for (int i = 0; i < shuffleCount(); i++) {
            float[] from = shufflePos(i, 1.0F, cx, cy);
            float x = Mth.lerp(u, from[0], cx + i * 0.4F);
            float y = Mth.lerp(u, from[1], cy - i * 0.8F);
            float rot = Mth.lerp(u, from[2], 0.0F);
            drawCard(graphics, CARD_BACK, x, y, DECK_SCALE, DECK_SCALE, rot, 1.0F);
        }
    }

    /** Kartu teratas terangkat dan membalik; sisa tumpukan diam di bawahnya. */
    private void renderFlip(GuiGraphics graphics, float t, float cx, float cy) {
        for (int i = 1; i < shuffleCount(); i++) {
            drawCard(graphics, CARD_BACK, cx + i * 0.4F, cy - i * 0.8F, DECK_SCALE, DECK_SCALE, 0.0F, 1.0F);
        }

        float u = Mth.clamp((t - T_GATHER_END) / (T_FLIP_END - T_GATHER_END), 0.0F, 1.0F);
        float eased = easeOut(u);
        // Lebar menyusut ke nol di tengah putaran lalu melebar lagi: efek kartu membalik.
        float squash = Math.abs(Mth.cos(u * Mth.PI));
        float scale = Mth.lerp(eased, DECK_SCALE, REVEAL_SCALE);
        float lift = eased * CARD_LIFT;
        ResourceLocation face = u < 0.5F ? CARD_BACK : roleTexture();

        drawCard(graphics, face, cx, cy - lift, scale * Math.max(squash, 0.02F), scale, 0.0F, 1.0F);
    }

    /**
     * Posisi satu kartu selama fase kocok.
     *
     * <p>Kartu bernomor genap dan ganjil selalu jadi bayangan cermin satu sama lain:
     * saat satu kelompok terlempar ke kiri, kelompok lainnya ke kanan, lalu keduanya
     * bertukar sisi dengan menyeberang lewat tengah.</p>
     *
     * <p>Kuncinya {@code swing} memakai sinus <b>penuh</b> (-1..1), bukan cuma lengkung
     * positifnya. Dengan lengkung positif saja nilainya tidak pernah negatif, sehingga
     * tiap kartu cuma bisa keluar-masuk di sisinya sendiri dan tidak pernah menyeberang —
     * hasilnya terbaca sebagai dua kelompok yang bergerak searah, bukan berlawanan.</p>
     *
     * <p>{@link #SHUFFLE_CASCADE} memberi tunda fase kecil antar kartu supaya tiap
     * kelompok tidak bergerak sekaku satu papan.</p>
     */
    private float[] shufflePos(int index, float u, float cx, float cy) {
        float phase = u * SHUFFLE_CYCLES + (index / (float) shuffleCount()) * SHUFFLE_CASCADE;
        float swing = Mth.sin(phase * Mth.PI * 2.0F);
        int side = (index % 2 == 0) ? -1 : 1;
        float offset = side * swing;
        return new float[]{
                cx + offset * SHUFFLE_SPREAD,
                cy - index * 0.8F - Math.abs(swing) * SHUFFLE_LIFT,
                offset * 24.0F
        };
    }

    private ResourceLocation slotTexture(int index) {
        return isJackSlot[index] ? CARD_JACK : CARD_ACE;
    }

    private ResourceLocation roleTexture() {
        return role == Role.JACK ? CARD_JACK : CARD_ACE;
    }

    private void renderCaption(GuiGraphics graphics, float t) {
        int y = this.height / 2 + 80;

        if (t < T_CLOSE_END) {
            // Angkanya jumlah pemain sebenarnya, bukan jumlah kartu yang muat digambar.
            int captionY = (int) lineupBottom() + 10;
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.jackofhearts.reveal.lineup", totalPlayers, totalJacks)
                            .withStyle(ChatFormatting.GOLD),
                    this.width / 2, captionY, 0xFFFFFF);
            if (totalPlayers > cardCount) {
                graphics.drawCenteredString(this.font,
                        Component.translatable("screen.jackofhearts.reveal.capped", cardCount)
                                .withStyle(ChatFormatting.DARK_GRAY),
                        this.width / 2, captionY + 12, 0xFFFFFF);
            }
            return;
        }

        if (t < T_GATHER_END) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.jackofhearts.reveal.shuffling").withStyle(ChatFormatting.GRAY),
                    this.width / 2, y, 0xFFFFFF);
            return;
        }

        // Teks peran baru muncul setelah kartunya benar-benar terbuka.
        float fade = Mth.clamp((t - T_FLIP_END) / 0.4F, 0.0F, 1.0F);
        if (fade <= 0.0F) {
            return;
        }
        int alpha = ((int) (fade * 255.0F) << 24) | 0x00FFFFFF;

        graphics.drawCenteredString(this.font,
                Component.translatable("message.jackofhearts.your_role", role.getDisplayName()),
                this.width / 2, y, alpha);
        // graphics.drawCenteredString(this.font,
        //         Component.translatable("message.jackofhearts.role_hint_" + role.getId())
        //                 .withStyle(ChatFormatting.GRAY),
        //         this.width / 2, y + 14, alpha);
    }

    /**
     * Menggambar satu kartu berpusat di (cx, cy). Posisi, rotasi, dan skala dikerjakan
     * lewat PoseStack karena blit sendiri cuma menerima koordinat bulat.
     */
    private void drawCard(GuiGraphics graphics, ResourceLocation texture,
                          float cx, float cy, float scaleX, float scaleY, float rotation, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);

        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        if (rotation != 0.0F) {
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        }
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.pose().translate(-CARD_W / 2.0F, -CARD_H / 2.0F, 0.0F);
        graphics.blit(texture, 0, 0, CARD_W, CARD_H, 0.0F, 0.0F, CARD_W, CARD_H, CARD_W, CARD_H);
        graphics.pose().popPose();

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static float easeOut(float u) {
        float c = Mth.clamp(u, 0.0F, 1.0F);
        return 1.0F - (1.0F - c) * (1.0F - c);
    }
}
