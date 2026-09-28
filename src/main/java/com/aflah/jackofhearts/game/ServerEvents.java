package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.block.PrisonDoorTracker;
import com.aflah.jackofhearts.cafeteria.Cafeteria;
import com.aflah.jackofhearts.cafeteria.CafeteriaFood;
import com.aflah.jackofhearts.cafeteria.FoodStash;
import com.aflah.jackofhearts.command.JackCommand;
import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.item.SymbolArmorItem;
import com.aflah.jackofhearts.registry.ModItems;
import net.minecraft.world.item.Items;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
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

    /**
     * Selama permainan berjalan, satu-satunya yang bisa melukai pemain adalah lapar.
     *
     * <p>Kerusakan apa pun ke pemain dibatalkan: pukulan pemain lain, jatuh, api, mob,
     * ledakan, semuanya. Yang tersisa cuma dua jalan keluar dari permainan ini — laser
     * fase menebak, yang tidak pernah lewat sistem kerusakan sama sekali (korbannya
     * langsung dijadikan penonton), dan perut yang kosong.</p>
     *
     * <p>Dibatalkan di {@code LivingAttackEvent}, bukan di {@code LivingHurtEvent},
     * supaya seluruh kejadiannya batal: tidak ada dorongan, tidak ada kedipan merah,
     * dan tidak ada bunyi kesakitan. Kerusakan yang setengah batal justru bikin orang
     * mengira dia sedang kehabisan darah.</p>
     *
     * <p>Yang tetap lolos selain lapar cuma kerusakan yang menembus kekebalan apa pun —
     * jatuh ke luar dunia dan {@code /kill}. Keduanya bukan bagian permainan: yang
     * pertama tidak punya dasar untuk berhenti sendiri, dan yang kedua alat host yang
     * harus tetap bisa dipakai kalau ada yang tersangkut.</p>
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer) || !GameManager.get().isRunning()) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypes.STARVE) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Peserta yang kehabisan nyawa karena lapar tidak benar-benar mati.
     *
     * <p>Kejadiannya dibatalkan dan orangnya langsung dijadikan penonton, persis seperti
     * korban laser: tidak ada layar kematian, tidak ada isi tas yang tercecer, dan tidak
     * ada respawn yang harus diurus. Yang tertinggal di ruangan mayatnya, bukan
     * kekosongan.</p>
     */
    /**
     * Tidak ada mob yang boleh masuk arena.
     *
     * <p>Diperiksa saat entitasnya <b>bergabung ke dunia</b>, bukan saat spawn alaminya
     * dinilai. Spawn alami cuma satu dari banyak jalan masuk: spawner, telur, telur
     * penyu, ternak yang beranak, dan {@code /summon} tidak melewati pemeriksaan itu sama
     * sekali. Satu pintu yang dijaga lebih murah dan lebih rapat daripada lima.</p>
     *
     * <p>Yang disaring cuma {@link Mob}. Mayat, badan pengganti, kursi, dan kamera
     * sinematik semuanya bukan Mob — mereka entitas milik permainan ini sendiri, dan
     * memang harus bisa masuk.</p>
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!JackConfig.CONFIG.arenaNoMobs.get()
                || event.getLevel().isClientSide
                || !(event.getEntity() instanceof Mob)) {
            return;
        }
        if (event.getLevel().dimension().equals(Arena.key())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && GameManager.get().onPlayerDeath(player, event.getSource())) {
            event.setCanceled(true);
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
     * Peserta yang keluar di tengah permainan meninggalkan badan penggantinya.
     *
     * <p>Kejadian ini dipicu sebelum pemainnya benar-benar dikeluarkan dari dunia, jadi
     * posisi, arah hadap, seragam, dan isi perutnya masih bisa dibaca apa adanya —
     * itulah yang disalin ke badan penggantinya.</p>
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GameManager.get().onPlayerLogout(player);
            FoodStash.forget(player);
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
        ItemStack stack = event.getEntity().getItem();
        if (!(stack.getItem() instanceof SymbolArmorItem)
                && !CafeteriaFood.isCafeteriaFood(stack)) {
            return;
        }
        event.setCanceled(true);
        // Membatalkan kejadian ini cuma menahan barangnya masuk ke dunia; barangnya
        // sendiri sudah terlanjur dikeluarkan dari tas sebelum kejadiannya dipicu. Tanpa
        // dikembalikan di sini, menjatuhkan makanan justru jadi cara paling cepat
        // menghabiskan stok - barangnya lenyap begitu saja.
        if (event.getPlayer() instanceof ServerPlayer player) {
            player.getInventory().add(stack);
            player.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * Makanan kafetaria yang dititipkan ke wadah dikembalikan ke tas pemiliknya.
     *
     * <p>Diperiksa tiap tick, bukan saat kliknya, karena tidak ada kejadian klik slot
     * yang bisa dicegat - lihat {@link FoodStash}.</p>
     *
     * <p>Mode kreatif dikecualikan, supaya host masih bisa menata dunia sambil
     * permainannya berjalan.</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.isCreative()
                || !GameManager.get().isRunning()) {
            return;
        }
        if (FoodStash.tick(player)) {
            refuseStash(player);
        }
    }

    /**
     * Makanan kafetaria juga tidak bisa dititipkan ke <b>entitas</b>: bingkai barang,
     * hewan yang diberi makan, gerobak berpeti.
     *
     * <p>Yang diperiksa isi tangan, bukan isi tas, karena itulah satu-satunya cara
     * makanan berpindah ke entitas — dan pemain masih boleh menaiki apa pun sambil
     * membawa bekalnya.</p>
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || !GameManager.get().isRunning()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        if (!CafeteriaFood.isCafeteriaFood(event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        refuseStash(player);
    }

    /**
     * Penolakan disampaikan di baris status, bukan di chat: percobaan ini sering
     * terjadi berkali-kali dalam sedetik (tombol klik kanan yang ditahan), dan chat
     * yang membanjir bikin orang berhenti membaca chat sama sekali.
     */
    private static void refuseStash(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.jackofhearts.food_no_stash")
                .withStyle(ChatFormatting.RED), true);
        GameManager.get().muteStatusBar(player, 40);
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

        // Tong cuma boleh dibuka di fase menebak. Tong berdiri di ruang tebak, dan
        // ruangan itu memang cuma bisa dimasuki di fase itu - tanpa penjagaan ini,
        // susunya bisa disapu lebih dulu lewat celah pintu yang kebetulan terbuka.
        //
        // Mode kreatif TIDAK dikecualikan: selama permainan berjalan, host yang sedang
        // kreatif tetap orang yang bisa mengosongkan tong lebih dulu. Yang dikecualikan
        // cuma Penanda Wadah, karena menandai tong memang harus bisa dilakukan kapan
        // saja.
        if (MilkSupply.isBarrel(state) && GameManager.get().isRunning()
                && GameManager.get().getPhase() != GamePhase.GUESS
                && !event.getItemStack().is(ModItems.CONTAINER_MARKER.get())) {
            player.displayClientMessage(Component.translatable("message.jackofhearts.barrel_locked")
                    .withStyle(ChatFormatting.RED), true);
            GameManager.get().muteStatusBar(player, 40);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (Cafeteria.get().take(player, event.getPos(), state)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Porsi yang diracuni Jack (skill racun, tombol P) baru bekerja saat benar-benar ditelan.
     *
     * <p>Diperiksa saat makannya selesai, bukan saat mulai: makan yang dibatalkan di
     * tengah jalan tidak menghabiskan makanannya, dan tidak semestinya menghabiskan
     * korbannya juga.</p>
     */
    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Susu adalah penawarnya: racun, mabuk, dan lapar hilang sekaligus. Satu
        // gelas menghapus ketiganya, dan itu disengaja - yang minum tidak pernah tahu
        // pasti sedang kena yang mana.
        if (event.getItem().is(Items.MILK_BUCKET)) {
            String cured = GameManager.get().cure(player);
            if (cured != null) {
                player.sendSystemMessage(Component.translatable("message.jackofhearts.milk_cured",
                                Component.translatable("affliction.jackofhearts." + cured))
                        .withStyle(ChatFormatting.GREEN));
            }
            return;
        }
        CompoundTag tag = event.getItem().getTag();
        if (tag != null && tag.getBoolean(Cafeteria.POISON_TAG)) {
            GameManager.get().onPoisonedFoodEaten(player);
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
    /**
     * Map arena dipasang sebelum server memuat dunia mana pun.
     *
     * <p>Waktunya menentukan: kalau dipasang belakangan, chunk arena yang sudah terlanjur
     * dibuat sebagai void akan menang dan berkas region bawaannya tidak pernah terbaca.</p>
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ArenaWorld.install(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Cafeteria.get().onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        // Cutscene ikut: penontonnya sedang dipinjam badannya di mode penonton, dan
        // server yang mati di situ meninggalkan mereka melayang di arena.
        if (GameManager.get().isRunning() || GameManager.get().isCutscenePlaying()) {
            GameManager.get().stop(server, false);
        }
    }
}
