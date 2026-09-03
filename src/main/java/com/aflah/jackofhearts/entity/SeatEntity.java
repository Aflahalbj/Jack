package com.aflah.jackofhearts.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Kursi tak terlihat yang dinaiki pemain saat duduk di tangga yang bisa diduduki.
 *
 * <p>Tidak punya bentuk, tidak bisa ditabrak atau diserang, dan tidak bergerak sama
 * sekali — satu-satunya tugasnya menjadi "kendaraan" supaya
 * {@link net.minecraft.world.entity.player.Player#getEyeHeight()} pemain duduk di
 * ketinggian yang benar. Entitas ini dibuang sendiri begitu tidak ada lagi yang
 * menungganginya, jadi tidak pernah menumpuk di dunia.</p>
 *
 * <p>{@code noSave()} di {@link com.aflah.jackofhearts.registry.ModEntities} berarti
 * entitas ini tidak pernah ikut tersimpan ke disk — kalau server dimatikan sementara
 * ada yang duduk, kursinya tidak akan muncul lagi setelah dinyalakan ulang. Itu
 * pilihan yang disengaja: kursi tanpa penumpang yang selamat dari restart cuma jadi
 * sampah entitas yang harus dibersihkan lagi.</p>
 */
public class SeatEntity extends Entity {

    public SeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData() {
        // Tidak ada data yang perlu disinkronkan; kursinya tidak punya state apa pun.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public double getPassengersRidingOffset() {
        // Ketinggian duduk sudah ditentukan lewat posisi spawn kursinya sendiri,
        // jadi penumpang tidak perlu diangkat lagi di atas itu.
        return 0.0D;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getPassengers().isEmpty()) {
            discard();
        }
    }
}
