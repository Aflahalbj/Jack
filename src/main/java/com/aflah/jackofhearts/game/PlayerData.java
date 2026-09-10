package com.aflah.jackofhearts.game;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nullable;
import java.util.UUID;

/** State satu peserta selama satu sesi game. */
public class PlayerData {
    public final UUID uuid;
    public final String name;

    public Role role = Role.ACE;
    /**
     * Pihak yang akhirnya dibela Unknown; null selama belum ditentukan. Tidak dipakai
     * untuk peran lain — Jack dan Ace sudah tahu pihaknya sejak kartu dibalik.
     *
     * <p>Ditentukan oleh siapa yang habis lebih dulu selagi dia masih hidup: kalau ada
     * Jack yang gugur, dia ikut Ace; kalau Ace habis dan dia tinggal sendiri, dia jadi
     * Jack. Unknown yang gugur sebelum pihaknya ditentukan tidak ikut menang siapa-siapa.</p>
     */
    @Nullable
    public Role allegiance;
    public Symbol symbol = Symbol.HEART;
    /** Tebakan yang sudah disimpan untuk ronde ini, null = belum menebak. */
    @Nullable
    public Symbol guess;
    public boolean alive = true;
    /** Berapa peserta lain yang sudah dibunuh pemain ini. */
    public int kills;
    /**
     * Sisa waktu racun sebelum korbannya gugur, dalam tick. 0 berarti tidak diracun.
     *
     * <p>Berjalan terus walau pemiliknya sedang keluar dari server: racun tidak peduli
     * orangnya sedang menonton layarnya atau tidak.</p>
     */
    public int poisonTicks;
    /**
     * Ronde saat dia mabuk, 0 berarti tidak pernah.
     *
     * <p>Disimpan sebagai nomor ronde, bukan sisa waktu, karena mabuknya memang milik
     * satu ronde utuh — dan ronde bisa dipotong lebih cepat lewat pemungutan suara.</p>
     */
    public int drunkRound;
    /** Laparnya digandakan sampai disembuhkan. */
    public boolean hungerCursed;
    /**
     * Ronde terakhir Jack memakai skill apa pun, termasuk racun. 0 berarti belum pernah.
     *
     * <p>Satu penanda untuk ketiganya, bukan satu per skill: batasnya memang satu
     * tindakan per ronde. Kalau tiap skill punya jatah rondenya sendiri, satu ronde bisa
     * diisi mabuk, lapar, dan racun sekaligus — dan ronde itu praktis menghabiskan
     * seluruh persediaan Jack dalam sekali jalan.</p>
     */
    public int skillRound;
    /** Sisa pemakaian tiap skill; cuma dipakai kalau dia Jack. */
    public final int[] skillUses = new int[Skill.VALUES.length];

    /**
     * Terminal Tebakan terakhir yang diklik pemain ini. Laser eksekusi keluar dari
     * situ, jadi terminal tempat dia menjawab adalah terminal yang menghukumnya.
     */
    @Nullable
    public BlockPos lastGuessBlock;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        java.util.Arrays.fill(this.skillUses, Skill.MAX_USES);
    }

    public boolean isJack() {
        return role == Role.JACK;
    }

    public boolean isAce() {
        return role == Role.ACE;
    }

    public boolean isUnknown() {
        return role == Role.UNKNOWN;
    }

    /** Pihak yang dibelanya sekarang: perannya sendiri, atau pihak Unknown yang sudah ditentukan. */
    @Nullable
    public Role side() {
        return role == Role.UNKNOWN ? allegiance : role;
    }

    /** Nama perannya untuk daftar peran, termasuk pihak Unknown kalau sudah ditentukan. */
    public Component roleLabel() {
        MutableComponent label = role.getDisplayName().copy();
        if (role == Role.UNKNOWN && allegiance != null) {
            label.append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                    .append(allegiance.getDisplayName());
        }
        return label;
    }
}
