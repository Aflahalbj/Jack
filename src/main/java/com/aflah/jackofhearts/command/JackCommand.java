package com.aflah.jackofhearts.command;

import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.game.GameManager;
import com.aflah.jackofhearts.game.PlayerData;
import com.aflah.jackofhearts.game.Role;
import com.mojang.brigadier.CommandDispatcher;
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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("jack")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("start")
                .executes(JackCommand::startDefault)
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(JackCommand::startWithTargets)));

        root.then(Commands.literal("regis")
                .executes(ctx -> setRegistered(ctx, List.of(ctx.getSource().getPlayerOrException()), true))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> setRegistered(ctx, EntityArgument.getPlayers(ctx, "targets"), true))));

        root.then(Commands.literal("regisall")
                .executes(ctx -> setRegistered(ctx, ctx.getSource().getServer().getPlayerList().getPlayers(), true)));

        root.then(Commands.literal("unregis")
                .executes(ctx -> setRegistered(ctx, List.of(ctx.getSource().getPlayerOrException()), false))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> setRegistered(ctx, EntityArgument.getPlayers(ctx, "targets"), false))));

        root.then(Commands.literal("unregisall").executes(JackCommand::unregisterAll));
        root.then(Commands.literal("listplayer").executes(JackCommand::listPlayers));

        // Preview animasi buat menyetel tampilan tanpa harus memulai game.
        root.then(Commands.literal("shuffle")
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

        root.then(Commands.literal("stop").executes(JackCommand::stop));
        root.then(Commands.literal("status").executes(JackCommand::status));
        root.then(Commands.literal("skip").executes(JackCommand::skip));
        root.then(Commands.literal("setroom").executes(JackCommand::setRoom));
        root.then(Commands.literal("reveal").executes(JackCommand::reveal));
        root.then(Commands.literal("debugrole").executes(JackCommand::debugRole));

        // Pratinjau eksekusi tanpa harus menyusun roster dan menunggu satu ronde penuh.
        root.then(Commands.literal("debug")
                .then(Commands.literal("laser")
                        .executes(ctx -> debugLaser(ctx, List.of(ctx.getSource().getPlayerOrException()), true))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> debugLaser(ctx, EntityArgument.getPlayers(ctx, "targets"), true))))
                // Versi gertakannya: yang didapat orang yang tebakannya benar.
                .then(Commands.literal("safe")
                        .executes(ctx -> debugLaser(ctx, List.of(ctx.getSource().getPlayerOrException()), false))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> debugLaser(ctx, EntityArgument.getPlayers(ctx, "targets"), false))))
                .then(Commands.literal("clearcorpse").executes(JackCommand::clearCorpses)));

        dispatcher.register(root);
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

    /** {@code /jack start <targets>} mengganti isi roster, lalu memulai seperti biasa. */
    private static int startWithTargets(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        GameManager manager = GameManager.get();
        if (manager.isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("message.jackofhearts.already_running"));
            return 0;
        }
        manager.clearRegistered();
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
            manager.register(player);
        }
        return startDefault(ctx);
    }

    /**
     * Game selalu memakai roster terdaftar. Tidak ada lagi jalur "pakai semua pemain
     * online": kalau roster kosong, perintahnya gagal supaya tidak ada yang ikut main
     * tanpa sengaja hanya karena kebetulan sedang online.
     */
    private static int startDefault(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        GameManager manager = GameManager.get();

        if (manager.getRegistered().isEmpty()) {
            source.sendFailure(Component.translatable("message.jackofhearts.no_registered")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        List<String> missing = new ArrayList<>();
        List<ServerPlayer> players = manager.resolveRegistered(source.getServer(), missing);
        if (!missing.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.jackofhearts.registered_offline",
                    String.join(", ", missing)).withStyle(ChatFormatting.YELLOW), false);
        }
        if (players.isEmpty()) {
            source.sendFailure(Component.translatable("message.jackofhearts.roster_all_offline")
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

    private static int skip(CommandContext<CommandSourceStack> ctx) {
        GameManager manager = GameManager.get();
        if (!manager.isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("message.jackofhearts.not_running"));
            return 0;
        }
        manager.skipPhase();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.jackofhearts.phase_skipped"), true);
        return 1;
    }

    private static int setRoom(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GameManager.get().setGuessRoom(player);
        BlockPos pos = player.blockPosition();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.jackofhearts.room_set",
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GREEN), true);
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
