package com.aflah.jackofhearts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Mengambil skin pemain dari UUID-nya.
 *
 * <p>Sumbernya daftar pemain yang sudah dipegang client, bukan unduhan sendiri: skin
 * semua orang yang online sudah termuat di situ, jadi tidak ada permintaan jaringan
 * tambahan dan tidak ada jeda saat papan atau mayat pertama kali digambar.</p>
 *
 * <p>Batasnya: orang yang <b>keluar dari server</b> hilang dari daftar itu, dan
 * skinnya jatuh ke bawaan. Untuk permainan ini jarang jadi masalah — yang gugur cuma
 * dijadikan penonton, bukan dikeluarkan, jadi dia tetap ada di daftar.</p>
 */
public final class PlayerSkins {

    private PlayerSkins() {
    }

    public static ResourceLocation texture(@Nullable UUID uuid) {
        PlayerInfo info = info(uuid);
        if (info != null) {
            return info.getSkinLocation();
        }
        return DefaultPlayerSkin.getDefaultSkin(uuid != null ? uuid : Util.FALLBACK);
    }

    public static boolean isSlim(@Nullable UUID uuid) {
        PlayerInfo info = info(uuid);
        if (info != null) {
            return "slim".equals(info.getModelName());
        }
        return uuid != null && "slim".equals(DefaultPlayerSkin.getSkinModelName(uuid));
    }

    @Nullable
    private static PlayerInfo info(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.getPlayerInfo(uuid);
    }

    private static final class Util {
        /** UUID nol: cuma dipakai supaya skin bawaan tetap punya sesuatu untuk diacak. */
        static final UUID FALLBACK = new UUID(0L, 0L);
    }
}
