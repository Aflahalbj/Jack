package com.aflah.jackofhearts.network;

import com.aflah.jackofhearts.game.GameManager;
import com.aflah.jackofhearts.game.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -&gt; server: Jack memakai satu skill.
 *
 * <p>Sasarannya dikirim sebagai id entitas, bukan dibiarkan dicari ulang oleh server.
 * Alasannya bukan hemat: yang dilihat Jack di layarnya adalah kotak sasaran yang
 * dipilih client-nya sendiri, dan kalau server memilih ulang dengan hitungannya
 * sendiri, dua orang yang berdiri berdekatan bisa membuat yang kena bukan yang
 * dikotaki. Server tetap memeriksa jaraknya — id yang dikirim cuma boleh menunjuk
 * orang yang memang cukup dekat.</p>
 *
 * <p>Racun sasarannya piring, jadi yang dikirim posisi bloknya ({@code food}), dengan
 * alasan yang sama: piring yang digaris di layar Jack harus piring yang diracuni.</p>
 */
public record UseSkillPacket(int skill, int targetId, boolean self, BlockPos food) {

    public static UseSkillPacket decode(FriendlyByteBuf buf) {
        return new UseSkillPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBlockPos());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(skill);
        buf.writeVarInt(targetId);
        buf.writeBoolean(self);
        buf.writeBlockPos(food);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                GameManager.get().useSkill(player, Skill.byOrdinal(skill), targetId, self, food);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
