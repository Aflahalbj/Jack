package com.aflah.jackofhearts.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -&gt; client: keadaan kafetaria, beserta kotak mejanya.
 *
 * <p>Kotaknya ikut dikirim, tidak dibaca dari config di client, dan itu bukan
 * kelebihan data: config ini bertipe COMMON, jadi di server terpisah client punya
 * berkas confignya <b>sendiri</b> yang isinya belum tentu sama. Kotak yang beda antara
 * client dan server berarti client menebak-nebak blok mana yang diurus server - persis
 * hal yang bikin barang hilang di layar tapi tidak hilang di server.</p>
 *
 * <p>Papan angka juga dihitung dari kotak ini, bukan dikirim terpisah, supaya cuma ada
 * satu sumber kebenaran soal di mana kafetarianya.</p>
 */
public record CafeteriaPacket(boolean active, int stock, int total,
                              ResourceLocation dimension, BlockPos min, BlockPos max) {

    public static CafeteriaPacket decode(FriendlyByteBuf buf) {
        return new CafeteriaPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                buf.readResourceLocation(), buf.readBlockPos(), buf.readBlockPos());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeVarInt(stock);
        buf.writeVarInt(total);
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(min);
        buf.writeBlockPos(max);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.aflah.jackofhearts.client.ClientCafeteria.update(this)));
        ctx.get().setPacketHandled(true);
    }
}
