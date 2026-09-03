package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.command.JackCommand;
import com.aflah.jackofhearts.item.SymbolArmorItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JackOfHeartsMod.MOD_ID)
public class ServerEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        JackCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            GameManager.get().tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GameManager.get().onPlayerDeath(player, event.getSource());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GameManager.get().onPlayerRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GameManager.get().onPlayerJoin(player);
        }
    }

    /**
     * Seragam tidak boleh keluar dari pemainnya. Kalau dibiarkan jatuh ke tanah,
     * slot armor akan diisi ulang dan barang di tanah itu jadi salinan kedua.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!GameManager.get().isRunning()) {
            return;
        }
        if (event.getEntity().getItem().getItem() instanceof SymbolArmorItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        if (GameManager.get().isRunning()) {
            GameManager.get().stop(server, false);
        }
    }
}
