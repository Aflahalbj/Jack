package com.aflah.jackofhearts.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client: skin peserta, supaya wajahnya tetap ada walau orangnya tidak.
 *
 * <p>Client biasanya mengambil skin dari daftar pemainnya sendiri, dan itu cukup
 * selama semua orang online. Tapi permainan ini justru harus tetap menggambar orang
 * yang <b>sudah keluar</b>: potretnya di Papan Peserta, badan penggantinya yang
 * berdiri di ruangan, dan mayatnya kalau dia gugur selagi pergi. Begitu seseorang
 * keluar, dia hilang dari daftar pemain client dan skinnya jatuh ke bawaan — tiga
 * gambar tadi berubah jadi Steve tanpa pemberitahuan apa pun.</p>
 *
 * <p>Karena itu skinnya dititipkan sejak dia masih ada: server menyimpan properti
 * {@code textures} dari profil pemain begitu dia terdaftar, lalu membagikannya ke
 * semua client. Isinya persis data yang dipakai vanilla untuk menggambar skin siapa
 * pun, jadi client tinggal menyodorkannya ke mesin skin bawaannya.</p>
 *
 * <p>Batasnya: properti itu cuma ada di server yang memverifikasi akun ke Mojang. Di
 * server yang tidak, tidak ada yang bisa dikirim — dan client jatuh ke ingatannya
 * sendiri dari saat orangnya masih online.</p>
 */
public record SkinDataPacket(List<Entry> entries) {

    /**
     * @param textures nilai properti {@code textures} milik profilnya, apa adanya
     *                 (JSON yang di-base64). Tanda tangannya sengaja tidak ikut:
     *                 client membacanya lewat jalur tidak-terverifikasi, yang memang
     *                 tidak memeriksa tanda tangan.
     */
    public record Entry(UUID uuid, String name, String textures) {
    }

    public static SkinDataPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readUUID(), buf.readUtf(64), buf.readUtf(Short.MAX_VALUE)));
        }
        return new SkinDataPacket(entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUUID(entry.uuid());
            buf.writeUtf(entry.name(), 64);
            buf.writeUtf(entry.textures(), Short.MAX_VALUE);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.aflah.jackofhearts.client.PlayerSkins.accept(this)));
        ctx.get().setPacketHandled(true);
    }
}
