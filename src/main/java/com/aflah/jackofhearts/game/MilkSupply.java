package com.aflah.jackofhearts.game;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.config.JackConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Susu penawar yang diselipkan ke dalam wadah-wadah pilihan di map.
 *
 * <h2>Wadahnya ditunjuk, bukan dicari</h2>
 *
 * <p>Yang dipakai peti, tungku, dispenser, hopper, dan tong vanilla — tapi tidak semua,
 * cuma yang ditunjuk lebih dulu dengan Penanda Wadah. Map yang luas hampir pasti punya
 * wadah di luar arena permainan (peti penyimpanan, tungku dekorasi, hopper mesin
 * redstone), dan susu yang mendarat di sana sama saja tidak pernah muncul.</p>
 *
 * <p>Daftarnya tersimpan ke dunia lewat {@link MilkSpots}, jadi cukup sekali disusun
 * untuk semua permainan berikutnya. Untuk memeriksanya ada {@code /jack checkblock true}
 * — wadah yang sudah terdaftar mengeluarkan percikan hijau, tanpa bloknya diubah sama
 * sekali.</p>
 */
public final class MilkSupply {

    private MilkSupply() {
    }

    /** Peluang satu susu mendarat di tong, bukan di wadah jenis lain. */
    private static final float BARREL_SHARE = 0.30F;

    /** Jarak antar percikan penanda saat mode pemeriksaan menyala, dalam tick. */
    private static final int MARK_INTERVAL = 20;

    /** Mode pemeriksaan: wadah terdaftar dipercik supaya kelihatan dari jauh. */
    private static boolean checking;

    /**
     * Blok ini sejenis wadah yang bisa jadi tempat susu.
     *
     * <p>Sengaja terpisah dari {@link #isEnabled}: yang ini menjawab "wadahnya masih ada
     * di situ", dan itu yang dipakai untuk membersihkan daftar dari titik yang bloknya
     * sudah dibongkar. Kalau keduanya digabung, mematikan satu jenis wadah lewat perintah
     * akan diam-diam <b>menghapus</b> semua titik jenis itu dari daftar — dan
     * menyalakannya lagi tidak mengembalikan apa pun.</p>
     */
    public static boolean isSupply(BlockState state) {
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof AbstractFurnaceBlock
                || state.getBlock() instanceof DispenserBlock
                || state.getBlock() instanceof HopperBlock
                || state.getBlock() instanceof BarrelBlock;
    }

    /** Jenis wadah ini sedang dinyalakan lewat {@code /jack milkspots <jenis> true|false}. */
    public static boolean isEnabled(BlockState state) {
        if (state.getBlock() instanceof BarrelBlock) {
            return JackConfig.CONFIG.milkBarrel.get();
        }
        if (state.getBlock() instanceof ChestBlock) {
            return JackConfig.CONFIG.milkChest.get();
        }
        if (state.getBlock() instanceof AbstractFurnaceBlock) {
            return JackConfig.CONFIG.milkFurnace.get();
        }
        if (state.getBlock() instanceof DispenserBlock) {
            return JackConfig.CONFIG.milkDispenser.get();
        }
        if (state.getBlock() instanceof HopperBlock) {
            return JackConfig.CONFIG.milkHopper.get();
        }
        return false;
    }

    public static boolean isBarrel(BlockState state) {
        return state.getBlock() instanceof BarrelBlock;
    }

    // ------------------------------------------------------------------ pemeriksaan visual

    public static boolean isChecking() {
        return checking;
    }

    public static void setChecking(boolean value) {
        checking = value;
    }

    /**
     * Percikan penanda di atas wadah terdaftar.
     *
     * <p>Sengaja partikel, bukan bloknya yang diganti jadi sesuatu yang mencolok:
     * mengganti blok berarti menyimpan blok aslinya dan mengembalikannya nanti, dan
     * satu kegagalan di jalur itu (server mati di tengah pemeriksaan) meninggalkan map
     * yang rusak. Percikan tidak pernah bisa merusak apa pun.</p>
     */
    public static void tickChecking(MinecraftServer server) {
        if (!checking || server.getTickCount() % MARK_INTERVAL != 0) {
            return;
        }
        for (MilkSpots.Spot spot : MilkSpots.get(server).all()) {
            ServerLevel level = server.getLevel(spot.dimension());
            if (level == null || !level.isLoaded(spot.pos())) {
                continue;
            }
            mark(level, spot.pos(), true);
        }
    }

    /** Satu percikan di atas satu wadah. */
    public static void mark(ServerLevel level, BlockPos pos, boolean registered) {
        level.sendParticles(registered ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE,
                pos.getX() + 0.5D, pos.getY() + 1.15D, pos.getZ() + 0.5D,
                6, 0.25D, 0.15D, 0.25D, 0.0D);
    }

    // ------------------------------------------------------------------ pembagian

    /**
     * Berapa susu yang muncul di ronde ini.
     *
     * <p>Jatah totalnya dibagi rata sepanjang batas ronde permainan. Pembagiannya lewat
     * selisih pembulatan, bukan pembagian bulat: 9 susu untuk 10 ronde menghasilkan satu
     * ronde tanpa susu yang jatuh di <b>tengah</b> permainan, bukan di ronde pertama.
     * Ronde pertama yang kosong terasa seperti fiturnya rusak; ronde kosong di tengah
     * terasa seperti nasib.</p>
     */
    public static int forRound(int round) {
        int max = JackConfig.CONFIG.milkMax.get();
        int rounds = Math.max(1, JackConfig.CONFIG.jackWinRound.get());
        if (max <= 0 || round <= 0) {
            return 0;
        }
        long before = Math.round((double) max * (round - 1) / rounds);
        long now = Math.round((double) max * Math.min(round, rounds) / rounds);
        return (int) Math.max(0, now - before);
    }

    /**
     * Selipkan sejumlah susu ke wadah acak yang sedang termuat.
     *
     * <p>Tong dapat jatah tersendiri karena tempatnya beda: tong berdiri di ruang
     * tebak, yang cuma bisa dimasuki di fase menebak. Kalau semua wadah diundi rata,
     * sebagian besar susu akan mendarat di ruangan yang justru paling jarang bisa
     * dibuka.</p>
     *
     * @return berapa susu yang benar-benar terpasang.
     */
    public static int spawn(MinecraftServer server, int count) {
        if (count <= 0) {
            return 0;
        }
        List<Slot> barrels = new ArrayList<>();
        List<Slot> others = new ArrayList<>();
        collect(server, barrels, others);
        if (barrels.isEmpty() && others.isEmpty()) {
            JackOfHeartsMod.LOGGER.warn(
                    "[JackOfHearts] Tidak ada wadah terdaftar yang termuat; susu tidak bisa ditaruh. "
                            + "Tunjuk peti/tungku/dispenser/hopper/tong dengan Penanda Wadah dulu.");
            return 0;
        }

        Random random = new Random(server.overworld().getRandom().nextLong());
        Collections.shuffle(barrels, random);
        Collections.shuffle(others, random);

        // Isi kolamnya dicatat tiap kali membagi. Inilah yang menjelaskan kenapa susu
        // terasa terlalu sering mendarat di tong: undiannya memang 30/70, tapi kalau
        // wadah jenis lain semuanya penuh (tungku hias yang sudah ada isinya, peti yang
        // penuh), jatah 70% itu jatuh balik ke tong karena tidak ada tempat lain.
        JackOfHeartsMod.LOGGER.info("[JackOfHearts] Membagi {} susu; wadah siap diisi: {} tong, {} lainnya.",
                count, barrels.size(), others.size());

        int placed = 0;
        for (int i = 0; i < count; i++) {
            // Undiannya menentukan jenis dulu, baru wadahnya. Kalau jenis yang terpilih
            // sudah habis, jatahnya jatuh ke jenis satunya daripada hilang begitu saja.
            boolean wantBarrel = random.nextFloat() < BARREL_SHARE;
            List<Slot> first = wantBarrel ? barrels : others;
            List<Slot> second = wantBarrel ? others : barrels;
            Slot slot = take(first);
            if (slot == null) {
                slot = take(second);
            }
            if (slot == null) {
                break;
            }
            slot.container().setItem(slot.index(), new ItemStack(Items.MILK_BUCKET));
            slot.container().setChanged();
            placed++;
            JackOfHeartsMod.LOGGER.info("[JackOfHearts] Susu ditaruh di {} ({}).",
                    slot.pos().toShortString(), wantBarrel ? "undian tong" : "undian bukan tong");
        }
        return placed;
    }

    /** Satu tempat kosong yang bisa diisi susu. */
    private record Slot(BlockPos pos, Container container, int index) {
    }

    @Nullable
    private static Slot take(List<Slot> from) {
        return from.isEmpty() ? null : from.remove(from.size() - 1);
    }

    /**
     * Satu tempat kosong per wadah, bukan semua slot kosongnya.
     *
     * <p>Kalau semua slot ikut diundi, peti besar praktis memenangkan seluruh undian —
     * satu peti punya 27 tiket sementara hopper cuma 5, dan susunya akan menumpuk di
     * tempat yang sama.</p>
     */
    private static void collect(MinecraftServer server, List<Slot> barrels, List<Slot> others) {
        MilkSpots spots = MilkSpots.get(server);
        for (MilkSpots.Spot spot : spots.all()) {
            ServerLevel level = server.getLevel(spot.dimension());
            if (level == null || !level.isLoaded(spot.pos())) {
                continue;
            }
            BlockState state = level.getBlockState(spot.pos());
            if (!isSupply(state)) {
                // Wadahnya sudah tidak ada di situ; daftarnya ikut dibersihkan supaya
                // tidak menumpuk jadi titik mati yang diperiksa tiap ronde.
                spots.remove(spot.dimension(), spot.pos());
                continue;
            }
            if (!isEnabled(state)) {
                // Jenisnya sedang dimatikan. Dilewati, bukan dihapus - menyalakannya lagi
                // harus mengembalikan titik ini apa adanya.
                continue;
            }
            Container container = containerAt(level, spot.pos());
            if (container == null) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).isEmpty()) {
                    (isBarrel(state) ? barrels : others).add(new Slot(spot.pos(), container, i));
                    break;
                }
            }
        }
    }

    @Nullable
    private static Container containerAt(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? container : null;
    }

    // ------------------------------------------------------------------ pemeriksaan

    /**
     * Isi kolam undian sekarang: berapa wadah terdaftar, dan berapa yang benar-benar
     * masih punya tempat kosong.
     *
     * <p>Selisih di antara keduanya yang biasanya jadi jawaban kalau susunya terasa
     * menumpuk di satu jenis wadah: yang penuh tidak ikut diundi sama sekali.</p>
     *
     * @return {tong terdaftar, lainnya terdaftar, tong siap, lainnya siap,
     *         tidak terjangkau}
     */
    public static int[] poolReport(MinecraftServer server) {
        int barrelsTotal = 0;
        int othersTotal = 0;
        int unreachable = 0;
        for (MilkSpots.Spot spot : MilkSpots.get(server).all()) {
            ServerLevel level = server.getLevel(spot.dimension());
            if (level == null || !level.isLoaded(spot.pos())) {
                // Dimensinya tidak ada, atau chunk-nya sedang tidak dimuat. Dua-duanya
                // berarti titik itu tidak bisa diisi apa pun sekarang - dan kalau tidak
                // dihitung terpisah, hasilnya cuma angka nol tanpa penjelasan.
                unreachable++;
                continue;
            }
            BlockState state = level.getBlockState(spot.pos());
            if (!isSupply(state)) {
                continue;
            }
            if (isBarrel(state)) {
                barrelsTotal++;
            } else {
                othersTotal++;
            }
        }
        List<Slot> barrels = new ArrayList<>();
        List<Slot> others = new ArrayList<>();
        collect(server, barrels, others);
        return new int[]{barrelsTotal, othersTotal, barrels.size(), others.size(), unreachable};
    }

    /** Semua wadah yang sekarang berisi susu, untuk {@code /jack milkreveal}. */
    public static List<BlockPos> findMilk(MinecraftServer server) {
        List<BlockPos> found = new ArrayList<>();
        for (MilkSpots.Spot spot : MilkSpots.get(server).sorted()) {
            ServerLevel level = server.getLevel(spot.dimension());
            if (level == null || !level.isLoaded(spot.pos())) {
                continue;
            }
            if (holdsMilk(containerAt(level, spot.pos()))) {
                found.add(spot.pos());
            }
        }
        return found;
    }

    private static boolean holdsMilk(@Nullable Container container) {
        if (container == null) {
            return false;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(Items.MILK_BUCKET)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Buang semua susu yang tersisa di wadah terdaftar.
     *
     * <p>Dipanggil saat permainan mulai dan berhenti. Susu sisa permainan kemarin adalah
     * penawar gratis yang tidak pernah dijatah siapa pun.</p>
     */
    public static int clearAll(MinecraftServer server) {
        int removed = 0;
        for (MilkSpots.Spot spot : MilkSpots.get(server).all()) {
            ServerLevel level = server.getLevel(spot.dimension());
            if (level == null || !level.isLoaded(spot.pos())) {
                continue;
            }
            Container container = containerAt(level, spot.pos());
            if (container == null) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).is(Items.MILK_BUCKET)) {
                    container.setItem(i, ItemStack.EMPTY);
                    container.setChanged();
                    removed++;
                }
            }
        }
        return removed;
    }
}
