package com.aflah.jackofhearts;

import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.network.ModNetwork;
import com.aflah.jackofhearts.registry.ModBlockEntities;
import com.aflah.jackofhearts.registry.ModBlocks;
import com.aflah.jackofhearts.registry.ModCreativeTabs;
import com.aflah.jackofhearts.registry.ModEntities;
import com.aflah.jackofhearts.registry.ModItems;
import com.aflah.jackofhearts.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(JackOfHeartsMod.MOD_ID)
public class JackOfHeartsMod {
    public static final String MOD_ID = "jackofhearts";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JackOfHeartsMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modBus);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModCreativeTabs.register(modBus);
        ModEntities.register(modBus);
        ModSounds.register(modBus);

        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, JackConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
