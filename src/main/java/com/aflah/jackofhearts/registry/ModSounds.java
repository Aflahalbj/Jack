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

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(JackOfHeartsMod.MOD_ID, name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
