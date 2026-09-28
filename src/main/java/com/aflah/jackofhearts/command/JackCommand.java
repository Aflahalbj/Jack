package com.aflah.jackofhearts.command;

import com.aflah.jackofhearts.config.JackConfig;
import com.aflah.jackofhearts.game.GameManager;
import com.aflah.jackofhearts.game.PlayerData;
import com.aflah.jackofhearts.game.Arena;
import com.aflah.jackofhearts.game.ArenaWorld;
import com.aflah.jackofhearts.game.MilkSpots;
import com.aflah.jackofhearts.game.MilkSupply;
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
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraft.server.level.ServerLevel;
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
                                                IntegerArgumentType.getInteger(ctx, "jacks"), Role.ACE)))
                                .then(Commands.literal("as_unknown")
                                        .executes(ctx -> previewShuffle(ctx,
                                                IntegerArgumentType.getInteger(ctx, "players"),
                                                IntegerArgumentType.getInteger(ctx, "jacks"), Role.UNKNOWN))))));

        // Setelan host: siapa yang dipaksa jadi Jack, dan berapa Jack yang dibagikan.
        root.then(host("setting")
                // Satu cabang per peran: jumlahnya dan siapa yang ditunjuk ada di tempat
                // yang sama, jadi yang dicari host cuma nama perannya.
                .then(forcedBranches(Commands.literal("jack"), Role.JACK)
                        .executes(JackCommand::showSettings)
                        .then(Commands.literal("max")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                        .executes(ctx -> setMaxJack(ctx,
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(forcedBranches(Commands.literal("unknown"), Role.UNKNOWN)
                        .executes(JackCommand::showSettings)
                        .then(Commands.literal("on").executes(ctx -> setUnknownEnabled(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setUnknownEnabled(ctx, false)))
                        .then(Commands.literal("max")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                        .executes(ctx -> setUnknownMax(ctx,
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
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
                // Koordinat schematic disetel lewat perintah, bukan lewat berkas config:
                // yang mengetiknya sedang berdiri di ruangan itu, dan "~ ~ ~" jauh lebih
                // sulit salah daripada tiga angka yang diketik dari ingatan.
                .then(Commands.literal("foodschem")
                        .executes(JackCommand::showSettings)
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> setSchematicOrigin(ctx,
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                // Dua setelan dimensi, dan keduanya gampang ketinggalan menunjuk dunia yang
                // salah. Disetel dari tempat berdiri, bukan diketik ke berkas config:
                // yang menjalankannya sedang melihat benda yang dimaksud dengan matanya
                // sendiri.
                .then(Commands.literal("area")
                        .executes(JackCommand::showSettings)
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> setArea(ctx,
                                                BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "to"))))))
                .then(Commands.literal("arenahere").executes(JackCommand::setArenaHere))
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
                .then(skillSetting("drunk", JackConfig.CONFIG.skillDrunk, JackConfig.CONFIG.skillDrunkMax))
                .then(skillSetting("hunger", JackConfig.CONFIG.skillHunger, JackConfig.CONFIG.skillHungerMax))
                .then(skillSetting("poison", JackConfig.CONFIG.skillPoison, JackConfig.CONFIG.skillPoisonMax))
                .then(Commands.literal("milk")
                        .executes(JackCommand::showSettings)
                        .then(Commands.literal("max")
                                .then(Commands.argument("count", IntegerArgumentType.integer(0, 1000))
                                        .executes(ctx -> setMilkMax(ctx,
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("list").executes(JackCommand::showSettings)));

        root.then(host("cutscene")
                .then(Commands.literal("on").executes(ctx -> setCutscene(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> setCutscene(ctx, false)))
                .then(Commands.literal("play")
                        .executes(ctx -> playCutscene(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> playCutscene(ctx, EntityArgument.getPlayers(ctx, "targets"))))));
        root.then(host("refill").executes(JackCommand::refill));
        root.then(host("gotoarena").executes(ctx -> goTo(ctx, true)));
        root.then(host("gotooverworld").executes(ctx -> goTo(ctx, false)));
        root.then(host("pastearena").executes(JackCommand::pasteArena));
        root.then(host("arena")
                .then(Commands.literal("export").executes(JackCommand::exportArena))
                .then(Commands.literal("install")
                        .executes(ctx -> installArena(ctx, false))
                        .then(Commands.literal("force").executes(ctx -> installArena(ctx, true)))));
        root.then(host("milkspots")
                .then(Commands.literal("export").executes(ctx -> milkSpotsFile(ctx, true)))
                .then(Commands.literal("import").executes(ctx -> milkSpotsFile(ctx, false)))
                .then(Commands.literal("clear").executes(JackCommand::clearMilkSpots))
                .then(milkType("chest", JackConfig.CONFIG.milkChest))
                .then(milkType("furnace", JackConfig.CONFIG.milkFurnace))
                .then(milkType("dispenser", JackConfig.CONFIG.milkDispenser))
                .then(milkType("hopper", JackConfig.CONFIG.milkHopper))
                .then(milkType("barrel", JackConfig.CONFIG.milkBarrel)));
        root.then(host("milkreveal").executes(JackCommand::milkReveal));
        root.then(host("checkblock")
                .executes(ctx -> setChecking(ctx, !MilkSupply.isChecking()))
                .then(Commands.argument("show", BoolArgumentType.bool())
                        .executes(ctx -> setChecking(ctx, BoolArgumentType.getBool(ctx, "show")))));
        root.then(host("stop").executes(JackCommand::stop));
        root.then(host("status").executes(JackCommand::status));
        // Satu-satunya cabang tanpa izin host: pemungutan suara mempercepat ronde.
        root.then(Commands.literal("skip").executes(JackCommand::voteSkip));
        root.then(host("forceskip").executes(JackCommand::forceSkip));
        root.then(host("reveal").executes(JackCommand::reveal));

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
     * Cabang penunjukan satu peran: {@code set <pemain> [once|always]},
     * {@code unset <pemain>}, dan {@code clear}.
     *
     * <p>Jack dan Unknown berbentuk sama persis, jadi dibangun satu kali di sini.
     * Tanpa once/always berarti once: penunjukan yang berlaku selamanya adalah yang
     * paling gampang dilupakan, jadi itu yang harus diketik.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> forcedBranches(
            LiteralArgumentBuilder<CommandSourceStack> branch, Role role) {
        return branch
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> setForced(ctx, role, EntityArgument.getPlayers(ctx, "targets"), false))
                                .then(Commands.literal("once")
                                        .executes(ctx -> setForced(ctx, role,
                                                EntityArgument.getPlayers(ctx, "targets"), false)))
                                .then(Commands.literal("always")
                                        .executes(ctx -> setForced(ctx, role,
                                                EntityArgument.getPlayers(ctx, "targets"), true)))))
                .then(Commands.literal("unset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> unsetForced(ctx, role, EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("clear").executes(ctx -> clearForced(ctx, role)));
    }

    private static String roleName(Role role) {
        return role == Role.UNKNOWN ? "Unknown" : "Jack";
    }

    /**
     * Menunjuk pemain yang dipaksa dapat satu peran di game berikutnya.
     *
     * <p>Balasannya cuma ke pemanggil, dan sasarannya sendiri tidak diberi tahu
     * apa-apa. Peran baru boleh diketahui pemiliknya lewat animasi pembagian peran,
     * dan pesan "kamu dipilih jadi Jack" di sini akan mendahuluinya — sekaligus
     * membocorkan ke siapa pun yang kebetulan melihat layarnya.</p>
     */
    private static int setForced(CommandContext<CommandSourceStack> ctx, Role role,
                                 Collection<ServerPlayer> targets, boolean always) {
        GameManager manager = GameManager.get();
        int changed = 0;
        for (ServerPlayer player : targets) {
            if (manager.setForced(role, player, always)) {
                changed++;
            }
        }
        final int done = changed;
        ctx.getSource().sendSuccess(() -> Component.literal("Ditunjuk jadi " + roleName(role) + " "
                        + (always ? "selamanya" : "untuk satu game") + ": " + done + " pemain.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (role == Role.UNKNOWN && !JackConfig.CONFIG.unknownRole.get()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                            "Peran Unknown sedang OFF, tapi yang ditunjuk tetap jadi Unknown.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        reportJackSettings(ctx.getSource());
        return changed;
    }

    private static int unsetForced(CommandContext<CommandSourceStack> ctx, Role role,
                                   Collection<ServerPlayer> targets) {
        GameManager manager = GameManager.get();
        int changed = 0;
        for (ServerPlayer player : targets) {
            if (manager.unsetForced(role, player)) {
                changed++;
            }
        }
        final int done = changed;
        ctx.getSource().sendSuccess(() -> Component.literal("Penunjukan " + roleName(role)
                        + " dibatalkan: " + done + " pemain.")
                .withStyle(ChatFormatting.YELLOW), false);
        reportJackSettings(ctx.getSource());
        return changed;
    }

    private static int clearForced(CommandContext<CommandSourceStack> ctx, Role role) {
        int removed = GameManager.get().clearForced(role);
        ctx.getSource().sendSuccess(() -> Component.literal("Daftar penunjukan " + roleName(role)
                + " dikosongkan (" + removed + " pemain).").withStyle(ChatFormatting.YELLOW), false);
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
        ctx.getSource().sendSuccess(() -> Component.literal("Jack max disetel ke " + count + ".")
                .withStyle(ChatFormatting.AQUA), true);
        reportJackSettings(ctx.getSource());
        return count;
    }

    /**
     * Cabang setelan satu skill: {@code on}, {@code off}, dan {@code max <jumlah>}.
     *
     * <p>Ketiganya berbentuk sama persis, jadi dibangun satu kali di sini. Menyalinnya
     * tiga kali berarti perbedaan kecil di antaranya (batas angka, urutan, kata) bisa
     * menyelinap masuk tanpa ada yang memutuskannya.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> skillSetting(
            String name, ForgeConfigSpec.BooleanValue enabled, ForgeConfigSpec.IntValue max) {
        return Commands.literal(name)
                .executes(JackCommand::showSettings)
                .then(Commands.literal("on").executes(ctx -> setSkillEnabled(ctx, name, enabled, true)))
                .then(Commands.literal("off").executes(ctx -> setSkillEnabled(ctx, name, enabled, false)))
                .then(Commands.literal("max")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 64))
                                .executes(ctx -> setSkillMax(ctx, name, max,
                                        IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static int setSkillEnabled(CommandContext<CommandSourceStack> ctx, String name,
                                       ForgeConfigSpec.BooleanValue value, boolean enabled) {
        value.set(enabled);
        value.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Skill " + name + ": "
                        + (enabled ? "ON" : "OFF") + ". Berlaku mulai game berikutnya.")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return enabled ? 1 : 0;
    }

    private static int setSkillMax(CommandContext<CommandSourceStack> ctx, String name,
                                   ForgeConfigSpec.IntValue value, int count) {
        value.set(count);
        value.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Skill " + name + " max: " + count
                        + " per game. Berlaku mulai game berikutnya.")
                .withStyle(ChatFormatting.AQUA), true);
        return count;
    }

    /**
     * Peran Unknown: dibagikan atau tidak.
     *
     * <p>Bentuknya sengaja sama dengan setelan skill ({@code on}, {@code off},
     * {@code max}), tapi kalimat balasannya milik sendiri: ini peran, bukan skill, dan
     * host yang membaca "Skill unknown" akan mencarinya di panel Jack.</p>
     */
    private static int setUnknownEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        JackConfig.CONFIG.unknownRole.set(enabled);
        JackConfig.CONFIG.unknownRole.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Peran Unknown: " + (enabled ? "ON" : "OFF")
                        + (enabled ? " (" + JackConfig.CONFIG.unknownMax.get() + " pemain)" : "")
                        + ". Berlaku mulai game berikutnya.")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return enabled ? 1 : 0;
    }

    private static int setUnknownMax(CommandContext<CommandSourceStack> ctx, int count) {
        JackConfig.CONFIG.unknownMax.set(count);
        JackConfig.CONFIG.unknownMax.save();
        boolean enabled = JackConfig.CONFIG.unknownRole.get();
        ctx.getSource().sendSuccess(() -> Component.literal("Unknown max: " + count
                        + " pemain. Berlaku mulai game berikutnya."
                        + (enabled ? "" : " Perannya masih OFF - nyalakan lewat /jack setting unknown on."))
                .withStyle(enabled ? ChatFormatting.AQUA : ChatFormatting.YELLOW), true);
        return count;
    }

    private static int setCutscene(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        JackConfig.CONFIG.cutscene.set(enabled);
        JackConfig.CONFIG.cutscene.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Cutscene pembuka: " + (enabled ? "ON" : "OFF")
                        + ". Berlaku mulai /jack start berikutnya.")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return enabled ? 1 : 0;
    }

    /**
     * Pratinjau cutscene tanpa memulai game. Selesainya mengembalikan semua penonton ke
     * tempat dan mode permainan asalnya.
     *
     * <p>Ditolak selama game berjalan: cutscene meminjam badan penontonnya, dan badan
     * peserta yang dipinjam di tengah ronde tidak bisa dikembalikan tanpa merusak
     * rondenya.</p>
     */
    private static int playCutscene(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        GameManager manager = GameManager.get();
        if (manager.isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("message.jackofhearts.already_running"));
            return 0;
        }
        Component error = manager.playCutscene(ctx.getSource().getServer(), targets, false);
        if (error != null) {
            ctx.getSource().sendFailure(error);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Cutscene diputar untuk " + targets.size()
                + " pemain.").withStyle(ChatFormatting.AQUA), false);
        return targets.size();
    }

    private static int setMilkMax(CommandContext<CommandSourceStack> ctx, int count) {
        JackConfig.CONFIG.milkMax.set(count);
        JackConfig.CONFIG.milkMax.save();
        int rounds = JackConfig.CONFIG.jackWinRound.get();
        ctx.getSource().sendSuccess(() -> Component.literal("Susu: " + count + " sepanjang game, dibagi ke "
                        + rounds + " ronde.").withStyle(ChatFormatting.AQUA), true);
        return count;
    }

    /** Pindah antara arena dan overworld. Alat penyiapan map, bukan bagian permainan. */
    private static int goTo(CommandContext<CommandSourceStack> ctx, boolean arena)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (arena) {
            if (!Arena.send(player)) {
                ctx.getSource().sendFailure(Component.literal("Dimensi arena '"
                                + JackConfig.CONFIG.arenaDimension.get() + "' tidak ada.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Selamat datang di arena.")
                    .withStyle(ChatFormatting.AQUA), false);
            return 1;
        }
        ServerLevel overworld = ctx.getSource().getServer().overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        ctx.getSource().sendSuccess(() -> Component.literal("Kembali ke overworld.")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    /**
     * Memasang map arena bawaan mod ke dunia ini.
     *
     * <p>Biasanya tidak perlu dipanggil: pemasangannya jalan sendiri saat server menyala,
     * selama dimensi arenanya masih kosong. Yang butuh perintah ini adalah dunia yang
     * arenanya telanjur dikunjungi sekali — sekali kunjungan sudah membuat berkas region
     * berisi kekosongan, dan kekosongan itu terhitung sebagai "sudah ada isinya".</p>
     */
    private static int installArena(CommandContext<CommandSourceStack> ctx, boolean force) {
        CommandSourceStack source = ctx.getSource();
        int copied = ArenaWorld.install(source.getServer(), force);
        source.sendSuccess(() -> Component.literal(copied > 0
                        ? copied + " berkas dipasang. Restart server supaya chunk-nya dibaca ulang."
                        : "Tidak ada yang dipasang: dimensi arenanya sudah punya isi, atau mod tidak "
                                + "membawa map bawaan. Pakai /jack arena install force untuk menimpanya.")
                .withStyle(copied > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW), true);
        return copied;
    }

    /**
     * Menyalin dimensi arena yang sekarang ke config, siap dibungkus ke dalam mod.
     *
     * <p>Inilah cara membungkus map: bangun arenanya seperti biasa, jalankan perintah
     * ini, lalu pindahkan folder hasilnya ke folder sumber mod. Yang disalin berkas
     * region apa adanya - chunk yang sudah jadi lengkap dengan cahaya dan block
     * entity-nya, bukan daftar blok yang harus dipasang ulang satu per satu.</p>
     */
    private static int exportArena(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int copied = ArenaWorld.export(source.getServer());
        if (copied < 0) {
            source.sendFailure(Component.literal("Dimensi arena '"
                            + JackConfig.CONFIG.arenaDimension.get() + "' tidak ada.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(copied
                        + " berkas disalin ke config/jackofhearts/arena_world/. "
                        + "Pindahkan isinya ke src/main/resources/data/jackofhearts/arena_world/ lalu build ulang.")
                .withStyle(ChatFormatting.AQUA), true);
        return copied;
    }

    /**
     * Memasang seluruh map arena dari schematic.
     *
     * <p>Dijalankan sekali saat menyiapkan dunia, bukan tiap permainan: isinya bisa
     * jutaan blok, dan pemasangannya berjalan di satu tick.</p>
     */
    private static int pasteArena(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int placed = Arena.paste(source.getServer());
        if (placed < 0) {
            source.sendFailure(Component.literal(
                            "Gagal memasang arena. Cek log server; berkasnya dicari di config/jackofhearts/"
                                    + JackConfig.CONFIG.arenaSchematic.get() + " lalu di dalam mod.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Arena dipasang: " + placed + " blok di "
                        + Arena.arenaOrigin().toShortString() + ".")
                .withStyle(ChatFormatting.AQUA), true);
        return placed;
    }

    /**
     * Cabang penyalaan satu jenis wadah.
     *
     * <p>Mematikan satu jenis <b>tidak</b> menghapus titik yang sudah ditandai — titiknya
     * cuma dilewati saat susu dibagikan. Menyalakannya lagi mengembalikan semuanya apa
     * adanya, tanpa perlu ditandai ulang.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> milkType(
            String name, ForgeConfigSpec.BooleanValue value) {
        return Commands.literal(name)
                .then(Commands.argument("allowed", BoolArgumentType.bool())
                        .executes(ctx -> setMilkType(ctx, name, value,
                                BoolArgumentType.getBool(ctx, "allowed"))));
    }

    private static int setMilkType(CommandContext<CommandSourceStack> ctx, String name,
                                   ForgeConfigSpec.BooleanValue value, boolean allowed) {
        value.set(allowed);
        value.save();
        ctx.getSource().sendSuccess(() -> Component.literal(name + " sebagai tempat susu: "
                        + (allowed ? "ON" : "OFF")
                        + (allowed ? "" : ". Titik yang sudah ditandai tetap tersimpan, cuma dilewati."))
                .withStyle(allowed ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return allowed ? 1 : 0;
    }

    /**
     * Mengosongkan seluruh daftar wadah susu.
     *
     * <p>Ada karena daftarnya bisa jadi salah dengan cara yang tidak kelihatan: titik yang
     * menunjuk dimensi yang tidak ada tetap terhitung terdaftar, tapi tidak pernah bisa
     * diisi apa pun. Menandai ulang dari nol seringkali lebih cepat daripada mencari satu
     * per satu mana yang sudah tidak berlaku.</p>
     */
    private static int clearMilkSpots(CommandContext<CommandSourceStack> ctx) {
        int removed = MilkSpots.get(ctx.getSource().getServer()).clear();
        ctx.getSource().sendSuccess(() -> Component.literal("Daftar wadah susu dikosongkan ("
                        + removed + " titik).").withStyle(ChatFormatting.YELLOW), true);
        return removed;
    }

    /**
     * Menyimpan atau memuat daftar wadah susu sebagai berkas.
     *
     * <p>Daftar wadah tersimpan di berkas dunia, sementara map-nya dibungkus ke dalam mod
     * sebagai schematic — dua tempat yang berbeda. Tanpa jembatan ini, map yang dipasang
     * di dunia baru akan datang tanpa satu pun wadah susu, dan semuanya harus ditandai
     * ulang dengan tangan.</p>
     */
    private static int milkSpotsFile(CommandContext<CommandSourceStack> ctx, boolean export) {
        CommandSourceStack source = ctx.getSource();
        int count = export
                ? MilkSpots.get(source.getServer()).export()
                : MilkSpots.get(source.getServer())
                        .importFile(source.getServer(), source.getLevel().dimension());
        if (count < 0) {
            source.sendFailure(Component.literal(export
                            ? "Gagal menulis berkas. Cek log server."
                            : "Berkas daftar wadah tidak ada, baik di config/jackofhearts/ maupun di dalam mod.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(export
                        ? count + " wadah ditulis ke config/jackofhearts/" + MilkSpots.FILE
                        : count + " wadah dimuat ke dimensi "
                                + source.getLevel().dimension().location() + ".")
                .withStyle(ChatFormatting.AQUA), true);
        return count;
    }

    /**
     * Menyalakan percikan penanda di atas tiap wadah terdaftar.
     *
     * <p>Bloknya sengaja tidak diganti jadi sesuatu yang mencolok: mengganti blok berarti
     * menyimpan blok aslinya dan mengembalikannya nanti, dan satu kegagalan di jalur itu
     * (server mati di tengah pemeriksaan) meninggalkan map yang rusak. Percikan tidak
     * pernah bisa merusak apa pun.</p>
     */
    private static int setChecking(CommandContext<CommandSourceStack> ctx, boolean show) {
        MilkSupply.setChecking(show);
        CommandSourceStack source = ctx.getSource();
        int count = MilkSpots.get(source.getServer()).size();
        source.sendSuccess(() -> Component.literal(show
                        ? "Penanda wadah: ON. " + count + " wadah terdaftar akan berpercik hijau."
                        : "Penanda wadah: OFF.")
                .withStyle(show ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);

        // Isi kolam undiannya ikut dilaporkan: itu yang menjelaskan kenapa susu bisa
        // terasa menumpuk di satu jenis wadah.
        int[] pool = MilkSupply.poolReport(source.getServer());
        source.sendSuccess(() -> Component.literal("Terdaftar: " + pool[0] + " tong, " + pool[1]
                        + " lainnya | siap diisi: " + pool[2] + " tong, " + pool[3] + " lainnya")
                .withStyle(ChatFormatting.GRAY), false);
        if (pool[4] > 0) {
            source.sendSuccess(() -> Component.literal(pool[4]
                            + " wadah ada di dimensi yang tidak dimuat atau tidak ada. "
                            + "Berdirilah di dekatnya, atau daftarkan ulang lewat "
                            + "/jack milkspots import sambil berdiri di dimensi map-nya.")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else if (pool[3] == 0 && pool[2] > 0) {
            source.sendSuccess(() -> Component.literal(
                            "Tidak ada wadah non-tong yang punya slot kosong, jadi semua susu jatuh ke tong.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return count;
    }

    /**
     * Di mana saja susu yang belum diambil sekarang berada.
     *
     * <p>Alat host, bukan alat pemain: yang menjalankannya sedang memeriksa apakah
     * susunya mendarat di tempat yang masuk akal — bukan sedang mencarinya untuk
     * diminum.</p>
     */
    private static int milkReveal(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<BlockPos> found = MilkSupply.findMilk(source.getServer());
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Tidak ada susu yang tersisa di wadah mana pun.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Susu tersisa (" + found.size() + "):")
                .withStyle(ChatFormatting.GOLD), false);
        for (BlockPos pos : found) {
            ServerLevel level = source.getLevel();
            String block = level.getBlockState(pos).getBlock().getName().getString();
            source.sendSuccess(() -> Component.literal(" " + block + " di " + pos.toShortString())
                    .withStyle(ChatFormatting.WHITE), false);
        }
        return found.size();
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

    /**
     * Kotak kafetaria, sekaligus dimensinya.
     *
     * <p>Dimensinya diambil dari tempat perintahnya dijalankan, bukan diketik terpisah.
     * Kotak dan dimensinya adalah satu keterangan yang sama — "meja itu ada di sini" —
     * dan memisahkannya jadi dua setelan cuma memberi satu kesempatan lagi untuk salah.</p>
     */
    private static int setArea(CommandContext<CommandSourceStack> ctx, BlockPos from, BlockPos to) {
        CommandSourceStack source = ctx.getSource();
        String dimension = source.getLevel().dimension().location().toString();
        JackConfig.CONFIG.areaDimension.set(dimension);
        JackConfig.CONFIG.areaDimension.save();
        JackConfig.CONFIG.areaFrom.set(from.getX() + " " + from.getY() + " " + from.getZ());
        JackConfig.CONFIG.areaFrom.save();
        JackConfig.CONFIG.areaTo.set(to.getX() + " " + to.getY() + " " + to.getZ());
        JackConfig.CONFIG.areaTo.save();
        source.sendSuccess(() -> Component.literal("Kotak kafetaria: " + from.toShortString()
                        + " sampai " + to.toShortString() + " di " + dimension)
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    /**
     * Dimensi arena dan titik kumpulnya, diambil dari tempat berdiri.
     *
     * <p>Ini yang menjawab "kok kafetarianya kosong padahal mejanya jelas ada": mesin
     * permainan sedang melihat dunia yang berbeda dengan yang sedang kamu tempati.</p>
     */
    private static int setArenaHere(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String dimension = player.level().dimension().location().toString();
        BlockPos pos = player.blockPosition();
        JackConfig.CONFIG.arenaDimension.set(dimension);
        JackConfig.CONFIG.arenaDimension.save();
        JackConfig.CONFIG.arenaSpawn.set(pos.getX() + " " + pos.getY() + " " + pos.getZ());
        JackConfig.CONFIG.arenaSpawn.save();
        source.sendSuccess(() -> Component.literal("Arena: " + dimension + ", titik kumpul "
                        + pos.toShortString()).withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    /**
     * Titik mendaratnya schematic meja: sudut dengan koordinat paling kecil.
     *
     * <p>Blok pertama di dalam schematic mendarat tepat di sini. Kalau hasilnya meleset,
     * geser titik ini sebanyak selisihnya — batas hasil pemasangan terakhir dicatat di
     * log server, jadi selisihnya tidak perlu ditebak.</p>
     */
    private static int setSchematicOrigin(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        String value = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        JackConfig.CONFIG.foodSchematicOrigin.set(value);
        JackConfig.CONFIG.foodSchematicOrigin.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Sudut schematic makanan: " + value
                        + ". Pakai /jack refill untuk memasangnya sekarang.")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int showSettings(CommandContext<CommandSourceStack> ctx) {
        reportJackSettings(ctx.getSource());
        CommandSourceStack source = ctx.getSource();
        int fixed = JackConfig.CONFIG.foodTotal.get();
        String food = fixed > 0
                ? fixed + " porsi (pasti)"
                : "default, " + JackConfig.CONFIG.foodPerPlayer.get() + " x jumlah pemain";
        source.sendSuccess(() -> Component.literal("skill: drunk "
                        + skillLabel(JackConfig.CONFIG.skillDrunk, JackConfig.CONFIG.skillDrunkMax)
                        + " | hunger "
                        + skillLabel(JackConfig.CONFIG.skillHunger, JackConfig.CONFIG.skillHungerMax)
                        + " | poison "
                        + skillLabel(JackConfig.CONFIG.skillPoison, JackConfig.CONFIG.skillPoisonMax)
                        + " | milk " + JackConfig.CONFIG.milkMax.get()
                        + " | cutscene " + (JackConfig.CONFIG.cutscene.get() ? "on" : "off"))
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        reportDimensions(source);
        source.sendSuccess(() -> Component.literal("foodschem: "
                        + JackConfig.CONFIG.foodSchematic.get() + " di "
                        + JackConfig.CONFIG.foodSchematicOrigin.get())
                .withStyle(ChatFormatting.AQUA), false);
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

    /**
     * Dua setelan dimensi, beserta ada-tidaknya dunia yang ditunjuk.
     *
     * <p>Inilah keterangan yang paling sering jadi jawaban saat sesuatu "kosong padahal
     * jelas ada": mesin permainan menunjuk dunia yang berbeda dengan yang ditempati.
     * Ditandai juga dimensi yang sedang kamu tempati sendiri, supaya perbandingannya
     * tidak perlu diingat-ingat.</p>
     */
    private static void reportDimensions(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        String arena = JackConfig.CONFIG.arenaDimension.get();
        String area = JackConfig.CONFIG.areaDimension.get();
        String here = source.getLevel().dimension().location().toString();

        source.sendSuccess(() -> Component.literal("arena: " + arena + dimensionMark(server, arena)
                        + " | kafetaria: " + area + dimensionMark(server, area)
                        + " | kamu di: " + here)
                .withStyle(ChatFormatting.AQUA), false);
        if (!arena.equals(area)) {
            source.sendSuccess(() -> Component.literal(
                            "Arena dan kafetaria di dimensi yang berbeda. Biasanya keduanya harus sama.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static String dimensionMark(MinecraftServer server, String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        boolean exists = parsed != null
                && server.getLevel(ResourceKey.create(Registries.DIMENSION, parsed)) != null;
        return exists ? "" : " (TIDAK ADA)";
    }

    private static String skillLabel(ForgeConfigSpec.BooleanValue enabled, ForgeConfigSpec.IntValue max) {
        return enabled.get() ? "on/" + max.get() : "off";
    }

    /** Setelan Jack dan Unknown apa adanya, termasuk angka yang benar-benar akan dipakai nanti. */
    private static void reportJackSettings(CommandSourceStack source) {
        GameManager manager = GameManager.get();
        var jacks = manager.getForced(Role.JACK);
        var unknowns = manager.getForced(Role.UNKNOWN);
        int max = JackConfig.CONFIG.jackCount.get();
        int effective = GameManager.jackCountFor(jacks.size());

        source.sendSuccess(() -> Component.literal("jack: max " + max
                        + (effective > max ? " -> dipakai " + effective + " (ngikut jumlah yang ditunjuk)" : "")
                        + " | ditunjuk (" + jacks.size() + "): " + forcedNames(jacks))
                .withStyle(effective > max ? ChatFormatting.YELLOW : ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("unknown: "
                        + skillLabel(JackConfig.CONFIG.unknownRole, JackConfig.CONFIG.unknownMax)
                        + " | ditunjuk (" + unknowns.size() + "): " + forcedNames(unknowns))
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (!jacks.isEmpty() || !unknowns.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                            "Yang ditunjuk pasti dapat perannya; sisa kursinya diacak seperti biasa. "
                                    + "Penunjukan (once) hangus begitu /jack start dijalankan.")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
    }

    private static String forcedNames(java.util.Map<java.util.UUID, GameManager.Forced> forced) {
        if (forced.isEmpty()) {
            return "-";
        }
        return forced.values().stream()
                .map(f -> f.name() + (f.always() ? " (always)" : " (once)"))
                .collect(java.util.stream.Collectors.joining(", "));
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
        if (manager.isRunning() || manager.isCutscenePlaying()) {
            source.sendFailure(Component.translatable("message.jackofhearts.already_running"));
            return 0;
        }
        Component error = manager.startWithCutscene(source.getServer(), new ArrayList<>(targets));
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

        var jacks = manager.getForced(Role.JACK);
        if (!jacks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("jack set: " + forcedNames(jacks)
                            + " | Jack dibagikan: " + GameManager.jackCountFor(jacks.size()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        var unknowns = manager.getForced(Role.UNKNOWN);
        if (!unknowns.isEmpty()) {
            source.sendSuccess(() -> Component.literal("unknown set: " + forcedNames(unknowns))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }

        List<PlayerData> data = new ArrayList<>(manager.getParticipants().values());
        for (PlayerData entry : data) {
            source.sendSuccess(() -> Component.literal(" - " + entry.name)
                    .append(" ")
                    .append(entry.roleLabel())
                    .append(Component.literal(entry.alive ? " [hidup]" : " [mati]")
                            .withStyle(entry.alive ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    /** Debug/host: tampilkan peran + simbol semua peserta ke pemanggil saja. */
    private static int reveal(CommandContext<CommandSourceStack> ctx) {
        GameManager manager = GameManager.get();
        MinecraftServer server = ctx.getSource().getServer();
        for (PlayerData entry : manager.getParticipants().values()) {
            ServerPlayer online = server.getPlayerList().getPlayer(entry.uuid);
            ctx.getSource().sendSuccess(() -> Component.literal(entry.name + " -> ")
                    .append(entry.roleLabel())
                    .append(Component.literal(" / "))
                    .append(entry.symbol.getLabel())
                    .append(Component.literal(online == null ? " (offline)" : "")), false);
        }
        return 1;
    }
}
