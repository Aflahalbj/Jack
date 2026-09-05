package com.aflah.jackofhearts.command;

import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.game.GameManager;
import com.aflah.jackofhearts.game.PlayerData;
import com.aflah.jackofhearts.game.Role;
import com.mojang.brigadier.CommandDispatcher;
import com.aflah.jackofhearts.cafeteria.Cafeteria;
import com.aflah.jackofhearts.cafeteria.FoodBalance;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JackCommand {

    /**
     * Cabang perintah yang cuma boleh dipakai host.
     *
     * <p>Izinnya dipasang per cabang, bukan sekali di akar {@code /jack}, karena satu
     * cabang memang untuk semua orang: {@code /jack skip}. Izin di akar akan ikut
     * menutup seluruh isinya, dan pemain biasa tidak akan pernah melihat perintah itu
     * ada.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> host(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("jack");

        root.then(host("start").executes(JackCommand::startDefault));

        root.then(host("regis")
                .executes(ctx -> setRegistered(ctx, List.of(ctx.getSource().getPlayerOrException()), true))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> setRegistered(ctx, EntityArgument.getPlayers(ctx, "targets"), true))));

        root.then(host("regisall")
                .executes(ctx -> setRegistered(ctx, ctx.getSource().getServer().getPlayerList().getPlayers(), true)));

        root.then(host("unregis")
                .executes(ctx -> setRegistered(ctx, List.of(ctx.getSource().getPlayerOrException()), false))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> setRegistered(ctx, EntityArgument.getPlayers(ctx, "targets"), false))));

        root.then(host("unregisall").executes(JackCommand::unregisterAll));
        root.then(host("listplayer").executes(JackCommand::listPlayers));

        // Preview animasi buat menyetel tampilan tanpa harus memulai game.
        root.then(host("shuffle")
                .then(Commands.argument("players", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> previewShuffle(ctx,
                                IntegerArgumentType.getInteger(ctx, "players"),
                                JackConfig.CONFIG.jackCount.get(), null))
                        .then(Commands.argument("jacks", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> previewShuffle(ctx,
                                        IntegerArgumentType.getInteger(ctx, "players"),
                                        IntegerArgumentType.getInteger(ctx, "jacks"), null))
                                .then(Commands.literal("as_jack")
                                        .executes(ctx -> previewShuffle(ctx,
                                                IntegerArgumentType.getInteger(ctx, "players"),
                                                IntegerArgumentType.getInteger(ctx, "jacks"), Role.JACK)))
                                .then(Commands.literal("as_ace")
                                        .executes(ctx -> previewShuffle(ctx,
                                                IntegerArgumentType.getInteger(ctx, "players"),
                                                IntegerArgumentType.getInteger(ctx, "jacks"), Role.ACE))))));

        // Setelan host: siapa yang dipaksa jadi Jack, dan berapa Jack yang dibagikan.
        root.then(host("setting")
                // Tanpa once/always berarti once: penunjukan yang berlaku selamanya
                // adalah yang paling gampang dilupakan, jadi itu yang harus diketik.
                .then(Commands.literal("setjack")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> setForcedJack(ctx, EntityArgument.getPlayers(ctx, "targets"), false))
                                .then(Commands.literal("once")
                                        .executes(ctx -> setForcedJack(ctx,
                                                EntityArgument.getPlayers(ctx, "targets"), false)))
                                .then(Commands.literal("always")
                                        .executes(ctx -> setForcedJack(ctx,
                                                EntityArgument.getPlayers(ctx, "targets"), true)))))
                .then(Commands.literal("unsetjack")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> unsetForcedJack(ctx, EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("clearjack").executes(JackCommand::clearForcedJacks))
                .then(Commands.literal("maxjack")
                        .executes(JackCommand::showSettings)
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                .executes(ctx -> setMaxJack(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("maxfood")
                        .executes(JackCommand::showSettings)
                        .then(Commands.literal("default").executes(JackCommand::setMaxFoodDefault))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                                .executes(ctx -> setMaxFood(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(Commands.literal("hungermultiplier")
                        .executes(JackCommand::showSettings)
                        .then(Commands.argument("count", DoubleArgumentType.doubleArg(1.0D, 10.0D))
                                .executes(ctx -> setHungerMultiplier(ctx,
                                        DoubleArgumentType.getDouble(ctx, "count")))))
                .then(Commands.literal("foodhunger")
                        .executes(JackCommand::showSettings)
                        .then(Commands.argument("point", IntegerArgumentType.integer(1, 20))
                                .then(Commands.argument("saturation", DoubleArgumentType.doubleArg(0.0D, 2.0D))
                                        .executes(ctx -> setFoodValue(ctx,
                                                IntegerArgumentType.getInteger(ctx, "point"),
                                                DoubleArgumentType.getDouble(ctx, "saturation"))))))
                .then(Commands.literal("settimer")
                        .executes(JackCommand::showSettings)
                        .then(Commands.literal("round")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 7200))
                                        .executes(ctx -> setTimer(ctx, true,
                                                IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("guess")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                                        .executes(ctx -> setTimer(ctx, false,
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("list").executes(JackCommand::showSettings)));

        root.then(host("refill").executes(JackCommand::refill));
        root.then(host("stop").executes(JackCommand::stop));
        root.then(host("status").executes(JackCommand::status));
        // Satu-satunya cabang tanpa izin host: pemungutan suara mempercepat ronde.
        root.then(Commands.literal("skip").executes(JackCommand::voteSkip));
        root.then(host("forceskip").executes(JackCommand::forceSkip));
        root.then(host("reveal").executes(JackCommand::reveal));
        root.then(host("debugrole").executes(JackCommand::debugRole));

        // Pratinjau eksekusi tanpa harus menyusun roster dan menunggu satu ronde penuh.
        root.then(host("debug")
                .then(Commands.literal("laser")
                        .executes(ctx -> debugLaser(ctx, List.of(ctx.getSource().getPlayerOrException()), true))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> debugLaser(ctx, EntityArgument.getPlayers(ctx, "targets"), true))))
                // Versi gertakannya: yang didapat orang yang tebakannya benar.
                .then(Commands.literal("safe")
                        .executes(ctx -> debugLaser(ctx, List.of(ctx.getSource().getPlayerOrException()), false))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> debugLaser(ctx, EntityArgument.getPlayers(ctx, "targets"), false))))
                .then(Commands.literal("clearcorpse").executes(JackCommand::clearCorpses))
                // /jack debug true|false [targets] -- tebakan sasaran selalu dinilai benar.
                .then(Commands.argument("always_correct", BoolArgumentType.bool())
                        .executes(ctx -> setAlwaysCorrect(ctx, List.of(ctx.getSource().getPlayerOrException()),
                                BoolArgumentType.getBool(ctx, "always_correct")))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> setAlwaysCorrect(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                        BoolArgumentType.getBool(ctx, "always_correct"))))));

        dispatcher.register(root);
    }

    /**
     * Alat testing: tebakan sasaran dinilai benar untuk <b>ronde yang sedang berjalan
     * saja</b>, lalu setelannya hilang sendiri begitu fase menebak dinilai.
     *
     * <p>Tebakannya juga langsung diisikan benar begitu fase menebak dimulai, supaya
     * yang terlihat di HUD sama dengan yang akan dinilai.</p>
     */
    private static int setAlwaysCorrect(CommandContext<CommandSourceStack> ctx,
                                        Collection<ServerPlayer> targets, boolean value) {
        GameManager manager = GameManager.get();
        int changed = 0;
        for (ServerPlayer player : targets) {
            if (manager.setAlwaysCorrect(player, value)) {
                changed++;
            }
            player.sendSystemMessage(Component.translatable(value
                            ? "message.jackofhearts.debug_correct_on"
                            : "message.jackofhearts.debug_correct_off")
                    .withStyle(value ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        }
        final int done = changed;
        final int total = targets.size();
        ctx.getSource().sendSuccess(() -> Component.translatable(value
                        ? "message.jackofhearts.debug_correct_set_on"
                        : "message.jackofhearts.debug_correct_set_off", done, total)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return done;
    }

    /**
     * Menunjuk (atau membatalkan) pemain yang dipaksa jadi Jack di game berikutnya.
     *
     * <p>Balasannya cuma ke pemanggil, dan sasarannya sendiri tidak diberi tahu
     * apa-apa. Peran baru boleh diketahui pemiliknya lewat animasi pembagian peran,
     * dan pesan "kamu dipilih jadi Jack" di sini akan mendahuluinya — sekaligus
     * membocorkan ke siapa pun yang kebetulan melihat layarnya.</p>
     */
    private static int setForcedJack(CommandContext<CommandSourceStack> ctx,
                                     Collection<ServerPlayer> targets, boolean always) {
        GameManager manager = GameManager.get();
        int changed = 0;
        for (ServerPlayer player : targets) {
            if (manager.setForcedJack(player, always)) {
                changed++;
            }
        }
        final int done = changed;
        ctx.getSource().sendSuccess(() -> Component.literal("Ditunjuk jadi Jack "
                        + (always ? "selamanya" : "untuk satu game") + ": " + done + " pemain.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        reportJackSettings(ctx.getSource());
        return changed;
    }

    private static int unsetForcedJack(CommandContext<CommandSourceStack> ctx,
                                       Collection<ServerPlayer> targets) {
        GameManager manager = GameManager.get();
        int changed = 0;
        for (ServerPlayer player : targets) {
            if (manager.unsetForcedJack(player)) {
                changed++;
            }
        }
        final int done = changed;
        ctx.getSource().sendSuccess(() -> Component.literal("Penunjukan dibatalkan: " + done + " pemain.")
                .withStyle(ChatFormatting.YELLOW), false);
        reportJackSettings(ctx.getSource());
        return changed;
    }

    private static int clearForcedJacks(CommandContext<CommandSourceStack> ctx) {
        int removed = GameManager.get().clearForcedJacks();
        ctx.getSource().sendSuccess(() -> Component.literal("Daftar setjack dikosongkan (" + removed
                + " pemain).").withStyle(ChatFormatting.YELLOW), false);
        reportJackSettings(ctx.getSource());
        return removed;
    }

    /**
     * Mengubah jumlah Jack yang dibagikan, dan menyimpannya ke file config.
     *
     * <p>Disimpan, bukan cuma berlaku sampai server mati: ini setelan permainan, dan
     * host yang menyetelnya lewat perintah tidak punya alasan menduga setelannya akan
     * hilang sendiri.</p>
     */
    private static int setMaxJack(CommandContext<CommandSourceStack> ctx, int count) {
        JackConfig.CONFIG.jackCount.set(count);
        JackConfig.CONFIG.jackCount.save();
        ctx.getSource().sendSuccess(() -> Component.literal("maxjack disetel ke " + count + ".")
                .withStyle(ChatFormatting.AQUA), true);
        reportJackSettings(ctx.getSource());
        return count;
    }

    /** Stok makanan satu game, angka pasti - tidak peduli ada berapa pemain. */
    private static int setMaxFood(CommandContext<CommandSourceStack> ctx, int amount) {
        JackConfig.CONFIG.foodTotal.set(amount);
        JackConfig.CONFIG.foodTotal.save();
        ctx.getSource().sendSuccess(() -> Component.literal("maxfood: " + amount
                        + " porsi untuk satu game, berapa pun jumlah pemainnya. "
                        + "Pakai /jack refill supaya langsung berlaku.")
                .withStyle(ChatFormatting.AQUA), true);
        return amount;
    }

    /**
     * Kembali ke hitungan bawaan: 8 porsi per pemain.
     *
     * <p>Jatah per pemainnya ikut dikembalikan ke angka bawaan, bukan cuma dimatikan
     * angka totalnya. Kalau tidak, setelan yang pernah dipasang lewat versi lama
     * perintah ini akan tertinggal di config dan "default" jadi bukan default.</p>
     */
    private static int setMaxFoodDefault(CommandContext<CommandSourceStack> ctx) {
        JackConfig.CONFIG.foodTotal.set(0);
        JackConfig.CONFIG.foodTotal.save();
        JackConfig.CONFIG.foodPerPlayer.set(JackConfig.DEFAULT_FOOD_PER_PLAYER);
        JackConfig.CONFIG.foodPerPlayer.save();
        int perPlayer = JackConfig.CONFIG.foodPerPlayer.get();
        int players = playerCountForFood(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("maxfood: default, " + perPlayer
                        + " porsi per pemain (" + players + " pemain sekarang -> "
                        + perPlayer * players + " porsi).")
                .withStyle(ChatFormatting.AQUA), true);
        return perPlayer;
    }

    /**
     * Jumlah pemain yang dipakai menghitung stok: peserta game yang sedang berjalan,
     * atau kalau tidak ada game, pemain yang sedang online.
     */
    private static int playerCountForFood(MinecraftServer server) {
        GameManager manager = GameManager.get();
        return manager.isRunning()
                ? manager.getParticipants().size()
                : server.getPlayerList().getPlayerCount();
    }

    /**
     * Pasang ulang meja dari schematic dan isi stoknya kembali penuh dengan setelan
     * maxfood yang berlaku sekarang.
     */
    private static int refill(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int total = Cafeteria.get().refill(source.getServer(), playerCountForFood(source.getServer()));
        if (total < 0) {
            source.sendFailure(Component.literal("Dunia kafetaria tidak ketemu; cek areaDimension di config.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(total > 0
                        ? "Meja dipasang ulang, stok diisi penuh: " + total + " porsi."
                        : "Meja dipasang ulang. Stoknya baru diisi saat game dimulai.")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int setHungerMultiplier(CommandContext<CommandSourceStack> ctx, double count) {
        JackConfig.CONFIG.hungerMultiplier.set(count);
        JackConfig.CONFIG.hungerMultiplier.save();
        ctx.getSource().sendSuccess(() -> Component.literal("hungermultiplier: " + count
                        + "x, berlaku sama untuk yang bergerak maupun yang diam.")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    /**
     * Nilai gizi satu porsi makanan kafetaria.
     *
     * <p>{@link FoodBalance#apply()} dipanggil ulang di sini karena nilai itu ditulis
     * langsung ke itemnya, bukan dibaca tiap kali dimakan — dan config yang diubah lewat
     * perintah tidak memicu kejadian reload yang biasanya mengurus itu.</p>
     */
    private static int setFoodValue(CommandContext<CommandSourceStack> ctx, int point, double saturation) {
        JackConfig.CONFIG.foodNutrition.set(point);
        JackConfig.CONFIG.foodNutrition.save();
        JackConfig.CONFIG.foodSaturation.set(saturation);
        JackConfig.CONFIG.foodSaturation.save();
        FoodBalance.apply();
        ctx.getSource().sendSuccess(() -> Component.literal("foodhunger: " + point + " lapar, saturasi x"
                        + saturation + " (total " + (point + point * saturation * 2.0D) + ").")
                .withStyle(ChatFormatting.AQUA), true);
        return point;
    }

    /**
     * Durasi ronde atau fase menebak.
     *
     * <p>Berlaku mulai fase berikutnya, bukan memotong yang sedang berjalan: hitungan
     * yang tiba-tiba berubah di tengah jalan membuat sisa waktu di layar semua orang
     * melompat tanpa sebab. Untuk memotong yang sekarang ada {@code /jack forceskip}.</p>
     */
    private static int setTimer(CommandContext<CommandSourceStack> ctx, boolean round, int seconds) {
        if (round) {
            JackConfig.CONFIG.roundSeconds.set(seconds);
            JackConfig.CONFIG.roundSeconds.save();
        } else {
            JackConfig.CONFIG.guessSeconds.set(seconds);
            JackConfig.CONFIG.guessSeconds.save();
        }
        ctx.getSource().sendSuccess(() -> Component.literal((round ? "Waktu ronde: " : "Waktu menebak: ")
                        + seconds + " detik. Berlaku mulai fase berikutnya.")
                .withStyle(ChatFormatting.AQUA), true);
        return seconds;
    }

    private static int showSettings(CommandContext<CommandSourceStack> ctx) {
        reportJackSettings(ctx.getSource());
        CommandSourceStack source = ctx.getSource();
        int fixed = JackConfig.CONFIG.foodTotal.get();
        String food = fixed > 0
                ? fixed + " porsi (pasti)"
                : "default, " + JackConfig.CONFIG.foodPerPlayer.get() + " x jumlah pemain";
        source.sendSuccess(() -> Component.literal("settimer: ronde "
                        + JackConfig.CONFIG.roundSeconds.get() + "s, menebak "
                        + JackConfig.CONFIG.guessSeconds.get() + "s")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("maxfood: " + food
                        + " | hungermultiplier: " + JackConfig.CONFIG.hungerMultiplier.get()
                        + "x | foodhunger: " + JackConfig.CONFIG.foodNutrition.get() + " lapar, saturasi x"
                        + JackConfig.CONFIG.foodSaturation.get())
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    /** Setelan Jack apa adanya, termasuk angka yang benar-benar akan dipakai nanti. */
    private static void reportJackSettings(CommandSourceStack source) {
        var forced = GameManager.get().getForcedJacks();
        int max = JackConfig.CONFIG.jackCount.get();
        int effective = GameManager.jackCountFor(forced.size());

        source.sendSuccess(() -> Component.literal("maxjack: " + max
                        + (effective > max ? " -> dipakai " + effective + " (ngikut jumlah setjack)" : ""))
                .withStyle(effective > max ? ChatFormatting.YELLOW : ChatFormatting.AQUA), false);
        String names = forced.values().stream()
                .map(f -> f.name() + (f.always() ? " (always)" : " (once)"))
                .collect(java.util.stream.Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal("setjack (" + forced.size() + "): "
                        + (forced.isEmpty() ? "-" : names))
                .withStyle(ChatFormatting.GRAY), false);
        if (!forced.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                            "Yang ditunjuk pasti dapat Jack; sisa kursinya diacak seperti biasa. "
                                    + "Penunjukan (once) hangus begitu /jack start dijalankan.")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
    }

    private static int setRegistered(CommandContext<CommandSourceStack> ctx,
                                     Collection<ServerPlayer> targets, boolean add) {
        CommandSourceStack source = ctx.getSource();
        GameManager manager = GameManager.get();
        int changed = 0;
        for (ServerPlayer player : targets) {
            if (add ? manager.register(player) : manager.unregister(player)) {
                changed++;
                player.sendSystemMessage(Component.translatable(add
                        ? "message.jackofhearts.you_registered"
                        : "message.jackofhearts.you_unregistered")
                        .withStyle(add ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            }
        }
        // Papan menampilkan daftar terdaftar selama game belum jalan, jadi ikut
        // diperbarui setiap roster berubah.
        manager.syncRoster(source.getServer());
        final int done = changed;
        final int total = manager.getRegistered().size();
        source.sendSuccess(() -> Component.translatable(add
                ? "message.jackofhearts.registered"
                : "message.jackofhearts.unregistered", done, total).withStyle(ChatFormatting.AQUA), true);
        return done;
    }

    /** @param forced peran yang ditampilkan; null berarti diacak. */
    private static int previewShuffle(CommandContext<CommandSourceStack> ctx, int players, int jacks,
                                      @Nullable Role forced)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Role role = forced != null
                ? forced
                : (player.getRandom().nextBoolean() ? Role.JACK : Role.ACE);

        GameManager.get().previewReveal(player, players, jacks, role);

        int shown = Math.min(Math.max(players, 1), GameManager.MAX_LINEUP_CARDS);
        ctx.getSource().sendSuccess(() -> Component.literal(
                        "Preview: " + players + " pemain -> " + shown + " kartu, "
                                + Math.min(jacks, shown) + " Jack, kamu " + role.getId().toUpperCase())
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /**
     * Menembakkan laser eksekusi ke sasaran di luar permainan, lengkap dengan kamera
     * sinematik dan mayat yang ditinggalkan. Arah datangnya sinar mengikuti aturan yang
     * sama dengan game: dari Terminal Tebakan kalau ada blok penanda ruang di bawah
     * kaki sasaran, kalau tidak turun dari langit.
     */
    private static int debugLaser(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                  boolean fires) {
        CommandSourceStack source = ctx.getSource();
        Component error = GameManager.get().debugLaser(source.getServer(), new ArrayList<>(targets), fires);
        if (error != null) {
            source.sendFailure(error);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Pratinjau "
                        + (fires ? "laser" : "gertakan") + ": " + targets.size() + " sasaran.")
                .withStyle(ChatFormatting.AQUA), false);
        return targets.size();
    }

    private static int clearCorpses(CommandContext<CommandSourceStack> ctx) {
        int removed = GameManager.get().clearCorpses(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(removed + " mayat disapu.")
                .withStyle(ChatFormatting.YELLOW), true);
        return removed;
    }

    private static int unregisterAll(CommandContext<CommandSourceStack> ctx) {
        int removed = GameManager.get().clearRegistered();
        GameManager.get().syncRoster(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("message.jackofhearts.unregistered_all", removed)
                .withStyle(ChatFormatting.YELLOW), true);
        return removed;
    }

    private static int listPlayers(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        var roster = GameManager.get().getRegistered();

        if (roster.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.jackofhearts.roster_empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("message.jackofhearts.roster_header", roster.size())
                .withStyle(ChatFormatting.GOLD), false);
        int index = 1;
        for (var entry : roster.entrySet()) {
            boolean online = server.getPlayerList().getPlayer(entry.getKey()) != null;
            final int number = index++;
            source.sendSuccess(() -> Component.literal(" " + number + ". " + entry.getValue())
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(online ? " [online]" : " [offline]")
                            .withStyle(online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)), false);
        }
        return roster.size();
    }

    /**
     * Game selalu memakai roster terdaftar, dan itu satu-satunya jalurnya. Tidak ada
     * bentuk {@code /jack start <targets>}: yang dulu dikerjakannya cuma menimpa isi
     * daftar lalu memulai — persis {@code /jack regis} yang disambung, tapi dengan
     * jebakan menghapus daftar yang sudah disusun tanpa ditanya lebih dulu.
     */
    private static int startDefault(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        GameManager manager = GameManager.get();

        if (manager.getRegistered().isEmpty()) {
            source.sendFailure(Component.translatable("message.jackofhearts.no_registered")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Satu pun yang offline sudah cukup untuk membatalkan. Memulai tanpa dia berarti
        // dia dicoret diam-diam dari daftar yang barusan disusun host - dan itu baru
        // ketahuan setelah peran dibagikan, saat sudah tidak bisa diulang.
        List<String> missing = new ArrayList<>();
        List<ServerPlayer> players = manager.resolveRegistered(source.getServer(), missing);
        if (!missing.isEmpty()) {
            source.sendFailure(Component.translatable("message.jackofhearts.start_needs_online",
                    String.join(", ", missing)).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (players.isEmpty()) {
            source.sendFailure(Component.translatable("message.jackofhearts.roster_empty")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return start(ctx, players);
    }

    private static int start(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        CommandSourceStack source = ctx.getSource();
        GameManager manager = GameManager.get();
        if (manager.isRunning()) {
            source.sendFailure(Component.translatable("message.jackofhearts.already_running"));
            return 0;
        }
        Component error = manager.start(source.getServer(), new ArrayList<>(targets));
        if (error != null) {
            source.sendFailure(error);
            return 0;
        }
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        GameManager.get().stop(ctx.getSource().getServer(), true);
        return 1;
    }

    /** Host: potong sisa waktu fase yang sedang berjalan, tanpa menunggu siapa pun. */
    private static int forceSkip(CommandContext<CommandSourceStack> ctx) {
        GameManager manager = GameManager.get();
        if (!manager.isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("message.jackofhearts.not_running"));
            return 0;
        }
        manager.skipPhase();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.jackofhearts.phase_skipped"), true);
        return 1;
    }

    /**
     * Pemain: satu suara untuk mempercepat ronde ke fase menebak. Perintah ini satu-
     * satunya yang tidak butuh izin host — memang untuk semua peserta.
     */
    private static int voteSkip(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GameManager manager = GameManager.get();
        if (!manager.isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("message.jackofhearts.not_running"));
            return 0;
        }
        Component error = manager.voteSkip(player);
        if (error != null) {
            ctx.getSource().sendFailure(error);
            return 0;
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        GameManager manager = GameManager.get();
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("Fase: " + manager.getPhase()
                + " | Ronde: " + manager.getRound() + "/" + JackConfig.CONFIG.jackWinRound.get()
                + " | Sisa: " + (manager.getTimer() / 20) + "s").withStyle(ChatFormatting.AQUA), false);

        source.sendSuccess(() -> Component.literal("Terdaftar: " + manager.getRegistered().size()
                + (manager.getRegistered().isEmpty() ? " (/jack start akan pakai semua pemain online)"
                        : " -> " + String.join(", ", manager.getRegistered().values())))
                .withStyle(ChatFormatting.GRAY), false);

        var forced = manager.getForcedJacks();
        if (!forced.isEmpty()) {
            String names = forced.values().stream()
                    .map(f -> f.name() + (f.always() ? " (always)" : " (once)"))
                    .collect(java.util.stream.Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal("setjack: " + names
                            + " | Jack dibagikan: " + GameManager.jackCountFor(forced.size()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }

        List<PlayerData> data = new ArrayList<>(manager.getParticipants().values());
        for (PlayerData entry : data) {
            source.sendSuccess(() -> Component.literal(" - " + entry.name)
                    .append(" ")
                    .append(entry.role.getDisplayName())
                    .append(Component.literal(entry.alive ? " [hidup]" : " [mati]")
                            .withStyle(entry.alive ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    /**
     * Debug/host: daftar peran semua peserta, dikelompokkan per peran. Sengaja tidak
     * menampilkan simbol punggung — untuk itu ada {@code /jack reveal}. Memisahkannya
     * berarti host bisa mengecek pembagian peran tanpa ikut membocorkan simbol ke
     * layarnya sendiri kalau kebetulan ada yang menonton.
     */
    private static int debugRole(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        GameManager manager = GameManager.get();
        MinecraftServer server = source.getServer();

        if (manager.getParticipants().isEmpty()) {
            source.sendFailure(Component.literal("Belum ada peserta. Peran baru dibagikan saat /jack start."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Peran peserta (" + manager.getParticipants().size() + "):")
                .withStyle(ChatFormatting.GOLD), false);

        for (Role role : Role.values()) {
            List<PlayerData> group = manager.getParticipants().values().stream()
                    .filter(d -> d.role == role)
                    .toList();
            if (group.isEmpty()) {
                continue;
            }
            long alive = group.stream().filter(d -> d.alive).count();
            source.sendSuccess(() -> Component.empty()
                    .append(role.getDisplayName())
                    .append(Component.literal(" (" + alive + "/" + group.size() + " hidup)")
                            .withStyle(ChatFormatting.GRAY)), false);

            for (PlayerData entry : group) {
                boolean online = server.getPlayerList().getPlayer(entry.uuid) != null;
                String kills = entry.kills > 0 ? "  " + entry.kills + " kill" : "";
                source.sendSuccess(() -> Component.literal("  - " + entry.name)
                        .withStyle(entry.alive ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY)
                        .append(Component.literal(entry.alive ? " [hidup]" : " [mati]")
                                .withStyle(entry.alive ? ChatFormatting.GREEN : ChatFormatting.RED))
                        .append(Component.literal(online ? "" : " [offline]")
                                .withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(kills).withStyle(ChatFormatting.YELLOW)), false);
            }
        }
        return manager.getParticipants().size();
    }

    /** Debug/host: tampilkan peran + simbol semua peserta ke pemanggil saja. */
    private static int reveal(CommandContext<CommandSourceStack> ctx) {
        GameManager manager = GameManager.get();
        MinecraftServer server = ctx.getSource().getServer();
        for (PlayerData entry : manager.getParticipants().values()) {
            ServerPlayer online = server.getPlayerList().getPlayer(entry.uuid);
            ctx.getSource().sendSuccess(() -> Component.literal(entry.name + " -> ")
                    .append(entry.role.getDisplayName())
                    .append(Component.literal(" / "))
                    .append(entry.symbol.getLabel())
                    .append(Component.literal(online == null ? " (offline)" : "")), false);
        }
        return 1;
    }
}
