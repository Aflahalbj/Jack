package com.aflah.jackofhearts.block;

import com.aflah.jackofhearts.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Blok terminal tebakan. Klik kanan untuk membuka GUI pilihan simbol.
 *
 * <p>Muka bergambar simbol ada di sisi utara model ({@code guess_block.json}), dan
 * lewat {@link #FACING} muka itu diputar menghadap pemain yang menaruhnya — persis
 * seperti furnace atau dispenser vanilla.</p>
 */
public class GuessBlock extends HorizontalDirectionalBlock {

    public GuessBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Blok menghadap pemain, bukan mengikuti arah klik: FACING adalah arah muka
        // depannya, jadi kebalikan dari arah hadap pemain saat menaruh blok.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            GameManager.get().onGuessBlockUsed(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
