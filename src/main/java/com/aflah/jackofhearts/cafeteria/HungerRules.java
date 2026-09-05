package com.aflah.jackofhearts.cafeteria;

import com.aflah.jackofhearts.config.JackConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.food.FoodData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lapar yang dipercepat selama permainan berlangsung.
 *
 * <p>Dua bagian, dan keduanya perlu:</p>
 *
 * <ol>
 *   <li><b>Pengali.</b> Tiap tick, tambahan exhaustion yang baru saja didapat pemain
 *       dari kegiatannya sendiri (berlari, melompat, memukul, menyembuhkan diri)
 *       ditambahkan lagi sebanyak (pengali - 1) kali. Hasilnya persis "sekian kali
 *       lebih cepat lapar daripada vanilla" untuk semua sumber lapar sekaligus,
 *       tanpa perlu mendaftar sumbernya satu per satu.</li>
 *   <li><b>Tetesan diam.</b> Di vanilla, <i>berjalan biasa memakan exhaustion nol</i>.
 *       Artinya pengali seberapa pun tidak akan membuat pemain yang cuma berjalan
 *       menyusuri lorong dan mengobrol jadi lapar - dan kafetarianya tidak akan
 *       pernah didatangi siapa pun. Karena itu ada tetesan tetap per detik, yang
 *       ikut dikali juga.</li>
 * </ol>
 *
 * <p>Tambahannya dihitung dari <b>selisih</b> exhaustion antar tick, bukan dengan
 * mencegat tiap sumbernya. Sekali per tick {@link FoodData#tick} memotong 4.0
 * exhaustion begitu ambangnya lewat, jadi selisih yang negatif berarti pemotongan
 * itu barusan terjadi dan bukan berarti pemain jadi kurang lapar - itulah yang
 * dikoreksi di {@link #tick}.</p>
 */
public final class HungerRules {

    private HungerRules() {
    }

    /** Ambang pemotongan exhaustion di {@link FoodData#tick}. */
    private static final float DRAIN_STEP = 4.0F;

    private static final Map<UUID, Float> LAST = new HashMap<>();

    public static void tick(ServerPlayer player) {
        FoodData food = player.getFoodData();
        UUID uuid = player.getUUID();
        float current = food.getExhaustionLevel();

        if (player.isCreative() || player.isSpectator()) {
            LAST.put(uuid, current);
            return;
        }

        float last = LAST.getOrDefault(uuid, current);
        float gained = current - last;
        if (gained < 0.0F) {
            gained += DRAIN_STEP;
        }
        gained = Mth.clamp(gained, 0.0F, DRAIN_STEP);

        float multiplier = JackConfig.CONFIG.hungerMultiplier.get().floatValue();
        float idlePerTick = JackConfig.CONFIG.hungerIdleDrain.get().floatValue() / 20.0F;
        float extra = gained * (multiplier - 1.0F) + idlePerTick * multiplier;
        if (extra > 0.0F) {
            food.addExhaustion(extra);
        }
        LAST.put(uuid, food.getExhaustionLevel());
    }

    /** Kenyang penuh, saturasi vanilla, exhaustion nol. */
    public static void reset(ServerPlayer player) {
        FoodData food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(5.0F);
        food.setExhaustion(0.0F);
        LAST.put(player.getUUID(), 0.0F);
    }

    public static void clear() {
        LAST.clear();
    }
}
