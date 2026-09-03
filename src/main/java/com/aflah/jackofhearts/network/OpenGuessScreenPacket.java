package com.aflah.jackofhearts.network;

import com.aflah.jackofhearts.client.ClientGameState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: buka GUI tebakan. */
public record OpenGuessScreenPacket(int currentGuess, int ticksLeft) {

    public static OpenGuessScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenGuessScreenPacket(buf.readVarInt() - 1, buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(currentGuess + 1);
        buf.writeVarInt(ticksLeft);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientGameState.openGuessScreen(this)));
        ctx.get().setPacketHandled(true);
    }
}
