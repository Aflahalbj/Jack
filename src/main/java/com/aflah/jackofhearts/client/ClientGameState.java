package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.game.GamePhase;
import com.aflah.jackofhearts.game.Role;
import com.aflah.jackofhearts.game.Symbol;
import com.aflah.jackofhearts.network.OpenGuessScreenPacket;
import com.aflah.jackofhearts.network.OpenRoleRevealPacket;
import com.aflah.jackofhearts.network.SyncStatePacket;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

/** Salinan state game di client, cuma untuk HUD, GUI, dan kunci kamera. */
public class ClientGameState {
    public static GamePhase phase = GamePhase.IDLE;
    public static int round;
    public static int ticksLeft;
    public static boolean participating;
    public static boolean alive;
    public static boolean lockView;
    @Nullable
    public static Role role;
    @Nullable
    public static Symbol savedGuess;
    /** Sisa waktu animasi kocok simbol di punggung baju, dalam tick. */
    public static int symbolShuffleTicks;

    /** Lamanya simbol di punggung berganti-ganti setiap ronde baru. */
    private static final int SYMBOL_SHUFFLE_TICKS = 30;

    public static void accept(SyncStatePacket packet) {
        phase = GamePhase.byOrdinal(packet.phase());
        // Ronde baru berarti simbol semua orang baru saja diacak ulang.
        if (packet.round() != round && packet.round() > 0) {
            symbolShuffleTicks = SYMBOL_SHUFFLE_TICKS;
        }
        round = packet.round();
        ticksLeft = packet.ticksLeft();
        participating = packet.participating();
        alive = packet.alive();
        lockView = packet.lockView();
        role = packet.role() >= 0 ? Role.values()[packet.role()] : null;
        savedGuess = Symbol.byOrdinal(packet.savedGuess());
    }

    public static void openRoleRevealScreen(OpenRoleRevealPacket packet) {
        Role revealed = packet.role() >= 0 && packet.role() < Role.values().length
                ? Role.values()[packet.role()]
                : Role.ACE;
        // Preview tidak boleh menyentuh state game; ia cuma menumpang layarnya.
        if (!packet.preview()) {
            role = revealed;
            phase = GamePhase.REVEAL;
            ticksLeft = packet.ticksLeft();
        }
        Minecraft.getInstance().setScreen(
                new RoleRevealScreen(revealed, packet.cardCount(), packet.jackCards(),
                        packet.totalPlayers(), packet.totalJacks(), packet.preview()));
    }

    public static void openGuessScreen(OpenGuessScreenPacket packet) {
        ticksLeft = packet.ticksLeft();
        savedGuess = Symbol.byOrdinal(packet.currentGuess());
        Minecraft.getInstance().setScreen(new GuessScreen(savedGuess));
    }

    /** Hitung mundur lokal di antara paket sinkronisasi supaya angka tidak tersendat. */
    public static void clientTick() {
        if (symbolShuffleTicks > 0) {
            symbolShuffleTicks--;
        }
        if (ticksLeft > 0 && (phase == GamePhase.ROUND || phase == GamePhase.GUESS)) {
            ticksLeft--;
        }
    }

    public static void reset() {
        phase = GamePhase.IDLE;
        round = 0;
        ticksLeft = 0;
        participating = false;
        alive = false;
        lockView = false;
        role = null;
        savedGuess = null;
        symbolShuffleTicks = 0;
    }

    public static int secondsLeft() {
        return (ticksLeft + 19) / 20;
    }
}
