package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.game.GamePhase;
import com.aflah.jackofhearts.game.Symbol;
import com.aflah.jackofhearts.network.ModNetwork;
import com.aflah.jackofhearts.network.SubmitGuessPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/** GUI pilihan simbol yang muncul saat blok tebakan diklik kanan. */
public class GuessScreen extends Screen {

    private static final int CARD_WIDTH = 52;
    private static final int CARD_HEIGHT = 68;
    private static final int CARD_GAP = 8;
    private static final int ICON_SIZE = 32;
    /** Ukuran file PNG ikon; berbeda dari ICON_SIZE, yang ukuran gambarnya di layar. */
    private static final int ICON_TEX = 64;

    private static final ResourceLocation[] ICONS = new ResourceLocation[Symbol.VALUES.length];

    static {
        for (Symbol symbol : Symbol.VALUES) {
            ICONS[symbol.ordinal()] = new ResourceLocation(JackOfHeartsMod.MOD_ID,
                    "textures/gui/symbol_" + symbol.getId() + ".png");
        }
    }

    private final Button[] cardButtons = new Button[Symbol.VALUES.length];
    @Nullable
    private Symbol selected;
    private int gridLeft;
    private int gridTop;

    public GuessScreen(@Nullable Symbol current) {
        super(Component.translatable("screen.jackofhearts.guess"));
        this.selected = current;
    }

    @Override
    protected void init() {
        int totalWidth = Symbol.VALUES.length * CARD_WIDTH + (Symbol.VALUES.length - 1) * CARD_GAP;
        gridLeft = (this.width - totalWidth) / 2;
        gridTop = this.height / 2 - CARD_HEIGHT / 2 - 6;

        for (Symbol symbol : Symbol.VALUES) {
            int x = gridLeft + symbol.ordinal() * (CARD_WIDTH + CARD_GAP);
            Button button = Button.builder(Component.empty(), b -> this.selected = symbol)
                    .bounds(x, gridTop, CARD_WIDTH, CARD_HEIGHT)
                    .build();
            cardButtons[symbol.ordinal()] = button;
            this.addRenderableWidget(button);
        }

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.jackofhearts.confirm"), b -> confirm())
                .bounds(this.width / 2 - 80, gridTop + CARD_HEIGHT + 14, 160, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.jackofhearts.close"), b -> this.onClose())
                .bounds(this.width / 2 - 80, gridTop + CARD_HEIGHT + 38, 160, 20)
                .build());
    }

    private void confirm() {
        if (selected != null) {
            ModNetwork.CHANNEL.sendToServer(new SubmitGuessPacket(selected.ordinal()));
            ClientGameState.savedGuess = selected;
        }
        this.onClose();
    }

    @Override
    public void tick() {
        // Tutup otomatis kalau fase menebak sudah lewat.
        if (ClientGameState.phase != GamePhase.GUESS || !ClientGameState.alive) {
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font,
                Component.translatable("screen.jackofhearts.guess").withStyle(ChatFormatting.GOLD),
                this.width / 2, gridTop - 46, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.jackofhearts.guess_hint").withStyle(ChatFormatting.GRAY),
                this.width / 2, gridTop - 32, 0xFFFFFF);

        int seconds = ClientGameState.secondsLeft();
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.jackofhearts.time_left", seconds)
                        .withStyle(seconds <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW),
                this.width / 2, gridTop - 18, 0xFFFFFF);

        for (Symbol symbol : Symbol.VALUES) {
            Button button = cardButtons[symbol.ordinal()];
            int iconX = button.getX() + (CARD_WIDTH - ICON_SIZE) / 2;
            int iconY = button.getY() + 10;

            graphics.blit(ICONS[symbol.ordinal()], iconX, iconY, ICON_SIZE, ICON_SIZE,
                    0.0F, 0.0F, ICON_TEX, ICON_TEX, ICON_TEX, ICON_TEX);

            graphics.drawCenteredString(this.font, Component.translatable(symbol.getTranslationKey()),
                    button.getX() + CARD_WIDTH / 2, button.getY() + CARD_HEIGHT - 16,
                    symbol.getColor().getColor() == null ? 0xFFFFFF : symbol.getColor().getColor());

            if (selected == symbol) {
                drawSelectionBorder(graphics, button.getX() - 2, button.getY() - 2,
                        CARD_WIDTH + 4, CARD_HEIGHT + 4);
            }
        }

        Component saved = ClientGameState.savedGuess == null
                ? Component.translatable("screen.jackofhearts.not_saved").withStyle(ChatFormatting.DARK_RED)
                : Component.translatable("screen.jackofhearts.saved", ClientGameState.savedGuess.getLabel())
                        .withStyle(ChatFormatting.GREEN);
        graphics.drawCenteredString(this.font, saved, this.width / 2, gridTop + CARD_HEIGHT + 64, 0xFFFFFF);
    }

    private static void drawSelectionBorder(GuiGraphics graphics, int x, int y, int width, int height) {
        int color = 0xFFFFD700;
        graphics.fill(x, y, x + width, y + 2, color);
        graphics.fill(x, y + height - 2, x + width, y + height, color);
        graphics.fill(x, y, x + 2, y + height, color);
        graphics.fill(x + width - 2, y, x + width, y + height, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
