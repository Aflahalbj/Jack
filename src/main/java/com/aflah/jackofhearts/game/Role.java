package com.aflah.jackofhearts.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum Role {
    JACK("jack", ChatFormatting.DARK_RED),
    ACE("ace", ChatFormatting.AQUA);

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
}
