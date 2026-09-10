package com.aflah.jackofhearts.network;

import com.aflah.jackofhearts.client.ClientGameState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: buka layar animasi kocok kartu.
 *
 * <p>{@code cardCount} dan {@code jackCards} adalah jumlah kartu yang benar-benar
 * <b>digambar</b>, sudah dibatasi {@code MAX_LINEUP_CARDS}. {@code totalPlayers} dan
 * {@code totalJacks} adalah angka sebenarnya tanpa batas, dipakai untuk teks di bawah
 * barisan — jadi tulisannya tetap jujur walau kartunya tidak semuanya muat. Pasangan
 * {@code unknownCards}/{@code totalUnknowns} sama persis, untuk kartu Unknown.</p>
 *
 * <p>{@code preview} menandai pemutaran dari {@code /jack shuffle} yang tidak terikat
 * game apa pun. Layar biasa menutup sendiri saat server melapor fase sudah bukan
 * REVEAL; di luar game fasenya IDLE, jadi tanpa penanda ini layarnya akan langsung
 * tertutup di tick pertama.</p>
 */
public record OpenRoleRevealPacket(int role, int ticksLeft, int cardCount, int jackCards,
                                   int unknownCards, int totalPlayers, int totalJacks,
                                   int totalUnknowns, boolean preview) {

    public static OpenRoleRevealPacket decode(FriendlyByteBuf buf) {
        return new OpenRoleRevealPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(role);
        buf.writeVarInt(ticksLeft);
        buf.writeVarInt(cardCount);
        buf.writeVarInt(jackCards);
        buf.writeVarInt(unknownCards);
        buf.writeVarInt(totalPlayers);
        buf.writeVarInt(totalJacks);
        buf.writeVarInt(totalUnknowns);
        buf.writeBoolean(preview);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientGameState.openRoleRevealScreen(this)));
        ctx.get().setPacketHandled(true);
    }
}
