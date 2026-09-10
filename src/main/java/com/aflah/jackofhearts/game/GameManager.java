package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.block.MonitorBlock;
import com.aflah.jackofhearts.blockentity.MonitorTracker;
import com.aflah.jackofhearts.block.PrisonDoorTracker;
import com.aflah.jackofhearts.cafeteria.Cafeteria;
import com.aflah.jackofhearts.cafeteria.CafeteriaFood;
import com.aflah.jackofhearts.cafeteria.HungerRules;
import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.entity.Blood;
import com.aflah.jackofhearts.entity.CorpseEntity;
import com.aflah.jackofhearts.entity.MannequinEntity;
import com.aflah.jackofhearts.item.SymbolArmorItem;
import com.aflah.jackofhearts.network.ModNetwork;
import com.aflah.jackofhearts.network.JackListPacket;
import com.aflah.jackofhearts.network.LaserPacket;
import com.aflah.jackofhearts.network.PanelOffPacket;
import com.aflah.jackofhearts.network.RosterPacket;
import com.aflah.jackofhearts.network.SkillStatePacket;
import com.aflah.jackofhearts.network.SkinDataPacket;
import com.aflah.jackofhearts.network.OpenGuessScreenPacket;
import com.aflah.jackofhearts.network.OpenRoleRevealPacket;
import com.aflah.jackofhearts.network.SetCameraPacket;
import com.aflah.jackofhearts.network.SyncStatePacket;
import com.aflah.jackofhearts.registry.ModBlocks;
import com.aflah.jackofhearts.registry.ModEntities;
import com.aflah.jackofhearts.registry.ModItems;
import com.aflah.jackofhearts.registry.ModSounds;
import com.mojang.authlib.properties.Property;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
     * Peserta yang dipaksa jadi Jack di game berikutnya.
     *
     * <p>Bertahan lintas game seperti daftar terdaftar: setelan ini dipakai host untuk
     * mengatur skenario, dan menyusunnya ulang tiap kali game dimulai justru bikin
     * setelannya tidak berguna.</p>
     *
     * <p>Isinya tidak pernah diumumkan ke pemain mana pun. Sama seperti peran itu
     * sendiri, ini rahasia host.</p>
     */
    private final Map<UUID, Forced> forcedJacks = new LinkedHashMap<>();
    /**
     * Peserta yang dipaksa jadi Unknown di game berikutnya. Aturannya sama persis dengan
     * {@link #forcedJacks}; satu orang cuma bisa ada di salah satu daftar.
     */
    private final Map<UUID, Forced> forcedUnknowns = new LinkedHashMap<>();

    /**
     * Satu penunjukan peran.
     *
     * @param always true berarti berlaku terus sampai dibatalkan; false berarti cuma
     *               untuk satu game — dan penunjukannya hangus begitu game itu dimulai,
     *               bukan begitu game itu selesai. Bedanya kelihatan kalau gamenya
     *               dihentikan di tengah jalan: yang sudah terpakai tetap hangus, jadi
     *               "sekali" benar-benar berarti sekali.
     */
    public record Forced(String name, boolean always) {
    }
    /**
     * Peserta yang tebakannya selalu dinilai benar. Murni alat testing (/jack debug
     * true), supaya jalannya beberapa ronde bisa dicoba tanpa harus menebak benar
     * tiap kali. Bertahan lintas game seperti daftar terdaftar, karena yang
     * menyalakannya biasanya sedang menguji berturut-turut.
     */
    private final Set<UUID> alwaysCorrect = new LinkedHashSet<>();
    /**
     * Properti skin tiap pemain yang pernah masuk, dicatat selagi dia masih ada.
     *
     * <p>Inilah alasan UUID peserta disimpan sejak pendaftaran: begitu seseorang keluar
     * dari server, client kehilangan skinnya dan menggantinya dengan skin bawaan.
     * Padahal justru sesudah itu wajahnya paling dibutuhkan — di potret papan, di badan
     * penggantinya yang berdiri di ruangan, dan di mayatnya kalau dia gugur selagi
     * pergi. Catatan ini yang dibagikan ke semua client lewat {@link SkinDataPacket}.</p>
     */
    private final Map<UUID, SkinDataPacket.Entry> skins = new LinkedHashMap<>();
    /**
     * Fase terakhir yang sudah diterapkan ke pintu sel. Perbandingannya dilakukan di
     * tick, bukan di tiap tempat fase berganti — dengan begitu tidak ada satu pun
     * jalur pergantian fase (termasuk /jack skip dan /jack stop) yang bisa terlewat.
     */
    @Nullable
    private GamePhase lastDoorPhase;
    /**
     * Peserta yang sudah memilih mempercepat ronde ini.
     *
     * <p>Suaranya <b>tidak</b> dihapus waktu pemiliknya keluar dari server, cuma
     * berhenti dihitung: yang dihitung selalu peserta hidup yang sedang online, di
     * pembilang maupun penyebut. Jadi orang yang pergi tidak menahan ronde berjalan
     * terus, dan suaranya kembali sendiri begitu dia masuk lagi tanpa perlu memilih
     * ulang.</p>
     *
     * <p>Dikosongkan tiap ronde baru: ini pemungutan suara untuk satu ronde, bukan
     * setelan yang menempel di orangnya.</p>
     */
    private final Set<UUID> skipVotes = new LinkedHashSet<>();
    /**
     * Sampai tick berapa status bar seseorang tidak boleh ditimpa.
     *
     * <p>Status bar dikirim ulang tiap detik, dan tempatnya sama dengan tempat pesan
     * sekilas seperti peringatan pintu sel membanting. Tanpa jeda ini, peringatan itu
     * tertimpa sebelum sempat dibaca.</p>
     */
    private final Map<UUID, Integer> statusBarMuted = new LinkedHashMap<>();
    /**
     * Ronde terakhir yang memakan korban, apa pun sebabnya. 0 berarti belum ada.
     *
     * <p>Inilah yang dipakai menilai Jack, bukan jumlah orang yang dia bunuh sendiri:
     * Jack tidak bisa melukai siapa-siapa. Kerjanya membuat orang salah menebak
     * simbolnya atau kehabisan makanan, dan hasil kerja itu bentuknya cuma satu — ada
     * yang gugur. Jadi yang dihitung kematiannya, bukan pelakunya.</p>
     */
    private int lastDeathRound;
    /**
     * Berapa ronde beruntun tanpa korban yang membuat Jack dieksekusi ronde ini.
     *
     * <p>Disimpan sampai sinarnya jatuh, bukan diumumkan saat diputuskan: pengumuman di
     * awal fase eksekusi memberitahu semua orang bahwa yang akan mati adalah Jack,
     * sebelum sinarnya sempat mengisi. Seluruh adegannya jadi tidak ada gunanya.</p>
     */
    private int jackFailureRounds;
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
    /**
     * Peserta offline yang badan penggantinya sedang dikunci laser, per pemilik badan.
     *
     * <p>Terpisah dari {@link #pendingExecution} karena dua-duanya diselesaikan dengan
     * cara yang berbeda: korban yang hadir dipindahkan ke mode penonton, sedangkan yang
     * offline tidak punya apa pun untuk dipindahkan — yang harus dikerjakan cuma
     * menukar badan penggantinya dengan mayat.</p>
     */
    private final Map<UUID, MannequinShot> pendingOffline = new LinkedHashMap<>();
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

    /**
     * Satu adegan eksekusi untuk peserta yang sedang offline: badannya, asal sinarnya,
     * dan putaran badan yang akan menoleh ke arah sinar itu.
     */
    private record MannequinShot(MannequinEntity body, Vec3 origin, float startYaw, float targetYaw) {
    }

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
        // Skinnya dititipkan di detik pendaftaran, bukan saat dia keluar: saat dia
        // keluar, profilnya sudah tidak ada lagi untuk dibaca.
        rememberSkin(player, true);
        return registered.put(player.getUUID(), player.getGameProfile().getName()) == null;
    }

    /**
     * Catat properti skin pemain, dan bagikan ke semua client kalau dia memang ikut
     * bermain.
     *
     * <p>Profil pemain di server yang memverifikasi akun ke Mojang sudah membawa
     * properti ini sejak dia masuk, jadi tidak ada permintaan jaringan apa pun di sini.
     * Server yang tidak memverifikasi tidak punya properti itu sama sekali — dalam hal
     * itu tidak ada yang dicatat, dan client jatuh ke ingatannya sendiri.</p>
     *
     * @param share false untuk sekadar mencatat, tanpa mengirim apa-apa. Dipakai untuk
     *              pemain yang belum tentu ikut bermain: catatannya baru berguna kalau
     *              nanti dia terdaftar, dan pendaftaran itu yang akan membagikannya.
     */
    private void rememberSkin(ServerPlayer player, boolean share) {
        String textures = "";
        for (Property property : player.getGameProfile().getProperties().get("textures")) {
            textures = property.getValue();
            break;
        }
        if (textures.isEmpty()) {
            return;
        }
        SkinDataPacket.Entry entry = new SkinDataPacket.Entry(
                player.getUUID(), player.getGameProfile().getName(), textures);
        SkinDataPacket.Entry previous = skins.put(entry.uuid(), entry);
        if (share && (previous == null || !previous.textures().equals(textures))) {
            ModNetwork.toAll(new SkinDataPacket(List.of(entry)));
        }
    }

    /** Seluruh titipan skin sekaligus, untuk client yang baru bergabung. */
    private void sendSkins(ServerPlayer player) {
        if (!skins.isEmpty()) {
            ModNetwork.toPlayer(player, new SkinDataPacket(List.copyOf(skins.values())));
        }
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

    /** Daftar penunjukan untuk satu peran. Cuma Jack dan Unknown yang bisa ditunjuk. */
    private Map<UUID, Forced> forcedMap(Role role) {
        return role == Role.UNKNOWN ? forcedUnknowns : forcedJacks;
    }

    /**
     * Menunjuk pemain untuk satu peran di game berikutnya.
     *
     * <p>Penunjukan di peran lain ikut dicabut: satu orang tidak bisa dipaksa jadi Jack
     * sekaligus Unknown, dan yang terakhir diketik host adalah yang dia maksud.</p>
     *
     * @return true kalau penunjukannya baru, atau jangka waktunya berubah.
     */
    public boolean setForced(Role role, ServerPlayer player, boolean always) {
        forcedMap(role == Role.UNKNOWN ? Role.JACK : Role.UNKNOWN).remove(player.getUUID());
        Forced previous = forcedMap(role).put(player.getUUID(),
                new Forced(player.getGameProfile().getName(), always));
        return previous == null || previous.always() != always;
    }

    /** @return true kalau pemain ini memang sedang ditunjuk untuk peran itu. */
    public boolean unsetForced(Role role, ServerPlayer player) {
        return forcedMap(role).remove(player.getUUID()) != null;
    }

    public Map<UUID, Forced> getForced(Role role) {
        return Collections.unmodifiableMap(forcedMap(role));
    }

    public int clearForced(Role role) {
        Map<UUID, Forced> map = forcedMap(role);
        int size = map.size();
        map.clear();
        return size;
    }

    /**
     * Berapa Jack yang benar-benar dibagikan.
     *
     * <p>Setelan {@code jackCount} adalah batas yang <b>dinaikkan</b> oleh setjack,
     * bukan yang membatasinya: kalau host menunjuk tiga orang sementara batasnya dua,
     * yang mengalah batasnya. Menolak orang ketiga berarti perintah yang sudah
     * dijalankan host diam-diam tidak berlaku, dan dia baru tahu setelah permainannya
     * jalan.</p>
     *
     * @param forcedInPool berapa orang yang ditunjuk setjack yang benar-benar ikut main.
     */
    public static int jackCountFor(int forcedInPool) {
        return Math.max(JackConfig.CONFIG.jackCount.get(), forcedInPool);
    }

    /**
     * Berapa Unknown yang benar-benar dibagikan.
     *
     * <p>Sama seperti Jack, penunjukan host <b>menaikkan</b> jumlahnya, bukan dibatasi
     * olehnya — dan tetap berlaku walau peran Unknown sedang dimatikan. Host yang
     * mengetik "orang ini Unknown" jelas ingin ada Unknown di game itu; menolaknya
     * diam-diam karena satu setelan lain lupa dinyalakan cuma ketahuan setelah kartunya
     * dibalik.</p>
     *
     * <p>Dipotong supaya selalu tersisa paling tidak satu Ace. Tanpa Ace sama sekali,
     * permainannya selesai di pengecekan pertama: Ace "habis" sebelum ada yang mati, dan
     * Unknown langsung jadi Jack tanpa pernah bermain.</p>
     *
     * @param forcedInPool berapa orang yang ditunjuk jadi Unknown yang benar-benar ikut main.
     */
    public static int unknownCountFor(int players, int jackCount, int forcedInPool) {
        int wanted = JackConfig.CONFIG.unknownRole.get()
                ? Math.max(JackConfig.CONFIG.unknownMax.get(), forcedInPool)
                : forcedInPool;
        return Math.max(0, Math.min(wanted, players - jackCount - 1));
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

        int forcedInPool = (int) players.stream().filter(p -> forcedJacks.containsKey(p.getUUID())).count();
        int jackCount = jackCountFor(forcedInPool);
        if (players.size() < jackCount + 1) {
            return Component.translatable("message.jackofhearts.not_enough_players", jackCount + 1)
                    .withStyle(ChatFormatting.RED);
        }

        prepareArena(server, players);

        participants.clear();
        panelDark.clear();
        panelQueue.clear();
        pendingWinner = null;
        pendingWinReason = null;
        // Setiap game mulai dari lantai yang bersih, termasuk kalau game sebelumnya
        // ditinggalkan begitu saja tanpa /jack stop.
        clearCorpses(server);
        OfflinePlayers.get().clear(server);
        MilkSupply.clearAll(server);
        jackWatches.clear();
        skipVotes.clear();
        statusBarMuted.clear();
        lastDeathRound = 0;
        jackFailureRounds = 0;
        RandomSource random = server.overworld().getRandom();
        Collections.shuffle(players, new Random(random.nextLong()));

        // Barisannya disusun ulang jadi [kursi Jack | kursi Unknown | Ace], dan peran
        // dibagikan dari depan menurut urutan itu. Tiap kelompok tetap dalam urutan acak
        // hasil kocokan tadi, jadi kursi yang tidak diisi penunjukan jatuh ke orang acak.
        List<ServerPlayer> forcedJ = new ArrayList<>();
        List<ServerPlayer> forcedU = new ArrayList<>();
        List<ServerPlayer> rest = new ArrayList<>();
        for (ServerPlayer player : players) {
            (forcedJacks.containsKey(player.getUUID()) ? forcedJ
                    : forcedUnknowns.containsKey(player.getUUID()) ? forcedU : rest).add(player);
        }
        List<ServerPlayer> ordered = new ArrayList<>(forcedJ);
        // Kursi Jack yang belum terisi diambil dari orang acak dulu. Yang ditunjuk jadi
        // Unknown baru ikut diundi kalau memang tidak ada orang lain lagi.
        while (ordered.size() < jackCount) {
            ordered.add(!rest.isEmpty() ? rest.remove(0) : forcedU.remove(0));
        }
        int unknownCount = unknownCountFor(players.size(), jackCount, forcedU.size());
        while (ordered.size() < jackCount + unknownCount) {
            ordered.add(!forcedU.isEmpty() ? forcedU.remove(0) : rest.remove(0));
        }
        // Yang ditunjuk jadi Unknown tapi tidak kebagian kursi - pemainnya terlalu
        // sedikit untuk tetap menyisakan satu Ace - jadi Ace biasa.
        if (!forcedU.isEmpty()) {
            JackOfHeartsMod.LOGGER.info("[JackOfHearts] {} penunjukan Unknown tidak kebagian kursi dan jadi Ace.",
                    forcedU.size());
        }
        ordered.addAll(forcedU);
        ordered.addAll(rest);
        players = ordered;

        if (forcedInPool > 0) {
            JackOfHeartsMod.LOGGER.info("[JackOfHearts] {} pemain ditunjuk jadi Jack; total Jack game ini: {}.",
                    forcedInPool, jackCount);
        }
        if (JackConfig.CONFIG.unknownRole.get() && unknownCount < JackConfig.CONFIG.unknownMax.get()) {
            JackOfHeartsMod.LOGGER.info("[JackOfHearts] Unknown dikurangi jadi {} supaya masih ada Ace.",
                    unknownCount);
        }
        // Penunjukan "sekali" hangus di sini, di detik penunjukannya terpakai - bukan
        // saat gamenya selesai. Game yang dihentikan di tengah jalan tetap menghabiskan
        // jatahnya, jadi tidak ada yang diam-diam dapat peran yang sama dua kali.
        forcedJacks.values().removeIf(forced -> !forced.always());
        forcedUnknowns.values().removeIf(forced -> !forced.always());

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            // /jack start bisa dipanggil dengan sasaran sendiri, tanpa lewat
            // pendaftaran sama sekali - jadi skinnya dititipkan di sini juga.
            rememberSkin(player, true);
            PlayerData data = new PlayerData(player.getUUID(), player.getGameProfile().getName());
            data.skillUses[Skill.DRUNK.ordinal()] = JackConfig.CONFIG.skillDrunk.get()
                    ? JackConfig.CONFIG.skillDrunkMax.get() : -1;
            data.skillUses[Skill.HUNGER.ordinal()] = JackConfig.CONFIG.skillHunger.get()
                    ? JackConfig.CONFIG.skillHungerMax.get() : -1;
            data.skillUses[Skill.POISON.ordinal()] = JackConfig.CONFIG.skillPoison.get()
                    ? JackConfig.CONFIG.skillPoisonMax.get() : -1;
            data.role = i < jackCount ? Role.JACK
                    : i < jackCount + unknownCount ? Role.UNKNOWN
                    : Role.ACE;
            participants.put(player.getUUID(), data);
        }

        round = 0;


        // broadcast(server, Component.translatable("message.jackofhearts.game_started", players.size())
        //         .withStyle(ChatFormatting.GOLD));

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

        for (ServerPlayer player : players) {
            sendSkillState(player, participants.get(player.getUUID()));
        }

        syncRoster(server);
        announceStart(server);
        beginReveal(server);
        return null;
    }

    /**
     * Font ikon milik mod ini ({@code assets/jackofhearts/font/icons.json}), terpisah dari
     * {@code minecraft:default} supaya font vanilla tidak tersentuh sama sekali.
     */
    private static final ResourceLocation ICON_FONT = new ResourceLocation(JackOfHeartsMod.MOD_ID, "icons");
    /** U+E000 di font ikon: logo Discord. Harus sama persis dengan "chars" di icons.json. */
    private static final String DISCORD_GLYPH = "\uE000";

    /**
     * Logo Discord sebagai satu glyph font.
     *
     * <p>Warnanya wajib putih: glyph bitmap dikalikan dengan warna teksnya, jadi warna
     * lain - termasuk yang terbawa dari komponen induk - ikut mewarnai logonya. Putih
     * adalah pengali netral, yang keluar warna asli teksturnya.</p>
     */
    private static Component discordIcon() {
        return Component.literal(DISCORD_GLYPH)
                .withStyle(style -> style.withFont(ICON_FONT).withColor(ChatFormatting.WHITE));
    }

    /**
     * Spanduk pembuka di chat, tepat saat animasi kocok kartu dimulai. Bentuknya sama
     * dengan spanduk mod Deception.
     *
     * <p>Dikirim sebelum layar kocok kartu terbuka, jadi chat-nya sudah menunggu di
     * belakang layar itu - dan tidak berisi apa pun soal peran, jadi tidak ada yang
     * bocor walau terbaca lebih dulu.</p>
     */
    private void announceStart(MinecraftServer server) {
        broadcast(server, Component.literal("═══════════════════════").withStyle(ChatFormatting.GOLD));
        broadcast(server, Component.literal(""));
        broadcast(server, Component.literal("  ♥️ GAME DIMULAI ♥️").withStyle(ChatFormatting.GREEN));
        broadcast(server, Component.literal("  Tebak simbol di punggungmu, \n  dan bunuh jack untuk menang.")
                .withStyle(ChatFormatting.YELLOW));
        broadcast(server, Component.literal("  ").append(discordIcon())
                .append(Component.literal(" @aflahall").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" Dev Of ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("@corazonid").withStyle(ChatFormatting.AQUA))));
        broadcast(server, Component.literal(""));
        broadcast(server, Component.literal("═══════════════════════").withStyle(ChatFormatting.GOLD));
    }

    /**
     * Menyiapkan arena dan pesertanya, sebelum satu peran pun dibagikan.
     *
     * <p>Semuanya dikerjakan di sini, di satu tempat, karena semuanya harus sudah
     * selesai <b>sebelum</b> layar kocok kartu muncul: pemain yang masih tersebar di
     * seluruh map saat animasinya jalan akan melihat kartunya membalik sambil berdiri di
     * tempat yang salah, dan begitu layarnya tertutup dia sudah tertinggal satu ronde.</p>
     */
    private void prepareArena(MinecraftServer server, List<ServerPlayer> players) {
        server.setDifficulty(Difficulty.HARD, true);
        // Nilai lamanya disimpan supaya dunia dikembalikan seperti sebelum dipinjam.
        pvpBefore = server.isPvpAllowed();
        server.setPvpAllowed(false);

        boolean arena = Arena.level(server) != null;
        if (!arena) {
            JackOfHeartsMod.LOGGER.warn(
                    "[JackOfHearts] Dimensi arena '{}' tidak ada; peserta dibiarkan di tempatnya.",
                    JackConfig.CONFIG.arenaDimension.get());
        }
        for (ServerPlayer player : players) {
            if (arena) {
                Arena.send(player);
            }
            player.setGameMode(GameType.ADVENTURE);
            player.getInventory().clearContent();
            player.getInventory().selected = 0;
            setHealth(player, JackConfig.CONFIG.playerHealth.get());
            setReach(player, JackConfig.CONFIG.blockReach.get());
            player.inventoryMenu.broadcastChanges();
        }
    }

    /** Nilai pvp dunia sebelum permainan meminjamnya. */
    private boolean pvpBefore = true;

    /**
     * Jangkauan tangan peserta.
     *
     * <p>Dipendekkan supaya orang harus benar-benar berdiri di depan piring, tombol, dan
     * terminal — jangkauan vanilla 4,5 blok membuat setengah ruangan bisa disentuh dari
     * ambang pintu.</p>
     *
     * <p>Yang diubah nilai dasar atributnya, bukan lewat modifier: modifier harus dicabut
     * dengan UUID yang sama persis, dan satu permainan yang berhenti tidak wajar akan
     * meninggalkan pemain dengan jangkauan aneh yang tidak bisa dijelaskan asalnya.</p>
     */
    private static void setReach(ServerPlayer player, double blocks) {
        AttributeInstance reach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
        if (reach != null) {
            reach.setBaseValue(blocks);
        }
    }

    /**
     * Nyawa peserta.
     *
     * <p>Nilai dasar atributnya yang diubah, bukan sekadar nyawanya diturunkan: batang
     * nyawanya ikut memendek, jadi yang terlihat pemain adalah keadaan yang sebenarnya.
     * Nyawa sekarang diisikan ulang sesudahnya karena menurunkan batas atas tidak
     * menurunkan nyawa yang sedang dipegang - pemain akan berjalan dengan 20 nyawa di
     * batang yang cuma muat 5.</p>
     */
    private static void setHealth(ServerPlayer player, double half) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(half);
        }
        player.setHealth((float) half);
    }

    private static void restoreHealth(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getAttribute().getDefaultValue());
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void restoreReach(ServerPlayer player) {
        AttributeInstance reach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
        if (reach != null) {
            reach.setBaseValue(reach.getAttribute().getDefaultValue());
        }
    }

    public void stop(MinecraftServer server, boolean announce) {
        server.setPvpAllowed(pvpBefore);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            restoreReach(player);
            restoreHealth(player);
        }
        pendingExecution.clear();
        pendingSafe.clear();
        pendingOffline.clear();
        jackWatches.clear();
        skipVotes.clear();
        statusBarMuted.clear();
        endBoardView(server);
        clearExecutionCameras(server);
        endDebugLaser(server);
        clearCorpses(server);
        MilkSupply.clearAll(server);
        // Badan pengganti ikut dibubarkan: begitu permainannya berhenti, tidak ada
        // lagi yang diwakilinya. Isi perut yang sempat disimulasikan ikut hilang, dan
        // memang harus - pemainnya akan masuk kembali sebagai orang biasa, bukan
        // sebagai peserta yang tertinggal setengah kelaparan dari game yang sudah usai.
        OfflinePlayers.get().clear(server);
        setParticipantsInvulnerable(server, false);
        Cafeteria.get().stop(server);
        HungerRules.clear();
        for (ServerPlayer player : onlineParticipants(server)) {
            stripUniform(player);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetwork.toPlayer(player, new JackListPacket(List.of()));
            ModNetwork.toPlayer(player, SkillStatePacket.none());
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
        // Di luar penjagaan isRunning() di bawah: penanda wadah justru dipakai saat
        // tidak ada permainan, waktunya map disusun.
        MilkSupply.tickChecking(server);

        if (!isRunning()) {
            return;
        }

        timer--;

        // Fase reveal dilewati: layarnya mengunci input pemain, dan membuat orang
        // yang tidak bisa bergerak jadi lapar bukan tantangan, cuma pajak.
        if (phase != GamePhase.REVEAL) {
            tickHunger(server);
        }
        tickOfflinePlayers(server, phase != GamePhase.REVEAL);
        tickPoison(server);
        tickJackWatches(server);

        if (phase == GamePhase.REVEAL) {
            if (timer <= 0) {
                endReveal(server);
            }
        } else if (phase == GamePhase.ROUND) {
            if (server.getTickCount() % 20 == 0) {
                enforceUniforms(server);
            }
            tickSkipVote(server);
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
        // Sekali sedetik: pesan status bar vanilla hidup 3 detik, jadi kiriman sesering
        // ini sudah cukup untuk membuatnya terlihat menetap, dan angka detiknya memang
        // cuma berubah sedetik sekali.
        if (server.getTickCount() % 20 == 0) {
            sendStatusBars(server);
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
    /**
     * Hitung mundur satu Jack yang sedang pergi.
     *
     * <p>Dua tahap dalam satu catatan: {@link #countdown} berjalan sampai lasernya
     * dikirim, lalu {@link #impact} berjalan sampai sinarnya jatuh. Tahap kedua dimulai
     * dengan nilai negatif supaya bisa dibedakan dari "sudah nol" — nol berarti sinarnya
     * jatuh tick ini.</p>
     */
    private static final class JackWatch {
        int countdown;
        int impact = -1;
        @Nullable
        Vec3 origin;

        JackWatch(int countdown) {
            this.countdown = countdown;
        }
    }

    /** Jack yang sedang offline beserta sisa waktunya. */
    private final Map<UUID, JackWatch> jackWatches = new LinkedHashMap<>();

    /**
     * Umur paket laser untuk hukuman ini.
     *
     * <p>Harus lebih panjang dari masa mengisi dayanya: client membuang lasernya begitu
     * umurnya lewat, dan sinar yang dibuang tepat saat muatannya penuh berarti
     * tembakannya tidak pernah tergambar sama sekali. Sisanya dipakai sinar itu
     * meluncur, menyala, lalu memudar — panjang sisa yang sama dengan yang didapat
     * eksekusi biasa.</p>
     */
    private static final int JACK_LASER_TICKS = LASER_CHARGE_TICKS * 2;

    /**
     * Kapan saja sisa waktunya dicatat ke log server. Detik, dihitung dari akhir.
     *
     * <p>Cuma ke log, tidak pernah ke pemain. Pengumuman apa pun soal hitungan ini —
     * bahkan sekadar "ada yang sedang dihitung mundur" — sama saja mengumumkan bahwa
     * yang sedang pergi itu Jack, dan itu satu-satunya rahasia yang dijaga permainan
     * ini.</p>
     */
    private static final int[] JACK_WATCH_LOG_MARKS = {60, 30, 10, 5};

    /**
     * Mulai hitung mundur untuk Jack yang baru saja keluar.
     *
     * <p>Aturannya cuma untuk Jack, dan memang harus begitu: permainan berhenti
     * berjalan kalau pemburunya tidak ada. Ace yang pergi tidak butuh aturan tambahan —
     * fase menebak berikutnya sudah menghukumnya sendiri.</p>
     */
    private void watchOfflineJack(PlayerData data) {
        int seconds = JackConfig.CONFIG.jackOfflineSeconds.get();
        if (seconds <= 0 || !data.isJack()) {
            return;
        }
        jackWatches.put(data.uuid, new JackWatch(seconds * 20));
        JackOfHeartsMod.LOGGER.info(
                "[JackOfHearts] Jack {} keluar dari server. Hitung mundur {} detik sebelum dieksekusi laser.",
                data.name, seconds);
    }

    /**
     * Jack yang tidak kembali sampai waktunya habis dieksekusi laser dari langit.
     *
     * <p>Kembali kapan pun sebelum sinarnya benar-benar jatuh membatalkan semuanya,
     * termasuk selama laser sudah mengisi daya — sinarnya tinggal memudar tanpa
     * mengenai apa pun. Yang dituju aturan ini adalah Jack yang menghilang, bukan Jack
     * yang koneksinya putus sebentar.</p>
     */
    private void tickJackWatches(MinecraftServer server) {
        if (jackWatches.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, JackWatch>> it = jackWatches.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, JackWatch> entry = it.next();
            UUID uuid = entry.getKey();
            JackWatch watch = entry.getValue();
            PlayerData data = participants.get(uuid);

            if (data == null || !data.alive || !OfflinePlayers.get().has(uuid)) {
                it.remove();
                JackOfHeartsMod.LOGGER.info("[JackOfHearts] Hitung mundur Jack {} dibatalkan.",
                        data != null ? data.name : uuid);
                continue;
            }

            if (watch.impact >= 0) {
                if (--watch.impact > 0) {
                    continue;
                }
                it.remove();
                strikeOfflineJack(server, data, watch.origin);
                continue;
            }

            watch.countdown--;
            logJackCountdown(data, watch.countdown);
            if (watch.countdown > 0) {
                continue;
            }

            MannequinEntity body = OfflinePlayers.get().entity(uuid);
            if (body == null) {
                // Badannya tidak ada di dunia — chunk-nya tidak termuat, jadi tidak ada
                // seorang pun di sana yang bisa menyaksikan adegannya. Hukumannya tetap
                // jatuh; yang tidak ada cuma tontonannya.
                it.remove();
                strikeOfflineJack(server, data, null);
                continue;
            }
            watch.origin = fireLaserAt(body, uuid, JACK_LASER_TICKS, true, true);
            watch.impact = LASER_CHARGE_TICKS;
            JackOfHeartsMod.LOGGER.info(
                    "[JackOfHearts] Waktu habis: laser dari langit menembak badan Jack {}.", data.name);
        }
    }

    private static void logJackCountdown(PlayerData data, int ticksLeft) {
        if (ticksLeft <= 0 || ticksLeft % 20 != 0) {
            return;
        }
        int seconds = ticksLeft / 20;
        for (int mark : JACK_WATCH_LOG_MARKS) {
            if (seconds == mark) {
                JackOfHeartsMod.LOGGER.info("[JackOfHearts] Jack {} masih offline: sisa {} detik.",
                        data.name, seconds);
                return;
            }
        }
    }

    /**
     * Detik tumbukannya untuk Jack yang kehabisan waktu.
     *
     * <p>Kalau dia Jack terakhir, permainan berakhir di sini — dan urutannya penting:
     * kamera semua orang berpindah ke Papan Peserta dulu, panelnya padam di depan mata
     * mereka, baru kemenangan Ace diumumkan. Kalau pengumumannya duluan, layar
     * kemenangan menutupi satu-satunya adegan yang menjelaskan kenapa permainannya
     * berakhir.</p>
     */
    private void strikeOfflineJack(MinecraftServer server, PlayerData data, @Nullable Vec3 origin) {
        data.alive = false;
        recordDeath();
        strikeMannequin(data.uuid, OfflinePlayers.get().entity(data.uuid), origin);

        boolean lastJack = participants.values().stream().noneMatch(d -> d.isJack() && d.alive);
        if (lastJack) {
            int boardStart = boardViewStartDelay(1);
            queuePanelOff(data.uuid, boardStart + BOARD_VIEW_LEAD);
            scheduleBoardView(server, 1, boardStart);
        } else {
            // Masih ada Jack lain, jadi permainannya lanjut dan tidak ada adegan papan
            // yang perlu ditunggu: panelnya padam sekarang juga.
            queuePanelOff(data.uuid, 1);
        }

        JackOfHeartsMod.LOGGER.info("[JackOfHearts] Jack {} tereliminasi karena tidak kembali.", data.name);
        broadcast(server, eliminated(data.name));
        checkWinConditions(server, false);
    }

    /**
     * Peserta yang sedang offline: badan penggantinya dipastikan berdiri, dan perutnya
     * tetap berjalan.
     *
     * <p>Laparnya sengaja tidak berhenti selama dia pergi. Kalau berhenti, keluar dari
     * server jadi cara paling murah untuk melewati kelaparan — dan kafetaria, yang
     * seluruh tegangannya bertumpu pada stok makanan yang terbatas, kehilangan
     * artinya.</p>
     */
    private void tickOfflinePlayers(MinecraftServer server, boolean hunger) {
        for (UUID uuid : OfflinePlayers.get().tick(server, hunger)) {
            starveOffline(server, uuid);
        }
    }

    /**
     * Peserta offline yang nyawanya habis kelaparan.
     *
     * <p>Diperlakukan sama dengan peserta yang mati biasa, bukan yang dieksekusi:
     * panelnya padam sekarang juga tanpa menunggu adegan apa pun, karena memang tidak
     * ada adegan — tidak ada laser, dan tidak ada seorang pun yang menghukumnya.</p>
     */
    private void starveOffline(MinecraftServer server, UUID uuid) {
        PlayerData data = participants.get(uuid);
        if (data == null || !data.alive) {
            OfflinePlayers.get().remove(uuid);
            return;
        }
        data.alive = false;
        recordDeath();
        MannequinEntity body = OfflinePlayers.get().entity(uuid);
        if (body != null) {
            // Tidak ada asal sinar, jadi mayatnya ambruk ke belakang - arah bawaan
            // untuk kematian yang tidak datang dari mana pun.
            spawnCorpse(body, null, uuid);
        }
        OfflinePlayers.get().remove(uuid);
        queuePanelOff(uuid, 1);
        broadcast(server, eliminated(data.name));
        checkWinConditions(server, false);
    }

    /** Lapar yang dipercepat cuma berlaku untuk peserta yang masih hidup. */
    private void tickHunger(MinecraftServer server) {
        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data != null && data.alive) {
                HungerRules.tick(player, data.hungerCursed);
            }
        }
    }

    // ------------------------------------------------------------------ skill Jack

    /** Berapa lama racun bekerja sebelum korbannya gugur. */
    private static final int POISON_TICKS = 5 * 60 * 20;
    /** Sisa waktu racun saat korbannya mulai merasakan ada yang tidak beres. */
    private static final int POISON_WARNING_TICKS = 60 * 20;
    /** Sejauh apa skill boleh menjangkau, diukur di server. */
    private static final double SKILL_RANGE = 3.0D;

    /**
     * Jack memakai satu skill.
     *
     * <p>Jangkauannya diperiksa ulang di sini walau client sudah memilih sasarannya
     * sendiri: yang dikirim client cuma id, dan id bisa menunjuk siapa saja. Batasnya
     * sedikit lebih longgar daripada yang dipakai client ({@code 2}) supaya sasaran yang
     * bergerak tidak ditolak hanya karena paketnya sampai satu tick terlambat.</p>
     */
    public void useSkill(ServerPlayer jack, Skill skill, int targetId, boolean self, BlockPos food) {
        PlayerData data = participants.get(jack.getUUID());
        if (data == null || !data.alive || !data.isJack() || phase != GamePhase.ROUND) {
            return;
        }
        if (data.skillUses[skill.ordinal()] <= 0) {
            jack.displayClientMessage(Component.translatable("message.jackofhearts.skill_empty")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (data.skillRound == round) {
            jack.displayClientMessage(Component.translatable("message.jackofhearts.skill_this_round")
                    .withStyle(ChatFormatting.RED), true);
            muteStatusBar(jack, 40);
            return;
        }
        if (skill.targetsFood()) {
            usePoison(jack, data, food);
            return;
        }

        ServerPlayer victim = self ? jack : resolveSkillTarget(jack, targetId);
        if (victim == null) {
            jack.displayClientMessage(Component.translatable("message.jackofhearts.skill_no_target")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        PlayerData victimData = participants.get(victim.getUUID());
        if (victimData == null || !victimData.alive) {
            jack.displayClientMessage(Component.translatable("message.jackofhearts.skill_no_target")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        switch (skill) {
            case DRUNK ->
                    // Ronde berikutnya, bukan sekarang: korban yang langsung terhuyung
                    // di depan Jack praktis menunjuk siapa pelakunya.
                    victimData.drunkRound = round + 1;
            // Korbannya tidak diberi tahu apa-apa, dan itu justru seluruh gunanya.
            // Yang tahu dirinya sedang dicurangi akan berhenti mempercayai matanya
            // sendiri; yang tidak tahu akan membacakan kartu yang salah dengan mantap,
            // dan habis makan lebih cepat tanpa pernah curiga kenapa.
            case HUNGER -> victimData.hungerCursed = true;
            default -> {
                return;
            }
        }

        data.skillUses[skill.ordinal()]--;
        data.skillRound = round;
        jack.displayClientMessage(Component.translatable("message.jackofhearts.skill_used",
                        Component.translatable("skill.jackofhearts." + skill.getId()),
                        self ? Component.translatable("message.jackofhearts.skill_self")
                                : Component.literal(victimData.name),
                        data.skillUses[skill.ordinal()])
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        muteStatusBar(jack, 40);
        sendSkillState(jack, data);
        JackOfHeartsMod.LOGGER.info("[JackOfHearts] {} memakai skill {} ke {}.",
                data.name, skill.getId(), victimData.name);
    }

    @Nullable
    private ServerPlayer resolveSkillTarget(ServerPlayer jack, int targetId) {
        if (targetId < 0) {
            return null;
        }
        Entity entity = jack.serverLevel().getEntity(targetId);
        if (!(entity instanceof ServerPlayer victim) || victim == jack) {
            return null;
        }
        return victim.distanceTo(jack) <= SKILL_RANGE ? victim : null;
    }

    /**
     * Kelonggaran jangkauan racun di atas jangkauan tangan, diukur dari mata ke titik
     * tengah piring. Crosshair client mengukur sampai permukaan blok, bukan titik
     * tengahnya, jadi tanpa kelonggaran ini piring di ujung jangkauan yang jelas bisa
     * ditunjuk malah ditolak.
     */
    private static final double POISON_REACH_SLACK = 1.5D;

    /**
     * Racun: satu porsi dari jenis makanan di piring yang ditatap Jack jadi beracun.
     *
     * <p>Posisinya dikirim client — piring yang digaris di layarnya — tapi jaraknya
     * diperiksa ulang di sini dengan jangkauan tangan server, karena posisi yang
     * dikirim client bisa menunjuk piring mana saja di meja.</p>
     */
    private void usePoison(ServerPlayer jack, PlayerData data, BlockPos food) {
        double reach = jack.getBlockReach() + POISON_REACH_SLACK;
        BlockState state = jack.level().getBlockState(food);
        if (jack.getEyePosition().distanceToSqr(Vec3.atCenterOf(food)) > reach * reach
                || !Cafeteria.get().poison(jack, food, state)) {
            jack.displayClientMessage(Component.translatable("message.jackofhearts.poison_no_target")
                    .withStyle(ChatFormatting.RED), true);
            muteStatusBar(jack, 40);
            return;
        }
        data.skillUses[Skill.POISON.ordinal()]--;
        data.skillRound = round;
        // Tidak mungkin null di sini: poison() di atas sudah menolak yang bukan piring.
        ItemStack dish = new ItemStack(CafeteriaFood.foodOf(state));
        jack.displayClientMessage(Component.translatable("message.jackofhearts.poison_placed",
                        dish.getHoverName(), data.skillUses[Skill.POISON.ordinal()])
                .withStyle(ChatFormatting.DARK_GREEN), true);
        muteStatusBar(jack, 40);
        sendSkillState(jack, data);
        JackOfHeartsMod.LOGGER.info("[JackOfHearts] {} meracuni satu porsi {} di {}.",
                data.name, dish.getItem(), food.toShortString());
    }

    /** Sisa pemakaian skill, cuma dikirim ke Jack. */
    private void sendSkillState(ServerPlayer player, @Nullable PlayerData data) {
        if (data == null || !data.isJack() || !data.alive || !isRunning()) {
            ModNetwork.toPlayer(player, SkillStatePacket.none());
            return;
        }
        ModNetwork.toPlayer(player, new SkillStatePacket(data.skillUses.clone(), data.skillRound == round));
    }

    /**
     * Racun berjalan tiap tick untuk semua peserta, online maupun tidak.
     *
     * <p>Racun tidak peduli korbannya sedang menonton layarnya atau tidak — dan kalau
     * berhenti selama dia keluar, keluar dari server jadi penawar racun yang paling
     * murah.</p>
     */
    private void tickPoison(MinecraftServer server) {
        for (PlayerData data : List.copyOf(participants.values())) {
            if (!data.alive || data.poisonTicks <= 0) {
                continue;
            }
            data.poisonTicks--;
            ServerPlayer player = server.getPlayerList().getPlayer(data.uuid);
            if (data.poisonTicks == POISON_WARNING_TICKS && player != null) {
                player.sendSystemMessage(Component.translatable("message.jackofhearts.poison_warning")
                        .withStyle(ChatFormatting.DARK_RED));
            }
            if (data.poisonTicks <= 0) {
                killByPoison(server, data, player);
            }
        }
    }

    /**
     * Racunnya bekerja. Ditangani seperti kelaparan, bukan seperti laser: tidak ada
     * adegan, tidak ada sinar, cuma orang yang tiba-tiba jatuh di tempat dia berdiri.
     */
    private void killByPoison(MinecraftServer server, PlayerData data, @Nullable ServerPlayer player) {
        data.alive = false;
        recordDeath();
        if (player != null) {
            spawnCorpse(player, null, data.uuid);
            stripUniform(player);
            purgeCafeteriaFood(player);
            player.setHealth(player.getMaxHealth());
            HungerRules.reset(player);
            player.setInvulnerable(false);
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.translatable("message.jackofhearts.poison_died")
                    .withStyle(ChatFormatting.DARK_RED));
        } else {
            MannequinEntity body = OfflinePlayers.get().entity(data.uuid);
            if (body != null) {
                spawnCorpse(body, null, data.uuid);
            }
            OfflinePlayers.get().remove(data.uuid);
        }
        queuePanelOff(data.uuid, 1);
        broadcast(server, eliminated(data.name));
        checkWinConditions(server, false);
    }

    /** Peserta ini baru saja memakan porsi yang diracuni Jack. */
    public void onPoisonedFoodEaten(ServerPlayer player) {
        PlayerData data = participants.get(player.getUUID());
        if (data == null || !data.alive || !isRunning() || data.poisonTicks > 0) {
            return;
        }
        data.poisonTicks = POISON_TICKS;
        player.sendSystemMessage(Component.translatable("message.jackofhearts.poison_eaten")
                .withStyle(ChatFormatting.DARK_RED));
    }

    /**
     * Menyembuhkan semua yang bisa disembuhkan: racun, mabuk, dan lapar yang digandakan.
     *
     * <p>Belum ada yang memanggilnya — susu penawarnya menyusul. Dipisah sebagai satu
     * method supaya penawar apa pun nanti cukup memanggil satu tempat ini, bukan
     * menghapus tiga keadaan yang tersebar.</p>
     */
    @Nullable
    public String cure(ServerPlayer player) {
        PlayerData data = participants.get(player.getUUID());
        if (data == null) {
            return null;
        }
        List<String> active = new ArrayList<>(3);
        if (data.poisonTicks > 0) {
            active.add("poison");
        }
        if (data.drunkRound >= round) {
            active.add("drunk");
        }
        if (data.hungerCursed) {
            active.add("hunger");
        }
        if (active.isEmpty()) {
            return null;
        }

        // Satu gelas, satu penyakit - dan yang mana diundi. Kalau sekali minum
        // menyembuhkan semuanya, susu yang langka justru jadi tombol reset: siapa pun
        // yang berhasil menemukan satu ember langsung bersih dari seluruh kerja Jack
        // sepanjang permainan.
        String cured = active.get(player.getRandom().nextInt(active.size()));
        switch (cured) {
            case "poison" -> data.poisonTicks = 0;
            case "drunk" -> data.drunkRound = 0;
            default -> data.hungerCursed = false;
        }
        syncPlayer(player, data);
        return cured;
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
            // Tanpa animasi tidak ada yang perlu ditunggu: perannya diumumkan sekarang.
            beginRound(server);
            announceRoles(server);
            return;
        }
        seconds = Math.max(seconds, MIN_REVEAL_SECONDS);

        phase = GamePhase.REVEAL;
        timer = seconds * 20;

        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data == null) {
                continue;
            }
            player.setInvulnerable(true);
            ModNetwork.toPlayer(player, revealPacket(data.role, timer));
        }
        syncAll(server);
    }

    /**
     * Layar kocok kartu untuk satu peserta.
     *
     * <p>Kartu Unknown ikut dijejer terbuka seperti kartu Jack. Semua orang boleh tahu
     * ada berapa Unknown di meja, sama seperti mereka boleh tahu ada berapa Jack — yang
     * dirahasiakan cuma siapa.</p>
     */
    private OpenRoleRevealPacket revealPacket(Role role, int ticksLeft) {
        int totalPlayers = participants.size();
        int totalJacks = (int) participants.values().stream().filter(PlayerData::isJack).count();
        int totalUnknowns = (int) participants.values().stream().filter(PlayerData::isUnknown).count();
        int cards = Math.min(totalPlayers, MAX_LINEUP_CARDS);
        int jacks = Math.min(totalJacks, cards);
        int unknowns = Math.min(totalUnknowns, cards - jacks);
        return new OpenRoleRevealPacket(role.ordinal(), ticksLeft, cards, jacks, unknowns,
                totalPlayers, totalJacks, totalUnknowns, false);
    }

    /**
     * Memutar animasi reveal untuk satu pemain tanpa menyentuh state game sama sekali.
     * Dipakai {@code /jack shuffle} buat menyetel animasi tanpa harus memulai game.
     */
    public void previewReveal(ServerPlayer player, int playerCount, int jacks, Role role) {
        int total = Math.max(playerCount, 1);
        int cards = Math.min(total, MAX_LINEUP_CARDS);
        int jackCards = Math.min(jacks, cards);
        int unknowns = unknownCountFor(total, jacks, 0);
        ModNetwork.toPlayer(player, new OpenRoleRevealPacket(role.ordinal(), 0, cards, jackCards,
                Math.min(unknowns, cards - jackCards), total, jacks, unknowns, true));
    }

    private void endReveal(MinecraftServer server) {
        setParticipantsInvulnerable(server, false);
        // Ronde dimulai lebih dulu, baru perannya diumumkan. Urutannya bukan selera:
        // daftar Jack menolak dikirim selama fase masih REVEAL, dan fase itu baru
        // berganti di dalam beginRound.
        beginRound(server);
        announceRoles(server);
    }

    /**
     * Memberitahu tiap peserta perannya lewat chat, dan memberi tahu para Jack siapa
     * saja rekannya.
     *
     * <p>Baru dikirim <b>setelah</b> animasi kocok kartu selesai, bukan saat perannya
     * dibagikan. Kalau lebih awal, chat sudah menuliskan jawabannya sementara kartunya
     * masih berputar - dan seluruh animasi itu tidak ada gunanya lagi.</p>
     */
    private void announceRoles(MinecraftServer server) {
        for (ServerPlayer player : onlineParticipants(server)) {
            PlayerData data = participants.get(player.getUUID());
            if (data == null) {
                continue;
            }
            // Baris kosong di atas dan di bawah: pesan ini datang bersamaan dengan
            // ramainya awal ronde, dan tanpa jarak dia tenggelam di antara yang lain.
            player.sendSystemMessage(Component.empty());
            player.sendSystemMessage(Component.translatable("message.jackofhearts.your_role",
                    data.role.getDisplayName()).withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable("message.jackofhearts.role_hint_"
                    + data.role.getId()).withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.empty());
            sendJackList(player, data);
        sendSkillState(player, data);
        }
    }

    /**
     * Daftar Jack, dikirim <b>hanya</b> ke sesama Jack.
     *
     * <p>Inilah yang membuat nama rekan Jack tergambar merah di layarnya. Client Ace
     * tidak pernah menerima daftarnya sama sekali - bukan disembunyikan saat
     * menggambar, tapi memang tidak pernah sampai ke sana. Selama animasi kocok kartu
     * pun belum dikirim: nama merah yang muncul sebelum kartunya selesai membalik sama
     * saja membocorkan hasilnya lebih dulu.</p>
     */
    private void sendJackList(ServerPlayer player, @Nullable PlayerData data) {
        if (data == null || !data.isJack() || phase == GamePhase.REVEAL) {
            ModNetwork.toPlayer(player, new JackListPacket(List.of()));
            return;
        }
        List<UUID> jacks = new ArrayList<>();
        for (PlayerData other : participants.values()) {
            if (other.isJack()) {
                jacks.add(other.uuid);
            }
        }
        ModNetwork.toPlayer(player, new JackListPacket(jacks));
    }

    private void setParticipantsInvulnerable(MinecraftServer server, boolean invulnerable) {
        for (ServerPlayer player : onlineParticipants(server)) {
            player.setInvulnerable(invulnerable);
        }
    }

    private void beginRound(MinecraftServer server) {
        round++;
        phase = GamePhase.ROUND;
        // Pemungutan suaranya per ronde: yang sepakat mempercepat ronde lalu belum tentu
        // sepakat lagi sekarang.
        skipVotes.clear();
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
        MilkSupply.spawn(server, MilkSupply.forRound(round));
        // Jatah skill per ronde kembali terbuka: panelnya harus diberi tahu, kalau tidak
        // ikonnya tetap meredup sepanjang ronde baru padahal sudah boleh dipakai lagi.
        for (ServerPlayer player : onlineParticipants(server)) {
            sendSkillState(player, participants.get(player.getUUID()));
        }

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
        //
        // Yang badan penggantinya sedang berdiri di dunia ikut masuk adegan laser
        // seperti peserta lain: terminalnya mengunci badan itu, menembaknya, dan
        // mayatnya ambruk di situ. Dari mata penonton tidak ada bedanya dengan peserta
        // yang hadir tapi salah menebak — dan memang begitulah seharusnya terbaca,
        // karena yang terjadi persis sama: dia tidak menjawab tepat waktu.
        List<UUID> offlineWrong = new ArrayList<>();
        boolean eliminate = JackConfig.CONFIG.eliminateOnWrongGuess.get();
        for (PlayerData data : participants.values()) {
            if (!data.alive || server.getPlayerList().getPlayer(data.uuid) != null) {
                continue;
            }
            if (eliminate && OfflinePlayers.get().entity(data.uuid) != null) {
                offlineWrong.add(data.uuid);
                continue;
            }
            // Tidak ada badan yang bisa ditembak — chunk-nya belum dimuat, atau
            // eksekusi memang dimatikan lewat config. Tidak ada laser dan tidak ada
            // adegan, jadi panelnya padam sekarang juga.
            data.alive = false;
            recordDeath();
            OfflinePlayers.get().remove(data.uuid);
            queuePanelOff(data.uuid, 1);
            broadcast(server, eliminated(data.name));
        }

        // Tenggat Jack. Yang dihitung bukan berapa orang dibunuhnya - Jack tidak bisa
        // melukai siapa-siapa - melainkan ada tidaknya korban sama sekali: orang yang
        // salah menebak simbolnya, atau yang kehabisan makanan. Itulah satu-satunya
        // bentuk hasil kerja Jack.
        //
        // Ronde ini ikut dihitung sebagai ronde berkorban kalau ada yang gugur di
        // dalamnya, termasuk yang barusan diputuskan gugur di atas dan yang akan
        // ditembak sebentar lagi - kalau tidak, Jack yang baru saja berhasil justru
        // dihukum di detik keberhasilannya.
        boolean deathsThisRound = lastDeathRound == round || !wrong.isEmpty() || !offlineWrong.isEmpty();
        int deathlessRounds = deathsThisRound ? 0 : round - lastDeathRound;
        boolean jackFailed = deathlessRounds >= JackConfig.CONFIG.maxDeathlessRounds.get();
        if (jackFailed) {
            jackFailureRounds = deathlessRounds;
            // Semua Jack yang masih hidup ikut ditembak, tebakannya benar atau salah.
            // Yang sedang offline sudah masuk daftar di atas bersama peserta offline
            // lain, jadi yang tersisa cuma yang tebakannya tadi menyelamatkannya.
            safe.removeIf(player -> {
                PlayerData data = participants.get(player.getUUID());
                if (data == null || !data.isJack()) {
                    return false;
                }
                wrong.add(player);
                return true;
            });
        }

        for (PlayerData data : participants.values()) {
            data.guess = null;
        }
        // Debug tebakan-selalu-benar berlaku satu ronde saja: ronde yang barusan
        // dinilai. Tanpa ini, sekali dinyalakan pesertanya kebal sampai game selesai.
        alwaysCorrect.clear();

        if (eliminate) {
            // Selalu lewat fase eksekusi, termasuk ronde yang tidak memakan korban
            // sama sekali: yang menebak benar pun harus melihat lasernya mengisi daya
            // lalu padam. Kalau adegannya cuma muncul saat ada yang mati, adegan itu
            // sendiri sudah membocorkan hasilnya sebelum sinarnya jatuh.
            //
            // Hasilnya sengaja belum diumumkan ke siapa pun. Fase laser yang akan
            // mengumumkannya, tepat saat sinarnya jatuh.
            beginExecution(server, wrong, safe, offlineWrong);
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

        // Tanpa eksekusi, tenggat Jack tidak punya laser untuk menghukumnya. Hasil
        // akhirnya tetap sama - Jack gagal, yang bertahan menang - cuma diumumkan
        // langsung tanpa adegan.
        if (jackFailed) {
            jackFailureRounds = 0;
            // Jack-nya gagal, dan itu sama artinya dengan Jack yang gugur: Unknown yang
            // masih hidup ikut Ace.
            resolveUnknowns(server, Role.ACE, false);
            finish(server, Role.ACE,
                    Component.translatable("message.jackofhearts.jack_failed", deathlessRounds));
            return;
        }

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
     * @param offlineVictims peserta yang sedang offline dan tidak sempat menebak;
     *                       yang ditembak badan penggantinya.
     */
    private void beginExecution(MinecraftServer server, List<ServerPlayer> victims,
                                List<ServerPlayer> safe, List<UUID> offlineVictims) {
        // Pratinjau memakai executionTotalTicks/executionFireTick yang sama. Kalau ada
        // yang masih berjalan, ditutup dulu supaya hitungannya tidak saling menimpa.
        endDebugLaser(server);

        phase = GamePhase.EXECUTION;
        timer = JackConfig.CONFIG.executionSeconds.get() * 20;
        executionTotalTicks = timer;
        executionFireTick = timer - LASER_CHARGE_TICKS;

        pendingExecution.clear();
        pendingSafe.clear();
        pendingOffline.clear();
        clearExecutionCameras(server);

        for (ServerPlayer victim : victims) {
            pendingExecution.add(victim.getUUID());
            Vec3 origin = fireLaserAt(victim, victim.getUUID(), timer, true);
            beginCinematic(victim, origin, timer);
        }
        for (ServerPlayer survivor : safe) {
            pendingSafe.add(survivor.getUUID());
            Vec3 origin = fireLaserAt(survivor, survivor.getUUID(), timer, false);
            beginCinematic(survivor, origin, timer);
        }
        // Badan pengganti tidak dapat kamera sinematik: kameranya untuk korbannya
        // sendiri, dan pemiliknya sedang tidak melihat apa-apa. Yang lain tetap
        // melihat adegannya utuh dari kameranya masing-masing.
        for (UUID uuid : offlineVictims) {
            MannequinEntity body = OfflinePlayers.get().entity(uuid);
            if (body == null) {
                continue;
            }
            Vec3 origin = fireLaserAt(body, uuid, timer, true);
            pendingOffline.put(uuid, new MannequinShot(body, origin, body.getYRot(),
                    gazeYawTowards(body, origin)));
        }
        syncAll(server);
    }

    /**
     * Arah hadap yang membuat badan pengganti menoleh ke asal sinarnya.
     *
     * <p>Cuma sudut mendatarnya. Badan pengganti tidak punya kepala yang bisa
     * mendongak sendiri di sini, dan laser yang turun dari langit tidak punya arah
     * mendatar yang berarti — dalam hal itu arah hadapnya dibiarkan apa adanya
     * daripada berputar tanpa sebab.</p>
     */
    private static float gazeYawTowards(LivingEntity body, Vec3 origin) {
        Vec3 head = body.getEyePosition();
        Vec3 d = origin.subtract(head);
        return Math.sqrt(d.x * d.x + d.z * d.z) < 1.0E-3D ? body.getYRot() : yawTowards(head, origin);
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

        BlockPos block = isInGuessRoom(victim) ? findGuessBlock(victim, victim.getUUID()) : null;
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

        if (remaining <= executionFireTick) {
            return;
        }
        // Badan pengganti ikut menoleh ke terminal yang menembaknya. Bukan hiasan:
        // tanpa itu dia satu-satunya sasaran di ruangan yang berdiri membelakangi
        // lasernya, dan matanya sendiri yang membocorkan bahwa dia cuma patung.
        for (MannequinShot shot : pendingOffline.values()) {
            if (!shot.body().isRemoved()) {
                shot.body().setFacing(Mth.rotLerp(progress, shot.startYaw(), shot.targetYaw()));
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
    private Vec3 fireLaserAt(LivingEntity victim, UUID owner, int totalTicks, boolean fires) {
        return fireLaserAt(victim, owner, totalTicks, fires, false);
    }

    /**
     * @param fromSky paksa sinarnya turun dari langit, tanpa peduli korban sedang
     *                berdiri di ruang tebak atau tidak. Dipakai hukuman Jack yang
     *                terlalu lama pergi: hukuman itu tidak datang dari Terminal
     *                Tebakan mana pun, jadi tidak boleh keluar dari sana.
     */
    private Vec3 fireLaserAt(LivingEntity victim, UUID owner, int totalTicks, boolean fires,
                             boolean fromSky) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return victim.getEyePosition();
        }
        // Setinggi mata: itu posisi kepala. Client tetap mengikuti entitasnya tiap
        // frame, titik ini hanya cadangan kalau entitasnya tidak termuat di sana.
        Vec3 target = victim.getEyePosition();
        Vec3 origin = fromSky
                ? new Vec3(target.x, level.getMaxBuildHeight(), target.z)
                : laserOrigin(victim, owner, target);

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
    private Vec3 laserOrigin(LivingEntity victim, UUID owner, Vec3 target) {
        if (isInGuessRoom(victim)) {
            BlockPos block = findGuessBlock(victim, owner);
            if (block != null) {
                // Muka atas blok, tempat bagian merah teksturnya berada.
                return new Vec3(block.getX() + 0.5D, block.getY() + 1.02D, block.getZ() + 0.5D);
            }
        }
        return new Vec3(target.x, victim.level().getMaxBuildHeight(), target.z);
    }

    /** Cek blok penanda di bawah kaki korban, beberapa blok ke bawah. */
    private boolean isInGuessRoom(LivingEntity victim) {
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
    boolean standsOnRoomMarker(LivingEntity player, Block marker) {
        Level level = player.level();
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
     *
     * @param owner UUID <b>pesertanya</b>, bukan entitas yang ditembak. Untuk peserta
     *              yang offline, yang berdiri di sana badan pengganti dengan UUID-nya
     *              sendiri — sementara catatan terminal terakhir yang diklik tetap
     *              tersimpan atas nama pemainnya.
     */
    @Nullable
    BlockPos findGuessBlock(LivingEntity victim, UUID owner) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return null;
        }
        PlayerData data = participants.get(owner);

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
                recordDeath();
            }
            if (victim != null) {
                hit.add(victim);
                continue;
            }
            // Dia keluar dari server setelah lasernya mengunci, tapi sebelum sinarnya
            // jatuh. Tetap gugur - keluar di tengah adegan bukan cara membatalkannya -
            // hanya saja tidak ada adegan yang bisa ditampilkan kepadanya. Badan
            // penggantinya, kalau sempat berdiri, ditukar dengan mayat di tempat.
            ExecutionShot shot = executionCameras.get(uuid);
            MannequinEntity body = OfflinePlayers.get().entity(uuid);
            if (body != null) {
                spawnCorpse(body, shot == null ? null : shot.origin, uuid);
            }
            OfflinePlayers.get().remove(uuid);
            queuePanelOff(uuid, 1);
        }
        pendingExecution.clear();

        // Yang offline ikut dihitung: panelnya padam di barisan yang sama, dan lama
        // adegan papan memang ditentukan oleh berapa panel yang harus padam.
        int deaths = hit.size() + pendingOffline.size();
        int boardStart = boardViewStartDelay(deaths);

        for (int i = 0; i < hit.size(); i++) {
            ServerPlayer victim = hit.get(i);
            ExecutionShot shot = executionCameras.get(victim.getUUID());
            // Panelnya padam satu per satu, bukan serentak. Kalau tiga orang gugur
            // bersamaan dan papannya mati sekaligus, yang terdengar cuma satu bunyi
            // dan tidak ada yang sempat melihat siapa saja yang hilang.
            // Panelnya padam setelah kamera sampai di depan papan, bukan sebelumnya:
            // panel yang padam saat tidak ada yang menonton sama saja tidak padam.
            queuePanelOff(victim.getUUID(), boardStart + BOARD_VIEW_LEAD + i * PANEL_OFF_STAGGER);

            // Jack yang ditembak karena kehabisan tenggat belum tentu salah menebak -
            // seringnya justru benar. Memberitahunya "salah!" berarti berbohong tentang
            // satu-satunya hal yang dia kerjakan dengan benar ronde itu.
            PlayerData victimData = participants.get(victim.getUUID());
            boolean overdueJack = jackFailureRounds > 0 && victimData != null && victimData.isJack();
            if (overdueJack) {
                victim.sendSystemMessage(Component.translatable("message.jackofhearts.jack_failed_self",
                        jackFailureRounds).withStyle(ChatFormatting.DARK_RED));
            } else {
                // Simbol aslinya disingkap tepat sebelum sinarnya jatuh, bukan saat fase
                // tebakan berakhir.
                announceWrong(victim);
            }
            strikeVictim(victim, shot == null ? null : shot.origin);

            broadcast(server, eliminated(victim.getGameProfile().getName()));
            victim.sendSystemMessage(Component.translatable("message.jackofhearts.now_spectator")
                    .withStyle(ChatFormatting.GRAY));
        }

        // Peserta yang offline: yang ditembak badan penggantinya, dan badan itu yang
        // ditukar dengan mayat. Urutannya menyambung dari korban yang hadir, jadi
        // panelnya padam satu per satu dalam satu barisan yang sama.
        int index = hit.size();
        for (Map.Entry<UUID, MannequinShot> entry : pendingOffline.entrySet()) {
            UUID uuid = entry.getKey();
            MannequinShot shot = entry.getValue();
            PlayerData data = participants.get(uuid);
            if (data != null) {
                data.alive = false;
                recordDeath();
            }
            queuePanelOff(uuid, boardStart + BOARD_VIEW_LEAD + index * PANEL_OFF_STAGGER);
            index++;
            strikeMannequin(uuid, shot.body(), shot.origin());
            broadcast(server, eliminated(data != null ? data.name : uuid.toString()));
        }
        pendingOffline.clear();

        // Alasannya sudah ikut terumumkan di baris tembakannya masing-masing, jadi di
        // sini tinggal menutup catatannya.
        jackFailureRounds = 0;

        // Papan baru menyala setelah semua korban ronde ini terdaftar, jadi lamanya
        // adegan menyesuaikan berapa panel yang harus padam.
        scheduleBoardView(server, deaths, boardStart);

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
        spawnCorpse(victim, laserOrigin, victim.getUUID());

        levelGaze(victim);
        stripUniform(victim);
        victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        victim.setInvulnerable(false);
        victim.setGameMode(GameType.SPECTATOR);
    }

    /**
     * Detik tumbukan untuk peserta yang sedang offline.
     *
     * <p>Yang dikerjakan cuma separuh dari {@link #strikeVictim}: bunyi, darah, dan
     * mayat. Tidak ada seragam yang dilepas, tidak ada kamera yang dikembalikan, dan
     * tidak ada mode permainan yang diubah — semua itu baru dikerjakan nanti, saat
     * pemainnya benar-benar masuk lagi, di {@link #onPlayerJoin}.</p>
     *
     * <p>Mayatnya dibuat dari badan penggantinya, jadi skin, seragam, arah hadap, dan
     * arah robohnya sama persis dengan yang barusan terlihat berdiri di situ.</p>
     */
    private void strikeMannequin(UUID uuid, @Nullable MannequinEntity body, @Nullable Vec3 origin) {
        if (body == null || body.isRemoved() || !(body.level() instanceof ServerLevel level)) {
            OfflinePlayers.get().remove(uuid);
            return;
        }
        Vec3 at = body.getEyePosition();
        level.playSound(null, at.x, at.y, at.z,
                ModSounds.LASER_FIRE.get(), SoundSource.MASTER, 2.0F, 1.0F);
        Blood.spurt(level, at, origin);
        spawnCorpse(body, origin, uuid);
        // Badannya dibubarkan setelah mayatnya jadi, bukan sebelumnya: isi slot armor
        // disalin apa adanya dari badan ini.
        OfflinePlayers.get().remove(uuid);
    }

    /**
     * @param owner pemilik skin yang harus tergambar di mayatnya. Sama dengan UUID
     *              korban kalau yang tertembak pemainnya sendiri, tapi berbeda kalau
     *              yang tertembak badan pengganti — badan itu punya UUID entitasnya
     *              sendiri, dan mayat berwajah orang asing bukan mayat siapa-siapa.
     */
    private void spawnCorpse(LivingEntity victim, @Nullable Vec3 laserOrigin, UUID owner) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        CorpseEntity corpse = ModEntities.CORPSE.get().create(level);
        if (corpse == null) {
            return;
        }
        corpse.copyFrom(victim, laserOrigin);
        corpse.setOwner(owner);
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
            Vec3 origin = fireLaserAt(victim, victim.getUUID(), total, fires);
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

    /**
     * @return true kalau kematiannya diambil alih permainan ini dan tidak boleh benar-
     *         benar terjadi. Yang diambil alih cuma kelaparan: korbannya langsung
     *         dijadikan penonton, sama seperti korban laser - tanpa layar kematian,
     *         tanpa isi tas yang tercecer, dan tanpa respawn yang harus diurus.
     */
    public boolean onPlayerDeath(ServerPlayer dead, DamageSource source) {
        PlayerData data = participants.get(dead.getUUID());
        if (data == null || !isRunning()) {
            return false;
        }
        boolean starved = source.is(DamageTypes.STARVE);
        data.alive = false;
        recordDeath();
        // Bukan laser: tidak ada adegan yang perlu ditunggu, panelnya padam sekarang.
        queuePanelOff(dead.getUUID(), 1);
        // Yang mati kelaparan meninggalkan mayat di tempat dia tumbang, tanpa laser dan
        // tanpa adegan apa pun. Badannya sendiri hilang seketika begitu dia masuk layar
        // kematian, dan ruangan yang tiba-tiba kosong tidak memberi tahu siapa pun apa
        // yang barusan terjadi di situ.
        //
        // Dibuat sebelum seragamnya dilepas, karena isi slot armor disalin apa adanya.
        if (starved) {
            spawnCorpse(dead, null, dead.getUUID());
        }
        stripUniform(dead);
        // Makanan yang dibawanya ikut lenyap, tidak tercecer di lantai. Kalau tidak,
        // mati kelaparan justru jadi cara paling mudah membagi bekal: jatuhkan lewat
        // mayat sendiri, dan batas bawa satu porsi lewat begitu saja.
        purgeCafeteriaFood(dead);
        broadcast(dead.server, eliminated(data.name));

        if (starved) {
            // Nyawanya dikembalikan penuh dan perutnya diisi ulang sebelum dia
            // dipindahkan: yang dibatalkan pemanggil kelas ini cuma kematiannya, dan
            // penonton bernyawa nol akan langsung mati lagi begitu mode permainannya
            // dikembalikan nanti.
            dead.setHealth(dead.getMaxHealth());
            HungerRules.reset(dead);
            dead.setInvulnerable(false);
            dead.setGameMode(GameType.SPECTATOR);
            dead.sendSystemMessage(Component.translatable("message.jackofhearts.now_spectator")
                    .withStyle(ChatFormatting.GRAY));
        }

        if (source.getEntity() instanceof ServerPlayer killer && !killer.getUUID().equals(dead.getUUID())) {
            PlayerData killerData = participants.get(killer.getUUID());
            if (killerData != null) {
                killerData.kills++;
                // Tidak ada pengumuman terpisah untuk pembunuhan: siapa pun yang gugur
                // diumumkan dengan kalimat yang sama.

            }
        }

        checkWinConditions(dead.server, false);
        return starved;
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

    /**
     * Catat bahwa ada peserta yang gugur di ronde ini.
     *
     * <p>Dipanggil dari <b>semua</b> jalur kematian — laser fase menebak, kelaparan,
     * peserta offline yang tidak menjawab — karena yang dinilai memang bukan cara
     * matinya, melainkan ada-tidaknya korban.</p>
     */
    private void recordDeath() {
        lastDeathRound = round;
    }

    /** @return true kalau game berakhir di pengecekan ini. */
    private boolean checkWinConditions(MinecraftServer server, boolean endOfRound) {
        if (!isRunning()) {
            return true;
        }

        boolean anyJackAlive = participants.values().stream().anyMatch(d -> d.side() == Role.JACK && d.alive);
        // Unknown yang sudah memihak Ace dihitung sebagai Ace: dia ikut menahan
        // kemenangan Jack sama seperti Ace lainnya.
        boolean anyAceAlive = participants.values().stream().anyMatch(d -> d.side() == Role.ACE && d.alive);

        // Habisnya Ace diperiksa lebih dulu, dan urutan itu yang menentukan siapa
        // pemenang kalau semua orang mati bersamaan: Jack. Permainan ini soal bertahan
        // dari Jack, jadi ruangan yang habis tak bersisa bukan kemenangan pihak Ace.
        //
        // Unknown yang masih hidup dan belum memihak di sini tinggal sendiri: dia jadi
        // Jack, dan menang bersama Jack. Termasuk kalau Jack-nya sendiri ikut habis di
        // detik yang sama - dia satu-satunya yang tersisa.
        if (!anyAceAlive) {
            resolveUnknowns(server, Role.JACK, false);
            finish(server, Role.JACK, Component.translatable("message.jackofhearts.win_jack_all_aces_dead"));
            return true;
        }
        // Ada Jack yang gugur selagi Unknown masih hidup: Unknown ikut Ace. Diperiksa
        // sebelum kemenangan Ace supaya Unknown yang masih hidup ikut menang di bawah.
        if (participants.values().stream().anyMatch(d -> d.isJack() && !d.alive)) {
            resolveUnknowns(server, Role.ACE, anyJackAlive);
        }
        if (!anyJackAlive) {
            finish(server, Role.ACE, Component.translatable("message.jackofhearts.win_ace_jack_dead"));
            return true;
        }

        if (endOfRound) {
            // Tenggat Jack tidak diperiksa di sini. Hukumannya bukan "Ace menang" yang
            // diumumkan begitu saja, melainkan eksekusi laser yang harus jatuh di fase
            // eksekusi bersama korban lain - lihat resolveGuessPhase.
            if (round >= JackConfig.CONFIG.jackWinRound.get()) {
                finish(server, Role.JACK, Component.translatable("message.jackofhearts.win_jack_survived", round));
                return true;
            }
        }
        return false;
    }

    /**
     * Menentukan pihak semua Unknown yang masih hidup dan belum memihak.
     *
     * <p>Yang sudah gugur dilewati: pihaknya tidak pernah ditentukan, dan dia tidak ikut
     * menang siapa-siapa. Itulah tujuan Unknown - bertahan hidup sampai salah satu pihak
     * habis lebih dulu.</p>
     *
     * @param notify beri tahu yang bersangkutan lewat pesan pribadi. Cuma dipakai kalau
     *               permainannya masih berlanjut: tujuannya berubah, dan dia harus tahu.
     *               Kalau permainannya selesai di detik yang sama, layar menang/kalah
     *               sudah memberitahunya.
     */
    private void resolveUnknowns(MinecraftServer server, Role side, boolean notify) {
        for (PlayerData data : participants.values()) {
            if (!data.isUnknown() || !data.alive || data.allegiance != null) {
                continue;
            }
            data.allegiance = side;
            JackOfHeartsMod.LOGGER.info("[JackOfHearts] Unknown {} sekarang di pihak {}.", data.name, side.getId());
            ServerPlayer player = server.getPlayerList().getPlayer(data.uuid);
            if (notify && player != null) {
                player.sendSystemMessage(Component.translatable("message.jackofhearts.unknown_resolved",
                        side.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
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

        // Satu orang satu baris. Deretan panjang yang dipisah koma harus dibaca ulang
        // dari awal untuk mencari satu nama; daftar yang menurun bisa dipindai sekali
        // lihat, dan inilah satu-satunya saat semua orang boleh tahu isinya.
        broadcast(server, Component.translatable("message.jackofhearts.role_list")
                .withStyle(ChatFormatting.GOLD));
        for (PlayerData data : participants.values()) {
            broadcast(server, Component.literal(" " + data.name + " ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(data.roleLabel())
                    .append(Component.translatable(data.alive
                                    ? "message.jackofhearts.role_list_alive"
                                    : "message.jackofhearts.role_list_dead")
                            .withStyle(data.alive ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
        }

        // Menang-kalahnya per TIM, bukan per orang: Ace yang gugur di ronde dua tetap
        // ikut menang kalau Jack akhirnya tumbang, karena yang menumbangkannya dibangun
        // dari tebakan-tebakan yang dia ikut bayar juga. Menghitungnya per orang berarti
        // separuh pemenang diberi tahu bahwa mereka kalah.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData data = participants.get(player.getUUID());
            if (data == null) {
                // Bukan peserta. Dia tidak menang dan tidak kalah, jadi tidak ada layar
                // yang jujur bisa ditampilkan kepadanya.
                continue;
            }
            // Pihaknya, bukan kartunya: Unknown menang bersama pihak yang dia ikuti, dan
            // Unknown yang belum pernah memihak tidak menang bersama siapa pun.
            boolean won = data.side() == winner;
            title(player,
                    Component.translatable(won
                                    ? "message.jackofhearts.result_win"
                                    : "message.jackofhearts.result_lose")
                            .withStyle(won ? ChatFormatting.GOLD : ChatFormatting.RED),
                    Component.translatable(won
                                    ? "message.jackofhearts.you_win"
                                    : "message.jackofhearts.you_lose")
                            .withStyle(won ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                    10, 100, 20);
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER,
                    1.0F, won ? 1.0F : 0.6F);
        }
        setParticipantsInvulnerable(server, false);
        server.setPvpAllowed(pvpBefore);
        for (ServerPlayer player : onlineParticipants(server)) {
            stripUniform(player);
            restoreReach(player);
            restoreHealth(player);
        }
        clearCorpses(server);
        OfflinePlayers.get().clear(server);
        MilkSupply.clearAll(server);
        for (ServerPlayer player : onlineParticipants(server)) {
            ModNetwork.toPlayer(player, SkillStatePacket.none());
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
        enforceMannequinUniforms();
    }

    /**
     * Badan pengganti peserta offline ikut memakai seragam dengan simbol terbaru.
     *
     * <p>Wajib diperbarui tiap ronde, bukan cukup disalin sekali saat pemiliknya
     * keluar: simbol diacak ulang tiap ronde. Badan yang masih memakai simbol ronde
     * lalu bukan sekadar salah gambar — dia jadi satu-satunya peserta yang simbolnya
     * bisa ditebak dari ingatan.</p>
     */
    private void enforceMannequinUniforms() {
        OfflinePlayers offline = OfflinePlayers.get();
        for (UUID uuid : offline.all()) {
            PlayerData data = participants.get(uuid);
            if (data == null || !data.alive) {
                continue;
            }
            ItemStack chest = offline.gear(uuid, EquipmentSlot.CHEST);
            if (!chest.is(ModItems.UNIFORM_SHIRT.get()) || SymbolArmorItem.getSymbol(chest) != data.symbol) {
                offline.setGear(uuid, EquipmentSlot.CHEST,
                        SymbolArmorItem.withSymbol(new ItemStack(ModItems.UNIFORM_SHIRT.get()), data.symbol));
            }
            if (!offline.gear(uuid, EquipmentSlot.LEGS).is(ModItems.UNIFORM_PANTS.get())) {
                offline.setGear(uuid, EquipmentSlot.LEGS, new ItemStack(ModItems.UNIFORM_PANTS.get()));
            }
        }
    }

    /** Buang seluruh makanan kafetaria dari tas pemain. */
    private static void purgeCafeteriaFood(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (CafeteriaFood.isCafeteriaFood(inventory.getItem(slot))) {
                inventory.setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
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

        // Badan penggantinya dibubarkan lebih dulu, sebelum apa pun yang lain: lapar
        // dan nyawa hasil simulasi harus sudah menempel di pemainnya sebelum HUD-nya
        // dikirim, dan sebelum dia sempat terlihat berdiri berdampingan dengan
        // badannya sendiri.
        OfflinePlayers.get().restore(player);

        if (data != null && !data.alive && isRunning()) {
            // Dia gugur selagi offline. Tidak ada layar kematian yang bisa
            // memberitahunya dan tidak ada respawn yang lewat, jadi perpindahan ke mode
            // penonton dikerjakan di sini - kalau tidak, dia masuk kembali sebagai
            // peserta hidup yang sudah tidak ada di papan.
            stripUniform(player);
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.translatable("message.jackofhearts.now_spectator")
                    .withStyle(ChatFormatting.GRAY));
        }

        if (data != null && phase == GamePhase.REVEAL) {
            player.setInvulnerable(true);
            ModNetwork.toPlayer(player, revealPacket(data.role, Math.max(timer, 1)));
        }
        rememberSkin(player, data != null || registered.containsKey(player.getUUID()));
        sendJackList(player, data);
        syncPlayer(player, data);
        // Yang baru masuk belum punya isi papan sama sekali - termasuk skin peserta
        // yang sedang offline, yang tidak mungkin dia dapat dari daftar pemainnya.
        sendSkins(player);
        syncRoster(player.server);
        Cafeteria.get().syncTo(player);
    }

    /**
     * Peserta keluar dari server di tengah permainan: badan penggantinya berdiri di
     * tempatnya.
     *
     * <p>Yang sudah gugur dilewati. Dia tinggal penonton, badannya sudah tidak ada di
     * ruangan sejak tadi, dan menaruh patung atas namanya justru mengembalikan orang
     * yang sudah tidak ikut bermain.</p>
     */
    public void onPlayerLogout(ServerPlayer player) {
        PlayerData data = participants.get(player.getUUID());
        if (data == null || !data.alive || !isRunning()) {
            return;
        }
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            return;
        }
        OfflinePlayers.get().capture(player);
        broadcast(player.server, Component.translatable("message.jackofhearts.player_offline", data.name)
                .withStyle(ChatFormatting.YELLOW));
        watchOfflineJack(data);
    }

    public void syncPlayer(ServerPlayer player, @Nullable PlayerData data) {
        boolean participating = data != null;
        boolean alive = participating && data.alive;
        boolean lockView = alive && isRunning() && JackConfig.CONFIG.lockFirstPerson.get();
        int role = participating ? data.role.ordinal() : -1;
        int guess = participating && data.guess != null ? data.guess.ordinal() : -1;
        // Mabuknya berhenti bersama rondenya, bukan bersama hitungan waktu apa pun:
        // ronde bisa dipotong lebih cepat lewat pemungutan suara, dan fase menebak
        // adalah fase yang paling tidak adil untuk dijalani dengan mata yang berbohong.
        boolean drunk = alive && phase == GamePhase.ROUND && data.drunkRound == round;
        ModNetwork.toPlayer(player, new SyncStatePacket(phase.ordinal(), round, Math.max(timer, 0),
                participating, alive, lockView, role, guess, drunk));
    }

    /**
     * Satu kalimat untuk semua kematian, apa pun sebabnya.
     *
     * <p>Sengaja tidak menyebut caranya. Cara seseorang gugur adalah keterangan yang
     * paling berharga di permainan ini — "salah menebak" berarti simbolnya sempat
     * dipercaya orang, "kelaparan" berarti dia tidak kebagian meja, "tidak hadir"
     * berarti dia sudah lama tidak ada. Semua itu petunjuk, dan chat bukan tempat
     * membagikannya gratis ke semua orang.</p>
     */
    private static Component eliminated(String name) {
        return Component.translatable("message.jackofhearts.eliminated", name)
                .withStyle(ChatFormatting.RED);
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

    /** Dipakai perintah /jack forceskip untuk memotong sisa waktu fase saat ini. */
    public void skipPhase() {
        if (isRunning()) {
            timer = 1;
        }
    }

    // ------------------------------------------------------------------ voting percepat ronde

    /**
     * Satu peserta memilih (atau membatalkan pilihannya) mempercepat ronde.
     *
     * <p>Bisa dibatalkan, dan memang harus bisa: satu-satunya cara membatalkan pilihan
     * yang tidak bisa ditarik adalah menunggu ronde habis, padahal justru menunggu itu
     * yang sedang dihindari.</p>
     *
     * @return pesan kegagalan, atau null kalau suaranya tercatat.
     */
    @Nullable
    public Component voteSkip(ServerPlayer player) {
        PlayerData data = participants.get(player.getUUID());
        if (data == null) {
            return Component.translatable("message.jackofhearts.not_participant")
                    .withStyle(ChatFormatting.RED);
        }
        if (!data.alive) {
            return Component.translatable("message.jackofhearts.eliminated_already")
                    .withStyle(ChatFormatting.RED);
        }
        if (phase != GamePhase.ROUND) {
            return Component.translatable("message.jackofhearts.skip_not_round")
                    .withStyle(ChatFormatting.RED);
        }

        MinecraftServer server = player.server;
        boolean voted = skipVotes.add(player.getUUID());
        if (!voted) {
            skipVotes.remove(player.getUUID());
        }
        int votes = skipVoteCount(server);
        int needed = skipVoterCount(server);

        player.sendSystemMessage(Component.translatable(voted
                        ? "message.jackofhearts.skip_voted"
                        : "message.jackofhearts.skip_cancelled", votes, needed)
                .withStyle(voted ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        if (voted) {
            for (ServerPlayer other : onlineParticipants(server)) {
                if (!other.getUUID().equals(player.getUUID())) {
                    other.sendSystemMessage(Component.translatable("message.jackofhearts.skip_broadcast",
                            data.name, votes, needed).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        // Angkanya berubah sekarang juga, tidak menunggu kiriman berkala berikutnya:
        // pemungutan suara yang balasannya telat terasa seperti perintahnya tidak masuk.
        sendStatusBars(server);
        return null;
    }

    /** Peserta hidup yang sedang online — merekalah yang suaranya ditunggu. */
    private int skipVoterCount(MinecraftServer server) {
        int count = 0;
        for (PlayerData data : participants.values()) {
            if (data.alive && server.getPlayerList().getPlayer(data.uuid) != null) {
                count++;
            }
        }
        return count;
    }

    private int skipVoteCount(MinecraftServer server) {
        int count = 0;
        for (UUID uuid : skipVotes) {
            PlayerData data = participants.get(uuid);
            if (data != null && data.alive && server.getPlayerList().getPlayer(uuid) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Kalau semua yang ditunggu sudah memilih, ronde langsung dipotong ke fase menebak.
     *
     * <p>Diperiksa tiap tick, bukan cuma sesudah ada yang memilih. Jumlah yang ditunggu
     * bisa berubah tanpa ada yang memilih apa pun — ada yang keluar dari server, ada
     * yang gugur — dan justru perubahan itulah yang sering melengkapi suaranya.</p>
     */
    private void tickSkipVote(MinecraftServer server) {
        if (skipVotes.isEmpty() || timer <= 1) {
            return;
        }
        int needed = skipVoterCount(server);
        if (needed <= 0 || skipVoteCount(server) < needed) {
            return;
        }
        broadcastParticipants(server, Component.translatable("message.jackofhearts.skip_all")
                .withStyle(ChatFormatting.AQUA));
        timer = 1;
    }

    // ------------------------------------------------------------------ status bar

    /**
     * Baris status di atas hotbar: ronde, sisa waktu, dan hasil pemungutan suara.
     *
     * <p>Dikirim dari server, bukan digambar client, supaya angka suaranya selalu sama
     * dengan yang dipegang mesin game — client tidak pernah perlu tahu siapa saja yang
     * sudah memilih.</p>
     */
    private void sendStatusBars(MinecraftServer server) {
        if (phase != GamePhase.ROUND && phase != GamePhase.GUESS) {
            return;
        }
        int votes = skipVoteCount(server);
        int needed = skipVoterCount(server);
        int tick = server.getTickCount();

        for (ServerPlayer player : onlineParticipants(server)) {
            Integer mutedUntil = statusBarMuted.get(player.getUUID());
            if (mutedUntil != null && tick < mutedUntil) {
                continue;
            }
            PlayerData data = participants.get(player.getUUID());
            player.displayClientMessage(statusBar(data, votes, needed, skipVotes.contains(player.getUUID())), true);
        }
    }

    private Component statusBar(@Nullable PlayerData data, int votes, int needed, boolean voted) {
        int seconds = Math.max(timer, 0) / 20;
        Component separator = Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY);

        MutableComponent line = Component.translatable("hud.jackofhearts.round", round)
                .withStyle(ChatFormatting.GOLD)
                .append(separator)
                .append(Component.literal(String.format("%d:%02d", seconds / 60, seconds % 60))
                        .withStyle(phase == GamePhase.GUESS && seconds <= 5
                                ? ChatFormatting.RED : ChatFormatting.AQUA))
                .append(separator);

        if (data != null && !data.alive) {
            return line.append(Component.translatable("hud.jackofhearts.dead").withStyle(ChatFormatting.DARK_RED));
        }
        if (phase == GamePhase.GUESS) {
            // Di fase menebak tidak ada yang bisa dipercepat lagi, jadi tempat yang sama
            // dipakai untuk hal yang paling menentukan detik itu: sudah menebak atau belum.
            return line.append(data != null && data.guess != null
                    ? Component.translatable("hud.jackofhearts.saved_guess", data.guess.getLabel())
                            .withStyle(ChatFormatting.GREEN)
                    : Component.translatable("hud.jackofhearts.no_guess").withStyle(ChatFormatting.RED));
        }
        line = line.append(Component.literal(votes + "/" + needed)
                .withStyle(voted ? ChatFormatting.GREEN : ChatFormatting.YELLOW));

        // Sisa waktu racun, cuma untuk yang keracunan. Ditaruh di baris yang sama dengan
        // sisa waktu ronde supaya keduanya bisa dibaca sekali lihat: yang diracun sedang
        // berlomba dengan dua jam sekaligus, dan yang menentukan bukan yang lebih
        // panjang.
        if (data != null && data.poisonTicks > 0) {
            int poison = data.poisonTicks / 20;
            line = line.append(separator)
                    .append(Component.literal(String.format("%d:%02d", poison / 60, poison % 60))
                            .withStyle(ChatFormatting.DARK_RED));
        }
        return line;
    }

    /**
     * Tahan status bar sebentar supaya pesan sekilas di tempat yang sama sempat dibaca.
     *
     * <p>Dipakai peringatan pintu sel yang membanting. Tanpa ini, peringatan itu hidup
     * paling lama satu detik sebelum ditimpa kiriman berkala berikutnya.</p>
     */
    public void muteStatusBar(ServerPlayer player, int ticks) {
        statusBarMuted.put(player.getUUID(), player.server.getTickCount() + ticks);
    }
}
