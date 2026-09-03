package com.aflah.jackofhearts.registry;

import com.aflah.jackofhearts.JackOfHeartsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JackOfHeartsMod.MOD_ID);

    public static final RegistryObject<SoundEvent> CARD_SHUFFLE = register("card_shuffle");
    public static final RegistryObject<SoundEvent> CARD_FLIP = register("card_flip");
    public static final RegistryObject<SoundEvent> CARD_DEAL = register("card_deal");

    /** Bunyi laser mengisi daya saat mengunci sasaran. */
    public static final RegistryObject<SoundEvent> LASER_CHARGE = register("laser_charge");
    /** Bunyi tembakan laser. */
    public static final RegistryObject<SoundEvent> LASER_FIRE = register("laser_fire");

    /**
     * Klik satu panel papan peserta padam.
     *
     * <p>Sumbernya "switch kick click" oleh Ambiabstract di Freesound, lisensi CC0
     * (domain publik). Aslinya stereo 0,58 detik; yang dipakai di sini sudah diturunkan
     * ke mono, dipotong jadi 320 milidetik untuk membuang bunyi balik saklarnya, lalu
     * disaturasi dan dinormalkan.</p>
     *
     * <p>Mono itu wajib, bukan pilihan gaya: Minecraft hanya menerapkan peredaman jarak
     * dan arah pada berkas mono — berkas stereo terdengar sama kerasnya dari mana pun,
     * dan kliknya jadi tidak terasa keluar dari papannya.</p>
     *
     * <p>Saturasinya juga bukan hiasan. Versi pertama peak-nya sudah mentok tapi tetap
     * terdengar pelan, karena klik transien punya crest factor hampir 20 dB: puncaknya
     * tinggi, energinya tipis. Menaikkan parameter volume tidak menolong — di Minecraft
     * angka di atas 1.0 memperjauh jangkauan dengar, bukan memperkeras bunyinya. Yang
     * menaikkan kekerasan yang benar-benar terdengar adalah memampatkan crest factor
     * itu, dan di sini dilakukan dengan saturasi lembut sampai RMS-nya sedikit di atas
     * {@link #LASER_FIRE}.</p>
     */
    public static final RegistryObject<SoundEvent> PANEL_OFF = register("panel_off");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(JackOfHeartsMod.MOD_ID, name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
