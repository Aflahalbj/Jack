package com.aflah.jackofhearts.network;

import com.aflah.jackofhearts.client.ClientCameraOverride;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: pindahkan sudut pandang ke entitas lain, atau kembalikan ke
 * badan sendiri kalau {@code entityId} negatif.
 *
 * <p>Sengaja lewat paket sendiri, bukan {@code ServerPlayer.setCamera}. Method
 * server itu ikut <b>menteleport</b> pemain ke entitas kameranya — perilaku yang
 * benar untuk spectator, tapi salah di sini. Sisi client punya
 * {@code Minecraft.setCameraEntity} yang cuma mengganti sudut pandang tanpa
 * menyentuh posisi apa pun; itu juga cara mod freecam bekerja.</p>
 */
public record SetCameraPacket(int entityId) {

    public static SetCameraPacket decode(FriendlyByteBuf buf) {
        return new SetCameraPacket(buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCameraOverride.set(entityId)));
        ctx.get().setPacketHandled(true);
    }
}
