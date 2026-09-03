package com.aflah.jackofhearts.registry;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.entity.SeatEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JackOfHeartsMod.MOD_ID);

    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F)
                    .clientTrackingRange(8)
                    .noSave()
                    .noSummon()
                    .build("seat"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
