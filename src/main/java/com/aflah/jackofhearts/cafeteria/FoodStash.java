package com.aflah.jackofhearts.cafeteria;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Menjaga makanan kafetaria tidak bisa dititipkan ke dalam wadah apa pun.
 *
 * <p>Batas bawa satu porsi adalah seluruh isi mekanik kafetaria: ambil satu, habiskan,
 * baru boleh ambil lagi. Tanpa penjagaan ini batas itu tidak ada artinya — ambil satu,
 * taruh di peti, ambil lagi, dan dalam semenit satu orang sudah memindahkan seluruh
 * meja ke dalam kotaknya sendiri.</p>
 *
 * <h2>Yang ditolak cuma yang baru masuk</h2>
 *
 * <p>Pengecekannya bukan "buang semua makanan dari peti ini", melainkan "kembalikan
 * yang <b>bertambah</b> sejak wadahnya dibuka". Bedanya penting: peti yang isinya
 * sudah ada roti sejak sebelum permainan dimulai adalah isi dunia, bukan hasil
 * curian meja — dan peti dunia yang diacak-acak sendiri oleh mod jauh lebih
 * membingungkan daripada satu penolakan yang jelas.</p>
 *
 * <p>Patokannya ikut turun kalau isinya berkurang, jadi mengambil makanan yang memang
 * sudah ada di peti tetap boleh, dan mengembalikannya ke peti itu juga tidak dianggap
 * menitip.</p>
 *
 * <h2>Kenapa diperiksa tiap tick, bukan saat kliknya</h2>
 *
 * <p>Karena tidak ada kejadian yang bisa dicegat saat barang pindah slot: Forge tidak
 * menyediakan event klik slot, dan setiap wadah punya jalur pemindahannya sendiri
 * (klik, geser, shift-klik, tombol angka). Memeriksa hasilnya tiap tick menangkap
 * semuanya sekaligus, dan biayanya cuma menghitung slot wadah yang sedang terbuka.</p>
 */
public final class FoodStash {

    private FoodStash() {
    }

    /** Berapa porsi yang memang sudah ada di wadah yang sedang dibuka pemain ini. */
    private record Baseline(int containerId, int servings) {
    }

    private static final Map<UUID, Baseline> BASELINES = new HashMap<>();

    /**
     * @return true kalau ada makanan yang barusan dikembalikan ke tas pemainnya.
     */
    public static boolean tick(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        Baseline baseline = BASELINES.get(player.getUUID());
        int current = countOutside(menu);

        // Wadah lain (atau baru dibuka): isinya sekarang jadi patokan apa adanya.
        if (baseline == null || baseline.containerId() != menu.containerId) {
            BASELINES.put(player.getUUID(), new Baseline(menu.containerId, current));
            return false;
        }
        if (current <= baseline.servings()) {
            // Berkurang berarti dia mengambil, bukan menitip. Patokannya ikut turun
            // supaya mengembalikannya nanti tidak terbaca sebagai titipan baru.
            BASELINES.put(player.getUUID(), new Baseline(menu.containerId, current));
            return false;
        }

        int excess = current - baseline.servings();
        returnToPlayer(player, menu, excess);
        BASELINES.put(player.getUUID(), new Baseline(menu.containerId, countOutside(menu)));
        return true;
    }

    public static void forget(ServerPlayer player) {
        BASELINES.remove(player.getUUID());
    }

    public static void clear() {
        BASELINES.clear();
    }

    /**
     * Menarik kembali makanan yang baru dititipkan, dari slot paling belakang.
     *
     * <p>Dari belakang karena barang yang baru dimasukkan hampir selalu mendarat di
     * slot kosong terakhir; menariknya dari situ membuat isi peti yang lama tetap di
     * tempatnya.</p>
     */
    private static void returnToPlayer(ServerPlayer player, AbstractContainerMenu menu, int excess) {
        for (int i = menu.slots.size() - 1; i >= 0 && excess > 0; i--) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!CafeteriaFood.isCafeteriaFood(stack)) {
                continue;
            }
            int taking = Math.min(excess, stack.getCount());
            ItemStack moved = stack.split(taking);
            excess -= taking;
            slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);

            // Tidak muat berarti tasnya penuh; sisanya dikembalikan ke slot asalnya
            // daripada dijatuhkan ke lantai - lantai justru tempat yang sedang dijaga.
            if (!player.getInventory().add(moved) && !moved.isEmpty()) {
                slot.set(moved);
            }
        }
        menu.broadcastChanges();
        player.containerMenu.sendAllDataToRemote();
    }

    /** Porsi makanan kafetaria di slot yang bukan milik tas pemainnya sendiri. */
    private static int countOutside(AbstractContainerMenu menu) {
        int count = 0;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (CafeteriaFood.isCafeteriaFood(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
