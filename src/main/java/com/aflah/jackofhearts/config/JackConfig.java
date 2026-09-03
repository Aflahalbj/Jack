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
    public final ForgeConfigSpec.BooleanValue teleportToGuessRoom;
    public final ForgeConfigSpec.BooleanValue eliminateOnWrongGuess;

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

        teleportToGuessRoom = builder
                .comment("Teleport semua pemain hidup ke ruang tebak saat fase menebak dimulai (butuh /jack setroom).")
                .define("teleportToGuessRoom", false);

        eliminateOnWrongGuess = builder
                .comment("Salah menebak / tidak menebak = ditembak laser dan tereliminasi.")
                .define("eliminateOnWrongGuess", true);

        builder.pop();
    }

    static {
        Pair<JackConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(JackConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }
}
