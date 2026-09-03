package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.item.SymbolArmorItem;
import com.aflah.jackofhearts.network.ModNetwork;
import com.aflah.jackofhearts.network.LaserPacket;
import com.aflah.jackofhearts.network.OpenGuessScreenPacket;
import com.aflah.jackofhearts.network.OpenRoleRevealPacket;
import com.aflah.jackofhearts.network.SetCameraPacket;
import com.aflah.jackofhearts.network.SyncStatePacket;
import com.aflah.jackofhearts.registry.ModBlocks;
import com.aflah.jackofhearts.registry.ModItems;
import com.aflah.jackofhearts.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    @Nullable
    private ResourceKey<Level> guessRoomDimension;
    @Nullable
    private BlockPos guessRoomPos;
    private float guessRoomYaw;

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
     */
    private static final double CAMERA_OPEN_DISTANCE = 5.5D;
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
        final float startYaw;
        final float startPitch;
        final float targetYaw;
        final float targetPitch;

        ExecutionShot(ArmorStand camera,
                      float startYaw, float startPitch, float targetYaw, float targetPitch) {
            this.camera = camera;
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

    public void setGuessRoom(ServerPlayer player) {
        this.guessRoomDimension = player.level().dimension();
        this.guessRoomPos = player.blockPosition();
        this.guessRoomYaw = player.getYRot();
    }

    public boolean hasGuessRoom() {
        return guessRoomDimension != null && guessRoomPos != null;
    }

    @Nullable
    public BlockPos getGuessRoomPos() {
        return guessRoomPos;
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
        beginReveal(server);
        return null;
    }

    public void stop(MinecraftServer server, boolean announce) {
        pendingExecution.clear();
        clearExecutionCameras(server);
        setParticipantsInvulnerable(server, false);
        for (ServerPlayer player : onlineParticipants(server)) {
            stripUniform(player);
        }
        participants.clear();
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
        if (!isRunning()) {
            return;
        }

        timer--;

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
            tickExecutionCameras(server);
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

        boolean teleport = JackConfig.CONFIG.teleportToGuessRoom.get() && hasGuessRoom();
        ServerLevel roomLevel = teleport ? server.getLevel(guessRoomDimension) : null;

        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data == null || !data.alive) {
                continue;
            }
            if (roomLevel != null && guessRoomPos != null) {
                player.teleportTo(roomLevel, guessRoomPos.getX() + 0.5D, guessRoomPos.getY(),
                        guessRoomPos.getZ() + 0.5D, guessRoomYaw, 0.0F);
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

        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data == null || !data.alive) {
                continue;
            }
            if (data.guess != null && data.guess == data.symbol) {
                player.sendSystemMessage(Component.translatable("message.jackofhearts.guess_correct",
                        data.symbol.getLabel()).withStyle(ChatFormatting.GREEN));
                title(player, Component.translatable("message.jackofhearts.safe").withStyle(ChatFormatting.GREEN),
                        data.symbol.getLabel(), 5, 40, 10);
            } else {
                player.sendSystemMessage(Component.translatable("message.jackofhearts.guess_wrong",
                        data.symbol.getLabel()).withStyle(ChatFormatting.RED));
                wrong.add(player);
            }
        }

        // Peserta yang offline saat waktu habis dianggap tidak menebak.
        for (PlayerData data : participants.values()) {
            if (data.alive && server.getPlayerList().getPlayer(data.uuid) == null) {
                data.alive = false;
                broadcast(server, Component.translatable("message.jackofhearts.struck_offline", data.name)
                        .withStyle(ChatFormatting.RED));
            }
        }

        for (PlayerData data : participants.values()) {
            data.guess = null;
        }

        if (JackConfig.CONFIG.eliminateOnWrongGuess.get() && !wrong.isEmpty()) {
            beginExecution(server, wrong);
            return;
        }

        if (!checkWinConditions(server, true)) {
            beginRound(server);
        }
    }

    // ------------------------------------------------------------------ eksekusi laser

    /**
     * Fase laser. Kematian sengaja ditunda sampai sinarnya benar-benar menembak, dan
     * ronde berikutnya baru mulai setelah seluruh fase selesai - kalau tidak, Jack
     * sudah bisa berburu sementara orang masih dieksekusi.
     */
    private void beginExecution(MinecraftServer server, List<ServerPlayer> victims) {
        phase = GamePhase.EXECUTION;
        timer = JackConfig.CONFIG.executionSeconds.get() * 20;
        executionTotalTicks = timer;
        executionFireTick = timer - LASER_CHARGE_TICKS;

        pendingExecution.clear();
        clearExecutionCameras(server);

        for (ServerPlayer victim : victims) {
            pendingExecution.add(victim.getUUID());
            Vec3 origin = fireLaserAt(victim, timer);
            beginCinematic(victim, origin, timer);
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
            pitch = pitchTowards(camPos, head);
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

        executionCameras.put(victim.getUUID(), new ExecutionShot(camera,
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
     */
    private void tickExecutionCameras(MinecraftServer server) {
        // Putaran pandangan selesai tepat saat laser menembak; sisa fase setelah itu
        // dibiarkan apa adanya karena korban sudah jadi penonton.
        int rotationTicks = Math.max(1, executionTotalTicks - executionFireTick);
        float progress = Mth.clamp((executionTotalTicks - timer) / (float) rotationTicks, 0.0F, 1.0F);

        for (Map.Entry<UUID, ExecutionShot> entry : executionCameras.entrySet()) {
            ServerPlayer victim = server.getPlayerList().getPlayer(entry.getKey());
            ExecutionShot shot = entry.getValue();
            if (victim == null || !shot.camera.isAlive()) {
                continue;
            }
            aimYaw(shot.camera, shot.camera.position(), victim.getEyePosition());

            if (timer > executionFireTick) {
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

    /** Kirim sinar ke semua penonton di sekitar dan bunyikan suara penguncian. */
    private Vec3 fireLaserAt(ServerPlayer victim, int totalTicks) {
        ServerLevel level = victim.serverLevel();
        // Setinggi mata: itu posisi kepala. Client tetap mengikuti entitasnya tiap
        // frame, titik ini hanya cadangan kalau entitasnya tidak termuat di sana.
        Vec3 target = victim.getEyePosition();
        Vec3 origin = laserOrigin(victim, target);

        LaserPacket packet = new LaserPacket(origin, target, victim.getId(), LASER_CHARGE_TICKS, totalTicks);
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
        Block marker = ForgeRegistries.BLOCKS.getValue(
                ResourceLocation.tryParse(JackConfig.CONFIG.roomMarkerBlock.get()));
        if (marker == null) {
            JackOfHeartsMod.LOGGER.warn(
                    "[JackOfHearts] roomMarkerBlock '{}' tidak dikenal, laser memakai langit.",
                    JackConfig.CONFIG.roomMarkerBlock.get());
            return false;
        }
        ServerLevel level = victim.serverLevel();
        BlockPos feet = victim.blockPosition();
        int depth = JackConfig.CONFIG.roomMarkerDepth.get();
        for (int dy = 1; dy <= depth; dy++) {
            BlockState state = level.getBlockState(feet.below(dy));
            if (state.is(marker)) {
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
    private BlockPos findGuessBlock(ServerPlayer victim) {
        ServerLevel level = victim.serverLevel();
        PlayerData data = participants.get(victim.getUUID());

        if (data != null && data.lastGuessBlock != null
                && level.getBlockState(data.lastGuessBlock).is(ModBlocks.GUESS_BLOCK.get())) {
            return data.lastGuessBlock;
        }

        BlockPos feet = victim.blockPosition();
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

        for (ServerPlayer victim : hit) {
            ServerLevel level = victim.serverLevel();
            Vec3 at = victim.getEyePosition();

            level.playSound(null, at.x, at.y, at.z,
                    ModSounds.LASER_FIRE.get(), SoundSource.MASTER, 2.0F, 1.0F);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 6, 0.3D, 0.4D, 0.3D, 0.0D);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, 0.25D, 0.4D, 0.25D, 0.06D);

            broadcast(server, Component.translatable("message.jackofhearts.struck",
                    victim.getGameProfile().getName()).withStyle(ChatFormatting.RED));

            // Korban laser tidak dibunuh, hanya dijadikan penonton: tidak ada layar
            // kematian, tidak ada isi tas yang tercecer, dan tidak ada respawn yang
            // harus ditangani. Seragamnya dilepas di sini karena LivingDeathEvent
            // yang biasanya mengurus itu memang tidak akan terpicu.
            stripUniform(victim);
            victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            victim.setInvulnerable(false);
            victim.setGameMode(GameType.SPECTATOR);
            victim.sendSystemMessage(Component.translatable("message.jackofhearts.now_spectator")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void endExecution(MinecraftServer server) {
        clearExecutionCameras(server);
        if (!checkWinConditions(server, true)) {
            beginRound(server);
        }
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

    private void finish(MinecraftServer server, Role winner, Component reason) {
        phase = GamePhase.ENDED;
        timer = 0;

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
