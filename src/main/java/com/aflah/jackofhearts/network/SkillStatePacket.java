package com.aflah.jackofhearts.network;

import com.aflah.jackofhearts.game.Skill;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Server -&gt; client: sisa pemakaian tiap skill, untuk digambar di panel skill.
 *
 * <p>Dikirim <b>hanya ke Jack</b>, dan itu yang membuat panelnya tidak perlu
 * disembunyikan dengan cara apa pun di sisi client: yang bukan Jack tidak pernah punya
 * angkanya, jadi tidak ada yang bisa digambar.</p>
 *
 * <p>Angka -1 berarti "tidak punya skill ini": ikonnya tidak digambar. Semua -1 berarti
 * "kamu bukan Jack", dan panelnya hilang sama sekali — dipakai juga saat permainan
 * berhenti, supaya panel tidak tertinggal menyala setelah gamenya usai.</p>
 *
 * <p>{@code usedThisRound} ikut dikirim supaya ikon yang jatah rondenya sudah terpakai
 * ikut meredup. Kalau tidak, ikonnya menyala mengundang lalu tombolnya ditolak diam-diam
 * - persis keadaan yang panel ini dibuat untuk menghindarinya.</p>
 *
 * @param uses sisa pemakaian, satu angka per {@link Skill} menurut urutan ordinalnya.
 */
public record SkillStatePacket(int[] uses, boolean usedThisRound) {

    /** Nilai yang mematikan seluruh panel skill. */
    public static SkillStatePacket none() {
        int[] uses = new int[Skill.VALUES.length];
        Arrays.fill(uses, -1);
        return new SkillStatePacket(uses, false);
    }

    public static SkillStatePacket decode(FriendlyByteBuf buf) {
        int[] uses = new int[buf.readVarInt()];
        for (int i = 0; i < uses.length; i++) {
            uses[i] = buf.readVarInt() - 1;
        }
        return new SkillStatePacket(uses, buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(uses.length);
        for (int left : uses) {
            buf.writeVarInt(left + 1);
        }
        buf.writeBoolean(usedThisRound);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.aflah.jackofhearts.client.ClientSkills.accept(this)));
        ctx.get().setPacketHandled(true);
    }
}
