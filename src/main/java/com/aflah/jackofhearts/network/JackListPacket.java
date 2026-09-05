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
 * Server -&gt; client: siapa saja Jack di permainan ini. Dikirim <b>hanya ke sesama
 * Jack</b>.
 *
 * <p>Gunanya satu: nama rekan Jack tergambar merah di layar Jack, supaya mereka saling
 * mengenali tanpa harus berjanjian di luar permainan. Ace melihat nama yang sama persis
 * seperti biasa.</p>
 *
 * <p>Yang menjaga rahasianya bukan kode penggambarnya, melainkan pengirimannya: client
 * Ace tidak pernah menerima daftar ini sama sekali, jadi tidak ada apa pun di memorinya
 * yang bisa dibaca ulang oleh client yang dimodifikasi. Daftar kosong dikirim untuk
 * mengosongkan kembali — saat permainan berhenti, atau ke pemain yang bukan Jack.</p>
 */
public record JackListPacket(List<UUID> jacks) {

    public static JackListPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<UUID> jacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            jacks.add(buf.readUUID());
        }
        return new JackListPacket(jacks);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(jacks.size());
        for (UUID uuid : jacks) {
            buf.writeUUID(uuid);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.aflah.jackofhearts.client.ClientGameState.acceptJacks(this)));
        ctx.get().setPacketHandled(true);
    }
}
