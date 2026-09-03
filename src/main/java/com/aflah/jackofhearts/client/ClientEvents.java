package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.item.SymbolArmorItem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JackOfHeartsMod.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        ClientGameState.clientTick();
        ClientCameraOverride.tick();

        // Kunci first person: F5 boleh ditekan, tapi kamera langsung dipaksa balik,
        // jadi pemain tidak pernah bisa mengintip simbol di punggungnya sendiri.
        if (ClientGameState.lockView && minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    /**
     * Sembunyikan jubah selama seragam dipakai. Jubah menutupi seluruh punggung,
     * sedangkan simbol kartunya justru ada di punggung - tanpa ini pemain berjubah
     * jadi kebal ditebak.
     *
     * <p>Kondisinya dibaca dari isi slot dada pemain yang sedang dirender, bukan dari
     * state game, supaya berlaku untuk semua pemain yang terlihat tanpa client perlu
     * tahu siapa saja pesertanya. Nilainya diset ulang tiap frame, jadi jubah kembali
     * normal sendiri begitu seragamnya lepas.</p>
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        boolean wearingUniform = event.getEntity().getItemBySlot(EquipmentSlot.CHEST)
                .getItem() instanceof SymbolArmorItem;
        event.getRenderer().getModel().cloak.visible = !wearingUniform;
    }

    /**
     * Sembunyikan tangan first person selama pandangan dialihkan ke entitas lain.
     *
     * <p>Minecraft menggambar tangan berdasarkan tipe kamera (first person), bukan
     * berdasarkan entitas mana yang jadi sudut pandang. Saat kamera eksekusi aktif,
     * kameranya ada di armor stand tapi tipe kameranya tetap first person — jadi
     * tangan pemain ikut tergambar melayang di tengah adegan sinematik.</p>
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.getCameraEntity() != minecraft.player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientGameState.reset();
        ClientLasers.clear();
        ClientRoster.reset();
    }
}
