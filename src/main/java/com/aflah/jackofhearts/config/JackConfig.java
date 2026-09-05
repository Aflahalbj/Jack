package com.aflah.jackofhearts.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class JackConfig {
    public static final ForgeConfigSpec SPEC;
    public static final JackConfig CONFIG;

    public final ForgeConfigSpec.IntValue roundSeconds;
    public final ForgeConfigSpec.IntValue guessSeconds;
    public final ForgeConfigSpec.IntValue roleRevealSeconds;
    public final ForgeConfigSpec.IntValue executionSeconds;
    public final ForgeConfigSpec.ConfigValue<String> roomMarkerBlock;
    public final ForgeConfigSpec.IntValue roomMarkerDepth;
    public final ForgeConfigSpec.IntValue aceWinRoundIfNoKill;
    public final ForgeConfigSpec.IntValue jackWinRound;
    public final ForgeConfigSpec.IntValue jackCount;
    public final ForgeConfigSpec.BooleanValue reshuffleSymbolsEachRound;
    public final ForgeConfigSpec.BooleanValue lockFirstPerson;
    public final ForgeConfigSpec.BooleanValue eliminateOnWrongGuess;

    public final ForgeConfigSpec.DoubleValue hungerMultiplier;
    public final ForgeConfigSpec.DoubleValue hungerIdleDrain;
    public final ForgeConfigSpec.BooleanValue resetHungerOnStart;
    public final ForgeConfigSpec.BooleanValue uniformFood;
    public final ForgeConfigSpec.IntValue foodNutrition;
    public final ForgeConfigSpec.DoubleValue foodSaturation;
    public final ForgeConfigSpec.IntValue foodBase;
    public final ForgeConfigSpec.IntValue foodPerPlayer;
    public final ForgeConfigSpec.IntValue foodTotalOverride;
    public final ForgeConfigSpec.IntValue foodRefillPerRound;
    public final ForgeConfigSpec.IntValue foodCarryLimit;
    public final ForgeConfigSpec.ConfigValue<String> areaDimension;
    public final ForgeConfigSpec.ConfigValue<String> areaFrom;
    public final ForgeConfigSpec.ConfigValue<String> areaTo;

    private JackConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Pengaturan game Jack of Hearts").push("game");

        roundSeconds = builder
                .comment("Durasi satu ronde bebas sebelum masuk fase menebak (detik).")
                .defineInRange("roundSeconds", 600, 10, 7200);

        guessSeconds = builder
                .comment("Durasi fase menebak di ruangan (detik).")
                .defineInRange("guessSeconds", 20, 5, 600);

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

        aceWinRoundIfNoKill = builder
                .comment("Kalau Jack belum membunuh siapa pun sampai akhir ronde ini, Ace menang.")
                .defineInRange("aceWinRoundIfNoKill", 3, 1, 100);

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
                        "Ini bukan hiasan: di vanilla berjalan biasa memakan exhaustion NOL, jadi",
                        "pengali seberapa pun tidak membuat pemain yang cuma berjalan dan mengobrol",
                        "jadi lapar - dan kafetarianya tidak akan pernah didatangi.",
                        "Inilah yang menentukan cepat-lambatnya lapar buat pemain yang cuma jalan",
                        "dan mengobrol, karena buat mereka sumber lapar yang lain hampir nol.",
                        "Default 0.07 x 3.0 = 0.21/detik: batang lapar penuh habis sekitar 6,3",
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
                        "tidaknya makanan adalah foodBase/foodPerPlayer. Mengecilkan angka ini",
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

        foodBase = builder
                .comment("Bagian tetap dari stok makanan satu game.")
                .defineInRange("foodBase", 4, 0, 1000);

        foodPerPlayer = builder
                .comment("Tambahan stok makanan per pemain yang ikut. INI KENOP KELANGKAAN.",
                        "Stok = foodBase + foodPerPlayer x jumlah pemain. Dengan default 4 + 5:",
                        "4 pemain = 24 porsi, 8 pemain = 44 porsi, 12 pemain = 64 porsi.",
                        "Perhitungannya: satu pemain menghabiskan kira-kira satu porsi tiap 3 menit",
                        "main aktif, dan jumlah pemain menyusut sepanjang permainan karena laser.",
                        "Untuk ronde 5 menit, 44 porsi untuk 8 pemain habis sekitar ronde 5-6, jadi",
                        "ronde-ronde terakhir dijalani dengan perut kosong - memang begitu maunya.",
                        "Hitungannya berbasis MENIT, bukan ronde, jadi ronde yang dipotong lebih",
                        "cepat otomatis membuat stok yang sama menutup lebih banyak ronde.",
                        "PENTING: kalau roundSeconds masih 600 (10 menit per ronde), permainannya",
                        "dua kali lebih panjang dan angka ini perlu dinaikkan kira-kira dua kali.")
                .defineInRange("foodPerPlayer", 5, 0, 100);

        foodTotalOverride = builder
                .comment("Kalau diisi lebih dari 0, stok makanan dipaksa ke angka ini dan rumus",
                        "foodBase/foodPerPlayer diabaikan.")
                .defineInRange("foodTotalOverride", 0, 0, 10000);

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

        areaDimension = builder
                .comment("Dimensi tempat meja kafetaria berada.")
                .define("areaDimension", "minecraft:overworld");

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
