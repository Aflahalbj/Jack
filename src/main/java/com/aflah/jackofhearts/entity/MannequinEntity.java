package com.aflah.jackofhearts.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Badan pengganti peserta yang sedang keluar dari server.
 *
 * <p>Peserta yang offline tidak bisa dihapus begitu saja dari permainan: dia masih
 * punya peran, masih punya simbol di punggung, dan masih terhitung hidup. Tapi
 * badannya ikut hilang bersama koneksinya, dan yang tersisa cuma nama di papan.
 * Entitas inilah badannya: berdiri di tempat terakhir dia berdiri, memakai skin dan
 * seragamnya, jadi ruangan tetap berisi orang yang jumlahnya sama dengan yang
 * tertulis di papan.</p>
 *
 * <p>{@link LivingEntity}, bukan {@link Entity} biasa, dengan alasan yang sama seperti
 * {@link CorpseEntity}: lapisan armor vanilla hanya mau menerima {@code LivingEntity},
 * dan lewat jalur itulah seragam pesertanya ikut tergambar lengkap dengan simbolnya.
 * Nyawanya sendiri tidak dipakai — semua kerusakan ditolak di sini. Lapar dan nyawa
 * pemiliknya dihitung di luar entitas ini, di
 * {@link com.aflah.jackofhearts.game.OfflinePlayers}, karena keduanya harus tetap
 * berjalan (dan tetap bisa dikembalikan ke pemainnya) walau entitasnya kebetulan
 * belum termuat.</p>
 *
 * <p>Yang bisa menyentuhnya cuma laser fase menebak. Itu memang satu-satunya cara
 * peserta offline gugur karena orang lain — dan karena badan ini tidak bisa dipukul,
 * dilempar, atau didorong, keluar dari server tidak pernah jadi cara untuk menghindari
 * atau justru mengundang pembunuhan.</p>
 */
public class MannequinEntity extends LivingEntity {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(MannequinEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /**
     * Tinggi mata pemain berdiri.
     *
     * <p>Ditulis tetap, bukan dibiarkan memakai hitungan bawaan {@code LivingEntity}
     * (0,85 x tinggi = 1,53), karena inilah titik yang dikunci laser. Kalau berbeda
     * dari pemain sungguhan, sinar yang menembak badan pengganti akan mendarat di dada,
     * bukan di kepala.</p>
     */
    private static final float EYE_HEIGHT = 1.62F;

    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);

    public MannequinEntity(EntityType<? extends MannequinEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER, Optional.empty());
    }

    /**
     * Menyalin pemiliknya: posisi, arah hadap, dan pemilik skinnya.
     *
     * <p>Arah hadapnya dikunci di badan sekaligus kepala, dan nilai tick sebelumnya
     * ikut diisi. Kalau yang lama dibiarkan nol, frame pertama menginterpolasi dari
     * yaw 0 dan badannya terlihat berputar sekejap sebelum diam.</p>
     */
    public void copyFrom(LivingEntity owner) {
        this.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0.0F);
        this.setFacing(owner.getYRot());
        this.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setItemSlot(slot, owner.getItemBySlot(slot).copy());
        }
        this.setHealth(this.getMaxHealth());
    }

    /** Memutar seluruh badan ke satu arah, tanpa menyisakan sisa putaran frame lama. */
    public void setFacing(float yaw) {
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.yHeadRot = yaw;
        this.yHeadRotO = yaw;
        this.setYHeadRot(yaw);
    }

    public UUID getOwner() {
        return this.entityData.get(DATA_OWNER).orElse(this.getUUID());
    }

    public void setOwner(UUID uuid) {
        this.entityData.set(DATA_OWNER, Optional.of(uuid));
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return EYE_HEIGHT;
    }

    // ------------------------------------------------------------------ tidak ikut campur

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    protected void pushEntities() {
        // Ditembus, bukan menghalangi: tidak mendorong siapa pun.
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    protected boolean isImmobile() {
        // Tetap "bergerak" supaya gravitasi LivingEntity jalan: pemain yang keluar
        // sambil melompat atau berdiri di tepi tetap mendarat di lantai.
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void dropAllDeathLoot(DamageSource source) {
        // Murni tampilan. Barang aslinya ikut pemiliknya yang sedang offline.
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        return true;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return attacker instanceof Player;
    }

    // ------------------------------------------------------------------ perlengkapan

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armor;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> this.hands.get(slot.getIndex());
            case ARMOR -> this.armor.get(slot.getIndex());
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        this.verifyEquippedItem(stack);
        switch (slot.getType()) {
            case HAND -> this.hands.set(slot.getIndex(), stack);
            case ARMOR -> this.armor.set(slot.getIndex(), stack);
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
