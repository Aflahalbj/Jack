package com.aflah.jackofhearts.blockentity;

import com.aflah.jackofhearts.block.MonitorBlock;
import com.aflah.jackofhearts.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Block entity papan peserta.
 *
 * <p>Tidak menyimpan state apa pun. Keberadaannya semata-mata supaya papannya punya
 * tempat menggantungkan renderer — isi papan dibaca langsung dari salinan roster di
 * client saat digambar.</p>
 *
 * <p>Satu-satunya tugas nyatanya adalah mencatatkan posisinya saat dimuat, di kedua
 * sisi. Di client daftar itu dipakai supaya bunyi panel padam keluar dari papannya
 * dan bukan dari kepala pemain; di server dipakai untuk menempatkan kamera yang
 * mengarahkan semua orang ke papan setelah eksekusi. Tanpa catatan itu, mencari satu
 * blok di dunia yang luasnya tak terbatas terlalu mahal untuk dilakukan setiap
 * ronde.</p>
 */
public class MonitorBlockEntity extends BlockEntity {

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONITOR.get(), pos, state);
    }

    /**
     * Kotak yang dipakai memutuskan papan ini masih perlu digambar atau tidak.
     *
     * <p>Bawaannya cuma satu blok, padahal yang digambar membentang 13 blok ke samping
     * dan 7 ke atas. Tanpa pelebaran ini papannya berkedip hilang setiap kali blok
     * pengendalinya sendiri lepas dari pandangan — padahal bidang papannya masih
     * memenuhi layar. Dilebarkan ke segala arah karena arah hadapnya baru diketahui
     * saat digambar, dan kotak yang kelewat besar cuma berarti satu pemeriksaan yang
     * lebih longgar.</p>
     */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition).inflate(
                MonitorBlock.BOARD_WIDTH, MonitorBlock.BOARD_HEIGHT, MonitorBlock.BOARD_WIDTH);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level == null) {
            return;
        }
        if (this.level.isClientSide) {
            // DistExecutor, bukan sekadar cabang if: kelas client tidak boleh sampai
            // ikut dimuat di server khusus.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.aflah.jackofhearts.client.ClientMonitors.add(this.worldPosition));
        } else {
            MonitorTracker.add(this.level.dimension(), this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null) {
            if (this.level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.aflah.jackofhearts.client.ClientMonitors.remove(this.worldPosition));
            } else {
                MonitorTracker.remove(this.level.dimension(), this.worldPosition);
            }
        }
        super.setRemoved();
    }
}
