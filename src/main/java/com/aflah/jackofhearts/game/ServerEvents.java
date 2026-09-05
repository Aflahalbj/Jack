package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.block.PrisonDoorTracker;
import com.aflah.jackofhearts.cafeteria.Cafeteria;
import com.aflah.jackofhearts.command.JackCommand;
import com.aflah.jackofhearts.item.SymbolArmorItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
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

    /**
     * Klik kanan piring kafetaria diambil alih dari mod makanannya.
     *
     * <p>Harus diambil alih, bukan sekadar dihitung: kalau kliknya diteruskan, mod
     * makanannya akan mengambilkan makanan itu sendiri di luar hitungan stok, dan
     * angka di papan langsung berbohong. Jadi selama game berjalan, satu-satunya yang
     * boleh mengeluarkan makanan dari meja adalah {@link Cafeteria}.</p>
     *
     * <p>Semua tangan diproses. Klik kanan yang tidak menghasilkan apa-apa di tangan
     * utama akan dicoba lagi dengan tangan kiri, dan percobaan kedua itu jalur yang sama
     * persis - menyaring tangan utama saja berarti tangan kiri lolos ke mod makanannya.
     * Yang menjaga satu klik tidak terhitung dua kali adalah penjaga tick di
     * {@link Cafeteria#take}, bukan penyaring tangan.</p>
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (Cafeteria.get().take(player, event.getPos(), state)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Piring kafetaria tidak bisa dipukul habis selama game berjalan.
     *
     * <p>Tanpa ini seluruh mekanik stok bisa dilewati begitu saja: pukul piringnya,
     * makanannya jatuh sebagai drop, dan angka di papan tidak pernah bergerak.</p>
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer().isCreative() || !(event.getLevel() instanceof Level level)) {
            return;
        }
        if (Cafeteria.get().protects(level, event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    /**
     * Pintu sel mencatatkan diri saat chunk-nya dimuat.
     *
     * <p>Perlu, bukan sekadar mencatat saat dipasang: pintu yang sudah berdiri di
     * dunia sejak sebelum server menyala tidak pernah lewat jalur pemasangan, dan
     * mesin game harus tetap bisa menguncinya.</p>
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            PrisonDoorTracker.scanChunk(level, chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PrisonDoorTracker.forgetChunk(level.dimension(), event.getChunk().getPos());
        }
    }

    /**
     * Meja kafetaria dipulihkan ke penataan tersimpan begitu server siap.
     *
     * <p>Perlu kalau server mati di tengah game: mejanya tertinggal setengah kosong,
     * dan tanpa pemulihan di sini pembacaan berkala akan menyimpan kekosongan itu
     * sebagai penataan resminya.</p>
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Cafeteria.get().onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        if (GameManager.get().isRunning()) {
            GameManager.get().stop(server, false);
        }
    }
}
