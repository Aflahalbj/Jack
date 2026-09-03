package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.game.GamePhase;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** HUD kecil di kiri atas: ronde, sisa waktu, peran, dan status tebakan. */
public class JackHudOverlay implements IGuiOverlay {
    public static final JackHudOverlay INSTANCE = new JackHudOverlay();

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || !ClientGameState.participating) {
            return;
        }
        if (ClientGameState.phase == GamePhase.IDLE) {
            return;
        }

        int x = 6;
        int y = 6;
        int line = minecraft.font.lineHeight + 2;

        Component header = Component.translatable("hud.jackofhearts.round", ClientGameState.round)
                .withStyle(ChatFormatting.GOLD);
        graphics.drawString(minecraft.font, header, x, y, 0xFFFFFF, true);
        y += line;

        if (ClientGameState.role != null) {
            graphics.drawString(minecraft.font,
                    Component.translatable("hud.jackofhearts.role", ClientGameState.role.getDisplayName()),
                    x, y, 0xFFFFFF, true);
            y += line;
        }

        int seconds = ClientGameState.secondsLeft();
        if (ClientGameState.phase == GamePhase.GUESS) {
            graphics.drawString(minecraft.font,
                    Component.translatable("hud.jackofhearts.guess_time", seconds)
                            .withStyle(seconds <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW),
                    x, y, 0xFFFFFF, true);
            y += line;

            Component saved = ClientGameState.savedGuess == null
                    ? Component.translatable("hud.jackofhearts.no_guess").withStyle(ChatFormatting.RED)
                    : Component.translatable("hud.jackofhearts.saved_guess",
                            ClientGameState.savedGuess.getLabel()).withStyle(ChatFormatting.GREEN);
            graphics.drawString(minecraft.font, saved, x, y, 0xFFFFFF, true);
        } else if (ClientGameState.phase == GamePhase.ROUND) {
            graphics.drawString(minecraft.font,
                    Component.translatable("hud.jackofhearts.round_time",
                            String.format("%d:%02d", seconds / 60, seconds % 60)).withStyle(ChatFormatting.AQUA),
                    x, y, 0xFFFFFF, true);
        }

        if (!ClientGameState.alive) {
            graphics.drawString(minecraft.font,
                    Component.translatable("hud.jackofhearts.dead").withStyle(ChatFormatting.DARK_RED),
                    x, y + line, 0xFFFFFF, true);
        }
    }
}
