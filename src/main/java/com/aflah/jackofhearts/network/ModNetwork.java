package com.aflah.jackofhearts.network;

import com.aflah.jackofhearts.JackOfHeartsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL = "1";
    private static int nextId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(JackOfHeartsMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    public static void register() {
        CHANNEL.messageBuilder(SyncStatePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncStatePacket::encode)
                .decoder(SyncStatePacket::decode)
                .consumerMainThread(SyncStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenGuessScreenPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenGuessScreenPacket::encode)
                .decoder(OpenGuessScreenPacket::decode)
                .consumerMainThread(OpenGuessScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenRoleRevealPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenRoleRevealPacket::encode)
                .decoder(OpenRoleRevealPacket::decode)
                .consumerMainThread(OpenRoleRevealPacket::handle)
                .add();

        CHANNEL.messageBuilder(LaserPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LaserPacket::encode)
                .decoder(LaserPacket::decode)
                .consumerMainThread(LaserPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetCameraPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SetCameraPacket::encode)
                .decoder(SetCameraPacket::decode)
                .consumerMainThread(SetCameraPacket::handle)
                .add();

        CHANNEL.messageBuilder(RosterPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RosterPacket::encode)
                .decoder(RosterPacket::decode)
                .consumerMainThread(RosterPacket::handle)
                .add();

        CHANNEL.messageBuilder(PanelOffPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PanelOffPacket::encode)
                .decoder(PanelOffPacket::decode)
                .consumerMainThread(PanelOffPacket::handle)
                .add();

        CHANNEL.messageBuilder(JackListPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(JackListPacket::encode)
                .decoder(JackListPacket::decode)
                .consumerMainThread(JackListPacket::handle)
                .add();

        CHANNEL.messageBuilder(SkinDataPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkinDataPacket::encode)
                .decoder(SkinDataPacket::decode)
                .consumerMainThread(SkinDataPacket::handle)
                .add();

        CHANNEL.messageBuilder(CafeteriaPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CafeteriaPacket::encode)
                .decoder(CafeteriaPacket::decode)
                .consumerMainThread(CafeteriaPacket::handle)
                .add();

        CHANNEL.messageBuilder(SkillStatePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillStatePacket::encode)
                .decoder(SkillStatePacket::decode)
                .consumerMainThread(SkillStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(UseSkillPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UseSkillPacket::encode)
                .decoder(UseSkillPacket::decode)
                .consumerMainThread(UseSkillPacket::handle)
                .add();

        CHANNEL.messageBuilder(SubmitGuessPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubmitGuessPacket::encode)
                .decoder(SubmitGuessPacket::decode)
                .consumerMainThread(SubmitGuessPacket::handle)
                .add();

        CHANNEL.messageBuilder(CutscenePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CutscenePacket::encode)
                .decoder(CutscenePacket::decode)
                .consumerMainThread(CutscenePacket::handle)
                .add();
    }

    public static void toServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Ke semua pemain yang terhubung, termasuk yang bukan peserta.
     *
     * <p>Isi Papan Peserta memang tontonan umum — penonton dan yang sudah gugur justru
     * yang paling butuh melihatnya.</p>
     */
    public static void toAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}
