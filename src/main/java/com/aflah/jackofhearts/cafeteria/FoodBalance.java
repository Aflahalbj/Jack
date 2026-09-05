package com.aflah.jackofhearts.cafeteria;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.config.JackConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Menyamakan nilai gizi ke-12 makanan kafetaria.
 *
 * <p>Alasannya bukan kerapian angka, tapi keadilan: di vanilla satu bit mengisi 1
 * lapar sedangkan satu daging sapi matang mengisi 8. Kalau dibiarkan, yang
 * menentukan siapa bertahan lama bukan permainannya, melainkan piring mana yang
 * kebetulan paling dekat dengan tempat duduknya.</p>
 *
 * <p>Yang diubah adalah {@link Item#foodProperties} item vanillanya langsung.
 * Forge tidak menyediakan jalan resmi untuk itu, jadi fieldnya dibuka lewat
 * access transformer. Konsekuensinya disengaja: roti dari mana pun asalnya
 * bernilai sama, jadi tidak ada celah "bawa bekal roti sendiri lebih untung".</p>
 *
 * <p>Nilai aslinya disimpan supaya setelan bisa dimatikan lewat config tanpa
 * perlu me-restart server.</p>
 */
public final class FoodBalance {

    private FoodBalance() {
    }

    private static final Map<Item, FoodProperties> ORIGINAL = new HashMap<>();

    /**
     * Item yang harus disamakan.
     *
     * <p>Dua sumber, dan keduanya perlu: konstanta {@code Items.X}, dan isi registry di
     * id yang sama. Decorative Food menukar isi registry dengan item miliknya sendiri
     * supaya makanan bisa ditaruh sebagai blok, jadi dua sumber itu belum tentu benda
     * yang sama - dan yang dipegang pemain belum tentu yang dikenal lewat konstanta.</p>
     */
    private static Set<Item> targets() {
        Set<Item> items = new LinkedHashSet<>(CafeteriaFood.UNIFORM);
        for (String id : CafeteriaFood.UNIFORM_IDS) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item != null && item.isEdible()) {
                items.add(item);
            }
        }
        return items;
    }

    public static void apply() {
        Set<Item> targets = targets();
        for (Item item : targets) {
            ORIGINAL.putIfAbsent(item, item.foodProperties);
        }

        if (!JackConfig.CONFIG.uniformFood.get()) {
            restore();
            return;
        }

        int nutrition = JackConfig.CONFIG.foodNutrition.get();
        float saturation = JackConfig.CONFIG.foodSaturation.get().floatValue();
        FoodProperties uniform = new FoodProperties.Builder()
                .nutrition(nutrition)
                .saturationMod(saturation)
                .build();

        for (Item item : targets) {
            item.foodProperties = uniform;
        }
        JackOfHeartsMod.LOGGER.info("[JackOfHearts] Nilai {} item makanan kafetaria disamakan: {} lapar, saturasi x{}",
                targets.size(), nutrition, saturation);
    }

    private static void restore() {
        for (Map.Entry<Item, FoodProperties> entry : ORIGINAL.entrySet()) {
            entry.getKey().foodProperties = entry.getValue();
        }
    }
}
