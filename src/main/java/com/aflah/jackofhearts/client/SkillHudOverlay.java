package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.game.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Panel skill Jack di pojok kanan bawah.
 *
 * <p>Tiap ikon menyala kalau skillnya benar-benar bisa dipakai detik itu — ada orang
 * dalam jangkauan, atau tombol menunduk sedang ditahan (yang berarti sasarannya diri
 * sendiri), atau untuk racun: sedang menatap piring kafetaria — dan meredup kalau tidak. Itu satu-satunya isyarat jangkauan yang dipunya
 * Jack: menekan tombol lalu ditolak diam-diam membuat dia tidak pernah tahu sedekat apa
 * dia harus berdiri.</p>
 *
 * <p>Panelnya tidak digambar sama sekali untuk yang bukan Jack, dan itu bukan sekadar
 * penyaring tampilan: sisa pemakaian skill memang tidak pernah dikirim ke client
 * mereka.</p>
 */
public class SkillHudOverlay implements IGuiOverlay {

    public static final SkillHudOverlay INSTANCE = new SkillHudOverlay();

    private static final ResourceLocation DRUNK =
            new ResourceLocation(JackOfHeartsMod.MOD_ID, "textures/gui/skill_drunk.png");
    private static final ResourceLocation HUNGER =
            new ResourceLocation(JackOfHeartsMod.MOD_ID, "textures/gui/skill_hunger.png");
    private static final ResourceLocation POISON =
            new ResourceLocation(JackOfHeartsMod.MOD_ID, "textures/gui/skill_poison.png");

    /** Sisi satu ikon di layar. */
    private static final int ICON = 26;
    /** Jarak antar ikon, dan jarak dari tepi layar. */
    private static final int GAP = 6;
    private static final int MARGIN = 10;

    /** Seterang apa ikon yang sedang tidak bisa dipakai. */
    private static final float DIM = 0.32F;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || !ClientSkills.active()) {
            return;
        }
        boolean shift = SkillKeys.selfHeld();

        // Digambar dari kanan ke kiri: yang paling kanan skill terakhir, jadi urutannya
        // tetap sama walau nanti ada skill yang ditambahkan.
        int x = screenWidth - MARGIN - ICON;
        int y = screenHeight - MARGIN - ICON;
        for (int i = Skill.VALUES.length - 1; i >= 0; i--) {
            Skill skill = Skill.VALUES[i];
            if (!ClientSkills.has(skill)) {
                continue;
            }
            drawIcon(graphics, minecraft, skill, x, y, shift);
            x -= ICON + GAP;
        }
    }

    private void drawIcon(GuiGraphics graphics, Minecraft minecraft, Skill skill,
                          int x, int y, boolean shift) {
        int left = ClientSkills.uses(skill);
        boolean ready = left > 0 && ClientSkills.ready(skill, shift);
        float shade = ready ? 1.0F : DIM;

        // Latar gelap di belakang ikon: tanpa itu ikon yang meredup hilang sama sekali
        // di atas pemandangan yang terang.
        graphics.fill(x - 2, y - 2, x + ICON + 2, y + ICON + 2, ready ? 0xB0000000 : 0x70000000);

        graphics.setColor(shade, shade, shade, 1.0F);
        graphics.blit(icon(skill), x, y, 0, 0.0F, 0.0F, ICON, ICON, ICON, ICON);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Sisa pemakaian di pojok kanan bawah ikon, dan huruf tombolnya di bawahnya.
        // Keduanya kecil dan menempel, supaya panelnya tetap terbaca sebagai satu benda.
        Component uses = Component.literal(String.valueOf(left));
        graphics.drawString(minecraft.font, uses,
                x + ICON - minecraft.font.width(uses), y + ICON - 7,
                left > 0 ? 0xFFFFFFFF : 0xFFFF5555, true);

        String key = keyName(skill);
        graphics.drawString(minecraft.font, key,
                x + (ICON - minecraft.font.width(key)) / 2, y - 11,
                ready ? 0xFFFFFFFF : 0xFF808080, true);
    }

    private static ResourceLocation icon(Skill skill) {
        return switch (skill) {
            case DRUNK -> DRUNK;
            case HUNGER -> HUNGER;
            case POISON -> POISON;
        };
    }

    /**
     * Nama tombolnya dibaca dari setelan, bukan ditulis tetap, supaya tetap benar kalau
     * pemainnya mengganti tombol lewat menu kontrol Minecraft.
     */
    private static String keyName(Skill skill) {
        return SkillKeys.mapping(skill).getTranslatedKeyMessage().getString().toUpperCase();
    }
}
