package com.aflah.jackofhearts.game;

/**
 * Kemampuan yang cuma dimiliki Jack.
 *
 * <p>Ketiganya menjawab masalah yang sama: Jack tidak bisa melukai siapa-siapa, jadi
 * satu-satunya cara dia bekerja adalah membuat orang lain gagal sendiri — salah
 * menebak simbolnya, atau kehabisan makanan. Racun mempercepat yang kedua, mabuk
 * mengacaukan yang pertama, dan lapar menggandakan tekanan keduanya.</p>
 *
 * <p>Racun sasarannya piring, bukan orang: yang dikirim saat tombolnya ditekan adalah
 * posisi piring yang sedang ditatap Jack, lihat {@link #targetsFood()}.</p>
 */
public enum Skill {
    /** Sasarannya mabuk sepanjang ronde berikutnya. */
    DRUNK("drunk"),
    /** Sasarannya jadi dua kali lebih cepat lapar sampai disembuhkan. */
    HUNGER("hunger"),
    /** Satu porsi dari jenis makanan yang ditatap jadi beracun. */
    POISON("poison");

    /** Berapa kali satu skill boleh dipakai dalam satu permainan. */
    public static final int MAX_USES = 2;

    public static final Skill[] VALUES = values();

    private final String id;

    Skill(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * Skill ini mengenai piring kafetaria, bukan orang. Tidak ada "ke diri sendiri"
     * untuknya, dan jangkauannya jangkauan tangan — piringnya harus bisa disentuh.
     */
    public boolean targetsFood() {
        return this == POISON;
    }

    public static Skill byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : DRUNK;
    }
}
