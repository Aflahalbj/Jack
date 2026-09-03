package com.aflah.jackofhearts.block;

import com.aflah.jackofhearts.entity.SeatEntity;
import com.aflah.jackofhearts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/**
 * Tangga yang bisa diduduki lewat klik kanan. Bentuk, tekstur, dan shape hitboxnya
 * persis tangga vanilla — ini murni {@link StairBlock} biasa dengan satu interaksi
 * tambahan — jadi blockstate dan model-nya menunjuk langsung ke resource vanilla
 * ({@code minecraft:block/quartz_stairs}, dst.) tanpa menduplikasi tekstur apa pun.
 *
 * <p>Duduknya lewat {@link SeatEntity}: entitas tak terlihat yang di-spawn di atas
 * anak tangga lalu dinaiki pemain. Sengaja bukan memindahkan posisi pemain secara
 * langsung, karena menunggangi entitas otomatis mendapat semua penanganan vanilla
 * untuk lepas naik (tombol sneak) dan sinkronisasi ke pemain lain.</p>
 */
public class SittableStairBlock extends StairBlock {

    /**
     * Tinggi duduk di atas dasar blok. Anak tangga rendah (bagian yang solid penuh
     * selebar blok) permukaannya di Y+0.5; angka ini sedikit di bawah itu karena pose
     * duduk pemain tidak benar-benar menurunkan pinggangnya ke permukaan — kalau
     * dipasang persis di Y+0.5, badan tetap terlihat "melayang" di atas anak tangga.
     * Tidak dibedakan antara {@code HALF.BOTTOM} dan {@code HALF.TOP} — ini fitur
     * dekoratif, bukan simulasi fisik anak tangga.
     */
    private static final double SEAT_HEIGHT = 0.25D;

    /**
     * Geseran mendatar ke arah <b>berlawanan</b> dengan {@code FACING}. Untuk tangga,
     * {@code FACING} menunjuk ke sisi yang <b>tinggi</b> (arah placer menghadap saat
     * memasang), sedangkan sisi rendah dan terbuka — tempat yang wajar diduduki —
     * ada di sisi sebaliknya. Tanpa geseran ini titik duduk persis di tengah blok, pas
     * di batas antara kedua sisi, sehingga separuh badan menembus bagian yang tinggi.
     */
    private static final double SEAT_FORWARD_OFFSET = 0.15D;

    public SittableStairBlock(Supplier<BlockState> baseState, Properties properties) {
        super(baseState, properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // Sneak melewati duduk, supaya blok ini tetap bisa diperlakukan seperti
        // tangga biasa saat pemain sedang menempatkan sesuatu di atasnya.
        if (player.isShiftKeyDown() || player.isPassenger()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        AABB searchBox = new AABB(pos).inflate(0.2D);
        if (!level.getEntitiesOfClass(SeatEntity.class, searchBox).isEmpty()) {
            // Sudah ada yang duduk di sini.
            return InteractionResult.PASS;
        }

        SeatEntity seat = ModEntities.SEAT.get().create(level);
        if (seat == null) {
            return InteractionResult.PASS;
        }

        // Menghadap berlawanan dengan arah tangga, seperti duduk membelakangi
        // anak tangga yang naik.
        Direction facing = state.getValue(FACING);
        seat.setYRot(facing.getOpposite().toYRot());

        double x = pos.getX() + 0.5D - facing.getStepX() * SEAT_FORWARD_OFFSET;
        double z = pos.getZ() + 0.5D - facing.getStepZ() * SEAT_FORWARD_OFFSET;
        seat.setPos(x, pos.getY() + SEAT_HEIGHT, z);

        level.addFreshEntity(seat);
        player.startRiding(seat);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // Blok yang diduduki hancur/diganti: lepaskan siapa pun yang masih duduk
            // supaya tidak menggantung di kursi tak terlihat yang kehilangan alasnya.
            for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, new AABB(pos).inflate(0.2D))) {
                seat.getPassengers().forEach(net.minecraft.world.entity.Entity::stopRiding);
                seat.discard();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
