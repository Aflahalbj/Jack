package com.aflah.jackofhearts.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum Role {
    JACK("jack", ChatFormatting.DARK_RED),
    ACE("ace", ChatFormatting.AQUA),
    /**
     * Belum punya pihak. Pihaknya ditentukan oleh siapa yang habis lebih dulu, lihat
     * {@link PlayerData#allegiance}.
     */
    UNKNOWN("unknown", ChatFormatting.DARK_PURPLE);

    private final String id;
    private final ChatFormatting color;

    Role(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public Component getDisplayName() {
        return Component.translatable("role.jackofhearts." + id).withStyle(color);
    }

    public static Role byOrdinal(int ordinal) {
        Role[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ACE;
    }
}
