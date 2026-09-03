package com.aflah.jackofhearts.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client: panel satu peserta padam sekarang juga.
 *
 * <p>Terpisah dari {@link RosterPacket} yang juga membawa keadaan padam, karena
 * keduanya menjawab pertanyaan yang berbeda. Roster menjawab "seperti apa papannya
 * saat ini" — dipakai saat bergabung atau menyegarkan, dan tidak boleh berbunyi.
 * Paket ini menjawab "panel ini baru saja padam" — itulah yang memicu bunyi kliknya.
 * Kalau digabung, pemain yang baru masuk akan disambut belasan klik sekaligus dari
 * kematian yang sudah lama berlalu.</p>
 */
public record PanelOffPacket(UUID uuid) {

    public static PanelOffPacket decode(FriendlyByteBuf buf) {
        return new PanelOffPacket(buf.readUUID());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.aflah.jackofhearts.client.ClientRoster.panelOff(this.uuid)));
        ctx.get().setPacketHandled(true);
    }
}
