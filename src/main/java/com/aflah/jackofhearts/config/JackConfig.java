package com.aflah.jackofhearts.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class JackConfig {
    public static final ForgeConfigSpec SPEC;
    public static final JackConfig CONFIG;

    /**
     * Porsi makanan per pemain, nilai bawaannya.
     *
     * <p>Ditulis sebagai konstanta, bukan cuma angka di dalam {@code defineInRange},
     * karena {@code /jack setting maxfood default} harus bisa mengembalikannya - dan
     * "default" yang tidak benar-benar mengembalikan angka bawaan cuma memindahkan
     * kebingungan, bukan menghilangkannya.</p>
     */
    public static final int DEFAULT_FOOD_PER_PLAYER = 8;

    public final ForgeConfigSpec.IntValue roundSeconds;
    public final ForgeConfigSpec.IntValue guessSeconds;
    public final ForgeConfigSpec.IntValue roleRevealSeconds;
    public final ForgeConfigSpec.IntValue executionSeconds;
    public final ForgeConfigSpec.ConfigValue<String> roomMarkerBlock;
    public final ForgeConfigSpec.IntValue roomMarkerDepth;
    public final ForgeConfigSpec.IntValue maxDeathlessRounds;
    public final ForgeConfigSpec.IntValue jackWinRound;
    public final ForgeConfigSpec.IntValue jackCount;
    public final ForgeConfigSpec.BooleanValue reshuffleSymbolsEachRound;
    public final ForgeConfigSpec.BooleanValue lockFirstPerson;
    public final ForgeConfigSpec.BooleanValue eliminateOnWrongGuess;
    public final ForgeConfigSpec.IntValue jackOfflineSeconds;
    public final ForgeConfigSpec.ConfigValue<String> arenaDimension;
    public final ForgeConfigSpec.ConfigValue<String> arenaSpawn;
    public final ForgeConfigSpec.ConfigValue<String> arenaSchematic;
    public final ForgeConfigSpec.ConfigValue<String> arenaSchematicOrigin;
    public final ForgeConfigSpec.IntValue blockReach;
    public final ForgeConfigSpec.IntValue playerHealth;
    public final ForgeConfigSpec.BooleanValue arenaNoMobs;
    public final ForgeConfigSpec.BooleanValue milkChest;
    public final ForgeConfigSpec.BooleanValue milkFurnace;
    public final ForgeConfigSpec.BooleanValue milkDispenser;
    public final ForgeConfigSpec.BooleanValue milkHopper;
    public final ForgeConfigSpec.BooleanValue milkBarrel;
    public final ForgeConfigSpec.BooleanValue skillDrunk;
    public final ForgeConfigSpec.IntValue skillDrunkMax;
    public final ForgeConfigSpec.BooleanValue skillHunger;
    public final ForgeConfigSpec.IntValue skillHungerMax;
    public final ForgeConfigSpec.BooleanValue skillPoison;
    public final ForgeConfigSpec.IntValue skillPoisonMax;
    public final ForgeConfigSpec.IntValue milkMax;
    public final ForgeConfigSpec.BooleanValue unknownRole;
    public final ForgeConfigSpec.IntValue unknownMax;

    public final ForgeConfigSpec.DoubleValue hungerMultiplier;
    public final ForgeConfigSpec.DoubleValue hungerIdleDrain;
    public final ForgeConfigSpec.BooleanValue resetHungerOnStart;
    public final ForgeConfigSpec.BooleanValue uniformFood;
    public final ForgeConfigSpec.IntValue foodNutrition;
    public final ForgeConfigSpec.DoubleValue foodSaturation;
    public final ForgeConfigSpec.IntValue foodPerPlayer;
    public final ForgeConfigSpec.IntValue foodTotal;
    public final ForgeConfigSpec.IntValue foodRefillPerRound;
    public final ForgeConfigSpec.IntValue foodCarryLimit;
    public final ForgeConfigSpec.ConfigValue<String> foodSchematic;
    public final ForgeConfigSpec.ConfigValue<String> foodSchematicOrigin;
    public final ForgeConfigSpec.ConfigValue<String> areaDimension;
    public final ForgeConfigSpec.ConfigValue<String> areaFrom;
    public final ForgeConfigSpec.ConfigValue<String> areaTo;

    private JackConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Pengaturan game Jack of Hearts").push("game");

        roundSeconds = builder
                .comment("Durasi satu ronde bebas sebelum masuk fase menebak (detik).",
                        "Bisa diubah selagi server jalan lewat /jack setting settimer round <detik>.")
                .defineInRange("roundSeconds", 600, 10, 7200);

        guessSeconds = builder
                .comment("Durasi fase menebak di ruangan (detik).",
                        "Bisa diubah selagi server jalan lewat /jack setting settimer guess <detik>.")
                .defineInRange("guessSeconds", 30, 5, 600);

        roleRevealSeconds = builder
                .comment("Durasi animasi kocok kartu pembagian peran (detik). Isi 0 untuk mematikan",
                        "dan langsung masuk ronde 1. Nilai 1-7 akan dinaikkan ke 8, karena",
                        "animasinya sendiri butuh 7,2 detik: barisan kartu pembuka, menutup,",
                        "menumpuk, mengocok, lalu membalik kartu peran.")
                .defineInRange("roleRevealSeconds", 9, 0, 30);

        executionSeconds = builder
                .comment("Durasi fase eksekusi laser setelah waktu menebak habis (detik).",
                        "Ronde berikutnya baru mulai setelah fase ini selesai, supaya Jack tidak",
                        "sudah bisa berburu sementara orang masih dieksekusi.")
                .defineInRange("executionSeconds", 3, 2, 15);

        roomMarkerBlock = builder
                .comment("Blok lantai penanda ruang tebak. Kalau ada blok ini di bawah kaki pemain,",
                        "dia dianggap berada di dalam ruangan dan lasernya keluar dari Terminal",
                        "Tebakan; kalau tidak, lasernya turun dari langit.")
                .define("roomMarkerBlock", "minecraft:beehive");

        roomMarkerDepth = builder
                .comment("Berapa blok ke bawah yang diperiksa untuk mencari blok penanda.",
                        "Default 4 supaya pemain yang berdiri di atas blok tambahan atau sedang",
                        "di puncak lompatan tetap terhitung berada di dalam ruangan.")
                .defineInRange("roomMarkerDepth", 4, 1, 16);

        maxDeathlessRounds = builder
                .comment("Berapa ronde beruntun boleh lewat tanpa ada satu pun peserta yang gugur",
                        "sebelum Jack dieksekusi laser.",
                        "Yang dihitung kematian siapa pun karena sebab apa pun - salah menebak",
                        "simbol maupun kelaparan - bukan pembunuhan oleh tangan Jack, karena Jack",
                        "memang tidak bisa melukai siapa-siapa. Tugas Jack adalah membuat orang",
                        "salah menebak atau kehabisan makanan, dan hitungan inilah tenggatnya.",
                        "Hitungannya berjalan lagi dari nol setiap kali ada yang gugur: berhasil",
                        "di ronde 2 berarti tenggat berikutnya ronde 5.")
                .defineInRange("maxDeathlessRounds", 3, 1, 100);

        jackWinRound = builder
                .comment("Kalau Jack masih hidup setelah menyelesaikan ronde ini, Jack menang.")
                .defineInRange("jackWinRound", 10, 1, 100);

        jackCount = builder
                .comment("Jumlah pemain yang jadi Jack saat pengocokan peran.")
                .defineInRange("jackCount", 1, 1, 16);

        reshuffleSymbolsEachRound = builder
                .comment("Acak ulang simbol punggung setiap ronde baru.")
                .define("reshuffleSymbolsEachRound", true);

        lockFirstPerson = builder
                .comment("Kunci kamera ke first person (F5 dimatikan) selama pemain masih hidup.")
                .define("lockFirstPerson", true);


        eliminateOnWrongGuess = builder
                .comment("Salah menebak / tidak menebak = ditembak laser dan tereliminasi.")
                .define("eliminateOnWrongGuess", true);

        arenaDimension = builder
                .comment("Dimensi tempat permainan berlangsung. Bawaannya dimensi arena milik",
                        "mod ini sendiri - dunia void yang isinya dipasang dari schematic.",
                        "Ganti kalau map-mu tinggal di dimensi lain.")
                .define("arenaDimension", "jackofhearts:arena");

        arenaSpawn = builder
                .comment("Titik kumpul di arena (x y z). Semua peserta dipindahkan ke sini saat",
                        "/jack start, dan ke sini juga /jack gotoarena mengantar.")
                .define("arenaSpawn", "0 0 0");

        arenaSchematic = builder
                .comment("Nama berkas schematic seluruh map arena, dicari di config/jackofhearts/",
                        "lalu di dalam jar mod. Dipasang lewat /jack pastearena - sekali saja saat",
                        "menyiapkan dunia, bukan tiap permainan.",
                        "Kosongkan untuk mematikan pemasangan arena.")
                .define("arenaSchematic", "arena.schem");

        arenaSchematicOrigin = builder
                .comment("Koordinat sudut schematic arena yang paling kecil (x y z).")
                .define("arenaSchematicOrigin", "0 0 0");

        blockReach = builder
                .comment("Jangkauan tangan peserta selama permainan, dalam blok. Vanilla 4.5.",
                        "Dipasang saat /jack start dan dikembalikan saat permainan berhenti.")
                .defineInRange("blockReach", 3, 1, 10);

        playerHealth = builder
                .comment("Nyawa peserta selama permainan, dalam setengah hati. Vanilla 20.",
                        "Default 5 (2,5 hati): jatuh dari ketinggian sedang saja sudah cukup,",
                        "dan kelaparan tidak perlu berjam-jam untuk menghabisi orang.",
                        "Dipasang saat /jack start dan dikembalikan saat permainan berhenti.")
                .defineInRange("playerHealth", 5, 1, 20);

        arenaNoMobs = builder
                .comment("Tolak semua mob masuk ke dimensi arena, dari sumber mana pun -",
                        "spawn alami, spawner, telur, maupun /summon.",
                        "Biome arena memang tidak punya daftar spawn, tapi itu cuma menutup",
                        "spawn alami; yang lain tetap lolos.")
                .define("arenaNoMobs", true);

        milkChest = builder.comment("Peti boleh jadi tempat susu.").define("milkChest", true);
        milkFurnace = builder.comment("Tungku boleh jadi tempat susu.").define("milkFurnace", true);
        milkDispenser = builder.comment("Dispenser/dropper boleh jadi tempat susu.")
                .define("milkDispenser", true);
        milkHopper = builder.comment("Hopper boleh jadi tempat susu.").define("milkHopper", true);
        milkBarrel = builder.comment("Tong boleh jadi tempat susu.").define("milkBarrel", true);

        skillDrunk = builder
                .comment("Jack punya skill Mabuk. Disetel lewat /jack setting drunk on|off.")
                .define("skillDrunk", true);

        skillDrunkMax = builder
                .comment("Berapa kali skill Mabuk boleh dipakai dalam satu permainan.")
                .defineInRange("skillDrunkMax", 2, 0, 64);

        skillHunger = builder
                .comment("Jack punya skill Lapar. Disetel lewat /jack setting hunger on|off.")
                .define("skillHunger", true);

        skillHungerMax = builder
                .comment("Berapa kali skill Lapar boleh dipakai dalam satu permainan.")
                .defineInRange("skillHungerMax", 2, 0, 64);

        skillPoison = builder
                .comment("Jack punya skill Racun (tombol P sambil menatap piring kafetaria).",
                        "Disetel lewat /jack setting poison on|off.")
                .define("skillPoison", true);

        skillPoisonMax = builder
                .comment("Berapa kali skill Racun boleh dipakai dalam satu permainan.")
                .defineInRange("skillPoisonMax", 2, 0, 64);

        unknownRole = builder
                .comment("Bagikan peran Unknown. Unknown belum punya pihak: kalau ada Jack yang gugur",
                        "selagi dia masih hidup, dia ikut Ace; kalau semua Ace habis dan dia tinggal",
                        "sendiri, dia jadi Jack. Unknown yang gugur sebelum pihaknya ditentukan tidak",
                        "ikut menang siapa-siapa.",
                        "Disetel lewat /jack setting unknown on|off.")
                .define("unknownRole", false);

        unknownMax = builder
                .comment("Berapa pemain yang jadi Unknown. Dikurangi sendiri kalau pemainnya tidak",
                        "cukup: selalu disisakan paling tidak satu Ace.",
                        "Disetel lewat /jack setting unknown max <jumlah>.")
                .defineInRange("unknownMax", 1, 1, 16);

        milkMax = builder
                .comment("Berapa susu penawar yang muncul sepanjang satu permainan.",
                        "Jatahnya dibagi rata ke jackWinRound ronde. Dengan default 20 susu dan",
                        "10 ronde: dua susu per ronde. Isi 9 dan ada satu ronde tanpa susu; isi",
                        "12 dan ada dua ronde yang dapat dua.",
                        "Susunya diselipkan ke peti, tungku, dispenser, hopper, dan tong yang",
                        "tersebar di map - 30% ke tong, sisanya ke wadah jenis lain.",
                        "Disetel lewat /jack setting milk max <jumlah>.")
                .defineInRange("milkMax", 20, 0, 1000);

        jackOfflineSeconds = builder
                .comment("Berapa lama Jack boleh keluar dari server sebelum dieksekusi laser dari",
                        "langit (detik). Aturan ini cuma berlaku untuk Jack: permainan berhenti",
                        "berjalan kalau pemburunya tidak ada, sementara Ace yang pergi masih",
                        "dihukum sendiri oleh fase menebak berikutnya.",
                        "Hitung mundurnya tidak pernah diumumkan ke pemain - cuma ke log server -",
                        "karena pengumuman apa pun tentangnya sama saja membocorkan siapa Jack.",
                        "Isi 0 untuk mematikan aturan ini.")
                .defineInRange("jackOfflineSeconds", 90, 0, 3600);

        builder.pop();

        builder.comment("Kafetaria: lapar dipercepat, dan stok makanan di meja kafetaria.").push("cafeteria");

        hungerMultiplier = builder
                .comment("Pengali kecepatan lapar selama game berjalan. 3.0 = tiga kali lebih cepat",
                        "daripada vanilla. Yang dikalikan adalah exhaustion, yaitu biaya lapar dari",
                        "berlari, melompat, memukul, dan menyembuhkan diri sendiri.")
                .defineInRange("hungerMultiplier", 3.0D, 1.0D, 10.0D);

        hungerIdleDrain = builder
                .comment("Exhaustion tambahan per detik untuk pemain yang tidak melakukan apa-apa,",
                        "dihitung sebelum dikali hungerMultiplier.",
                        "Ini LANTAI, bukan tambahan: yang dipakai tiap tick adalah yang lebih besar",
                        "antara exhaustion dari kegiatan pemain dan angka ini, lalu baru dikali",
                        "hungerMultiplier. Jadi pengalinya sama persis untuk semua orang - yang",
                        "berlari tidak membayar dua kali (kegiatannya plus tetesan diam).",
                        "Ini bukan hiasan: di vanilla berjalan biasa memakan exhaustion NOL, jadi",
                        "pengali seberapa pun tidak membuat pemain yang cuma berjalan dan mengobrol",
                        "jadi lapar - dan kafetarianya tidak akan pernah didatangi.",
                        "Inilah yang menentukan cepat-lambatnya lapar buat pemain yang cuma jalan",
                        "dan mengobrol, karena buat mereka sumber lapar yang lain hampir nol.",
                        "Default 0.07 x 3.0 = 0.21/detik untuk yang diam: batang lapar penuh habis",
                        "sekitar 6,3",
                        "menit walau pemainnya cuma berdiri diam. Turunkan ke 0.05 kalau terasa",
                        "terlalu sering bolak-balik, naikkan ke 0.09 kalau masih terasa lambat.")
                .defineInRange("hungerIdleDrain", 0.07D, 0.0D, 1.0D);

        resetHungerOnStart = builder
                .comment("Kenyangkan semua peserta saat game dimulai, supaya tiap ronde pertama",
                        "dimulai dari titik yang sama untuk semua orang.")
                .define("resetHungerOnStart", true);

        uniformFood = builder
                .comment("Samakan nilai gizi ke-12 makanan kafetaria (roti, sweet berry, kentang",
                        "bakar, apel, semangka, wortel, bit, kue, ayam/salmon/kod/daging matang).",
                        "Tanpa ini bit cuma mengisi 1 sementara daging sapi matang mengisi 8, jadi",
                        "yang menentukan siapa bertahan bukan permainannya, tapi kebetulan piring",
                        "mana yang lebih dekat dengan tempat duduknya.",
                        "Berlaku ke item vanillanya sendiri, jadi ikut berlaku untuk makanan yang",
                        "sama dari mana pun asalnya.")
                .define("uniformFood", true);

        foodNutrition = builder
                .comment("Isi lapar tiap makanan kafetaria (2 = satu paha ayam di HUD).",
                        "INI KENOP FREKUENSI, BUKAN KENOP KELANGKAAN. Yang diaturnya seberapa",
                        "sering satu pemain harus berjalan ke kafetaria; yang mengatur habis atau",
                        "tidaknya makanan adalah maxfood. Mengecilkan angka ini",
                        "tanpa mengecilkan stok cuma melipatgandakan jumlah klik untuk hasil akhir",
                        "yang sama persis.",
                        "Default 6, bersama foodSaturation 0.8 = 6 lapar + 9,6 saturasi = 15,6",
                        "poin: cukup untuk sekitar 5 menit diam atau 3 menit main aktif, yaitu",
                        "satu sampai dua kali datang per ronde 5 menit.")
                .defineInRange("foodNutrition", 6, 1, 20);

        foodSaturation = builder
                .comment("Pengali saturasi tiap makanan kafetaria. Saturasi = cadangan tak terlihat",
                        "yang habis duluan sebelum batang lapar turun.",
                        "Saturasi yang didapat = foodNutrition x nilai ini x 2.")
                .defineInRange("foodSaturation", 0.8D, 0.0D, 2.0D);

        foodPerPlayer = builder
                .comment("Porsi makanan per pemain, dipakai kalau foodTotal masih 0.",
                        "Default 8: 2 pemain = 16 porsi, 8 pemain = 64 porsi.",
                        "Kembali ke perhitungan ini kapan saja lewat /jack setting maxfood default.")
                .defineInRange("foodPerPlayer", DEFAULT_FOOD_PER_PLAYER, 0, 100);

        foodTotal = builder
                .comment("Stok makanan satu game, angka pasti - tidak peduli ada berapa pemain.",
                        "Isi 0 untuk memakai foodPerPlayer x jumlah pemain.",
                        "Disetel lewat /jack setting maxfood <porsi>, dan dikembalikan ke hitungan",
                        "per pemain lewat /jack setting maxfood default.")
                .defineInRange("foodTotal", 0, 0, 10000);

        foodRefillPerRound = builder
                .comment("Stok yang ditambahkan tiap ronde baru dimulai, dibatasi stok awal.",
                        "Default 0: sekali habis, habis.")
                .defineInRange("foodRefillPerRound", 0, 0, 1000);

        foodCarryLimit = builder
                .comment("Berapa banyak makanan kafetaria yang boleh dibawa satu pemain sekaligus.",
                        "Default 1: ambil satu, habiskan dulu, baru boleh ambil lagi.",
                        "Inilah yang membuat kafetarianya jadi tempat yang harus didatangi berulang",
                        "kali. Tanpa batas ini, satu orang bisa menyapu seluruh meja dalam sekali",
                        "datang dan sisa pemain lain kelaparan tanpa pernah punya kesempatan.",
                        "Isi 0 untuk mematikan batasnya.")
                .defineInRange("foodCarryLimit", 1, 0, 64);

        foodSchematic = builder
                .comment("Nama berkas schematic meja makanan, dicari di config/jackofhearts/.",
                        "Dipasang ulang tiap /jack start dan tiap /jack refill, jadi tiap permainan",
                        "dimulai dari meja yang benar-benar sama - bukan dari potret keadaan",
                        "terakhir yang kebetulan sempat terbaca.",
                        "Format Sponge Schematic (.schem keluaran WorldEdit), versi 2 maupun 3.",
                        "Blok udara di dalamnya ikut dipasang, persis seperti //paste biasa: itu",
                        "yang membuat piring nyasar ikut tersapu, bukan menumpuk.",
                        "Kosongkan untuk mematikan pemasangan schematic sama sekali.")
                .define("foodSchematic", "food.schem");

        foodSchematicOrigin = builder
                .comment("Koordinat sudut schematic yang paling kecil (x y z).",
                        "Blok pertama di dalam schematic mendarat tepat di sini, jadi kalau",
                        "hasilnya meleset, geser angka ini - bukan schematic-nya.")
                .define("foodSchematicOrigin", "-4 1 -45");

        areaDimension = builder
                .comment("Dimensi tempat meja kafetaria berada. Bawaannya dimensi arena,",
                        "karena di situlah seluruh permainan berlangsung.")
                .define("areaDimension", "jackofhearts:arena");

        areaFrom = builder
                .comment("Sudut pertama kotak meja kafetaria, ditulis \"x y z\".",
                        "Kotak inilah satu-satunya tempat mod ini menyentuh blok makanan.",
                        "Batasan lewat kotak, bukan lewat nama mod, karena blok Decorative Food",
                        "didaftarkan memakai id vanilla (minecraft:bread, minecraft:cooked_beef,",
                        "...) - tidak ada nama mod yang bisa dipakai menyaring, dan menyaring",
                        "lewat nama blok akan ikut menangkap ladang wortel di seberang map.")
                .define("areaFrom", "-4 1 -44");

        areaTo = builder
                .comment("Sudut kedua kotak meja kafetaria, ditulis \"x y z\". Urutan sudut bebas.")
                .define("areaTo", "1 1 -45");

        builder.pop();
    }

    static {
        Pair<JackConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(JackConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }
}
