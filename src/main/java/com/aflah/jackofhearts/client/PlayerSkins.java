package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.network.SkinDataPacket;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mengambil skin pemain dari UUID-nya, termasuk pemain yang sudah keluar dari server.
 *
 * <p>Ada tiga sumber, dipakai berurutan:</p>
 *
 * <ol>
 *   <li><b>Daftar pemain client.</b> Sumber vanilla, dan yang paling benar selama
 *       orangnya masih online: skinnya sudah termuat di situ, tanpa permintaan
 *       jaringan tambahan.</li>
 *   <li><b>Ingatan sendiri.</b> Setiap skin yang pernah terbaca dari daftar itu
 *       disimpan. Jadi orang yang keluar di tengah permainan tetap punya wajah, karena
 *       client sempat melihatnya sebelum dia pergi.</li>
 *   <li><b>Titipan server.</b> Properti {@code textures} yang dikirim lewat
 *       {@link SkinDataPacket}. Ini yang menutupi lubang terakhir: client yang baru
 *       bergabung <b>setelah</b> orangnya keluar tidak pernah punya kesempatan
 *       melihatnya, jadi tidak ada yang bisa diingat.</li>
 * </ol>
 *
 * <p>Kalau ketiganya kosong, dipakai skin bawaan — dan itu memang satu-satunya keadaan
 * yang tersisa: server yang tidak memverifikasi akun ke Mojang tidak punya properti
 * skin untuk dititipkan sama sekali.</p>
 */
public final class PlayerSkins {

    private PlayerSkins() {
    }

    /** Skin yang sudah pernah terbaca, per pemain. */
    private static final Map<UUID, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<UUID, Boolean> SLIM = new HashMap<>();
    /** Titipan yang belum sempat diolah, per pemain. Diolah saat skinnya diminta. */
    private static final Map<UUID, SkinDataPacket.Entry> PENDING = new HashMap<>();

    public static ResourceLocation texture(@Nullable UUID uuid) {
        if (uuid == null) {
            return DefaultPlayerSkin.getDefaultSkin(Util.FALLBACK);
        }
        PlayerInfo info = info(uuid);
        if (info != null) {
            ResourceLocation location = info.getSkinLocation();
            TEXTURES.put(uuid, location);
            SLIM.put(uuid, "slim".equals(info.getModelName()));
            return location;
        }
        resolvePending(uuid);
        ResourceLocation remembered = TEXTURES.get(uuid);
        return remembered != null ? remembered : DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    public static boolean isSlim(@Nullable UUID uuid) {
        if (uuid == null) {
            return false;
        }
        PlayerInfo info = info(uuid);
        if (info != null) {
            boolean slim = "slim".equals(info.getModelName());
            SLIM.put(uuid, slim);
            return slim;
        }
        resolvePending(uuid);
        Boolean remembered = SLIM.get(uuid);
        return remembered != null ? remembered : "slim".equals(DefaultPlayerSkin.getSkinModelName(uuid));
    }

    /**
     * Ingat skin pemain ini sekarang, selagi dia masih online.
     *
     * <p>Dipanggil dari luar supaya ingatannya tidak bergantung pada kebetulan: tanpa
     * ini, skin seseorang cuma tersimpan kalau kebetulan sudah pernah <b>digambar</b>
     * di client itu.</p>
     */
    public static void remember(UUID uuid) {
        PlayerInfo info = info(uuid);
        if (info != null) {
            TEXTURES.put(uuid, info.getSkinLocation());
            SLIM.put(uuid, "slim".equals(info.getModelName()));
        }
    }

    /** Titipan skin dari server. Belum diolah di sini — lihat {@link #resolvePending}. */
    public static void accept(SkinDataPacket packet) {
        for (SkinDataPacket.Entry entry : packet.entries()) {
            if (!entry.textures().isEmpty() && !TEXTURES.containsKey(entry.uuid())) {
                PENDING.put(entry.uuid(), entry);
            }
        }
    }

    /**
     * Mengolah titipan jadi tekstur yang bisa digambar.
     *
     * <p>Dikerjakan saat skinnya benar-benar diminta, bukan saat paketnya datang.
     * Alasannya bukan hemat: pengolahan ini menyentuh mesin tekstur, dan paket bisa
     * tiba di detik mana pun — termasuk sebelum dunianya selesai dimuat.</p>
     *
     * <p>Yang dipakai jalur "tidak terverifikasi" milik vanilla, sama seperti yang
     * dipakai kepala pemain di dunia. Jalur itu cuma membongkar base64-nya, tanpa
     * memeriksa tanda tangan dan tanpa menghubungi Mojang. Unduhan gambarnya sendiri
     * berjalan di latar; sampai selesai, tekstur yang sama menampilkan skin bawaan,
     * jadi tidak ada frame yang kosong.</p>
     */
    private static void resolvePending(UUID uuid) {
        SkinDataPacket.Entry entry = PENDING.remove(uuid);
        if (entry == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        SkinManager skins = minecraft.getSkinManager();
        GameProfile profile = new GameProfile(uuid, entry.name());
        profile.getProperties().put("textures", new Property("textures", entry.textures(), ""));

        MinecraftProfileTexture skin = skins.getInsecureSkinInformation(profile)
                .get(MinecraftProfileTexture.Type.SKIN);
        if (skin == null) {
            return;
        }
        TEXTURES.put(uuid, skins.registerTexture(skin, MinecraftProfileTexture.Type.SKIN));
        SLIM.put(uuid, "slim".equals(skin.getMetadata("model")));
    }

    public static void reset() {
        TEXTURES.clear();
        SLIM.clear();
        PENDING.clear();
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
