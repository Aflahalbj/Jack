package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.registry.ModBlockEntities;
import com.aflah.jackofhearts.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = JackOfHeartsMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    public static final ModelLayerLocation SHIRT_LAYER =
            new ModelLayerLocation(new ResourceLocation(JackOfHeartsMod.MOD_ID, "uniform"), "shirt");
    public static final ModelLayerLocation PANTS_LAYER =
            new ModelLayerLocation(new ResourceLocation(JackOfHeartsMod.MOD_ID, "uniform"), "pants");

    @Nullable
    private static SymbolArmorModel shirtModel;
    @Nullable
    private static SymbolArmorModel pantsModel;

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SHIRT_LAYER, SymbolArmorModel::createShirtLayer);
        event.registerLayerDefinition(PANTS_LAYER, SymbolArmorModel::createPantsLayer);
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SEAT.get(), SeatEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.CORPSE.get(), CorpseRenderer::new);
        event.registerEntityRenderer(ModEntities.MANNEQUIN.get(), MannequinRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MONITOR.get(), MonitorRenderer::new);
    }

    /** Dipanggil dari IClientItemExtensions saat armor dirender. */
    public static HumanoidModel<?> getArmorModel(EquipmentSlot slot, HumanoidModel<?> fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (slot == EquipmentSlot.LEGS) {
            if (pantsModel == null) {
                pantsModel = new SymbolArmorModel(minecraft.getEntityModels().bakeLayer(PANTS_LAYER));
            }
            return pantsModel;
        }
        if (shirtModel == null) {
            shirtModel = new SymbolArmorModel(minecraft.getEntityModels().bakeLayer(SHIRT_LAYER));
        }
        return shirtModel;
    }
}
