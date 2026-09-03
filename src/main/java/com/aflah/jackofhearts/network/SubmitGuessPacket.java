package com.aflah.jackofhearts.network;

import com.aflah.jackofhearts.game.GameManager;
import com.aflah.jackofhearts.game.Symbol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: simpan tebakan simbol. */
public record SubmitGuessPacket(int symbol) {

    public static SubmitGuessPacket decode(FriendlyByteBuf buf) {
        return new SubmitGuessPacket(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(symbol);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            Symbol picked = Symbol.byOrdinal(symbol);
            if (sender != null && picked != null) {
                GameManager.get().submitGuess(sender, picked);
            }
        });
        context.setPacketHandled(true);
    }
}
