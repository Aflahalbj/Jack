package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.cafeteria.HungerRules;
import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.entity.MannequinEntity;
import com.aflah.jackofhearts.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Peserta yang keluar dari server di tengah permainan.
 *
 * <p>Kelas ini memegang dua hal untuk tiap peserta yang sedang offline: badan
 * penggantinya di dunia ({@link MannequinEntity}) dan keadaan tubuh pemiliknya —
 * lapar dan nyawa — yang tetap dijalankan seolah dia masih ada.</p>
 *
 * <p>Keduanya sengaja dipisah. Badan pengganti bisa hilang kapan saja tanpa
 * pemberitahuan: begitu tidak ada seorang pun di dekat situ, chunk-nya dilepas dan
 * entitasnya ikut dibuang. Kalau lapar dan nyawa ikut disimpan di entitasnya, keduanya
 * akan ter-reset tiap kali ruangan itu ditinggalkan orang — pemain yang harusnya
 * kelaparan justru pulih sendiri karena kebetulan tidak ada yang menungguinya. Jadi
 * yang dianggap sumber kebenaran adalah catatan di sini; entitasnya cuma tampilan yang
 * dibuat ulang begitu chunk-nya dimuat lagi.</p>
 *
 * <p>Hitungan laparnya menyalin {@link FoodData#tick} apa adanya, termasuk aturan
 * kesulitan dan pemulihan nyawa, lalu ditambah percepatan yang sama dengan yang
 * dipakai peserta online di {@link HungerRules}. Tidak dipakai {@code FoodData} milik
 * pemainnya langsung karena method itu menuntut objek {@code Player} yang justru
 * sedang tidak ada — itulah keadaan yang sedang ditangani di sini.</p>
 */
public final class OfflinePlayers {

    private static final OfflinePlayers INSTANCE = new OfflinePlayers();

    public static OfflinePlayers get() {
        return INSTANCE;
    }

    /** Ambang pemotongan exhaustion, sama dengan vanilla. */
    private static final float DRAIN_STEP = 4.0F;
    /** Jeda antar kerusakan kelaparan dan antar pemulihan nyawa lambat, dalam tick. */
    private static final int STARVE_INTERVAL = 80;
    /** Jeda antar pemulihan nyawa cepat (kenyang penuh dan masih bersaturasi). */
    private static final int FAST_HEAL_INTERVAL = 10;

    private final Map<UUID, StandIn> standIns = new LinkedHashMap<>();

    private OfflinePlayers() {
    }

    /** Catatan satu peserta yang sedang offline. */
    private static final class StandIn {
        final UUID uuid;
        final String name;
        final ResourceKey<Level> dimension;
        final Vec3 pos;
        final float yRot;
        /** Wadah nilai lapar saja; yang menjalankannya {@link #tickHunger}. */
        final FoodData food = new FoodData();
        float health;
        float maxHealth;
        /** Penghitung vanilla untuk kerusakan kelaparan dan pemulihan nyawa. */
        int foodTickTimer;
        /**
         * Isi slot perlengkapan badan penggantinya.
         *
         * <p>Disimpan di sini, bukan cuma dipasang ke entitasnya, karena entitasnya
         * bisa dibuang dan dibuat ulang berkali-kali selama pemiliknya offline. Kalau
         * seragamnya cuma menempel di entitas, badan yang dibuat ulang akan berdiri
         * telanjang — dan simbol di punggungnya, satu-satunya alasan orang lain
         * memperhatikan badan itu, ikut hilang.</p>
         */
        final EnumMap<EquipmentSlot, ItemStack> gear = new EnumMap<>(EquipmentSlot.class);
        @Nullable
        MannequinEntity entity;

        StandIn(ServerPlayer player) {
            this.uuid = player.getUUID();
            this.name = player.getGameProfile().getName();
            this.dimension = player.level().dimension();
            this.pos = player.position();
            this.yRot = player.getYRot();
            this.food.setFoodLevel(player.getFoodData().getFoodLevel());
            this.food.setSaturation(player.getFoodData().getSaturationLevel());
            this.food.setExhaustion(player.getFoodData().getExhaustionLevel());
            this.health = player.getHealth();
            this.maxHealth = player.getMaxHealth();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                this.gear.put(slot, player.getItemBySlot(slot).copy());
            }
        }
    }

    // ------------------------------------------------------------------ masuk & keluar

    /**
     * Pemain peserta baru saja keluar: badan penggantinya berdiri di tempatnya.
     *
     * <p>Dipanggil dari kejadian logout, jadi posisinya masih posisi terakhir yang
     * sah dan isi perutnya masih nilai yang barusan tersimpan ke disk.</p>
     */
    public void capture(ServerPlayer player) {
        StandIn standIn = new StandIn(player);
        standIns.put(standIn.uuid, standIn);
        spawnEntity(player.getServer(), standIn);
    }

    /**
     * Pemainnya kembali: badan penggantinya dibubarkan dan tubuhnya dikembalikan.
     *
     * <p>Lapar dan nyawa hasil simulasi ditimpakan ke pemainnya — kalau tidak, seluruh
     * hitungan tadi tidak ada artinya: keluar sebentar lalu masuk lagi akan jadi cara
     * paling murah untuk berhenti lapar.</p>
     *
     * @return true kalau pemain ini memang sedang diwakili badan pengganti.
     */
    public boolean restore(ServerPlayer player) {
        StandIn standIn = standIns.remove(player.getUUID());
        if (standIn == null) {
            return false;
        }
        discard(standIn);

        FoodData food = player.getFoodData();
        food.setFoodLevel(standIn.food.getFoodLevel());
        food.setSaturation(standIn.food.getSaturationLevel());
        food.setExhaustion(standIn.food.getExhaustionLevel());
        // Percepatan lapar menghitung selisih exhaustion antar tick. Tanpa dicatat di
        // sini, selisih pertama sesudah masuk dihitung dari nilai sebelum dia keluar
        // dan bisa melonjak jauh.
        HungerRules.adopt(player);

        // Nyawa nol berarti dia sudah gugur saat offline, dan yang gugur langsung jadi
        // penonton. Penonton dengan nyawa nol akan langsung mati begitu dikembalikan ke
        // mode bertahan hidup, jadi angkanya dipulihkan penuh.
        player.setHealth(standIn.health > 0.0F
                ? Math.min(standIn.health, player.getMaxHealth())
                : player.getMaxHealth());
        return true;
    }

    public boolean has(UUID uuid) {
        return standIns.containsKey(uuid);
    }

    /** UUID semua peserta yang sedang diwakili badan pengganti. */
    public List<UUID> all() {
        return List.copyOf(standIns.keySet());
    }

    /** Badan pengganti yang sedang ada di dunia, atau null kalau chunk-nya belum dimuat. */
    @Nullable
    public MannequinEntity entity(UUID uuid) {
        StandIn standIn = standIns.get(uuid);
        return standIn == null || standIn.entity == null || standIn.entity.isRemoved()
                ? null
                : standIn.entity;
    }

    /** Isi satu slot perlengkapan badan penggantinya, seperti yang tercatat sekarang. */
    public ItemStack gear(UUID uuid, EquipmentSlot slot) {
        StandIn standIn = standIns.get(uuid);
        return standIn == null ? ItemStack.EMPTY : standIn.gear.getOrDefault(slot, ItemStack.EMPTY);
    }

    /** Pasang satu slot perlengkapan: dicatat, dan ikut dipasang ke badan yang sedang ada. */
    public void setGear(UUID uuid, EquipmentSlot slot, ItemStack stack) {
        StandIn standIn = standIns.get(uuid);
        if (standIn == null) {
            return;
        }
        standIn.gear.put(slot, stack.copy());
        if (standIn.entity != null && !standIn.entity.isRemoved()) {
            standIn.entity.setItemSlot(slot, stack.copy());
        }
    }

    /** Bubarkan badan pengganti satu orang dan lupakan catatannya. */
    public void remove(UUID uuid) {
        StandIn standIn = standIns.remove(uuid);
        if (standIn != null) {
            discard(standIn);
        }
    }

    /**
     * Bubarkan semuanya, lalu sapu sisa yang tidak ada di catatan mana pun.
     *
     * <p>Penyapuannya perlu, bukan sekadar hati-hati: permainan yang berakhir tidak
     * wajar (server mati di tengah jalan, lalu dinyalakan lagi) meninggalkan entitas
     * yang catatannya sudah hilang bersama memori.</p>
     */
    public void clear(MinecraftServer server) {
        for (StandIn standIn : standIns.values()) {
            discard(standIn);
        }
        standIns.clear();
        for (ServerLevel level : server.getAllLevels()) {
            for (MannequinEntity stray : level.getEntities(ModEntities.MANNEQUIN.get(), e -> true)) {
                stray.discard();
            }
        }
    }

    // ------------------------------------------------------------------ tick

    /**
     * @param hunger false selama fase yang membekukan pemain (animasi pembagian
     *               peran). Peserta online juga tidak dibuat lapar di fase itu, dan
     *               yang offline tidak boleh lebih rugi dari yang hadir.
     * @return peserta yang baru saja kehabisan nyawa karena kelaparan. Dikembalikan,
     *         bukan diurus sendiri, karena yang berhak menyatakan seseorang gugur cuma
     *         {@link GameManager} — di situlah papan, kondisi menang, dan mayatnya
     *         diurus sekaligus.
     */
    public List<UUID> tick(MinecraftServer server, boolean hunger) {
        if (standIns.isEmpty()) {
            return List.of();
        }
        List<UUID> starved = null;
        for (StandIn standIn : standIns.values()) {
            ServerLevel level = server.getLevel(standIn.dimension);
            if (level == null) {
                continue;
            }
            ensureEntity(level, standIn);
            if (hunger && tickHunger(level, standIn)) {
                if (starved == null) {
                    starved = new ArrayList<>(1);
                }
                starved.add(standIn.uuid);
            }
        }
        return starved == null ? List.of() : Collections.unmodifiableList(starved);
    }

    /**
     * Satu tick lapar dan nyawa, sama persis dengan yang dialami pemain yang online.
     *
     * @return true kalau nyawanya habis di tick ini.
     */
    private boolean tickHunger(ServerLevel level, StandIn standIn) {
        FoodData food = standIn.food;

        // Peserta offline berdiri diam, jadi dia tidak menghasilkan exhaustion dari
        // kegiatannya sendiri. Yang tersisa cuma tetesan diam, dikali sama seperti di
        // HungerRules - itulah yang membuat dia tetap lapar walau tidak berbuat apa-apa.
        float multiplier = JackConfig.CONFIG.hungerMultiplier.get().floatValue();
        float idlePerTick = JackConfig.CONFIG.hungerIdleDrain.get().floatValue() / 20.0F;
        food.addExhaustion(idlePerTick * multiplier);

        Difficulty difficulty = level.getDifficulty();
        if (food.getExhaustionLevel() > DRAIN_STEP) {
            food.setExhaustion(food.getExhaustionLevel() - DRAIN_STEP);
            if (food.getSaturationLevel() > 0.0F) {
                food.setSaturation(Math.max(food.getSaturationLevel() - 1.0F, 0.0F));
            } else if (difficulty != Difficulty.PEACEFUL) {
                food.setFoodLevel(Math.max(food.getFoodLevel() - 1, 0));
            }
        }

        boolean naturalRegen = level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        boolean hurt = standIn.health < standIn.maxHealth;
        if (naturalRegen && food.getSaturationLevel() > 0.0F && hurt && food.getFoodLevel() >= 20) {
            if (++standIn.foodTickTimer >= FAST_HEAL_INTERVAL) {
                float amount = Math.min(food.getSaturationLevel(), 6.0F);
                standIn.health = Math.min(standIn.health + amount / 6.0F, standIn.maxHealth);
                food.addExhaustion(amount);
                standIn.foodTickTimer = 0;
            }
        } else if (naturalRegen && food.getFoodLevel() >= 18 && hurt) {
            if (++standIn.foodTickTimer >= STARVE_INTERVAL) {
                standIn.health = Math.min(standIn.health + 1.0F, standIn.maxHealth);
                food.addExhaustion(6.0F);
                standIn.foodTickTimer = 0;
            }
        } else if (food.getFoodLevel() <= 0) {
            if (++standIn.foodTickTimer >= STARVE_INTERVAL) {
                standIn.foodTickTimer = 0;
                // Aturan vanilla: mudah berhenti di 10 nyawa, normal di 1, dan cuma
                // kesulitan susah yang benar-benar mematikan.
                if (standIn.health > 10.0F || difficulty == Difficulty.HARD
                        || (standIn.health > 1.0F && difficulty == Difficulty.NORMAL)) {
                    standIn.health -= 1.0F;
                    return standIn.health <= 0.0F;
                }
            }
        } else {
            standIn.foodTickTimer = 0;
        }
        return false;
    }

    // ------------------------------------------------------------------ badan pengganti

    /**
     * Pastikan badan penggantinya ada di dunia.
     *
     * <p>Dibuat ulang, bukan cuma dibuat sekali: chunk yang ditinggalkan orang akan
     * dilepas dan entitasnya ikut dibuang. Selama chunk-nya belum dimuat tidak ada yang
     * dikerjakan — tidak ada yang bisa melihat tempat itu, jadi tidak ada yang perlu
     * digambar, dan memaksa chunk-nya tetap dimuat cuma demi patung akan menahan
     * seluruh ruangan hidup sepanjang permainan.</p>
     */
    private void ensureEntity(ServerLevel level, StandIn standIn) {
        if (standIn.entity != null && !standIn.entity.isRemoved()) {
            return;
        }
        standIn.entity = null;
        if (!level.isLoaded(BlockPos.containing(standIn.pos))) {
            return;
        }
        spawn(level, standIn);
    }

    private void spawnEntity(@Nullable MinecraftServer server, StandIn standIn) {
        ServerLevel level = server == null ? null : server.getLevel(standIn.dimension);
        if (level != null) {
            spawn(level, standIn);
        }
    }

    private void spawn(ServerLevel level, StandIn standIn) {
        MannequinEntity mannequin = ModEntities.MANNEQUIN.get().create(level);
        if (mannequin == null) {
            return;
        }
        mannequin.moveTo(standIn.pos.x, standIn.pos.y, standIn.pos.z, standIn.yRot, 0.0F);
        mannequin.setFacing(standIn.yRot);
        mannequin.setOwner(standIn.uuid);
        mannequin.setCustomName(nameTag(standIn.name));
        mannequin.setCustomNameVisible(true);
        for (Map.Entry<EquipmentSlot, ItemStack> entry : standIn.gear.entrySet()) {
            mannequin.setItemSlot(entry.getKey(), entry.getValue().copy());
        }
        level.addFreshEntity(mannequin);
        standIn.entity = mannequin;
    }

    /**
     * Nama di atas kepala: nama pemainnya, ditandai BOT.
     *
     * <p>Tandanya perlu. Tanpa itu badan pengganti terbaca sebagai pemain sungguhan
     * yang sedang diam — dan orang akan menunggunya bergerak, menyapanya, atau
     * menghindarinya karena mengira dia Jack yang sedang mengintai.</p>
     */
    private static Component nameTag(String name) {
        return Component.literal(name + " ")
                .append(Component.literal("[BOT]").withStyle(ChatFormatting.GRAY));
    }

    private static void discard(StandIn standIn) {
        if (standIn.entity != null) {
            standIn.entity.discard();
            standIn.entity = null;
        }
    }
}
