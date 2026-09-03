package com.aflah.jackofhearts.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Darah eksekusi laser, dari semburan pertama sampai genangan yang tertinggal.
 *
 * <p>Semuanya partikel, bukan blok atau tekstur tempelan di tanah. Alasannya
 * praktis: partikel tidak pernah bertabrakan dengan permukaan lantai, tidak berkedip
 * di tanah miring, dan hilang sendiri kalau mayatnya disapu — sedangkan noda yang
 * digambar menempel di tanah harus tahu bentuk lantainya, dan salah sedikit langsung
 * terlihat mengambang atau tenggelam.</p>
 *
 * <p>Dua warna dipakai bersama dengan sengaja. Percikan segarnya lebih terang dan
 * lebih besar supaya terbaca dari jauh saat tembakan jatuh; genangannya lebih gelap
 * dan lebih pekat, mendekati warna darah yang sudah mengendap.</p>
 */
public final class Blood {

    private Blood() {
    }

    /** Percikan segar saat kena. Terang dan besar supaya terbaca dari jauh. */
    private static final DustParticleOptions SPRAY =
            new DustParticleOptions(new Vector3f(0.45F, 0.02F, 0.03F), 1.3F);
    /** Tetesan selama badannya ambruk. Lebih kecil, jatuh menyusuri badan. */
    private static final DustParticleOptions DRIP =
            new DustParticleOptions(new Vector3f(0.36F, 0.01F, 0.02F), 1.0F);
    /** Genangan yang mengendap. Paling gelap dan paling besar. */
    private static final DustParticleOptions POOL =
            new DustParticleOptions(new Vector3f(0.26F, 0.01F, 0.02F), 2.1F);
    /**
     * Serpihan berat yang melengkung lalu jatuh ke lantai. Blok wart nether dipakai
     * karena merahnya paling gelap di antara blok vanilla — merah terang seperti balok
     * redstone terbaca sebagai percikan cat, bukan darah.
     */
    private static final BlockParticleOption GORE =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());

    /**
     * Semburan di detik tumbukan.
     *
     * @param from titik asal laser; semburannya terlempar ke arah menjauhi sini.
     *             Boleh null, dan untuk laser yang turun tegak lurus dari langit
     *             arah mendatarnya memang tidak ada — semburannya jadi ke segala arah.
     */
    public static void spurt(ServerLevel level, Vec3 at, @Nullable Vec3 from) {
        // Kabut halus tepat di lukanya.
        level.sendParticles(SPRAY, at.x, at.y, at.z, 45, 0.22D, 0.22D, 0.22D, 0.0D);

        // Yang terlempar menjauhi laser. Yang digeser titik keluarnya, bukan kecepatan
        // tiap partikel: partikel vanilla mengacak sendiri arah gerak awalnya, jadi
        // menggeser sumbernya jauh lebih terbaca daripada mengatur kecepatannya.
        Vec3 center = at.add(0.0D, 0.1D, 0.0D);
        if (from != null) {
            double dx = at.x - from.x;
            double dz = at.z - from.z;
            double flat = Math.sqrt(dx * dx + dz * dz);
            if (flat > 1.0E-4D) {
                center = center.add(dx / flat * 0.55D, 0.0D, dz / flat * 0.55D);
            }
        }
        level.sendParticles(SPRAY, center.x, center.y, center.z, 30, 0.30D, 0.25D, 0.30D, 0.06D);
        level.sendParticles(GORE, center.x, center.y, center.z, 24, 0.20D, 0.20D, 0.20D, 0.35D);
    }

    /** Tetesan sepanjang badan yang sedang ambruk. */
    public static void drip(ServerLevel level, Vec3 at) {
        level.sendParticles(DRIP, at.x, at.y, at.z, 2, 0.12D, 0.08D, 0.12D, 0.01D);
    }

    /**
     * Genangan di bawah badan yang sudah tergeletak.
     *
     * <p>Sebaran tegaknya nol, jadi semua partikel duduk di ketinggian yang sama dan
     * yang terlihat bidang datar, bukan gumpalan. Kecepatannya juga nol; partikel debu
     * yang dikirim tanpa kecepatan mendatar meredam geraknya sendiri sepuluh kali
     * lipat, jadi genangannya praktis diam.</p>
     */
    public static void pool(ServerLevel level, Vec3 at) {
        level.sendParticles(POOL, at.x, at.y, at.z, 10, 0.45D, 0.0D, 0.45D, 0.0D);
    }
}
