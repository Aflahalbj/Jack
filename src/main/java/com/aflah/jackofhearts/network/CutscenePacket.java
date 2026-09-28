package com.aflah.jackofhearts.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -&gt; client: kendali cutscene pembuka.
 *
 * <p>Kameranya digerakkan client sendiri, per frame — kamera yang digerakkan server cuma
 * bisa berpindah 20 kali sedetik dan terlihat tersendat. Yang dipegang server cuma
 * kapan mulai dan kapan selesai, dan selesainya selalu dari server: permainannya dimulai
 * di detik yang sama, jadi client yang terlambat beberapa tick tidak pernah melihat
 * pembagian peran selagi kameranya masih melayang.</p>
 */
public record CutscenePacket(int action) {

    /** Hentikan cutscene dan kembalikan kamera ke pemain. */
    public static final int STOP = 0;
    /** Layar hitam; badan pemain sedang dipindahkan ke arena. */
    public static final int PREPARE = 1;
    /** Jalankan kameranya dari awal. */
    public static final int PLAY = 2;

    public static CutscenePacket decode(FriendlyByteBuf buf) {
        return new CutscenePacket(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.aflah.jackofhearts.client.ClientCutscene.accept(action)));
        ctx.get().setPacketHandled(true);
    }
}
