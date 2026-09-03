package com.aflah.jackofhearts.network;

import com.aflah.jackofhearts.client.ClientGameState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: state HUD, kunci kamera, dan tebakan tersimpan.
 * {@code role} dan {@code savedGuess} memakai -1 untuk "tidak ada".
 */
public record SyncStatePacket(int phase, int round, int ticksLeft, boolean participating,
                              boolean alive, boolean lockView, int role, int savedGuess) {

    public static SyncStatePacket decode(FriendlyByteBuf buf) {
        return new SyncStatePacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readVarInt() - 1, buf.readVarInt() - 1);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(phase);
        buf.writeVarInt(round);
        buf.writeVarInt(ticksLeft);
        buf.writeBoolean(participating);
        buf.writeBoolean(alive);
        buf.writeBoolean(lockView);
        buf.writeVarInt(role + 1);
        buf.writeVarInt(savedGuess + 1);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientGameState.accept(this)));
        ctx.get().setPacketHandled(true);
    }
}
