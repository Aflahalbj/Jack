package com.aflah.jackofhearts.block;

import com.aflah.jackofhearts.game.GameManager;
import com.aflah.jackofhearts.game.GamePhase;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Pintu sel: pintu baja dua blok tinggi dengan celah intip berjeruji setinggi mata,
 * seperti pintu ruang tahanan di permainan Jack of Hearts.
 *
 * <p>Celah intipnya <b>lubang sungguhan</b>, bukan gambar lubang: piksel di situ
 * beralpha nol dan modelnya digambar di layer {@code cutout}, jadi orang di luar
 * benar-benar melihat isi sel lewat celah itu — termasuk pemain yang bergerak di
 * baliknya.</p>
 *
 * <h2>Kuncinya dipegang permainan, bukan pemain</h2>
 *
 * <p>Tidak ada cara manual untuk mengunci atau membuka kunci pintu ini, dan cuma ada
 * satu alasan ia terkunci:</p>
 * <ul>
 *   <li><b>Biasanya</b> — tidak terkunci. Lampu status hijau, gerendel ditarik masuk.
 *       Siapa pun boleh membuka dan melewatinya, masuk maupun keluar, kapan pun.</li>
 *   <li><b>Ada yang masuk selagi fase menebak berjalan</b> — pintu membanting menutup
 *       dan mengunci di belakangnya. Lampu status merah, gerendel terlempar penuh.
 *       Klik kanan cuma menghasilkan bunyi gerendel, dan sinyal redstone pun tidak
 *       membukanya. Kuncinya lepas sendiri begitu fase menebak berakhir.</li>
 * </ul>
 *
 * <p>Kurungannya sengaja <b>tidak pernah lebih lama dari fase menebak</b>: begitu fase
 * itu berakhir, semua pintu dibuka kuncinya tanpa syarat. Versi sebelumnya menahan
 * kunci sampai ruangannya kosong atau sampai penghuninya keluar sendiri, dan itu yang
 * bikin pemain terjebak lebih lama dari yang pernah dimaksudkan aturan permainan.</p>
 *
 * <p>Dua tandanya sengaja beda jenis, bukan sekadar beda warna: lampu terbaca dari
 * jauh dalam sekejap, gerendel terbaca dari dekat dan tetap jelas untuk pemain yang
 * tidak bisa membedakan merah dengan hijau.</p>
 */
public class PrisonDoorBlock extends DoorBlock {

    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;

    public PrisonDoorBlock(Properties properties) {
        // BlockSetType.IRON: suara pintu besi, dan canOpenByHand-nya false supaya
        // zombie tidak pernah dianggap bisa mendobraknya. Buka-tutup untuk pemain
        // diurus use() di bawah, bukan lewat sifat itu.
        super(properties, BlockSetType.IRON);
        registerDefaultState(defaultBlockState().setValue(LOCKED, Boolean.TRUE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOCKED);
    }

    // ------------------------------------------------------------------ pencatatan

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel serverLevel && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            PrisonDoorTracker.add(serverLevel.dimension(), pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)
                && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            PrisonDoorTracker.remove(serverLevel.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Pintu yang baru dipasang langsung mengikuti fase yang sedang berjalan,
        // supaya tidak ada pintu menganga di tengah ronde cuma karena baru ditaruh.
        if (!level.isClientSide) {
            setLockedByGame(level, pos, level.getBlockState(pos),
                    GameManager.get().getPhase() != GamePhase.GUESS);
        }
    }

    // ------------------------------------------------------------------ dua separuh

    /** Posisi separuh bawah pintu, dihitung dari separuh mana pun. */
    private static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /**
     * Tulis satu sifat ke <b>kedua</b> separuh pintu, bukan cuma separuh yang diklik.
     *
     * <p>Vanilla cuma menulis separuh yang disentuh, lalu mengandalkan rambatan
     * {@code updateShape} untuk menyamakan separuh satunya. Di sini keduanya ditulis
     * langsung: hasilnya tidak lagi bergantung pada rambatan itu, dan pintu yang
     * separuhnya terlanjur berbeda memperbaiki dirinya sendiri.</p>
     *
     * @return true kalau ada separuh yang benar-benar berubah
     */
    private boolean writeBothHalves(Level level, BlockPos pos, BlockState state,
                                    BooleanProperty property, boolean value) {
        BlockPos lower = lowerPos(state, pos);
        boolean changed = false;
        for (BlockPos halfPos : List.of(lower, lower.above())) {
            BlockState halfState = level.getBlockState(halfPos);
            if (halfState.is(this) && halfState.getValue(property) != value) {
                level.setBlock(halfPos, halfState.setValue(property, value), UPDATE_CLIENTS | UPDATE_IMMEDIATE);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Buka/tutup pintu utuh. Menggantikan {@link #setOpen} bawaan DoorBlock, yang
     * cuma menyentuh satu blok.
     */
    private void setOpenBothHalves(@Nullable Player player, Level level, BlockPos pos,
                                   BlockState state, boolean open) {
        if (!writeBothHalves(level, pos, state, OPEN, open)) {
            return;
        }
        level.playSound(null, pos, open ? type().doorOpen() : type().doorClose(),
                SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    /**
     * Setel kunci pintu dari mesin permainan.
     *
     * <p>Mengunci berarti <b>menutup dulu</b> — terkunci tapi daunnya masih menganga
     * itu omong kosong. Membuka kunci <b>tidak</b> ikut membuka daunnya: pintunya
     * tetap rapat, cuma jadi boleh dibuka. Peserta yang membuka dan melangkah masuk
     * sendiri adalah bagian dari permainannya; pintu yang membuka sendiri merampas
     * bagian itu.</p>
     */
    public void setLockedByGame(Level level, BlockPos pos, BlockState state, boolean locked) {
        if (locked) {
            setOpenBothHalves(null, level, pos, state, false);
        }
        if (writeBothHalves(level, pos, state, LOCKED, locked)) {
            level.playSound(null, pos,
                    locked ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundSource.BLOCKS, 0.9F, locked ? 0.6F : 1.2F);
            level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
        }
    }

    // ------------------------------------------------------------------ interaksi

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(LOCKED)) {
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 0.6F);
            player.displayClientMessage(
                    Component.translatable("message.jackofhearts.prison_door.locked").withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.CONSUME;
        }
        setOpenBothHalves(player, level, pos, state, !state.getValue(OPEN));
        return InteractionResult.CONSUME;
    }

    /**
     * Jeda sebelum pintu membanting setelah dilewati, dalam tick.
     *
     * <p>Bukan nol supaya pemainnya sempat keluar dari ambang pintu dulu. Pintu yang
     * menutup tepat di tick yang sama dengan langkah masuknya akan menutup di badan
     * orangnya, bukan di belakangnya.</p>
     */
    private static final int SLAM_DELAY_TICKS = 6;

    /**
     * Satu-satunya hal yang mengunci pintu ini: ada yang masuk selagi fase menebak
     * berjalan. Pintunya membanting menutup dan mengunci di belakangnya.
     *
     * <p>Yang dijadwalkan cuma tick-nya, bukan penguncian itu sendiri — lihat
     * {@link #SLAM_DELAY_TICKS}.</p>
     */
    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || state.getValue(LOCKED) || !state.getValue(OPEN)) {
            return;
        }
        if (GameManager.get().getPhase() != GamePhase.GUESS) {
            return;
        }
        if (!(entity instanceof ServerPlayer player)
                || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            return;
        }
        // Badan pemain menyentuh KEDUA separuh pintu, jadi metode ini dipanggil dua kali
        // di tick yang sama. Penjagaannya dipusatkan ke separuh bawah — satu titik untuk
        // satu pintu.
        BlockPos lower = lowerPos(state, pos);
        if (level.getBlockTicks().hasScheduledTick(lower, this)) {
            return;
        }
        level.scheduleTick(lower, this, SLAM_DELAY_TICKS);
        player.displayClientMessage(
                Component.translatable("message.jackofhearts.prison_door.slammed").withStyle(ChatFormatting.RED),
                true);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Fasenya diperiksa ulang: jeda membanting bisa saja terlewati oleh berakhirnya
        // fase menebak, dan begitu fase itu lewat pintunya justru harus terbuka.
        if (state.getValue(LOCKED) || GameManager.get().getPhase() != GamePhase.GUESS) {
            return;
        }
        setLockedByGame(level, pos, state, true);
    }

    // ------------------------------------------------------------------ vanilla

    /**
     * Kedua separuh pintu harus selalu sepakat soal kuncinya; kalau tidak, separuh
     * atas bisa menyala hijau sementara gerendel di separuh bawah masih terpasang.
     *
     * <p>Vanilla sudah menyalin FACING/OPEN/HINGE/POWERED dari separuh seberang di
     * sini — {@link #LOCKED} cuma ikut menumpang jalur yang sama.</p>
     */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        DoubleBlockHalf half = state.getValue(HALF);
        boolean fromOtherHalf = direction.getAxis() == Direction.Axis.Y
                && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)
                && neighborState.is(this)
                && neighborState.getValue(HALF) != half;
        return fromOtherHalf && updated.is(this)
                ? updated.setValue(LOCKED, neighborState.getValue(LOCKED))
                : updated;
    }

    /**
     * Redstone tetap dicatat saat pintu terkunci, tapi tidak membukanya — kunci
     * menang atas arus.
     *
     * <p>Ditulis ulang penuh (tidak memanggil super) karena vanilla cuma mengubah
     * blok yang menerima kabar, sementara di sini kedua separuh pintu selalu ditulis
     * berbarengan.</p>
     */
    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        if (defaultBlockState().is(block)) {
            // Kabarnya datang dari separuh pintu ini sendiri; vanilla pun mengabaikannya,
            // kalau tidak menaruh separuh atas akan langsung memicu perhitungan ulang.
            return;
        }
        BlockPos lower = lowerPos(state, pos);
        boolean signal = level.hasNeighborSignal(lower) || level.hasNeighborSignal(lower.above());
        if (signal == state.getValue(POWERED)) {
            return;
        }
        writeBothHalves(level, pos, state, POWERED, signal);
        if (!state.getValue(LOCKED) && signal != state.getValue(OPEN)) {
            setOpenBothHalves(null, level, pos, state, signal);
        }
    }
}
