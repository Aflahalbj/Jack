package com.aflah.jackofhearts.game;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

/** State satu peserta selama satu sesi game. */
public class PlayerData {
    public final UUID uuid;
    public final String name;

    public Role role = Role.ACE;
    public Symbol symbol = Symbol.HEART;
    /** Tebakan yang sudah disimpan untuk ronde ini, null = belum menebak. */
    @Nullable
    public Symbol guess;
    public boolean alive = true;
    /** Berapa peserta lain yang sudah dibunuh pemain ini. */
    public int kills;
    /**
     * Terminal Tebakan terakhir yang diklik pemain ini. Laser eksekusi keluar dari
     * situ, jadi terminal tempat dia menjawab adalah terminal yang menghukumnya.
     */
    @Nullable
    public BlockPos lastGuessBlock;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public boolean isJack() {
        return role == Role.JACK;
    }

    public boolean isAce() {
        return role == Role.ACE;
    }
}
