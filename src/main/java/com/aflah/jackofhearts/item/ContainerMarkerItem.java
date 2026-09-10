package com.aflah.jackofhearts.item;

import com.aflah.jackofhearts.game.MilkSpots;
import com.aflah.jackofhearts.game.MilkSupply;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Penanda wadah: menunjuk peti, tungku, dispenser, hopper, atau tong sebagai tempat yang
 * boleh kejatuhan susu.
 *
 * <p>Perlu ditunjuk satu per satu, bukan dicari otomatis, karena map yang luas hampir
 * pasti punya wadah di luar arena permainan — peti penyimpanan, tungku dekorasi, hopper
 * mesin redstone. Susu yang mendarat di sana sama saja tidak pernah muncul.</p>
 *
 * <p>Klik kanan sekali untuk menunjuk, klik kanan lagi untuk mencabut. Daftarnya
 * tersimpan ke dunia, jadi cukup sekali disusun untuk semua permainan berikutnya.</p>
 */
public class ContainerMarkerItem extends Item {

    public ContainerMarkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) {
            // Client menebak hasilnya SUCCESS supaya tangannya tetap mengayun; yang
            // memutuskan tetap server.
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!MilkSupply.isSupply(state)) {
            player.displayClientMessage(Component.translatable("message.jackofhearts.marker_invalid")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        boolean added = MilkSpots.get(player.server).toggle(level.dimension(), pos);
        // Ditandai tetap, cuma diberi tahu. Jenis yang sedang dimatikan besok bisa
        // dinyalakan lagi, dan tanda yang sudah dipasang harus tetap berlaku saat itu -
        // menolak menandainya sekarang cuma memaksa seluruh map ditandai ulang nanti.
        if (added && !MilkSupply.isEnabled(state)) {
            player.sendSystemMessage(Component.translatable("message.jackofhearts.marker_type_off",
                    state.getBlock().getName()).withStyle(ChatFormatting.YELLOW));
        }
        player.displayClientMessage(Component.translatable(added
                        ? "message.jackofhearts.marker_added"
                        : "message.jackofhearts.marker_removed",
                        state.getBlock().getName(), MilkSpots.get(player.server).size())
                .withStyle(added ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);

        if (level instanceof ServerLevel serverLevel) {
            MilkSupply.mark(serverLevel, pos, added);
            serverLevel.playSound(null, pos, added ? SoundEvents.NOTE_BLOCK_BELL.value()
                            : SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.BLOCKS, 0.7F, added ? 1.6F : 0.8F);
            if (!added) {
                serverLevel.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D,
                        pos.getY() + 1.1D, pos.getZ() + 0.5D, 8, 0.2D, 0.1D, 0.2D, 0.01D);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.jackofhearts.marker").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.jackofhearts.marker.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
