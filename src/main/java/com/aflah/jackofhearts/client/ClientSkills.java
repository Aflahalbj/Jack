package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.game.GamePhase;
import com.aflah.jackofhearts.game.Role;
import com.aflah.jackofhearts.game.Skill;
import com.aflah.jackofhearts.network.ModNetwork;
import com.aflah.jackofhearts.network.SkillStatePacket;
import com.aflah.jackofhearts.network.UseSkillPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Keadaan panel skill di client: sisa pemakaian, dan siapa yang sedang jadi sasaran.
 *
 * <p>Sasarannya dipilih di sini, bukan di server, supaya kotak yang terlihat di layar
 * Jack dan orang yang benar-benar kena adalah orang yang sama. Id sasaran itu ikut
 * dikirim saat skillnya dipakai; server tetap memeriksa jaraknya sendiri.</p>
 *
 * <p>Racun punya sasarannya sendiri: piring kafetaria yang sedang ditatap Jack. Aturan
 * yang sama berlaku — piring yang digaris di layarnya adalah piring yang dikirim.</p>
 */
public final class ClientSkills {

    private ClientSkills() {
    }

    /** Sejauh apa skill bisa menjangkau orang. */
    public static final double RANGE = 2.0D;

    /**
     * Sisa pemakaian tiap skill. Nilai negatif berarti pemain ini bukan Jack — dan
     * memang tidak pernah dikirimi angkanya sama sekali.
     */
    private static final int[] USES = new int[Skill.VALUES.length];

    static {
        Arrays.fill(USES, -1);
    }

    /** Jatah satu skill per ronde sudah terpakai. */
    private static boolean usedThisRound;

    /** Sasaran yang sedang dikotaki, atau null kalau tidak ada yang cukup dekat. */
    @Nullable
    private static Player target;

    /** Piring kafetaria yang sedang ditatap, atau null kalau tidak sedang menatap piring. */
    @Nullable
    private static BlockPos foodTarget;

    public static void accept(SkillStatePacket packet) {
        int[] uses = packet.uses();
        for (int i = 0; i < USES.length; i++) {
            USES[i] = i < uses.length ? uses[i] : -1;
        }
        usedThisRound = packet.usedThisRound();
    }

    public static int uses(Skill skill) {
        return USES[skill.ordinal()];
    }

    /** Panel skill cuma ada untuk Jack yang masih hidup di permainan yang sedang jalan. */
    public static boolean active() {
        if (!ClientGameState.participating || !ClientGameState.alive || ClientGameState.role != Role.JACK) {
            return false;
        }
        for (int left : USES) {
            if (left >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Skill yang dimatikan host tidak pernah punya angka, jadi tidak pernah digambar. */
    public static boolean has(Skill skill) {
        return USES[skill.ordinal()] >= 0;
    }

    @Nullable
    public static Player target() {
        return target;
    }

    @Nullable
    public static BlockPos foodTarget() {
        return foodTarget;
    }

    public static void reset() {
        Arrays.fill(USES, -1);
        usedThisRound = false;
        target = null;
        foodTarget = null;
    }

    /**
     * Mencari orang terdekat dalam jangkauan, dan piring yang sedang ditatap, sekali per
     * tick.
     *
     * <p>Yang dicari pemain lain yang bisa dilihat, bukan entitas apa pun: badan
     * pengganti peserta yang sedang offline sengaja tidak bisa jadi sasaran — pemiliknya
     * tidak ada di sana untuk merasakan apa pun, dan skill yang habis ke patung cuma
     * membuang jatah.</p>
     *
     * <p>Piringnya dibaca dari blok yang sedang ditunjuk crosshair, jadi jangkauannya
     * sama persis dengan jangkauan tangan — yang dipendekkan server selama permainan.
     * Piring yang tidak bisa disentuh tidak bisa diracuni.</p>
     */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer self = minecraft.player;
        if (self == null || minecraft.level == null || !active()) {
            target = null;
            foodTarget = null;
            return;
        }
        AABB box = self.getBoundingBox().inflate(RANGE);
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player other : minecraft.level.players()) {
            if (other == self || other.isSpectator()) {
                continue;
            }
            double distance = other.distanceToSqr(self);
            if (distance < bestDistance && box.intersects(other.getBoundingBox())) {
                bestDistance = distance;
                best = other;
            }
        }
        target = best;

        foodTarget = null;
        if (has(Skill.POISON) && minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && ClientCafeteria.intercepts(minecraft.level, hit.getBlockPos())) {
            foodTarget = hit.getBlockPos().immutable();
        }
    }

    /** true kalau skill ini sekarang bisa dipakai — itu yang membuat ikonnya menyala. */
    public static boolean ready(Skill skill, boolean self) {
        if (!active() || usedThisRound || uses(skill) <= 0 || ClientGameState.phase != GamePhase.ROUND) {
            return false;
        }
        if (skill.targetsFood()) {
            return foodTarget != null;
        }
        return self || target != null;
    }

    public static void use(Skill skill, boolean self) {
        if (skill.targetsFood()) {
            self = false;
        }
        if (!ready(skill, self)) {
            return;
        }
        ModNetwork.toServer(new UseSkillPacket(skill.ordinal(),
                self || target == null ? -1 : target.getId(), self,
                foodTarget == null ? BlockPos.ZERO : foodTarget));
    }
}
