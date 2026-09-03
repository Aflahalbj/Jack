package com.aflah.jackofhearts.registry;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.blockentity.MonitorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, JackOfHeartsMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<MonitorBlockEntity>> MONITOR =
            BLOCK_ENTITIES.register("monitor",
                    () -> BlockEntityType.Builder.of(MonitorBlockEntity::new, ModBlocks.MONITOR.get())
                            .build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
