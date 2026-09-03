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
 * Server -> client: seluruh daftar peserta, untuk digambar di Papan Peserta.
 *
 * <p>Ini paket pertama di mod ini yang membawa keadaan <b>orang lain</b>.
 * {@link SyncStatePacket} sengaja hanya membawa keadaan diri sendiri karena HUD tidak
 * boleh membocorkan apa pun tentang peserta lain — tapi papan justru ada untuk
 * menampilkan semua orang, jadi datanya memang harus sampai ke semua client.</p>
 *
 * <p>Yang dikirim per orang hanya UUID, nama, dan padam/tidak. UUID cukup untuk
 * mengambil skin, karena client sudah memegang skin semua pemain yang online lewat
 * daftar pemainnya sendiri. Tidak ada peran, simbol, atau tebakan di sini — kalau
 * ikut dikirim, siapa pun bisa membacanya dari paket dan seluruh permainan bocor.</p>
 *
 * <p>{@code dark} bukan kebalikan dari "hidup". Panel baru padam beberapa detik
 * setelah orangnya gugur, dan selama jeda itu orangnya sudah mati tapi panelnya masih
 * menyala. Nilai inilah yang menentukan tampilan papan, sehingga pemain yang baru
 * bergabung di tengah permainan melihat papan yang sama persis dengan yang lain.</p>
 */
public record RosterPacket(List<Entry> entries) {

    public record Entry(UUID uuid, String name, boolean dark) {
    }

    public static RosterPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readUUID(), buf.readUtf(64), buf.readBoolean()));
        }
        return new RosterPacket(entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUUID(entry.uuid());
            buf.writeUtf(entry.name(), 64);
            buf.writeBoolean(entry.dark());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.aflah.jackofhearts.client.ClientRoster.accept(this)));
        ctx.get().setPacketHandled(true);
    }
}
