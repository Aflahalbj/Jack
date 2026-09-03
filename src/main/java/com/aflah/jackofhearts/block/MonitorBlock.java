package com.aflah.jackofhearts.block;

import com.aflah.jackofhearts.blockentity.MonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import javax.annotation.Nullable;

/**
 * Papan peserta. Satu blok yang memproyeksikan seluruh papan berukuran
 * {@value #BOARD_WIDTH} x {@value #BOARD_HEIGHT} blok di depannya — bukan 91 blok
 * yang harus disusun satu per satu.
 *
 * <p>Blok ini adalah <b>sudut kiri-bawah</b> papan dilihat dari depan; papannya
 * memanjang ke kanan dan ke atas dari sini. Konvensi sudut, bukan titik tengah,
 * dipilih supaya posisinya bisa dihitung dengan pasti saat membangun dinding: kamu
 * tahu persis blok mana yang harus dipasangi tanpa perlu membagi dua lebar papan yang
 * kebetulan ganjil.</p>
 *
 * <p>Isi papannya sendiri tidak disimpan di sini. Yang digambar diambil dari salinan
 * roster di client, jadi satu ruangan boleh punya beberapa papan dan semuanya
 * menampilkan hal yang sama tanpa perlu disinkronkan satu-satu.</p>
 */
public class MonitorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /**
     * Lebar papan dalam blok. Ada di sisi common, bukan di renderer, karena server
     * juga perlu tahu ukurannya: kamera yang diarahkan ke papan harus mundur cukup
     * jauh untuk memuat seluruh bidangnya.
     */
    public static final int BOARD_WIDTH = 13;
    /** Tinggi papan dalam blok. */
    public static final int BOARD_HEIGHT = 7;

    public MonitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // FACING adalah arah muka layarnya, jadi kebalikan dari arah hadap pemain —
        // sama seperti GuessBlock, supaya layarnya menghadap orang yang memasang.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }
}
