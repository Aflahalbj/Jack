package com.aflah.jackofhearts.game;

public enum GamePhase {
    /** Tidak ada game berjalan. */
    IDLE,
    /** Animasi kocok kartu pembagian peran; ronde 1 belum mulai menghitung. */
    REVEAL,
    /** Ronde berjalan, pemain bebas bergerak. */
    ROUND,
    /** Waktu menebak di ruangan (default 20 detik). */
    GUESS,
    /** Laser mengunci lalu mengeksekusi yang salah menebak; ronde belum lanjut. */
    EXECUTION,
    /** Game selesai, menunggu di-reset. */
    ENDED;

    public static final GamePhase[] VALUES = values();

    public static GamePhase byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
    }
}
