package com.aflah.jackofhearts.cafeteria;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Cara mengenali piring makanan 3D dan makanan apa yang dibagikannya.
 *
 * <p>Sasarannya mod <a href="https://www.curseforge.com/minecraft/mc-mods/decorative-food">TW's
 * Decorative Food</a>, dan cara kerjanya sengaja dibaca dari mod itu:</p>
 *
 * <ul>
 *   <li>Blok makanannya <b>didaftarkan dengan id vanilla</b> ({@code minecraft:bread},
 *       {@code minecraft:cooked_beef}, ...), bukan dengan id mod sendiri. Registry blok
 *       dan registry item memang terpisah, jadi id yang sama tidak bentrok - itu yang
 *       bikin mod itu "hampir tidak pernah bentrok dengan mod lain". Konsekuensinya di
 *       sini: <b>tidak ada nama mod yang bisa dipakai untuk menyaring</b>, dan penyaring
 *       yang membuang namespace {@code minecraft} justru akan membuang semua piringnya.
 *       Yang menggantikan penyaring itu adalah kotak kafetaria di config - di luar kotak
 *       itu tidak ada blok yang disentuh sama sekali.</li>
 *   <li>Satu piring bisa <b>bertumpuk</b>, jumlahnya disimpan di properti blockstate
 *       {@code number_of_food} (apel maksimal 5, kue 7, daging sapi matang 3, ayam matang
 *       1). Jadi satu piring belum tentu satu porsi.</li>
 * </ul>
 *
 * <p>Pemetaan makanannya jadi sesederhana mungkin: blok {@code minecraft:cooked_beef}
 * membagikan item {@code minecraft:cooked_beef}. Yang dianggap piring cuma blok yang
 * id-nya benar-benar punya item yang bisa dimakan - jadi lantai, meja, dan tanaman di
 * dalam kotak kafetaria tetap tidak terhitung.</p>
 */
public final class CafeteriaFood {

    private CafeteriaFood() {
    }

    /** Nama properti jumlah tumpukan di blok Decorative Food. */
    public static final String COUNT_PROPERTY = "number_of_food";

    /**
     * Id ke-12 makanan kafetaria.
     *
     * <p>Disimpan sebagai id, bukan cuma sebagai {@code Items.X}, karena Decorative Food
     * <b>menggantikan item vanillanya</b>: item aslinya didaftarkan ulang jadi
     * {@code <id>_de}, lalu id lamanya diisi item milik mod itu supaya makanan bisa
     * ditaruh sebagai blok. Jadi "item roti" bisa berarti dua benda yang berbeda, dan
     * {@link FoodBalance} harus menyamakan nilai keduanya - kalau salah satu terlewat,
     * yang dipegang pemain bisa jadi justru yang nilainya belum disamakan.</p>
     */
    public static final List<String> UNIFORM_IDS = List.of(
            "bread",
            "sweet_berries",
            "baked_potato",
            "apple",
            "melon_slice",
            "carrot",
            "beetroot",
            "cookie",
            "cooked_chicken",
            "cooked_salmon",
            "cooked_cod",
            "cooked_beef");

    /** Ke-12 makanan kafetaria yang nilai gizinya disamakan {@link FoodBalance}. */
    public static final List<Item> UNIFORM = List.of(
            Items.BREAD,
            Items.SWEET_BERRIES,
            Items.BAKED_POTATO,
            Items.APPLE,
            Items.MELON_SLICE,
            Items.CARROT,
            Items.BEETROOT,
            Items.COOKIE,
            Items.COOKED_CHICKEN,
            Items.COOKED_SALMON,
            Items.COOKED_COD,
            Items.COOKED_BEEF);

    /**
     * Makanan yang dibagikan blok ini, atau null kalau ini bukan piring makanan.
     *
     * <p>Blok makanan Decorative Food memakai id yang sama persis dengan item
     * makanannya, jadi pemetaannya tidak perlu daftar kata kunci: cukup cari item
     * dengan id yang sama, lalu pastikan item itu memang bisa dimakan.</p>
     */
    @Nullable
    public static Item foodOf(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item != null && item.isEdible() ? item : null;
    }

    public static boolean isFoodBlock(BlockState state) {
        return foodOf(state) != null;
    }

    /** Properti jumlah tumpukan piring ini, atau null kalau bloknya tidak bertumpuk. */
    @Nullable
    public static IntegerProperty countProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer && integer.getName().equals(COUNT_PROPERTY)) {
                return integer;
            }
        }
        return null;
    }

    /** Berapa porsi yang tergambar di satu piring. Blok tanpa properti tumpukan dihitung satu. */
    public static int servings(BlockState state) {
        IntegerProperty property = countProperty(state);
        return property == null ? 1 : state.getValue(property);
    }

    /**
     * Berapa porsi makanan kafetaria yang sedang dibawa pemain.
     *
     * <p>Seluruh isi tas dihitung, bukan cuma tangan: batas bawaannya soal "sudah punya
     * makanan atau belum", dan makanan yang diselipkan ke baris paling bawah tas tetap
     * makanan yang bisa dimakan kapan saja.</p>
     *
     * <p>Akhiran {@code _de} ikut dianggap makanan yang sama. Decorative Food
     * mendaftarkan ulang item vanillanya dengan akhiran itu, jadi tanpa penyesuaian ini
     * "roti vanilla" dan "roti yang bisa ditaruh" akan terhitung sebagai dua barang
     * berbeda dan batasnya bisa dilewati.</p>
     */
    public static int carriedBy(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isCafeteriaFood(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean isCafeteriaFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        if (path.endsWith("_de")) {
            path = path.substring(0, path.length() - 3);
        }
        return UNIFORM_IDS.contains(path);
    }

    /**
     * Keadaan piring setelah satu porsi diambil, atau null kalau piringnya habis dan
     * harus dibuang dari meja.
     */
    @Nullable
    public static BlockState afterTakingOne(BlockState state) {
        IntegerProperty property = countProperty(state);
        if (property == null) {
            return null;
        }
        int left = state.getValue(property) - 1;
        return left < 1 ? null : state.setValue(property, left);
    }
}
