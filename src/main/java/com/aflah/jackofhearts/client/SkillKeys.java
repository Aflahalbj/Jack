package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.game.Skill;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Tombol skill Jack: {@code M} untuk mabuk, {@code H} untuk lapar, {@code P} untuk racun.
 *
 * <p>Menahan tombol menunduk saat menekannya berarti sasarannya diri sendiri. Sengaja
 * tidak didaftarkan sebagai tombol terpisah dengan modifier: Minecraft menganggap
 * kombinasi bermodifier sebagai tombol yang berbeda dan akan menolak keduanya kalau
 * tombol dasarnya sama, sehingga salah satu dari dua kegunaan itu tidak akan pernah
 * jalan. Menahannya sendiri dibaca saat tombolnya ditekan.</p>
 *
 * <p>Keduanya tetap muncul di menu kontrol Minecraft dan bisa diganti dari sana.</p>
 */
@Mod.EventBusSubscriber(modid = JackOfHeartsMod.MOD_ID, value = Dist.CLIENT)
public final class SkillKeys {

    private SkillKeys() {
    }

    private static final String CATEGORY = "key.categories." + JackOfHeartsMod.MOD_ID;

    private static final KeyMapping DRUNK = new KeyMapping(
            "key." + JackOfHeartsMod.MOD_ID + ".drunk", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);

    private static final KeyMapping HUNGER = new KeyMapping(
            "key." + JackOfHeartsMod.MOD_ID + ".hunger", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    /** Racun: tatap piring kafetaria, lalu tekan. Tidak ada versi "ke diri sendiri". */
    private static final KeyMapping POISON = new KeyMapping(
            "key." + JackOfHeartsMod.MOD_ID + ".poison", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);

    /**
     * Tombol yang mengalihkan skill ke diri sendiri, bawaannya <b>kosong</b>.
     *
     * <p>Kosong berarti dipakai tombol menunduk milik Minecraft — itulah yang bikin
     * "tahan menunduk + M" jalan tanpa perlu disetel sama sekali. Kalau diisi, isinya
     * yang dipakai.</p>
     *
     * <p>Sengaja tidak langsung dipasangi Shift kiri: Minecraft akan menandainya bentrok
     * dengan tombol menunduk dan mewarnainya merah di menu kontrol, padahal keduanya
     * memang dimaksudkan tombol yang sama.</p>
     */
    private static final KeyMapping SELF = new KeyMapping(
            "key." + JackOfHeartsMod.MOD_ID + ".self", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static KeyMapping mapping(Skill skill) {
        return switch (skill) {
            case DRUNK -> DRUNK;
            case HUNGER -> HUNGER;
            case POISON -> POISON;
        };
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(DRUNK);
        event.register(HUNGER);
        event.register(POISON);
        event.register(SELF);
    }

    /** Sedang menahan tombol "ke diri sendiri" — miliknya sendiri, atau menunduk. */
    public static boolean selfHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        return SELF.isUnbound() ? minecraft.options.keyShift.isDown() : SELF.isDown();
    }

    /**
     * Tombol dibaca di tick, bukan di kejadian penekanan tombol.
     *
     * <p>{@code consumeClick} mengosongkan antrean penekanan yang tertampung sejak tick
     * lalu, jadi satu tekanan selalu terhitung tepat sekali — termasuk tekanan yang
     * jatuh di antara dua tick, yang lewat jalur kejadian bisa terbaca dua kali atau
     * malah hilang.</p>
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        boolean self = selfHeld();
        for (Skill skill : Skill.VALUES) {
            KeyMapping mapping = mapping(skill);
            boolean used = false;
            while (mapping.consumeClick()) {
                used = true;
            }
            if (used) {
                ClientSkills.use(skill, self);
            }
        }
    }
}
