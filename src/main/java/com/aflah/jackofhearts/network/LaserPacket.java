package com.aflah.jackofhearts.network;

import com.aflah.jackofhearts.client.ClientLasers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: gambar satu sinar laser dari {@code from} ke arah korban.
 *
 * <p>{@code victimId} dikirim supaya client bisa mengambil posisi kepala korban
 * setiap frame. Tanpa itu sinarnya terpaku ke titik saat paket dikirim, dan korban
 * yang bergerak selama laser mengunci akan ditembak ke tempat dia berdiri tadi.
 * {@code fallback} dipakai kalau entitasnya tidak ada di client, misalnya karena
 * jaraknya di luar jangkauan pemuatan entitas.</p>
 *
 * <p>Dikirim ke semua pemain di sekitar, bukan cuma korbannya, karena justru
 * penonton yang perlu melihat siapa yang dieksekusi.</p>
 *
 * <p>{@code fires} membedakan tembakan sungguhan dari gertakan. Yang menebak benar
 * juga mendapat terminal yang mengisi daya ke arahnya, persis sama sampai detik
 * terakhir — bedanya muatannya padam begitu saja tanpa pernah menembak. Tanpa
 * gertakan ini, penguncian laser sama saja dengan pengumuman kematian: begitu
 * sinarnya mulai mengisi, yang bersangkutan sudah tahu nasibnya.</p>
 */
public record LaserPacket(Vec3 from, Vec3 fallback, int victimId, int chargeTicks, int totalTicks,
                          boolean fires) {

    public static LaserPacket decode(FriendlyByteBuf buf) {
        Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 fallback = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new LaserPacket(from, fallback, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(from.x);
        buf.writeDouble(from.y);
        buf.writeDouble(from.z);
        buf.writeDouble(fallback.x);
        buf.writeDouble(fallback.y);
        buf.writeDouble(fallback.z);
        buf.writeVarInt(victimId);
        buf.writeVarInt(chargeTicks);
        buf.writeVarInt(totalTicks);
        buf.writeBoolean(fires);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientLasers.add(this)));
        ctx.get().setPacketHandled(true);
    }
}
