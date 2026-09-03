package com.aflah.jackofhearts.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;

/** Empat simbol kartu yang dipakai di punggung baju. */
public enum Symbol {
    HEART("heart", "♥", ChatFormatting.RED),
    DIAMOND("diamond", "♦", ChatFormatting.RED),
    CLUB("club", "♣", ChatFormatting.DARK_GRAY),
    SPADE("spade", "♠", ChatFormatting.DARK_GRAY);

    public static final Symbol[] VALUES = values();

    private final String id;
    private final String glyph;
    private final ChatFormatting color;

    Symbol(String id, String glyph, ChatFormatting color) {
        this.id = id;
        this.glyph = glyph;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public String getGlyph() {
        return glyph;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public String getTranslationKey() {
        return "symbol.jackofhearts." + id;
    }

    public Component getDisplayName() {
        return Component.translatable(getTranslationKey()).withStyle(color);
    }

    /** Nama + glyph, contoh: "Hati ♥". */
    public Component getLabel() {
        return Component.translatable(getTranslationKey()).append(" " + glyph).withStyle(color);
    }

    public static Symbol random(RandomSource random) {
        return VALUES[random.nextInt(VALUES.length)];
    }

    @Nullable
    public static Symbol byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Symbol symbol : VALUES) {
            if (symbol.id.equals(id)) {
                return symbol;
            }
        }
        return null;
    }

    @Nullable
    public static Symbol byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }
}
