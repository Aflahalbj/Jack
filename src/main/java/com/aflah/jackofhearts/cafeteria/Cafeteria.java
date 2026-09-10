package com.aflah.jackofhearts.cafeteria;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.cafeteria.CafeteriaLayout.Spot;
import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.network.CafeteriaPacket;
import com.aflah.jackofhearts.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stok makanan kafetaria.
 *
 * <p>Idenya: di meja cuma ada 12 piring, tapi stok satu game jauh lebih banyak dari
 * itu. Selisihnya disimpan sebagai angka, bukan sebagai blok. Selama stok masih lebih
 * banyak daripada isi meja, mengambil makanan cuma menurunkan angka di papan ("48/48"
 * jadi "47/48") dan mejanya tetap penuh. Baru setelah stoknya menyusut sampai sama
 * dengan isi meja, tiap pengambilan benar-benar mengurangi piring - sampai mejanya
 * licin tandas.</p>
 *
 * <p>Aturannya satu kalimat: <b>porsi di meja selalu {@code min(stok, isi meja saat
 * ditata)}</b>. Semua jalur - mengambil, mengisi ulang tiap ronde, memulai game -
 * berakhir dengan menegakkan aturan itu lewat {@link #enforce}, jadi isi meja dan
 * angka di papan tidak pernah bisa bercerita hal yang berbeda.</p>
 *
 * <h2>Kenapa pakai kotak koordinat</h2>
 *
 * <p>Blok Decorative Food didaftarkan memakai <b>id vanilla</b>
 * ({@code minecraft:bread}, {@code minecraft:cooked_beef}, ...) - registry blok dan
 * registry item memang terpisah, jadi id yang sama tidak bentrok. Akibatnya tidak ada
 * nama mod yang bisa dipakai menyaring mana piring kafetaria dan mana bukan, dan
 * menyaring lewat nama blok akan ikut menangkap ladang wortel di seberang map. Yang
 * menggantikan penyaring itu kotak koordinat di config: di luar kotak, mod ini tidak
 * menyentuh blok apa pun.</p>
 *
 * <h2>Kenapa tidak perlu perintah apa pun</h2>
 *
 * <p>Permainan ini menghapus piring dari meja, jadi penataannya harus bisa dipasang
 * lagi. Potretnya diambil <b>sendiri</b>: selama tidak ada game berjalan, isi kotak
 * dibaca berkala dan disimpan ke dunia. Apa pun yang ditata di meja di luar game
 * otomatis jadi penataan resminya. Begitu game selesai, meja dipulihkan dulu ke
 * penataan itu, baru pembacaan berkala jalan lagi - kalau urutannya dibalik, meja yang
 * setengah kosong justru akan tersimpan sebagai penataan resmi.</p>
 */
public final class Cafeteria {

    private static final Cafeteria INSTANCE = new Cafeteria();

    public static Cafeteria get() {
        return INSTANCE;
    }

    private Cafeteria() {
    }

    /** Jarak antar pembacaan isi meja saat tidak ada game, dalam tick. */
    private static final int IDLE_SCAN_TICKS = 40;

    private boolean active;
    private int stock;
    private int total;

    /** Isi meja saat ditata; urutannya tetap supaya pemulihannya bisa diandalkan. */
    private final List<Spot> spots = new ArrayList<>();
    /** Sisa porsi tiap piring, sejajar dengan {@link #spots}. */
    private final List<Integer> onTable = new ArrayList<>();
    private int idleTimer;
    /**
     * Tick terakhir tiap pemain mengambil makanan.
     *
     * <p>Satu klik kanan bisa sampai ke server dua kali: kalau tangan utama tidak
     * menghasilkan apa-apa, tangan kiri dicoba lewat jalur yang sama persis. Tanpa
     * penjaga ini satu klik memotong stok dua kali.</p>
     */
    private final Map<UUID, Long> lastTake = new HashMap<>();
    /**
     * Berapa porsi beracun yang masih tersembunyi di antara tiap jenis makanan.
     *
     * <p>Racunnya menempel di <b>jenis</b> makanan, bukan di satu piring tertentu.
     * Alasannya bukan penyederhanaan: yang diambil orang dari meja adalah "satu roti",
     * dan piring mana yang diklik tidak pernah jadi pertimbangan siapa pun. Kalau
     * racunnya diikat ke satu piring, Jack praktis harus berharap korbannya kebetulan
     * mengklik piring yang itu.</p>
     */
    private final Map<Item, Integer> poisoned = new HashMap<>();

    // ------------------------------------------------------------------ state

    public boolean isActive() {
        return active;
    }

    public int getStock() {
        return stock;
    }

    public int getTotal() {
        return total;
    }

    // ------------------------------------------------------------------ kotak kafetaria

    @Nullable
    private static ServerLevel level(MinecraftServer server) {
        ResourceLocation id = ResourceLocation.tryParse(JackConfig.CONFIG.areaDimension.get());
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    @Nullable
    private static BlockPos parsePos(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Semua posisi di dalam kotak kafetaria, atau kosong kalau confignya tidak terbaca. */
    private static List<BlockPos> areaPositions() {
        BlockPos from = parsePos(JackConfig.CONFIG.areaFrom.get());
        BlockPos to = parsePos(JackConfig.CONFIG.areaTo.get());
        if (from == null || to == null) {
            return List.of();
        }
        List<BlockPos> positions = new ArrayList<>();
        for (int x = Math.min(from.getX(), to.getX()); x <= Math.max(from.getX(), to.getX()); x++) {
            for (int y = Math.min(from.getY(), to.getY()); y <= Math.max(from.getY(), to.getY()); y++) {
                for (int z = Math.min(from.getZ(), to.getZ()); z <= Math.max(from.getZ(), to.getZ()); z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    /** Sudut kotak dengan koordinat terkecil, atau {@link BlockPos#ZERO} kalau confignya rusak. */
    private static BlockPos areaMin() {
        BlockPos from = parsePos(JackConfig.CONFIG.areaFrom.get());
        BlockPos to = parsePos(JackConfig.CONFIG.areaTo.get());
        if (from == null || to == null) {
            return BlockPos.ZERO;
        }
        return new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()),
                Math.min(from.getZ(), to.getZ()));
    }

    private static BlockPos areaMax() {
        BlockPos from = parsePos(JackConfig.CONFIG.areaFrom.get());
        BlockPos to = parsePos(JackConfig.CONFIG.areaTo.get());
        if (from == null || to == null) {
            return BlockPos.ZERO;
        }
        return new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()),
                Math.max(from.getZ(), to.getZ()));
    }

    /** true kalau posisi ini isi meja kafetaria dan boleh disentuh mod ini. */
    public boolean inArea(Level world, BlockPos pos) {
        ResourceLocation dimension = ResourceLocation.tryParse(JackConfig.CONFIG.areaDimension.get());
        if (dimension == null || !world.dimension().location().equals(dimension)) {
            return false;
        }
        BlockPos min = areaMin();
        BlockPos max = areaMax();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    // ------------------------------------------------------------------ potret meja

    /** Membaca isi kotak apa adanya dari dunia. */
    private static List<Spot> readArea(ServerLevel level) {
        List<Spot> found = new ArrayList<>();
        for (BlockPos pos : areaPositions()) {
            BlockState state = level.getBlockState(pos);
            if (!CafeteriaFood.isFoodBlock(state)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            found.add(new Spot(level.dimension(), pos.immutable(), state,
                    blockEntity == null ? null : blockEntity.saveWithFullMetadata()));
        }
        return found;
    }

    /**
     * Menyimpan penataan meja sendiri selama tidak ada game berjalan.
     *
     * <p>Ini pengganti perintah "simpan penataan": apa pun yang ditata di meja di luar
     * game langsung jadi penataan resminya, tanpa perlu diingat siapa pun.</p>
     */
    public void idleTick(MinecraftServer server) {
        if (active || --idleTimer > 0) {
            return;
        }
        idleTimer = IDLE_SCAN_TICKS;
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }
        List<BlockPos> positions = areaPositions();
        // Kotak yang chunk-nya belum termuat cuma akan menyimpan udara sebagai penataan.
        if (positions.isEmpty() || !level.isLoaded(positions.get(0))) {
            return;
        }
        List<Spot> current = readArea(level);
        CafeteriaLayout layout = CafeteriaLayout.get(server);
        if (!sameLayout(layout.spots(), current)) {
            layout.set(current);
        }
    }

    private static boolean sameLayout(List<Spot> a, List<Spot> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).pos().equals(b.get(i).pos()) || a.get(i).state() != b.get(i).state()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Memulihkan meja ke penataan tersimpan saat server menyala.
     *
     * <p>Perlu, dan bukan sekadar kerapian: kalau server mati di tengah game, mejanya
     * tertinggal setengah kosong. Tanpa pemulihan di sini, pembacaan berkala akan
     * menyimpan kekosongan itu sebagai penataan resmi dan piring yang terlanjur dimakan
     * tidak akan pernah kembali.</p>
     */
    public void onServerStarted(MinecraftServer server) {
        loadSpots(server);
        restoreAll(server);
        spots.clear();
        onTable.clear();
        idleTimer = IDLE_SCAN_TICKS;
    }

    private void loadSpots(MinecraftServer server) {
        spots.clear();
        onTable.clear();
        spots.addAll(CafeteriaLayout.get(server).spots());
        for (Spot spot : spots) {
            onTable.add(CafeteriaFood.servings(spot.state()));
        }
    }

    // ------------------------------------------------------------------ game

    /** Menata ulang meja dan mengisi stok untuk satu game baru. */
    @Nullable
    public Component start(MinecraftServer server, int playerCount) {
        poisoned.clear();
        // Meja dipasang ulang dari schematic dulu, sebelum penataannya dibaca: yang
        // dipakai sepanjang permainan harus isi schematic itu, bukan potret keadaan
        // terakhir yang kebetulan sempat terbaca.
        ServerLevel schematicLevel = level(server);
        if (schematicLevel != null) {
            pasteSchematic(server, schematicLevel);
        }
        loadSpots(server);
        total = computeTotal(playerCount);
        stock = total;
        active = true;

        restoreAll(server);
        enforce(server, null);
        sync();
        JackOfHeartsMod.LOGGER.info("[JackOfHearts] Kafetaria: stok {} porsi, meja {} piring ({} porsi)",
                total, spots.size(), snapshotServings());

        return spots.isEmpty()
                ? Component.translatable("message.jackofhearts.cafeteria.no_table",
                                JackConfig.CONFIG.areaFrom.get(), JackConfig.CONFIG.areaTo.get())
                        .withStyle(ChatFormatting.YELLOW)
                : null;
    }

    public void stop(MinecraftServer server) {
        // Meja dipulihkan selagi potretnya masih dipegang; sesudah ini pembacaan berkala
        // jalan lagi dan akan membaca meja yang sudah penuh.
        restoreAll(server);
        active = false;
        stock = 0;
        total = 0;
        spots.clear();
        onTable.clear();
        lastTake.clear();
        poisoned.clear();
        FoodStash.clear();
        idleTimer = IDLE_SCAN_TICKS;
        sync();
    }

    /** Tambahan stok di awal tiap ronde; default confignya 0, jadi biasanya tidak terjadi apa-apa. */
    public void onRoundStart(MinecraftServer server) {
        int refill = JackConfig.CONFIG.foodRefillPerRound.get();
        if (!active || refill <= 0 || stock >= total) {
            return;
        }
        stock = Math.min(total, stock + refill);
        enforce(server, null);
        sync();
    }

    /**
     * Stok satu game: angka pasti dari {@code foodTotal}, atau kalau angkanya masih 0,
     * jatah per pemain dikali jumlah pemain yang benar-benar ikut.
     */
    public static int computeTotal(int playerCount) {
        int fixed = JackConfig.CONFIG.foodTotal.get();
        if (fixed > 0) {
            return fixed;
        }
        return JackConfig.CONFIG.foodPerPlayer.get() * Math.max(playerCount, 0);
    }

    /**
     * Pasang ulang meja dari schematic, lalu isi stoknya kembali penuh.
     *
     * <p>Isi meja hasil pemasangan itu langsung dijadikan penataan resminya, menimpa
     * potret lama. Kalau tidak, pemulihan meja setelah permainan selesai akan menarik
     * kembali susunan lama yang sudah tidak ada hubungannya dengan yang barusan
     * dipasang.</p>
     *
     * @return jumlah porsi stok yang baru, 0 kalau tidak ada game berjalan, atau -1
     *         kalau dunia kafetarianya sendiri tidak ketemu.
     */
    public int refill(MinecraftServer server, int playerCount) {
        ServerLevel level = level(server);
        if (level == null) {
            return -1;
        }
        if (!pasteSchematic(server, level)) {
            // Tanpa schematic, yang bisa dilakukan cuma memulihkan meja ke potret
            // terakhir - itu tetap mengembalikan piring yang sudah termakan.
            loadSpots(server);
            restoreAll(server);
        }
        loadSpots(server);
        if (!active) {
            return 0;
        }
        total = computeTotal(playerCount);
        stock = total;
        enforce(server, null);
        sync();
        return total;
    }

    /**
     * @return true kalau schematic-nya benar-benar terpasang, dan penataan meja sudah
     *         diperbarui mengikuti isinya.
     */
    private boolean pasteSchematic(MinecraftServer server, ServerLevel level) {
        int placed = FoodSchematic.paste(level);
        if (placed <= 0) {
            return false;
        }
        CafeteriaLayout.get(server).set(readArea(level));
        return true;
    }

    // ------------------------------------------------------------------ racun

    /** Penanda NBT porsi beracun. Tidak muncul di tooltip, jadi tidak terlihat siapa pun. */
    public static final String POISON_TAG = "JackPoison";

    /**
     * Jack meracuni satu jenis makanan.
     *
     * @return false kalau blok yang diklik bukan piring makanan kafetaria.
     */
    public boolean poison(ServerPlayer jack, BlockPos pos, BlockState state) {
        if (!active || !inArea(jack.level(), pos)) {
            return false;
        }
        Item food = CafeteriaFood.foodOf(state);
        if (food == null) {
            return false;
        }
        poisoned.merge(food, 1, Integer::sum);
        return true;
    }

    /**
     * Berapa porsi jenis makanan ini yang masih tersisa di meja.
     *
     * <p>Yang dihitung isi meja, bukan angka stok: stok adalah jumlah yang belum
     * dikeluarkan sama sekali, sedangkan yang bisa terambil orang berikutnya cuma yang
     * benar-benar tergambar di piring.</p>
     */
    private int servingsLeftOf(Item food) {
        int left = 0;
        for (int i = 0; i < spots.size(); i++) {
            if (CafeteriaFood.foodOf(spots.get(i).state()) == food) {
                left += onTable.get(i);
            }
        }
        return left;
    }

    public boolean isPoisoned(Item food) {
        return poisoned.getOrDefault(food, 0) > 0;
    }

    /**
     * Menentukan porsi yang barusan diambil beracun atau tidak.
     *
     * <p>Peluangnya setengah, dan begitu porsi beracunnya terambil, sisanya aman semua.
     * Itu yang membuat racun terasa seperti jebakan, bukan seperti hukuman: orang
     * berikutnya yang mengantre benar-benar aman, dan yang apes memang apes.</p>
     */
    private void taint(ServerPlayer player, Item food, ItemStack stack) {
        int left = poisoned.getOrDefault(food, 0);
        if (left <= 0) {
            return;
        }
        // Peluang setengah itu cuma berlaku selama masih ada porsi lain yang bisa
        // terambil. Begitu sisa porsi jenis ini tinggal sebanyak yang diracuni, tidak ada
        // lagi porsi aman untuk diundi - yang terambil pasti yang beracun.
        boolean forced = servingsLeftOf(food) <= left;
        if (!forced && player.getRandom().nextFloat() >= 0.5F) {
            return;
        }
        if (left <= 1) {
            poisoned.remove(food);
        } else {
            poisoned.put(food, left - 1);
        }
        stack.getOrCreateTag().putBoolean(POISON_TAG, true);
    }

    // ------------------------------------------------------------------ pengambilan

    /**
     * Pemain mengklik kanan sebuah piring.
     *
     * @return true kalau kliknya sudah ditangani di sini dan tidak boleh diteruskan ke
     *         Decorative Food. Klik yang diteruskan akan mengeluarkan satu porsi
     *         sendiri, di luar hitungan stok - persis yang mau dihindari.
     */
    public boolean take(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!active || !inArea(player.level(), pos)) {
            return false;
        }
        Item food = CafeteriaFood.foodOf(state);
        if (food == null) {
            return false;
        }

        long now = player.level().getGameTime();
        if (Long.valueOf(now).equals(lastTake.get(player.getUUID()))) {
            return true;
        }
        lastTake.put(player.getUUID(), now);

        if (stock <= 0) {
            refuse(player, pos, Component.translatable("message.jackofhearts.cafeteria.empty"));
            return true;
        }

        // Satu porsi per orang: habiskan dulu yang dibawa, baru boleh ambil lagi.
        // Inilah yang membuat kafetarianya harus didatangi berulang kali - tanpa batas
        // ini, orang pertama yang sampai bisa menyapu seluruh meja dalam sekali datang.
        int limit = JackConfig.CONFIG.foodCarryLimit.get();
        if (limit > 0 && CafeteriaFood.carriedBy(player.getInventory()) >= limit) {
            refuse(player, pos, Component.translatable("message.jackofhearts.cafeteria.carrying", limit));
            return true;
        }

        stock--;
        ItemStack stack = new ItemStack(food);
        taint(player, food, stack);
        Component name = stack.getHoverName();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.containerMenu.broadcastChanges();
        player.level().playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.6F);
        player.displayClientMessage(Component.translatable("message.jackofhearts.cafeteria.taken",
                name, stock, total).withStyle(ChatFormatting.GRAY), true);

        enforce(player.server, pos);
        sync();
        return true;
    }

    /**
     * Menolak pengambilan, sekaligus memaksa client menggambar ulang apa yang sebenarnya
     * ada.
     *
     * <p>Penyegaran itu bukan kehati-hatian berlebihan. Client menebak sendiri hasil klik
     * kanan sebelum server menjawab, dan tebakannya dijalankan lewat {@code use()} milik
     * mod makanan - yang memindahkan makanan dari tangan ke tumpukan. Kalau server cuma
     * diam karena menolak, tidak ada satu pun paket yang membantah tebakan itu, dan di
     * layar barangnya hilang dari tas <i>dan</i> dari meja padahal di server tidak
     * terjadi apa pun.</p>
     */
    private void refuse(ServerPlayer player, BlockPos pos, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 1.0F);
        player.containerMenu.broadcastFullState();
        if (player.level() instanceof ServerLevel level) {
            player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        }
    }

    /** Piring kafetaria tidak boleh dipukul habis; satu-satunya jalan mengambil adalah klik kanan. */
    public boolean protects(Level world, BlockPos pos, BlockState state) {
        return active && inArea(world, pos) && CafeteriaFood.isFoodBlock(state);
    }

    // ------------------------------------------------------------------ isi meja

    /** Porsi yang seharusnya ada di meja sekarang, tumpukan ikut dihitung. */
    private int tableServings() {
        int count = 0;
        for (int servings : onTable) {
            count += servings;
        }
        return count;
    }

    /** Porsi meja dalam keadaan tertata penuh. */
    private int snapshotServings() {
        int count = 0;
        for (Spot spot : spots) {
            count += CafeteriaFood.servings(spot.state());
        }
        return count;
    }

    /**
     * Menegakkan aturan tunggal kelas ini: porsi di meja = min(stok, isi meja saat
     * ditata).
     *
     * @param preferred piring yang barusan diklik; itu yang dikurangi duluan supaya yang
     *                  berkurang adalah piring yang benar-benar diambil orangnya, bukan
     *                  piring acak di ujung meja
     */
    private void enforce(MinecraftServer server, @Nullable BlockPos preferred) {
        int target = Math.min(stock, snapshotServings());
        while (tableServings() > target) {
            if (!removeOne(server, preferred)) {
                break;
            }
            preferred = null;
        }
        while (tableServings() < target) {
            if (!restoreOne(server)) {
                break;
            }
        }
    }

    /** Mengambil satu porsi dari meja: tumpukan berkurang satu, piring kosong hilang. */
    private boolean removeOne(MinecraftServer server, @Nullable BlockPos preferred) {
        int index = pickFilled(preferred);
        if (index < 0) {
            return false;
        }
        onTable.set(index, onTable.get(index) - 1);
        writeSpot(server, index);
        return true;
    }

    private int pickFilled(@Nullable BlockPos preferred) {
        int last = -1;
        for (int index = 0; index < spots.size(); index++) {
            if (onTable.get(index) <= 0) {
                continue;
            }
            if (preferred != null && spots.get(index).pos().equals(preferred)) {
                return index;
            }
            last = index;
        }
        return last;
    }

    private boolean restoreOne(MinecraftServer server) {
        for (int index = 0; index < spots.size(); index++) {
            if (onTable.get(index) < CafeteriaFood.servings(spots.get(index).state())) {
                onTable.set(index, onTable.get(index) + 1);
                writeSpot(server, index);
                return true;
            }
        }
        return false;
    }

    /** Pasang lagi semua piring persis seperti saat ditata. */
    private void restoreAll(MinecraftServer server) {
        for (int index = 0; index < spots.size(); index++) {
            onTable.set(index, CafeteriaFood.servings(spots.get(index).state()));
            writeSpot(server, index);
        }
    }

    /** Menulis satu piring ke dunia sesuai jumlah porsi yang seharusnya ada di situ. */
    private void writeSpot(MinecraftServer server, int index) {
        Spot spot = spots.get(index);
        int servings = onTable.get(index);
        ServerLevel level = server.getLevel(spot.dimension());
        if (level == null) {
            return;
        }
        if (servings <= 0) {
            BlockState current = level.getBlockState(spot.pos());
            if (CafeteriaFood.isFoodBlock(current)) {
                level.playSound(null, spot.pos(), current.getSoundType().getBreakSound(),
                        SoundSource.BLOCKS, 0.6F, 1.2F);
                level.removeBlock(spot.pos(), false);
            }
            return;
        }
        BlockState wanted = spot.state();
        IntegerProperty property = CafeteriaFood.countProperty(wanted);
        if (property != null) {
            wanted = wanted.setValue(property, servings);
        }
        if (level.getBlockState(spot.pos()) != wanted) {
            level.setBlock(spot.pos(), wanted, Block.UPDATE_ALL);
        }
        // Blok saja belum tentu cukup: piring yang menyimpan isinya di block entity akan
        // pulih dalam keadaan kosong bawaan kalau isinya tidak ikut dipasang balik.
        CompoundTag data = spot.blockEntity();
        if (data != null) {
            BlockEntity blockEntity = level.getBlockEntity(spot.pos());
            if (blockEntity != null) {
                blockEntity.load(data);
                blockEntity.setChanged();
                level.sendBlockUpdated(spot.pos(), wanted, wanted, Block.UPDATE_ALL);
            }
        }
    }

    // ------------------------------------------------------------------ sinkronisasi

    private void sync() {
        ModNetwork.toAll(packet());
    }

    /** Yang baru masuk harus langsung melihat angka yang sama dengan yang lain. */
    public void syncTo(ServerPlayer player) {
        ModNetwork.toPlayer(player, packet());
    }

    private CafeteriaPacket packet() {
        ResourceLocation dimension = ResourceLocation.tryParse(JackConfig.CONFIG.areaDimension.get());
        return new CafeteriaPacket(active, stock, total,
                dimension == null ? new ResourceLocation("minecraft", "overworld") : dimension,
                areaMin(), areaMax());
    }
}
