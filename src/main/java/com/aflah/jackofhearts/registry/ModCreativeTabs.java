package com.aflah.jackofhearts.registry;

import com.aflah.jackofhearts.JackOfHeartsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JackOfHeartsMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jackofhearts.main"))
                    .icon(() -> new ItemStack(ModItems.UNIFORM_SHIRT.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.UNIFORM_SHIRT.get());
                        output.accept(ModItems.UNIFORM_PANTS.get());
                        output.accept(ModBlocks.GUESS_BLOCK.get());
                        output.accept(ModBlocks.MONITOR.get());
                        output.accept(ModBlocks.MONITOR_FRAME.get());
                        output.accept(ModBlocks.PRISON_DOOR.get());
                        output.accept(ModBlocks.SITTABLE_QUARTZ_STAIRS.get());
                        output.accept(ModBlocks.SITTABLE_JUNGLE_STAIRS.get());
                    })
                    .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
