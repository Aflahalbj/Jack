package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.block.MonitorBlock;
import com.aflah.jackofhearts.blockentity.MonitorTracker;
import com.aflah.jackofhearts.block.PrisonDoorTracker;
import com.aflah.jackofhearts.cafeteria.Cafeteria;
import com.aflah.jackofhearts.cafeteria.HungerRules;
import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.entity.Blood;
import com.aflah.jackofhearts.entity.CorpseEntity;
import com.aflah.jackofhearts.item.SymbolArmorItem;
import com.aflah.jackofhearts.network.ModNetwork;
import com.aflah.jackofhearts.network.LaserPacket;
import com.aflah.jackofhearts.network.PanelOffPacket;
import com.aflah.jackofhearts.network.RosterPacket;
import com.aflah.jackofhearts.network.OpenGuessScreenPacket;
import com.aflah.jackofhearts.network.OpenRoleRevealPacket;
import com.aflah.jackofhearts.network.SetCameraPacket;
import com.aflah.jackofhearts.network.SyncStatePacket;
import com.aflah.jackofhearts.registry.ModBlocks;
import com.aflah.jackofhearts.registry.ModEntities;
import com.aflah.jackofhearts.registry.ModItems;
import com.aflah.jackofhearts.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Mesin state game Jack of Hearts. Satu instance per server, hidup di memori
 * selama sesi. Game dimulai lewat perintah /jack start.
 */
public class GameManager {
    private static final GameManager INSTANCE = new GameManager();

    public static GameManager get() {
        return INSTANCE;
    }

    private GamePhase phase = GamePhase.IDLE;
    private int round;
    private int timer;
    private int syncCooldown;
    private final Map<UUID, PlayerData> participants = new LinkedHashMap<>();
    /**
     * Daftar pemain yang terdaftar ikut game berikutnya. Terpisah dari
     * {@link #participants}: daftar ini bertahan lintas game supaya roster tidak
     * perlu disusun ulang tiap ronde baru dimulai.
     */
    private final Map<UUID, String> registered = new LinkedHashMap<>();
    /**
     * Peserta yang tebakannya selalu dinilai benar. Murni alat testing (/jack debug
     * true), supaya jalannya beberapa ronde bisa dicoba tanpa harus menebak benar
     * tiap kali. Bertahan lintas game seperti daftar terdaftar, karena yang
     * menyalakannya biasanya sedang menguji berturut-turut.
     */
    private final Set<UUID> alwaysCorrect = new LinkedHashSet<>();
    /**
     * Fase terakhir yang sudah diterapkan ke pintu sel. Perbandingannya dilakukan di
     * tick, bukan di tiap tempat fase berganti — dengan begitu tidak ada satu pun
     * jalur pergantian fase (termasuk /jack skip dan /jack stop) yang bisa terlewat.
     */
    @Nullable
    private GamePhase lastDoorPhase;
    /** Supaya peringatan roomMarkerBlock yang tidak dikenal cuma keluar sekali. */
    private boolean warnedMissingMarker;


    private GameManager() {
    }

    // ------------------------------------------------------------------ state

    public GamePhase getPhase() {
        return phase;
    }

    public int getRound() {
        return round;
    }

    public int getTimer() {
        return timer;
    }

    /** Animasi kocok kartu butuh 7,2 detik sampai kartunya selesai membalik. */
    private static final int MIN_REVEAL_SECONDS = 8;
    /** Barisan kartu pembuka dibatasi sebanyak ini (3 baris x 10 kolom). */
    public static final int MAX_LINEUP_CARDS = 30;

    /** Berapa lama laser mengunci sasaran sebelum menembak. */
    private static final int LASER_CHARGE_TICKS = 30;
    /** Radius pencarian Terminal Tebakan untuk korban yang tidak pernah mengklik apa pun. */
    private static final int GUESS_BLOCK_SEARCH = 12;
    /**
     * Batas petak yang ditelusuri saat mencari terminal sekamar. Ruangan sel jauh
     * lebih kecil dari ini; batasnya ada supaya ruangan terbuka (atau langit-langit
     * yang bolong) tidak membuat penelusurannya melebar ke seluruh peta.
     */
    private static final int ROOM_FLOOD_LIMIT = 4096;
    /** Sejauh apa laser masih dikirim ke pemain lain untuk digambar. */
    private static final double LASER_VIEW_RANGE = 192.0D;

    /** Tinggi kamera eksekusi di atas Terminal Tebakan. */
    private static final double CAMERA_HEIGHT = 3.0D;
    /**
     * Sudut tunduk kamera, tetap. Positif berarti menunduk; di layar F3 ini angka
     * kedua dari pasangan (yaw / pitch). Sengaja tidak dihitung dari posisi korban
     * supaya bingkainya konsisten walau korban berdiri jauh atau dekat.
     */
    private static final float CAMERA_PITCH = 60.0F;
    /**
     * Jarak mendatar kamera saat korban di luar ruangan. Di sana jangkarnya korban
     * itu sendiri, jadi tanpa jarak khusus kameranya menempel di kepalanya.
     *
     * <p>Sengaja dekat: yang harus terbaca adalah korbannya, bukan pemandangannya.
     * Dari jarak jauh badan korban tinggal beberapa piksel di tengah layar dan
     * ambruknya nyaris tidak kelihatan.</p>
     */
    private static final double CAMERA_OPEN_DISTANCE = 3.0D;
    /**
     * Sudut kamera di luar ruangan, tetap. Negatif berarti mendongak.
     *
     * <p>Sengaja tidak dihitung mengarah tepat ke kepala korban: dari jarak sedekat
     * ini sudut ke kepala hanya sekitar 24 derajat, dan bingkai yang tepat mengunci
     * kepala membuang sinar yang datang dari langit ke luar layar. Mendongak lebih
     * tinggi dari itu menaruh korban agak di bawah tengah dan menyisakan ruang di
     * atasnya untuk sinarnya turun.</p>
     */
    private static final float CAMERA_OPEN_PITCH = -30.0F;
    /**
     * Tinggi kamera di atas <b>kaki</b> korban saat di luar ruangan — sengaja rendah,
     * jadi sudutnya mendongak dari bawah. Lasernya turun dari langit, dan sudut rendah
     * yang melihat ke atas menangkap seluruh panjang sinarnya; kamera dari atas justru
     * membuang sinar itu ke luar bingkai.
     */
    private static final double CAMERA_OPEN_HEIGHT = 0.3D;
    /**
     * Kalau kamera berdiri hampir tepat di atas korban, sinar laser jadi segaris
     * dengan pandangan dan quad-nya menipis jadi tidak terlihat. Di bawah jarak
     * mendatar ini kamera digeser menyamping dulu.
     */
    private static final double CAMERA_MIN_HORIZONTAL = 1.5D;
    /** Tag entitas kamera, dipakai untuk membersihkan sisa yang tertinggal. */
    private static final String CAMERA_TAG = "jackofhearts_camera";

    /** Korban yang lasernya sedang mengunci; dieksekusi saat hitungannya habis. */
    private final List<UUID> pendingExecution = new ArrayList<>();
    /**
     * Yang ikut ditodong tapi tebakannya benar. Hasilnya baru diumumkan bersamaan
     * dengan tembakannya, bukan saat fase tebakan berakhir — kalau diumumkan lebih
     * awal, dia sudah tahu dia selamat sebelum lasernya selesai mengisi dan seluruh
     * adegannya kehilangan tegangan.
     */
    private final List<UUID> pendingSafe = new ArrayList<>();
    /** Adegan eksekusi yang sedang berjalan, per korban. */
    private final Map<UUID, ExecutionShot> executionCameras = new LinkedHashMap<>();
    /** Panjang fase eksekusi saat ini, dipakai menghitung kemajuan putaran pandangan. */
    private int executionTotalTicks;

    /**
     * Satu adegan eksekusi: kameranya, plus rencana putaran pandangan model korban.
     *
     * <p>Pandangan yang diputar adalah rotasi <b>model</b> korban, bukan kameranya.
     * Kameranya sudah dialihkan ke entitas lain, jadi memutar rotasi pemain tidak
     * menggeser bingkai sama sekali — yang terlihat hanya kepala dan badannya menoleh
     * ke arah datangnya laser.</p>
     */
    private static class ExecutionShot {
        final ArmorStand camera;
        /** Titik asal sinar. Disimpan karena mayatnya nanti roboh menjauh dari sini. */
        final Vec3 origin;
        final float startYaw;
        final float startPitch;
        final float targetYaw;
        final float targetPitch;

        ExecutionShot(ArmorStand camera, Vec3 origin,
                      float startYaw, float startPitch, float targetYaw, float targetPitch) {
            this.camera = camera;
            this.origin = origin;
            this.startYaw = startYaw;
            this.startPitch = startPitch;
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
        }
    }
    private int executionFireTick;

    public boolean isRunning() {
        return phase == GamePhase.REVEAL || phase == GamePhase.ROUND
                || phase == GamePhase.GUESS || phase == GamePhase.EXECUTION;
    }

    @Nullable
    public PlayerData getData(ServerPlayer player) {
        return participants.get(player.getUUID());
    }

    public Map<UUID, PlayerData> getParticipants() {
        return Collections.unmodifiableMap(participants);
    }

    // ------------------------------------------------------------------ registrasi

    /** @return false kalau pemain ini memang sudah terdaftar. */
    public boolean register(ServerPlayer player) {
        return registered.put(player.getUUID(), player.getGameProfile().getName()) == null;
    }

    /** @return false kalau pemain ini memang belum terdaftar. */
    public boolean unregister(ServerPlayer player) {
        return registered.remove(player.getUUID()) != null;
    }

    public int clearRegistered() {
        int size = registered.size();
        registered.clear();
        return size;
    }

    public Map<UUID, String> getRegistered() {
        return Collections.unmodifiableMap(registered);
    }

    /**
     * Nyalakan/matikan debug tebakan-selalu-benar untuk satu peserta. Berlaku untuk
     * ronde yang sedang berjalan saja — setelannya hilang sendiri begitu fase menebak
     * dinilai.
     *
     * @return true kalau nilainya benar-benar berubah
     */
    public boolean setAlwaysCorrect(ServerPlayer player, boolean value) {
        boolean changed = value
                ? alwaysCorrect.add(player.getUUID())
                : alwaysCorrect.remove(player.getUUID());
        PlayerData data = participants.get(player.getUUID());
        if (value && data != null && data.alive && phase == GamePhase.GUESS) {
            // Dinyalakan di tengah fase menebak: tebakannya diisikan sekarang juga,
            // supaya yang terlihat di HUD sama dengan yang akan dinilai nanti.
            data.guess = data.symbol;
            syncPlayer(player, data);
        }
        return changed;
    }

    public boolean isAlwaysCorrect(UUID uuid) {
        return alwaysCorrect.contains(uuid);
    }

    public boolean isRegistered(ServerPlayer player) {
        return registered.containsKey(player.getUUID());
    }

    /**
     * Pemain terdaftar yang sedang online. Yang terdaftar tapi sedang offline
     * dikumpulkan ke {@code missing} supaya pemanggil bisa melaporkannya.
     */
    public List<ServerPlayer> resolveRegistered(MinecraftServer server, List<String> missing) {
        List<ServerPlayer> online = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : registered.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                online.add(player);
            } else {
                missing.add(entry.getValue());
            }
        }
        return online;
    }

    // ------------------------------------------------------------------ start / stop

    /** Mengocok peran dan memulai ronde 1. Mengembalikan pesan error kalau gagal. */
    @Nullable
    public Component start(MinecraftServer server, List<ServerPlayer> pool) {
        List<ServerPlayer> players = new ArrayList<>(pool);
        players.removeIf(p -> p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR);

        int jackCount = JackConfig.CONFIG.jackCount.get();
        if (players.size() < jackCount + 1) {
            return Component.translatable("message.jackofhearts.not_enough_players", jackCount + 1)
                    .withStyle(ChatFormatting.RED);
        }

        participants.clear();
        panelDark.clear();
        panelQueue.clear();
        pendingWinner = null;
        pendingWinReason = null;
        // Setiap game mulai dari lantai yang bersih, termasuk kalau game sebelumnya
        // ditinggalkan begitu saja tanpa /jack stop.
        clearCorpses(server);
        RandomSource random = server.overworld().getRandom();
        Collections.shuffle(players, new Random(random.nextLong()));

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            PlayerData data = new PlayerData(player.getUUID(), player.getGameProfile().getName());
            data.role = i < jackCount ? Role.JACK : Role.ACE;
            participants.put(player.getUUID(), data);
        }

        round = 0;

        for (ServerPlayer player : players) {
            PlayerData data = participants.get(player.getUUID());
            player.sendSystemMessage(Component.translatable("message.jackofhearts.your_role", data.role.getDisplayName())
                    .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable("message.jackofhearts.role_hint_" + data.role.getId())
                    .withStyle(ChatFormatting.GRAY));
        }

        broadcast(server, Component.translatable("message.jackofhearts.game_started", players.size())
                .withStyle(ChatFormatting.GOLD));

        // Kafetaria ditata ulang dan stoknya dihitung dari jumlah pemain yang benar-
        // benar ikut, bukan dari jumlah yang terdaftar: yang sedang offline tidak
        // makan.
        HungerRules.clear();
        if (JackConfig.CONFIG.resetHungerOnStart.get()) {
            for (ServerPlayer player : players) {
                HungerRules.reset(player);
            }
        }
        Component cafeteriaWarning = Cafeteria.get().start(server, players.size());
        if (cafeteriaWarning != null) {
            broadcast(server, cafeteriaWarning);
        }

        syncRoster(server);
        beginReveal(server);
        return null;
    }

    public void stop(MinecraftServer server, boolean announce) {
        pendingExecution.clear();
        pendingSafe.clear();
        endBoardView(server);
        clearExecutionCameras(server);
        endDebugLaser(server);
        clearCorpses(server);
        setParticipantsInvulnerable(server, false);
        Cafeteria.get().stop(server);
        HungerRules.clear();
        for (ServerPlayer player : onlineParticipants(server)) {
            stripUniform(player);
        }
        participants.clear();
        panelDark.clear();
        panelQueue.clear();
        pendingWinner = null;
        pendingWinReason = null;
        syncRoster(server);
        phase = GamePhase.IDLE;
        round = 0;
        timer = 0;
        if (announce) {
            broadcast(server, Component.translatable("message.jackofhearts.game_stopped").withStyle(ChatFormatting.GRAY));
        }
        syncAll(server);
    }

    // ------------------------------------------------------------------ tick

    public void tick(MinecraftServer server) {
        // Di luar penjagaan isRunning() di bawah, dan sengaja paling awal: pintu sel
        // justru harus terkunci saat tidak ada game berjalan.
        syncPrisonDoors(server);
        // Di luar penjagaan isRunning() di bawah: pratinjau laser justru dipakai saat
        // tidak ada game yang berjalan, dan padamnya panel dijadwalkan melewati batas
        // fase.
        tickDebugLaser(server);
        tickPanelQueue(server);
        tickBoardView(server);
        tickPendingWin(server);
        // Di luar penjagaan isRunning() di bawah, dan memang harus: inilah yang
        // memotret penataan meja kafetaria, dan itu cuma boleh dilakukan saat tidak
        // ada game berjalan.
        Cafeteria.get().idleTick(server);

        if (!isRunning()) {
            return;
        }

        timer--;

        // Fase reveal dilewati: layarnya mengunci input pemain, dan membuat orang
        // yang tidak bisa bergerak jadi lapar bukan tantangan, cuma pajak.
        if (phase != GamePhase.REVEAL) {
            tickHunger(server);
        }

        if (phase == GamePhase.REVEAL) {
            if (timer <= 0) {
                endReveal(server);
            }
        } else if (phase == GamePhase.ROUND) {
            if (server.getTickCount() % 20 == 0) {
                enforceUniforms(server);
            }
            announceRoundCountdown(server);
            if (timer <= 0) {
                beginGuessPhase(server);
            }
        } else if (phase == GamePhase.GUESS) {
            announceGuessCountdown(server);
            if (timer <= 0) {
                resolveGuessPhase(server);
            }
        } else if (phase == GamePhase.EXECUTION) {
            tickExecutionCameras(server, timer);
            // Tembakannya jatuh di tengah fase, bukan di ujungnya: sisa waktunya
            // dipakai supaya sinar sempat memudar sebelum ronde berikutnya mulai.
            if (timer == executionFireTick) {
                resolveExecution(server);
            }
            if (timer <= 0) {
                endExecution(server);
            }
        }

        if (--syncCooldown <= 0) {
            syncCooldown = 5;
            syncAll(server);
        }
    }

    /**
     * Pintu sel terbuka <b>hanya</b> selama fase menebak; di fase mana pun selain itu
     * pintunya menutup dan terkunci.
     *
     * <p>Penyetelan penuh cuma dijalankan saat fasenya benar-benar berganti. Kalau
     * dijalankan tiap tick, pintu yang membanting sendiri karena dilewati peserta
     * akan langsung dibuka lagi — padahal membanting itu justru tujuannya.</p>
     */
    /**
     * Pintu sel dibuka kuncinya di <b>setiap</b> pergantian fase, tanpa syarat.
     *
     * <p>Yang menguncinya cuma satu hal, dan itu terjadi di luar sini: ada yang masuk
     * selagi fase menebak berjalan ({@link com.aflah.jackofhearts.block.PrisonDoorBlock}).
     * Karena kuncinya selalu dilepas di pergantian fase berikutnya, tidak ada seorang
     * pun yang bisa terkurung lebih lama dari satu fase menebak.</p>
     */
    /** Lapar yang dipercepat cuma berlaku untuk peserta yang masih hidup. */
    private void tickHunger(MinecraftServer server) {
        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data != null && data.alive) {
                HungerRules.tick(player);
            }
        }
    }

    private void syncPrisonDoors(MinecraftServer server) {
        if (phase != lastDoorPhase) {
            lastDoorPhase = phase;
            PrisonDoorTracker.applyToAll(server, false);
            return;
        }
        // Pintu yang baru dipasang atau chunk-nya baru dimuat menyusul di sini. Bukan
        // penyetelan penuh: pintu yang barusan mengunci karena dimasuki orang tidak
        // boleh ikut dibuka lagi di tick berikutnya.
        PrisonDoorTracker.applyToNew(server, false);
    }

    private void announceRoundCountdown(MinecraftServer server) {
        if (timer % 20 != 0) {
            return;
        }
        int seconds = timer / 20;
        if (seconds == 60 || seconds == 30 || seconds == 10) {
            broadcastParticipants(server, Component.translatable("message.jackofhearts.round_ending", seconds)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private void announceGuessCountdown(MinecraftServer server) {
        if (timer % 20 != 0) {
            return;
        }
        int seconds = timer / 20;
        if (seconds <= 0 || seconds > 5) {
            return;
        }
        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data != null && data.alive) {
                title(player, Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.RED),
                        Component.translatable("message.jackofhearts.pick_now").withStyle(ChatFormatting.GRAY),
                        0, 25, 5);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 1.0F, 1.0F);
            }
        }
    }

    // ------------------------------------------------------------------ fase

    /**
     * Fase animasi kocok kartu. Ronde 1 sengaja belum mulai menghitung di sini:
     * layar reveal mengunci input pemain sementara dunia tetap berjalan, jadi tanpa
     * fase terpisah Jack kehilangan waktu berburu dan peserta bisa dibunuh saat
     * tidak berdaya. Karena itu semua peserta juga dibuat kebal selama fase ini.
     */
    private void beginReveal(MinecraftServer server) {
        int seconds = JackConfig.CONFIG.roleRevealSeconds.get();
        if (seconds <= 0) {
            beginRound(server);
            return;
        }
        seconds = Math.max(seconds, MIN_REVEAL_SECONDS);

        phase = GamePhase.REVEAL;
        timer = seconds * 20;

        int totalPlayers = participants.size();
        int totalJacks = (int) participants.values().stream().filter(PlayerData::isJack).count();
        int cards = Math.min(totalPlayers, MAX_LINEUP_CARDS);
        int jacks = Math.min(totalJacks, cards);

        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data == null) {
                continue;
            }
            player.setInvulnerable(true);
            ModNetwork.toPlayer(player, new OpenRoleRevealPacket(data.role.ordinal(), timer, cards, jacks,
                    totalPlayers, totalJacks, false));
        }
        syncAll(server);
    }

    /**
     * Memutar animasi reveal untuk satu pemain tanpa menyentuh state game sama sekali.
     * Dipakai {@code /jack shuffle} buat menyetel animasi tanpa harus memulai game.
     */
    public void previewReveal(ServerPlayer player, int playerCount, int jacks, Role role) {
        int total = Math.max(playerCount, 1);
        int cards = Math.min(total, MAX_LINEUP_CARDS);
        ModNetwork.toPlayer(player, new OpenRoleRevealPacket(
                role.ordinal(), 0, cards, Math.min(jacks, cards), total, jacks, true));
    }

    private void endReveal(MinecraftServer server) {
        setParticipantsInvulnerable(server, false);
        beginRound(server);
    }

    private void setParticipantsInvulnerable(MinecraftServer server, boolean invulnerable) {
        for (ServerPlayer player : onlineParticipants(server)) {
            player.setInvulnerable(invulnerable);
        }
    }

    private void beginRound(MinecraftServer server) {
        round++;
        phase = GamePhase.ROUND;
        timer = JackConfig.CONFIG.roundSeconds.get() * 20;

        boolean reshuffle = round == 1 || JackConfig.CONFIG.reshuffleSymbolsEachRound.get();
        RandomSource random = server.overworld().getRandom();

        for (PlayerData data : participants.values()) {
            data.guess = null;
            if (reshuffle && data.alive) {
                data.symbol = Symbol.random(random);
            }
        }

        enforceUniforms(server);
        Cafeteria.get().onRoundStart(server);

        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data == null || !data.alive) {
                continue;
            }
            title(player,
                    Component.translatable("message.jackofhearts.round_title", round).withStyle(ChatFormatting.GOLD),
                    Component.translatable("message.jackofhearts.round_subtitle",
                            JackConfig.CONFIG.roundSeconds.get() / 60).withStyle(ChatFormatting.GRAY),
                    10, 60, 10);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6F, 1.2F);
        }
        syncAll(server);
    }

    private void beginGuessPhase(MinecraftServer server) {
        phase = GamePhase.GUESS;
        timer = JackConfig.CONFIG.guessSeconds.get() * 20;

        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data == null || !data.alive) {
                continue;
            }
            if (alwaysCorrect.contains(data.uuid)) {
                // Diisikan sejak awal, bukan cuma dinilai benar belakangan, supaya HUD
                // dan GUI-nya ikut menunjukkan tebakan yang akan lolos nanti.
                data.guess = data.symbol;
            }
            title(player, Component.translatable("message.jackofhearts.guess_title").withStyle(ChatFormatting.RED),
                    Component.translatable("message.jackofhearts.guess_subtitle",
                            JackConfig.CONFIG.guessSeconds.get()).withStyle(ChatFormatting.YELLOW),
                    5, 50, 10);
            player.playNotifySound(SoundEvents.ANVIL_LAND, SoundSource.MASTER, 0.7F, 1.0F);
        }
        syncAll(server);
    }

    private void resolveGuessPhase(MinecraftServer server) {
        List<ServerPlayer> wrong = new ArrayList<>();
        List<ServerPlayer> safe = new ArrayList<>();

        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data == null || !data.alive) {
                continue;
            }
            if (alwaysCorrect.contains(data.uuid) || (data.guess != null && data.guess == data.symbol)) {
                safe.add(player);
            } else {
                wrong.add(player);
            }
        }

        // Peserta yang offline saat waktu habis dianggap tidak menebak.
        for (PlayerData data : participants.values()) {
            if (data.alive && server.getPlayerList().getPlayer(data.uuid) == null) {
                data.alive = false;
                // Tidak ada laser dan tidak ada adegan, jadi panelnya padam sekarang
                // juga — jeda tiga detik itu milik eksekusi, bukan aturan umum.
                queuePanelOff(data.uuid, 1);
                broadcast(server, Component.translatable("message.jackofhearts.struck_offline", data.name)
                        .withStyle(ChatFormatting.RED));
            }
        }

        for (PlayerData data : participants.values()) {
            data.guess = null;
        }
        // Debug tebakan-selalu-benar berlaku satu ronde saja: ronde yang barusan
        // dinilai. Tanpa ini, sekali dinyalakan pesertanya kebal sampai game selesai.
        alwaysCorrect.clear();

        if (JackConfig.CONFIG.eliminateOnWrongGuess.get()) {
            // Selalu lewat fase eksekusi, termasuk ronde yang tidak memakan korban
            // sama sekali: yang menebak benar pun harus melihat lasernya mengisi daya
            // lalu padam. Kalau adegannya cuma muncul saat ada yang mati, adegan itu
            // sendiri sudah membocorkan hasilnya sebelum sinarnya jatuh.
            //
            // Hasilnya sengaja belum diumumkan ke siapa pun. Fase laser yang akan
            // mengumumkannya, tepat saat sinarnya jatuh.
            beginExecution(server, wrong, safe);
            return;
        }

        // Tidak ada eksekusi, jadi tidak ada yang perlu ditahan.
        for (ServerPlayer player : safe) {
            announceSafe(player);
        }
        for (ServerPlayer player : wrong) {
            announceWrong(player);
        }

        // Jalur ini cuma terpakai kalau eliminateOnWrongGuess dimatikan, jadi tidak
        // ada adegan laser yang perlu ditunggu selesai.
        scheduleBoardView(server, 0, BOARD_VIEW_LEAD);

        if (!checkWinConditions(server, true)) {
            beginRound(server);
        }
    }

    /** Kabar selamat: simbol aslinya baru disingkap di sini. */
    private void announceSafe(ServerPlayer player) {
        PlayerData data = participants.get(player.getUUID());
        if (data == null) {
            return;
        }
        player.sendSystemMessage(Component.translatable("message.jackofhearts.guess_correct",
                data.symbol.getLabel()).withStyle(ChatFormatting.GREEN));
        title(player, Component.translatable("message.jackofhearts.safe").withStyle(ChatFormatting.GREEN),
                data.symbol.getLabel(), 5, 40, 10);
    }

    private void announceWrong(ServerPlayer player) {
        PlayerData data = participants.get(player.getUUID());
        if (data == null) {
            return;
        }
        player.sendSystemMessage(Component.translatable("message.jackofhearts.guess_wrong",
                data.symbol.getLabel()).withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------------ eksekusi laser

    /**
     * Fase laser. Kematian sengaja ditunda sampai sinarnya benar-benar menembak, dan
     * ronde berikutnya baru mulai setelah seluruh fase selesai - kalau tidak, Jack
     * sudah bisa berburu sementara orang masih dieksekusi.
     *
     * <p>Yang tebakannya <b>benar</b> ikut masuk adegan ini, bukan dilewati. Dia
     * mendapat kamera yang sama, terminal yang sama, dan bola energi yang mengunci ke
     * arahnya sama persis — yang tidak dia dapat cuma tembakannya. Sampai muatannya
     * padam, tidak ada satu pun tanda yang membedakan dia dari yang akan mati.</p>
     *
     * @param safe yang menebak benar; ikut ditodong tapi tidak pernah ditembak.
     */
    private void beginExecution(MinecraftServer server, List<ServerPlayer> victims,
                                List<ServerPlayer> safe) {
        // Pratinjau memakai executionTotalTicks/executionFireTick yang sama. Kalau ada
        // yang masih berjalan, ditutup dulu supaya hitungannya tidak saling menimpa.
        endDebugLaser(server);

        phase = GamePhase.EXECUTION;
        timer = JackConfig.CONFIG.executionSeconds.get() * 20;
        executionTotalTicks = timer;
        executionFireTick = timer - LASER_CHARGE_TICKS;

        pendingExecution.clear();
        pendingSafe.clear();
        clearExecutionCameras(server);

        for (ServerPlayer victim : victims) {
            pendingExecution.add(victim.getUUID());
            Vec3 origin = fireLaserAt(victim, timer, true);
            beginCinematic(victim, origin, timer);
        }
        for (ServerPlayer survivor : safe) {
            pendingSafe.add(survivor.getUUID());
            Vec3 origin = fireLaserAt(survivor, timer, false);
            beginCinematic(survivor, origin, timer);
        }
        syncAll(server);
    }

    /**
     * Menaruh kamera eksekusi supaya korban menonton dirinya sendiri, dan bisa melihat
     * apakah dia kena atau selamat. Yang berpindah hanya pandangannya; badan korban
     * tetap di tempatnya.
     *
     * <p>Kameranya diikatkan ke Terminal Tebakan, bukan ke koordinat yang ditulis per
     * ruangan — kamu yang menaruh terminalnya, jadi posisinya otomatis benar di ruang
     * tebak mana pun.</p>
     */
    private void beginCinematic(ServerPlayer victim, Vec3 origin, int durationTicks) {
        ServerLevel level = victim.serverLevel();
        Vec3 head = victim.getEyePosition();

        BlockPos block = isInGuessRoom(victim) ? findGuessBlock(victim) : null;
        Vec3 camPos;
        float pitch;

        if (block != null) {
            // Di dalam ruangan: kamera duduk di atas Terminal Tebakan, tunduk tetap.
            Vec3 anchor = new Vec3(block.getX() + 0.5D, block.getY() + 1.0D, block.getZ() + 0.5D);
            camPos = clampToWall(level, victim, anchor, anchor.add(0.0D, CAMERA_HEIGHT, 0.0D));
            pitch = CAMERA_PITCH;
        } else {
            // Di luar ruangan tidak ada terminal untuk dijadikan jangkar. Kamera
            // mundur menyamping dan turun hampir ke tanah, lalu mendongak ke kepala
            // korban - sudut rendah inilah yang menangkap sinar dari langit sepanjang
            // layar.
            Vec3 look = victim.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0D, look.z);
            Vec3 dir = flat.lengthSqr() > 1.0E-4D ? flat.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
            Vec3 lowStart = new Vec3(victim.getX(), victim.getY() + CAMERA_OPEN_HEIGHT, victim.getZ());
            Vec3 wanted = lowStart.add(dir.scale(CAMERA_OPEN_DISTANCE));
            // Raycast-nya mendatar di ketinggian kamera, bukan dari kepala: kalau
            // ditarik dari kepala, sinarnya menukik dan langsung membentur tanah
            // sendiri, sehingga kamera selalu tertarik menempel ke korban.
            camPos = clampToWall(level, victim, lowStart, wanted);
            pitch = CAMERA_OPEN_PITCH;
        }

        // Kamera yang tepat di atas kepala membuat sinar segaris dengan pandangan,
        // dan sinar yang mengarah lurus ke kamera tidak punya lebar yang terlihat.
        double flatDist = Math.hypot(camPos.x - head.x, camPos.z - head.z);
        if (flatDist < CAMERA_MIN_HORIZONTAL) {
            Vec3 look = victim.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0D, look.z);
            Vec3 offset = flat.lengthSqr() > 1.0E-4D ? flat.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
            camPos = camPos.add(offset.scale(CAMERA_MIN_HORIZONTAL));
        }

        ArmorStand camera = EntityType.ARMOR_STAND.create(level);
        if (camera == null) {
            return;
        }
        camera.setPos(camPos.x, camPos.y, camPos.z);
        aimAt(camera, camPos, head, pitch);
        camera.setInvisible(true);
        camera.setNoGravity(true);
        camera.setInvulnerable(true);
        camera.setSilent(true);
        camera.setNoBasePlate(true);
        camera.addTag(CAMERA_TAG);
        // ArmorStand.setMarker private, jadi flag "tanpa hitbox" dipasang lewat NBT.
        CompoundTag tag = camera.saveWithoutId(new CompoundTag());
        tag.putBoolean("Marker", true);
        camera.load(tag);

        level.addFreshEntity(camera);

        // Sasaran pandangan korban adalah titik asal lasernya sendiri. Satu aturan
        // ini otomatis benar di dua-duanya: di dalam ruangan titik itu permukaan atas
        // Terminal Tebakan, jadi korban menoleh ke terminal; di luar ruangan titik itu
        // tepat di atas kepalanya, jadi korban mendongak ke langit.
        Vec3 d = origin.subtract(head);
        double flatDistance = Math.sqrt(d.x * d.x + d.z * d.z);
        // Kalau lasernya tegak lurus dari atas, arah mendatarnya tidak terdefinisi;
        // biarkan yaw korban apa adanya supaya dia tidak berputar tanpa alasan.
        float targetYaw = flatDistance < 1.0E-3D ? victim.getYRot() : yawTowards(head, origin);
        float targetPitch = pitchTowards(head, origin);

        executionCameras.put(victim.getUUID(), new ExecutionShot(camera, origin,
                victim.getYRot(), victim.getXRot(), targetYaw, targetPitch));

        // Sengaja TIDAK memakai ServerPlayer.setCamera: method itu ikut menteleport
        // pemain ke entitas kameranya. Paket sendiri ini memanggil
        // Minecraft.setCameraEntity di client, yang cuma mengganti sudut pandang dan
        // tidak menyentuh posisi sama sekali.
        ModNetwork.toPlayer(victim, new SetCameraPacket(camera.getId()));

        // Dibekukan supaya tidak berjalan keluar frame sementara kameranya diam.
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                durationTicks + 20, 255, false, false, false));
    }

    /**
     * Tiap tick: arahkan ulang kamera ke korbannya supaya tetap terkunci kalau dia
     * bergeser, dan putar pandangan model korban sedikit demi sedikit ke arah asal
     * laser sampai tembakan jatuh.
     *
     * @param remaining sisa tick adegan. Diterima sebagai parameter, bukan dibaca dari
     *                  {@link #timer}, supaya {@code /jack debug laser} bisa memakai
     *                  hitungannya sendiri tanpa menyentuh timer game.
     */
    private void tickExecutionCameras(MinecraftServer server, int remaining) {
        // Putaran pandangan selesai tepat saat laser menembak; sisa fase setelah itu
        // dibiarkan apa adanya karena korban sudah jadi penonton.
        int rotationTicks = Math.max(1, executionTotalTicks - executionFireTick);
        float progress = Mth.clamp((executionTotalTicks - remaining) / (float) rotationTicks, 0.0F, 1.0F);

        for (Map.Entry<UUID, ExecutionShot> entry : executionCameras.entrySet()) {
            ServerPlayer victim = server.getPlayerList().getPlayer(entry.getKey());
            ExecutionShot shot = entry.getValue();
            if (victim == null || !shot.camera.isAlive()) {
                continue;
            }
            aimYaw(shot.camera, shot.camera.position(), victim.getEyePosition());

            if (remaining > executionFireTick) {
                turnGazeTowards(victim, shot, progress);
            }
        }
    }

    /**
     * Memutar pandangan korban ke sasaran secara bertahap.
     *
     * <p>Dipakai {@link ServerPlayer#lookAt} — bukan sekadar {@code setYRot} — karena
     * rotasi pemain dikendalikan client: nilai yang diset di server akan langsung
     * ditimpa balik oleh paket rotasi client di tick berikutnya. {@code lookAt}
     * mengirim paket yang memaksa client memutar pandangannya, jadi perubahannya
     * bertahan dan ikut terlihat di client korban sendiri.</p>
     */
    private static void turnGazeTowards(ServerPlayer victim, ExecutionShot shot, float progress) {
        float yaw = Mth.rotLerp(progress, shot.startYaw, shot.targetYaw);
        float pitch = Mth.lerp(progress, shot.startPitch, shot.targetPitch);

        // lookAt hanya menerima titik, jadi sudut hasil interpolasi dikembalikan
        // menjadi titik di depan mata korban.
        Vec3 eye = victim.getEyePosition();
        victim.lookAt(EntityAnchorArgument.Anchor.EYES, eye.add(viewVector(yaw, pitch).scale(16.0D)));
    }

    /**
     * Datarkan pandangan pemain begitu lasernya jatuh, tanpa mengubah arah hadapnya.
     *
     * <p>Di luar ruangan sinarnya turun dari langit, jadi seluruh adegan dihabiskan
     * dengan mendongak — itu memang yang diinginkan selama lasernya mengisi. Tapi
     * begitu tembakannya selesai, pandangan itu tidak ada gunanya lagi: yang gugur
     * masuk mode penonton sambil menatap langit kosong, dan yang selamat kembali ke
     * permainan harus mencari arah sendiri lebih dulu.</p>
     *
     * <p>Dipakai {@code lookAt}, bukan {@code setXRot}, dengan alasan yang sama seperti
     * di {@link #turnGazeTowards}: rotasi pemain dikendalikan client, dan nilai yang
     * diset langsung di server akan ditimpa balik pada tick berikutnya.</p>
     */
    private static void levelGaze(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        player.lookAt(EntityAnchorArgument.Anchor.EYES,
                eye.add(viewVector(player.getYRot(), 0.0F).scale(16.0D)));
    }

    /** Arah pandang dari sepasang sudut, memakai rumus yang sama dengan vanilla. */
    private static Vec3 viewVector(float yaw, float pitch) {
        float x = pitch * ((float) Math.PI / 180.0F);
        float y = -yaw * ((float) Math.PI / 180.0F);
        float cosPitch = Mth.cos(x);
        return new Vec3(Mth.sin(y) * cosPitch, -Mth.sin(x), Mth.cos(y) * cosPitch);
    }

    private static float yawTowards(Vec3 from, Vec3 target) {
        Vec3 d = target.subtract(from);
        return (float) (Mth.atan2(d.z, d.x) * (180.0D / Math.PI)) - 90.0F;
    }

    /**
     * Hentikan titik kamera sebelum blok padat pertama, supaya tidak tertanam di
     * dalam tembok atau langit-langit dan pemain cuma melihat layar hitam.
     */
    private static Vec3 clampToWall(ServerLevel level, ServerPlayer victim, Vec3 from, Vec3 wanted) {
        BlockHitResult hit = level.clip(new ClipContext(from, wanted,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
        if (hit.getType() == HitResult.Type.MISS) {
            return wanted;
        }
        Vec3 d = hit.getLocation().subtract(from);
        double dist = Math.max(1.0D, d.length() - 0.4D);
        return from.add(d.normalize().scale(dist));
    }

    /** Sudut tunduk yang tepat mengarah dari {@code from} ke {@code target}. */
    private static float pitchTowards(Vec3 from, Vec3 target) {
        Vec3 d = target.subtract(from);
        return (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0D / Math.PI)));
    }

    private static void aimAt(ArmorStand camera, Vec3 from, Vec3 target, float pitch) {
        camera.setXRot(pitch);
        aimYaw(camera, from, target);
    }

    /**
     * Hanya memutar kiri-kanan. Pitch sengaja tidak disentuh supaya sudut tunduk yang
     * sudah ditetapkan saat kamera dipasang tetap bertahan, baik yang dikunci 60
     * derajat di dalam ruangan maupun yang dihitung di luar ruangan.
     */
    private static void aimYaw(ArmorStand camera, Vec3 from, Vec3 target) {
        Vec3 d = target.subtract(from);
        float yaw = (float) (Mth.atan2(d.z, d.x) * (180.0D / Math.PI)) - 90.0F;
        camera.setYRot(yaw);
        camera.setYHeadRot(yaw);
    }

    /** Kembalikan pandangan semua orang dan buang entitas kameranya. */
    private void clearExecutionCameras(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetwork.toPlayer(player, new SetCameraPacket(-1));
        }
        for (ExecutionShot shot : executionCameras.values()) {
            shot.camera.discard();
        }
        executionCameras.clear();

        // Jaring pengaman: kamera dari sesi yang berakhir tidak wajar tidak ada di
        // daftar mana pun lagi, jadi disapu lewat tag-nya di seluruh dunia.
        for (ServerLevel level : server.getAllLevels()) {
            for (ArmorStand stray : level.getEntities(EntityType.ARMOR_STAND,
                    e -> e.getTags().contains(CAMERA_TAG))) {
                stray.discard();
            }
        }
    }

    /**
     * Kirim sinar ke semua penonton di sekitar dan bunyikan suara penguncian.
     *
     * @param fires false berarti gertakan: terminalnya mengisi daya sampai penuh lalu
     *             padam tanpa menembak. Suara penguncian tetap dibunyikan dan paketnya
     *             tetap dikirim ke semua orang — kalau yang selamat dibedakan sejak
     *             awal, penguncian laser jadi pengumuman kematian.
     */
    private Vec3 fireLaserAt(ServerPlayer victim, int totalTicks, boolean fires) {
        ServerLevel level = victim.serverLevel();
        // Setinggi mata: itu posisi kepala. Client tetap mengikuti entitasnya tiap
        // frame, titik ini hanya cadangan kalau entitasnya tidak termuat di sana.
        Vec3 target = victim.getEyePosition();
        Vec3 origin = laserOrigin(victim, target);

        LaserPacket packet = new LaserPacket(origin, target, victim.getId(), LASER_CHARGE_TICKS, totalTicks, fires);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.position().closerThan(target, LASER_VIEW_RANGE)) {
                ModNetwork.toPlayer(viewer, packet);
            }
        }
        level.playSound(null, origin.x, origin.y, origin.z,
                ModSounds.LASER_CHARGE.get(), SoundSource.MASTER, 2.0F, 1.0F);
        return origin;
    }

    /**
     * Sinar keluar dari Terminal Tebakan kalau korban berdiri di lantai penanda ruang
     * tebak; selain itu turun lurus dari langit.
     */
    private Vec3 laserOrigin(ServerPlayer victim, Vec3 target) {
        if (isInGuessRoom(victim)) {
            BlockPos block = findGuessBlock(victim);
            if (block != null) {
                // Muka atas blok, tempat bagian merah teksturnya berada.
                return new Vec3(block.getX() + 0.5D, block.getY() + 1.02D, block.getZ() + 0.5D);
            }
        }
        return new Vec3(target.x, victim.serverLevel().getMaxBuildHeight(), target.z);
    }

    /** Cek blok penanda di bawah kaki korban, beberapa blok ke bawah. */
    private boolean isInGuessRoom(ServerPlayer victim) {
        Block marker = roomMarkerBlock();
        return marker != null && standsOnRoomMarker(victim, marker);
    }

    /**
     * Blok penanda lantai ruang tebak, atau null kalau isinya tidak dikenal.
     *
     * <p>Peringatannya cuma keluar sekali. Fungsi ini dipanggil berkali-kali per detik
     * untuk memeriksa isi ruangan, dan log yang membanjir justru menutupi masalahnya
     * sendiri.</p>
     */
    @Nullable
    Block roomMarkerBlock() {
        Block marker = ForgeRegistries.BLOCKS.getValue(
                ResourceLocation.tryParse(JackConfig.CONFIG.roomMarkerBlock.get()));
        if (marker == null && !warnedMissingMarker) {
            warnedMissingMarker = true;
            JackOfHeartsMod.LOGGER.warn(
                    "[JackOfHearts] roomMarkerBlock '{}' tidak dikenal: laser memakai langit, "
                            + "dan Pintu Sel tidak akan pernah tahu ruangannya masih terisi.",
                    JackConfig.CONFIG.roomMarkerBlock.get());
        }
        return marker;
    }

    /** Ada blok penanda di bawah kaki pemain, sampai {@code roomMarkerDepth} ke bawah. */
    boolean standsOnRoomMarker(ServerPlayer player, Block marker) {
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition();
        int depth = JackConfig.CONFIG.roomMarkerDepth.get();
        for (int dy = 1; dy <= depth; dy++) {
            if (level.getBlockState(feet.below(dy)).is(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Terminal yang menembak: yang terakhir diklik korban, atau kalau dia tidak pernah
     * menebak sama sekali, yang terdekat di sekitarnya.
     */
    @Nullable
    BlockPos findGuessBlock(ServerPlayer victim) {
        ServerLevel level = victim.serverLevel();
        PlayerData data = participants.get(victim.getUUID());

        if (data != null && data.lastGuessBlock != null
                && level.getBlockState(data.lastGuessBlock).is(ModBlocks.GUESS_BLOCK.get())) {
            return data.lastGuessBlock;
        }

        BlockPos feet = victim.blockPosition();
        BlockPos sameRoom = nearestReachableGuessBlock(level, feet);
        return sameRoom != null ? sameRoom : nearestGuessBlock(level, feet);
    }

    /**
     * Terminal terdekat yang benar-benar <b>bisa dijangkau dengan berjalan</b> dari
     * tempat korban berdiri.
     *
     * <p>Terdekat saja tidak cukup: jarak diukur lurus menembus tembok, jadi terminal
     * di kamar sebelah sering lebih dekat daripada terminal di kamar yang sedang
     * ditempati — dan lasernya keluar dari kamar orang lain. Garis pandang juga tidak
     * cukup, karena ia lolos lewat pintu yang kebetulan sedang terbuka.</p>
     *
     * <p>Jadi ruangannya ditelusuri seperti orang berjalan: petak demi petak lewat
     * ruang yang bisa dilalui. Tembok menghentikannya, dan pintu sel yang tertutup
     * juga — {@code isPathfindable} sebuah pintu bernilai false selama daunnya
     * tertutup. Itu persis arti "kamar dia".</p>
     */
    @Nullable
    private BlockPos nearestReachableGuessBlock(ServerLevel level, BlockPos from) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(from.immutable());
        queue.add(from.immutable());

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        while (!queue.isEmpty() && visited.size() < ROOM_FLOOD_LIMIT) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (Math.abs(next.getX() - from.getX()) > GUESS_BLOCK_SEARCH
                        || Math.abs(next.getY() - from.getY()) > GUESS_BLOCK_SEARCH
                        || Math.abs(next.getZ() - from.getZ()) > GUESS_BLOCK_SEARCH
                        || !visited.add(next.immutable())) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.is(ModBlocks.GUESS_BLOCK.get())) {
                    double d = next.distSqr(from);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = next.immutable();
                    }
                    // Terminalnya blok padat: ruangan tidak diteruskan menembusnya.
                    continue;
                }
                if (state.isPathfindable(level, next, PathComputationType.LAND)) {
                    queue.add(next.immutable());
                }
            }
        }
        return best;
    }

    /** Terminal terdekat apa adanya, tanpa peduli tembok. Jaring pengaman terakhir. */
    @Nullable
    private BlockPos nearestGuessBlock(ServerLevel level, BlockPos feet) {
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                feet.offset(-GUESS_BLOCK_SEARCH, -GUESS_BLOCK_SEARCH, -GUESS_BLOCK_SEARCH),
                feet.offset(GUESS_BLOCK_SEARCH, GUESS_BLOCK_SEARCH, GUESS_BLOCK_SEARCH))) {
            if (level.getBlockState(pos).is(ModBlocks.GUESS_BLOCK.get())) {
                double d = pos.distSqr(feet);
                if (d < best) {
                    best = d;
                    nearest = pos.immutable();
                }
            }
        }
        return nearest;
    }

    /** Saat sinar benar-benar menembak: korban mati, disertai ledakan kecil. */
    private void resolveExecution(MinecraftServer server) {
        List<ServerPlayer> hit = new ArrayList<>();
        for (UUID uuid : pendingExecution) {
            ServerPlayer victim = server.getPlayerList().getPlayer(uuid);
            PlayerData data = participants.get(uuid);
            if (data != null) {
                data.alive = false;
            }
            if (victim != null) {
                hit.add(victim);
            }
        }
        pendingExecution.clear();

        int boardStart = boardViewStartDelay(hit.size());

        for (int i = 0; i < hit.size(); i++) {
            ServerPlayer victim = hit.get(i);
            ExecutionShot shot = executionCameras.get(victim.getUUID());
            // Panelnya padam satu per satu, bukan serentak. Kalau tiga orang gugur
            // bersamaan dan papannya mati sekaligus, yang terdengar cuma satu bunyi
            // dan tidak ada yang sempat melihat siapa saja yang hilang.
            // Panelnya padam setelah kamera sampai di depan papan, bukan sebelumnya:
            // panel yang padam saat tidak ada yang menonton sama saja tidak padam.
            queuePanelOff(victim.getUUID(), boardStart + BOARD_VIEW_LEAD + i * PANEL_OFF_STAGGER);
            // Simbol aslinya disingkap tepat sebelum sinarnya jatuh, bukan saat fase
            // tebakan berakhir.
            announceWrong(victim);
            strikeVictim(victim, shot == null ? null : shot.origin);

            broadcast(server, Component.translatable("message.jackofhearts.struck",
                    victim.getGameProfile().getName()).withStyle(ChatFormatting.RED));
            victim.sendSystemMessage(Component.translatable("message.jackofhearts.now_spectator")
                    .withStyle(ChatFormatting.GRAY));
        }

        // Papan baru menyala setelah semua korban ronde ini terdaftar, jadi lamanya
        // adegan menyesuaikan berapa panel yang harus padam.
        scheduleBoardView(server, hit.size(), boardStart);

        // Detik yang sama untuk yang selamat: muatannya padam dan barulah dia tahu.
        for (UUID uuid : pendingSafe) {
            ServerPlayer survivor = server.getPlayerList().getPlayer(uuid);
            if (survivor == null) {
                continue;
            }
            ExecutionShot shot = executionCameras.get(uuid);
            Vec3 at = shot != null ? shot.origin : survivor.getEyePosition();
            // Bunyinya keluar dari terminalnya, bukan dari pemainnya: yang mati listrik
            // memang mesinnya.
            survivor.serverLevel().playSound(null, at.x, at.y, at.z,
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.4F, 1.6F);
            levelGaze(survivor);
            announceSafe(survivor);
        }
        pendingSafe.clear();
    }

    /**
     * Detik tumbukannya: ledakan kecil, mayat muncul, korban berpindah jadi penonton.
     *
     * <p>Korban laser tidak dibunuh, hanya dijadikan penonton: tidak ada layar
     * kematian, tidak ada isi tas yang tercecer, dan tidak ada respawn yang harus
     * ditangani. Konsekuensinya tidak ada animasi kematian vanilla yang bisa dipakai
     * dan badannya akan hilang begitu saja dalam satu frame — itulah yang digantikan
     * {@link CorpseEntity}. Mayatnya muncul di tick yang sama persis dengan perpindahan
     * ke mode penonton, jadi pertukarannya tidak terlihat.</p>
     *
     * <p>Seragamnya dilepas di sini karena {@code LivingDeathEvent} yang biasanya
     * mengurus itu memang tidak akan terpicu.</p>
     */
    private void strikeVictim(ServerPlayer victim, @Nullable Vec3 laserOrigin) {
        ServerLevel level = victim.serverLevel();
        Vec3 at = victim.getEyePosition();

        level.playSound(null, at.x, at.y, at.z,
                ModSounds.LASER_FIRE.get(), SoundSource.MASTER, 2.0F, 1.0F);
        // level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 6, 0.3D, 0.4D, 0.3D, 0.0D);
        // level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, 0.25D, 0.4D, 0.25D, 0.06D);
        Blood.spurt(level, at, laserOrigin);

        // Urutannya penting: mayat dibuat lebih dulu karena isi slot armor korban
        // disalin apa adanya. Kalau stripUniform jalan duluan, mayatnya keluar tanpa
        // seragam.
        spawnCorpse(victim, laserOrigin);

        levelGaze(victim);
        stripUniform(victim);
        victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        victim.setInvulnerable(false);
        victim.setGameMode(GameType.SPECTATOR);
    }

    private void spawnCorpse(ServerPlayer victim, @Nullable Vec3 laserOrigin) {
        ServerLevel level = victim.serverLevel();
        CorpseEntity corpse = ModEntities.CORPSE.get().create(level);
        if (corpse == null) {
            return;
        }
        corpse.copyFrom(victim, laserOrigin);
        level.addFreshEntity(corpse);
    }

    /** Sapu semua mayat di semua dimensi. */
    public int clearCorpses(MinecraftServer server) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (CorpseEntity corpse : level.getEntities(ModEntities.CORPSE.get(), e -> true)) {
                corpse.discard();
                removed++;
            }
        }
        return removed;
    }

    private void endExecution(MinecraftServer server) {
        // Yang selamat ikut dibekukan selama adegannya, dan dia tidak lewat
        // strikeVictim seperti korban — jadi perlambatannya dilepas di sini.
        for (UUID uuid : executionCameras.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }
        clearExecutionCameras(server);
        if (!checkWinConditions(server, true)) {
            beginRound(server);
        }
    }

    // ------------------------------------------------------------------ pratinjau laser

    /**
     * Sasaran pratinjau beserta mode permainannya sebelum ditembak, supaya bisa
     * dikembalikan setelah adegannya selesai. Sengaja terpisah dari
     * {@link #pendingExecution} agar pratinjau tidak pernah menyentuh state game.
     */
    private final Map<UUID, GameType> debugVictims = new LinkedHashMap<>();
    private int debugTimer;
    private boolean debugRunning;
    /** false berarti pratinjau gertakan: mengisi daya lalu padam tanpa menembak. */
    private boolean debugFires;

    /**
     * Menjalankan satu adegan eksekusi utuh di luar permainan: kamera sinematik,
     * pandangan korban yang berputar ke arah laser, sinarnya, lalu ambruk dan mayat
     * yang ditinggalkan.
     *
     * <p>Jalurnya persis sama dengan eksekusi sungguhan — memakai {@link #fireLaserAt}
     * dan {@link #beginCinematic} yang sama — jadi arah datangnya sinar juga ditentukan
     * dengan aturan yang sama: keluar dari Terminal Tebakan kalau ada blok penanda
     * ruang (bawaannya beehive) di bawah kaki, kalau tidak turun lurus dari langit.
     * Kalau jalurnya dijiplak ulang di sini, pratinjaunya bisa terlihat benar padahal
     * yang asli sudah berubah.</p>
     *
     * <p>Mode permainan sasaran dikembalikan sendiri setelah adegannya selesai supaya
     * yang menguji tidak tertinggal jadi penonton. Mayatnya sengaja dibiarkan.</p>
     *
     * @param fires false untuk melihat versi gertakannya — yang didapat orang yang
     *              tebakannya benar: mengisi daya penuh lalu padam tanpa menembak.
     * @return pesan kegagalan, atau null kalau pratinjaunya jalan.
     */
    @Nullable
    public Component debugLaser(MinecraftServer server, List<ServerPlayer> targets, boolean fires) {
        if (phase == GamePhase.EXECUTION) {
            return Component.literal("Fase eksekusi sedang berjalan. Tunggu sampai selesai.")
                    .withStyle(ChatFormatting.RED);
        }
        if (debugRunning) {
            return Component.literal("Pratinjau laser masih berjalan.").withStyle(ChatFormatting.RED);
        }

        List<ServerPlayer> victims = new ArrayList<>(targets);
        victims.removeIf(v -> v.gameMode.getGameModeForPlayer() == GameType.SPECTATOR);
        if (victims.isEmpty()) {
            return Component.literal("Tidak ada sasaran yang bisa ditembak; penonton dilewati.")
                    .withStyle(ChatFormatting.RED);
        }

        int total = JackConfig.CONFIG.executionSeconds.get() * 20;
        debugRunning = true;
        debugFires = fires;
        debugTimer = total;
        executionTotalTicks = total;
        executionFireTick = total - LASER_CHARGE_TICKS;

        clearExecutionCameras(server);
        debugVictims.clear();

        for (ServerPlayer victim : victims) {
            debugVictims.put(victim.getUUID(), victim.gameMode.getGameModeForPlayer());
            Vec3 origin = fireLaserAt(victim, total, fires);
            beginCinematic(victim, origin, total);
        }
        return null;
    }

    private void tickDebugLaser(MinecraftServer server) {
        if (!debugRunning) {
            return;
        }
        debugTimer--;
        tickExecutionCameras(server, debugTimer);

        if (debugTimer == executionFireTick) {
            for (UUID uuid : debugVictims.keySet()) {
                ServerPlayer victim = server.getPlayerList().getPlayer(uuid);
                if (victim == null) {
                    continue;
                }
                ExecutionShot shot = executionCameras.get(uuid);
                Vec3 origin = shot == null ? null : shot.origin;
                if (debugFires) {
                    strikeVictim(victim, origin);
                } else if (origin != null) {
                    victim.serverLevel().playSound(null, origin.x, origin.y, origin.z,
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.4F, 1.6F);
                }
            }
        }
        if (debugTimer <= 0) {
            endDebugLaser(server);
        }
    }

    // ------------------------------------------------------------------ papan peserta

    /** Peserta yang panelnya sudah padam. */
    private final Set<UUID> panelDark = new LinkedHashSet<>();
    /** Panel yang sudah dijadwalkan padam tapi belum sampai gilirannya. */
    private final List<PendingPanel> panelQueue = new ArrayList<>();

    /**
     * Jeda dari tembakan sampai panelnya padam.
     *
     * <p>Lima detik, jadi jatuhnya jauh <b>setelah</b> fase eksekusi berakhir dan sudah
     * masuk ronde berikutnya — fase eksekusi bawaannya cuma tiga detik dan tembakannya
     * jatuh di tengah. Itu memang disengaja: papan padam sebagai ketukan tersendiri
     * setelah adegannya selesai, bukan sebagai bagian dari adegannya.</p>
     */
    private static final int PANEL_OFF_DELAY = 100;
    /** Jarak antar panel kalau lebih dari satu orang gugur di ronde yang sama. */
    private static final int PANEL_OFF_STAGGER = 14;

    /**
     * Jarak kamera papan dari bidang papan.
     *
     * <p>Papannya 13 blok lebar. Dengan sudut pandang bawaan, jarak sembilan blok
     * memuat seluruh bidangnya beserta sedikit pinggiran; lebih dekat dari itu ujung
     * kiri dan kanannya terpotong. Kalau ruangannya tidak sedalam ini, kameranya
     * dimundurkan sejauh yang muat saja.</p>
     */
    private static final double BOARD_VIEW_DISTANCE = 9.0D;
    /** Kamera papan menyala sedikit sebelum panel pertama padam, jadi tidak ada yang terlewat. */
    private static final int BOARD_VIEW_LEAD = 10;
    /** Berapa lama kamera bertahan setelah panel terakhir padam. */
    private static final int BOARD_VIEW_TAIL = 40;
    /**
     * Jeda dari adegan laser <i>terpanjang</i> sampai kamera berpindah ke papan.
     *
     * <p>Diukur dari ujung adegan, bukan dari saat sinarnya jatuh: yang paling lama
     * selesai belum tentu tembakannya — sinar yang memudar dan mayat yang ambruk
     * jalannya sendiri-sendiri. Lihat {@link #boardViewStartDelay(int)}.</p>
     */
    private static final int SCENE_TO_BOARD_TICKS = 100;

    /** Pemenang yang sudah ditentukan tapi pengumumannya menunggu papan selesai. */
    @Nullable
    private Role pendingWinner;
    @Nullable
    private Component pendingWinReason;

    /** Hitung mundur sampai kamera papan menyala; 0 berarti tidak ada yang dijadwalkan. */
    private int boardViewDelay;
    /**
     * Lama kamera papan akan menyala nanti, disimpan terpisah dari sisa waktunya.
     *
     * <p>Harus dua nilai, bukan satu. Kalau sisa waktu diisi sejak dijadwalkan, dia
     * ikut berkurang selama masa tunggu — dan karena tunggunya lebih lama daripada
     * tayangnya, hitungannya habis sebelum kameranya sempat menyala, lalu penutupnya
     * menolkan jadwal yang belum jalan. Kameranya tidak pernah muncul sama sekali.</p>
     */
    private int boardViewDuration;
    /** Sisa waktu kamera papan menyala; 0 berarti tidak sedang menyala. */
    private int boardViewTicks;
    @Nullable
    private ArmorStand boardCamera;

    private static class PendingPanel {
        final UUID uuid;
        int ticksLeft;

        PendingPanel(UUID uuid, int ticksLeft) {
            this.uuid = uuid;
            this.ticksLeft = ticksLeft;
        }
    }

    /** Jadwalkan satu panel padam. Jadwal lama untuk orang yang sama diganti. */
    private void queuePanelOff(UUID uuid, int delayTicks) {
        panelQueue.removeIf(pending -> pending.uuid.equals(uuid));
        panelQueue.add(new PendingPanel(uuid, Math.max(1, delayTicks)));
    }

    /**
     * Antrean padamnya panel berjalan di luar penjagaan fase, karena jadwalnya memang
     * melewati batas fase: yang gugur di fase eksekusi baru padam ketika ronde
     * berikutnya sudah dimulai.
     */
    private void tickPanelQueue(MinecraftServer server) {
        if (panelQueue.isEmpty()) {
            return;
        }
        boolean changed = false;
        Iterator<PendingPanel> it = panelQueue.iterator();
        while (it.hasNext()) {
            PendingPanel pending = it.next();
            if (--pending.ticksLeft > 0) {
                continue;
            }
            it.remove();
            if (panelDark.add(pending.uuid)) {
                ModNetwork.toAll(new PanelOffPacket(pending.uuid));
                changed = true;
            }
        }
        if (changed) {
            syncRoster(server);
        }
    }

    /**
     * Alihkan pandangan semua orang ke Papan Peserta setelah eksekusi, supaya mereka
     * melihat sendiri panel siapa saja yang padam.
     *
     * <p>Satu entitas kamera dipakai bersama oleh semua pemain, bukan satu per orang:
     * yang ditonton sama persis untuk semua orang, jadi tidak ada gunanya membuat dua
     * puluh armor stand di titik yang sama.</p>
     *
     * <p>Kalau tidak ada papan yang termuat, tidak terjadi apa-apa sama sekali —
     * termasuk tidak ada yang dibekukan. Membekukan semua orang untuk menonton papan
     * yang tidak ada adalah cara paling pasti untuk merusak satu ronde penuh.</p>
     */
    /**
     * Berapa tick setelah sinar jatuh sampai kamera boleh berpindah ke papan.
     *
     * <p>Diambil dari adegan yang paling lama selesai, lalu ditambah
     * {@link #SCENE_TO_BOARD_TICKS}. Ada dua adegan yang jalan bersamaan dan panjangnya
     * beda: sinarnya sendiri baru habis memudar {@value #LASER_CHARGE_TICKS} tick
     * setelah jatuh (juga untuk yang gertakan dan yang turun dari langit), sementara
     * korban yang ambruk butuh {@link CorpseEntity#ANIM_TICKS} tick. Ronde tanpa korban
     * tidak punya adegan ambruk sama sekali, jadi yang dihitung cuma sinarnya.</p>
     */
    private int boardViewStartDelay(int deaths) {
        int longestScene = Math.max(LASER_CHARGE_TICKS, deaths > 0 ? CorpseEntity.ANIM_TICKS : 0);
        return longestScene + SCENE_TO_BOARD_TICKS;
    }

    /**
     * @param deaths     berapa panel yang akan padam selama adegan. 0 berarti ronde ini
     *                   tidak memakan korban — papannya tetap ditampilkan, justru itu
     *                   kabarnya: semua orang masih ada. Yang tidak dikerjakan cuma
     *                   memadamkan panel, karena memang tidak ada yang mati.
     * @param delayTicks jeda sampai kameranya berpindah.
     */
    private void scheduleBoardView(MinecraftServer server, int deaths, int delayTicks) {
        boardViewDelay = Math.max(1, delayTicks);
        boardViewDuration = BOARD_VIEW_LEAD + deaths * PANEL_OFF_STAGGER + BOARD_VIEW_TAIL;
    }

    private void startBoardView(MinecraftServer server) {
        // Dua adegan tidak boleh berebut kamera yang sama. Dengan executionSeconds
        // bawaan fase eksekusi sudah selesai jauh sebelum ini, tapi kalau diperpanjang
        // adegannya masih berjalan — dalam hal itu papan menunggu gilirannya, bukan
        // memotong di tengah.
        if (phase == GamePhase.EXECUTION) {
            boardViewDelay = 1;
            return;
        }

        List<ServerPlayer> viewers = server.getPlayerList().getPlayers();
        if (viewers.isEmpty()) {
            return;
        }

        ServerPlayer anchor = viewers.get(0);
        ServerLevel level = anchor.serverLevel();
        BlockPos monitor = MonitorTracker.nearest(level.dimension(), anchor.position());
        if (monitor == null) {
            return;
        }

        BlockState state = level.getBlockState(monitor);
        if (!state.hasProperty(MonitorBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(MonitorBlock.FACING);
        Direction right = facing.getCounterClockWise();
        Vec3 rightVec = new Vec3(right.getStepX(), 0.0D, right.getStepZ());
        Vec3 faceVec = new Vec3(facing.getStepX(), 0.0D, facing.getStepZ());

        // Titik tengah bidang papan, dihitung dari sudut kiri-bawahnya — yaitu blok
        // pengendali itu sendiri.
        Vec3 corner = new Vec3(monitor.getX() + 0.5D, monitor.getY(), monitor.getZ() + 0.5D)
                .subtract(rightVec.scale(0.5D))
                .add(faceVec.scale(0.5D));
        Vec3 center = corner
                .add(rightVec.scale(MonitorBlock.BOARD_WIDTH / 2.0D))
                .add(0.0D, MonitorBlock.BOARD_HEIGHT / 2.0D, 0.0D);

        Vec3 wanted = center.add(faceVec.scale(BOARD_VIEW_DISTANCE));
        Vec3 camPos = clampToWall(level, anchor, center.add(faceVec.scale(0.5D)), wanted);

        ArmorStand camera = EntityType.ARMOR_STAND.create(level);
        if (camera == null) {
            return;
        }
        // Sisa waktu baru diisi di sini, saat kameranya benar-benar jadi.
        boardViewTicks = boardViewDuration;
        camera.setPos(camPos.x, camPos.y, camPos.z);
        aimAt(camera, camPos, center, pitchTowards(camPos, center));
        camera.setInvisible(true);
        camera.setNoGravity(true);
        camera.setInvulnerable(true);
        camera.setSilent(true);
        camera.setNoBasePlate(true);
        camera.addTag(CAMERA_TAG);
        CompoundTag tag = camera.saveWithoutId(new CompoundTag());
        tag.putBoolean("Marker", true);
        camera.load(tag);
        level.addFreshEntity(camera);
        boardCamera = camera;

        for (ServerPlayer viewer : viewers) {
            ModNetwork.toPlayer(viewer, new SetCameraPacket(camera.getId()));
            if (viewer.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                viewer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        boardViewTicks + 20, 255, false, false, false));
            }
        }
    }

    private void endBoardView(MinecraftServer server) {
        boardViewTicks = 0;
        boardViewDelay = 0;
        boardViewDuration = 0;
        if (boardCamera != null) {
            boardCamera.discard();
            boardCamera = null;
        }
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            ModNetwork.toPlayer(viewer, new SetCameraPacket(-1));
            viewer.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    private void tickBoardView(MinecraftServer server) {
        if (boardViewDelay > 0 && --boardViewDelay == 0) {
            startBoardView(server);
        }
        if (boardViewTicks > 0 && --boardViewTicks == 0) {
            endBoardView(server);
        }
    }

    /**
     * Kirim ulang isi papan ke semua client.
     *
     * <p>Sebelum permainan dimulai isinya diambil dari daftar terdaftar, jadi papannya
     * sudah menampilkan barisan peserta sambil host menyusun roster — bukan menyala
     * mendadak begitu permainan dimulai.</p>
     */
    public void syncRoster(MinecraftServer server) {
        List<RosterPacket.Entry> entries = new ArrayList<>();
        if (participants.isEmpty()) {
            for (Map.Entry<UUID, String> entry : registered.entrySet()) {
                entries.add(new RosterPacket.Entry(entry.getKey(), entry.getValue(), false));
            }
        } else {
            for (PlayerData data : participants.values()) {
                entries.add(new RosterPacket.Entry(data.uuid, data.name, panelDark.contains(data.uuid)));
            }
        }
        ModNetwork.toAll(new RosterPacket(entries));
    }

    /** Lepas kamera dan kembalikan mode permainan sasaran. Mayatnya tetap ditinggal. */
    private void endDebugLaser(MinecraftServer server) {
        if (!debugRunning) {
            return;
        }
        debugRunning = false;
        debugTimer = 0;
        clearExecutionCameras(server);

        for (Map.Entry<UUID, GameType> entry : debugVictims.entrySet()) {
            ServerPlayer victim = server.getPlayerList().getPlayer(entry.getKey());
            if (victim == null) {
                continue;
            }
            victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            victim.setGameMode(entry.getValue());
            victim.sendSystemMessage(Component.literal(
                            "Pratinjau selesai. Mayatnya ditinggal di tempat; /jack debug clearcorpse untuk menyapunya.")
                    .withStyle(ChatFormatting.GRAY));
        }
        debugVictims.clear();
    }

    // ------------------------------------------------------------------ tebakan

    public void onGuessBlockUsed(ServerPlayer player, BlockPos pos) {
        PlayerData data = participants.get(player.getUUID());
        if (data != null) {
            // Dicatat walau tebakannya belum disimpan: terminal ini yang akan
            // menembaknya kalau nanti dia salah.
            data.lastGuessBlock = pos.immutable();
        }
        if (data == null) {
            player.sendSystemMessage(Component.translatable("message.jackofhearts.not_participant")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!data.alive) {
            player.sendSystemMessage(Component.translatable("message.jackofhearts.eliminated_already")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (phase != GamePhase.GUESS) {
            player.sendSystemMessage(Component.translatable("message.jackofhearts.not_guess_time")
                    .withStyle(ChatFormatting.RED));
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 0.7F, 0.7F);
            return;
        }
        ModNetwork.toPlayer(player, new OpenGuessScreenPacket(data.guess == null ? -1 : data.guess.ordinal(), timer));
    }

    public void submitGuess(ServerPlayer player, Symbol symbol) {
        PlayerData data = participants.get(player.getUUID());
        if (data == null || !data.alive || phase != GamePhase.GUESS) {
            return;
        }
        data.guess = symbol;
        player.sendSystemMessage(Component.translatable("message.jackofhearts.guess_saved", symbol.getLabel())
                .withStyle(ChatFormatting.YELLOW));
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.7F, 1.4F);
        syncPlayer(player, data);
    }

    // ------------------------------------------------------------------ kematian & kemenangan

    public void onPlayerDeath(ServerPlayer dead, DamageSource source) {
        PlayerData data = participants.get(dead.getUUID());
        if (data == null || !isRunning()) {
            return;
        }
        data.alive = false;
        // Dibunuh pemain lain, bukan laser: tidak ada adegan yang perlu ditunggu.
        queuePanelOff(dead.getUUID(), 1);
        stripUniform(dead);

        if (source.getEntity() instanceof ServerPlayer killer && !killer.getUUID().equals(dead.getUUID())) {
            PlayerData killerData = participants.get(killer.getUUID());
            if (killerData != null) {
                killerData.kills++;
                if (killerData.isJack()) {
                    broadcast(dead.server, Component.translatable("message.jackofhearts.jack_killed", data.name)
                            .withStyle(ChatFormatting.DARK_RED));
                }
            }
        }

        checkWinConditions(dead.server, false);
    }

    public void onPlayerRespawn(ServerPlayer player) {
        PlayerData data = participants.get(player.getUUID());
        if (data != null && !data.alive && isRunning()) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.translatable("message.jackofhearts.now_spectator")
                    .withStyle(ChatFormatting.GRAY));
        }
        syncPlayer(player, data);
    }

    /** @return true kalau game berakhir di pengecekan ini. */
    private boolean checkWinConditions(MinecraftServer server, boolean endOfRound) {
        if (!isRunning()) {
            return true;
        }

        boolean anyJackAlive = participants.values().stream().anyMatch(d -> d.isJack() && d.alive);
        boolean anyAceAlive = participants.values().stream().anyMatch(d -> d.isAce() && d.alive);

        if (!anyJackAlive) {
            finish(server, Role.ACE, Component.translatable("message.jackofhearts.win_ace_jack_dead"));
            return true;
        }
        if (!anyAceAlive) {
            finish(server, Role.JACK, Component.translatable("message.jackofhearts.win_jack_all_aces_dead"));
            return true;
        }

        if (endOfRound) {
            int jackKills = participants.values().stream().filter(PlayerData::isJack).mapToInt(d -> d.kills).sum();
            if (round >= JackConfig.CONFIG.aceWinRoundIfNoKill.get() && jackKills == 0) {
                finish(server, Role.ACE, Component.translatable("message.jackofhearts.win_ace_no_kill", round));
                return true;
            }
            if (round >= JackConfig.CONFIG.jackWinRound.get()) {
                finish(server, Role.JACK, Component.translatable("message.jackofhearts.win_jack_survived", round));
                return true;
            }
        }
        return false;
    }

    /**
     * Permainan berakhir. Pengumumannya belum tentu sekarang.
     *
     * <p>Kemenangan ditentukan begitu fase eksekusi selesai, sekitar satu setengah
     * detik setelah tembakan — sementara papan baru mulai padam di detik kelima. Kalau
     * pemenang langsung diumumkan di situ, layar kemenangan menutupi seluruh adegan
     * papan yang justru menjelaskan <b>kenapa</b> permainannya berakhir.</p>
     *
     * <p>Fasenya tetap diakhiri sekarang supaya tidak ada ronde baru yang dimulai;
     * yang ditunda hanya pengumumannya. Antrean panel dan kamera papan berjalan di
     * luar penjagaan fase, jadi keduanya tetap selesai sampai tuntas.</p>
     */
    private void finish(MinecraftServer server, Role winner, Component reason) {
        phase = GamePhase.ENDED;
        timer = 0;

        if (isBoardSequenceRunning()) {
            pendingWinner = winner;
            pendingWinReason = reason;
            syncAll(server);
            return;
        }
        announceWinner(server, winner, reason);
    }

    /** Papan masih punya panel yang harus padam, atau kameranya masih menyorot. */
    private boolean isBoardSequenceRunning() {
        return !panelQueue.isEmpty() || boardViewDelay > 0 || boardViewTicks > 0;
    }

    private void tickPendingWin(MinecraftServer server) {
        if (pendingWinner == null || isBoardSequenceRunning()) {
            return;
        }
        Role winner = pendingWinner;
        Component reason = pendingWinReason;
        pendingWinner = null;
        pendingWinReason = null;
        announceWinner(server, winner, reason);
    }

    private void announceWinner(MinecraftServer server, Role winner, Component reason) {
        Component header = Component.translatable("message.jackofhearts.winner", winner.getDisplayName())
                .withStyle(ChatFormatting.GOLD);
        broadcast(server, header);
        broadcast(server, reason.copy().withStyle(ChatFormatting.YELLOW));

        StringBuilder roster = new StringBuilder();
        for (PlayerData data : participants.values()) {
            roster.append(data.name).append(" = ").append(data.role.getId().toUpperCase())
                    .append(data.alive ? "" : " (mati)").append(", ");
        }
        if (roster.length() > 2) {
            roster.setLength(roster.length() - 2);
            broadcast(server, Component.literal(roster.toString()).withStyle(ChatFormatting.GRAY));
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            title(player, header, reason, 10, 100, 20);
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
        }
        setParticipantsInvulnerable(server, false);
        for (ServerPlayer player : onlineParticipants(server)) {
            stripUniform(player);
        }
        clearCorpses(server);
        syncAll(server);
    }

    // ------------------------------------------------------------------ util

    /** Pastikan setiap peserta hidup selalu memakai seragam dengan simbol terbaru. */
    private void enforceUniforms(MinecraftServer server) {
        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data == null || !data.alive) {
                continue;
            }
            // Slot armor diisi ulang di bawah, jadi salinan apa pun yang sempat
            // dipindahkan ke inventory harus dibuang lebih dulu. Tanpa ini, memindahkan
            // baju ke tas akan menghasilkan baju kedua saat slotnya diisi ulang.
            purgeSpareUniforms(player);

            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!chest.is(ModItems.UNIFORM_SHIRT.get()) || SymbolArmorItem.getSymbol(chest) != data.symbol) {
                player.setItemSlot(EquipmentSlot.CHEST,
                        SymbolArmorItem.withSymbol(new ItemStack(ModItems.UNIFORM_SHIRT.get()), data.symbol));
            }
            ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
            if (!legs.is(ModItems.UNIFORM_PANTS.get())) {
                player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.UNIFORM_PANTS.get()));
            }
        }
    }

    /**
     * Buang seragam yang ada di luar slot armor. Slot armor sendiri tidak disentuh -
     * itu tempat yang benar dan akan diisi ulang oleh pemanggilnya.
     */
    private static void purgeSpareUniforms(ServerPlayer player) {
        boolean changed = false;
        Inventory inventory = player.getInventory();
        for (NonNullList<ItemStack> list : List.of(inventory.items, inventory.offhand)) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getItem() instanceof SymbolArmorItem) {
                    list.set(i, ItemStack.EMPTY);
                    changed = true;
                }
            }
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    private void stripUniform(ServerPlayer player) {
        if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SymbolArmorItem) {
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
        if (player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof SymbolArmorItem) {
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private List<ServerPlayer> onlineParticipants(MinecraftServer server) {
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID uuid : participants.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    public void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player, participants.get(player.getUUID()));
        }
    }

    /** Peserta yang baru terhubung saat animasi masih jalan tetap ikut menonton dan ikut kebal. */
    public void onPlayerJoin(ServerPlayer player) {
        PlayerData data = participants.get(player.getUUID());
        if (data != null && phase == GamePhase.REVEAL) {
            player.setInvulnerable(true);
            int total = participants.size();
            int jacks = (int) participants.values().stream().filter(PlayerData::isJack).count();
            ModNetwork.toPlayer(player, new OpenRoleRevealPacket(data.role.ordinal(), Math.max(timer, 1),
                    Math.min(total, MAX_LINEUP_CARDS), Math.min(jacks, MAX_LINEUP_CARDS),
                    total, jacks, false));
        }
        syncPlayer(player, data);
        // Yang baru masuk belum punya isi papan sama sekali.
        syncRoster(player.server);
        Cafeteria.get().syncTo(player);
    }

    public void syncPlayer(ServerPlayer player, @Nullable PlayerData data) {
        boolean participating = data != null;
        boolean alive = participating && data.alive;
        boolean lockView = alive && isRunning() && JackConfig.CONFIG.lockFirstPerson.get();
        int role = participating ? data.role.ordinal() : -1;
        int guess = participating && data.guess != null ? data.guess.ordinal() : -1;
        ModNetwork.toPlayer(player, new SyncStatePacket(phase.ordinal(), round, Math.max(timer, 0),
                participating, alive, lockView, role, guess));
    }

    private void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
        JackOfHeartsMod.LOGGER.info("[JackOfHearts] {}", message.getString());
    }

    private void broadcastParticipants(MinecraftServer server, Component message) {
        for (ServerPlayer player : onlineParticipants(server)) {
            player.sendSystemMessage(message);
        }
    }

    private static void title(ServerPlayer player, Component title, Component subtitle, int in, int stay, int out) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /** Dipakai perintah /jack skip untuk memotong sisa waktu fase saat ini. */
    public void skipPhase() {
        if (isRunning()) {
            timer = 1;
        }
    }
}
