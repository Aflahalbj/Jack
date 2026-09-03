package com.aflah.jackofhearts.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Mayat peserta yang gugur kena laser.
 *
 * <p>Korban laser tidak pernah benar-benar dibunuh — dia langsung dijadikan penonton.
 * Artinya tidak ada animasi kematian vanilla yang bisa dipakai, dan badannya hilang
 * dalam satu frame. Entitas inilah penggantinya: dia muncul di tick yang sama persis
 * saat korban berpindah ke mode penonton, di posisi dan arah hadap yang sama, jadi
 * pertukarannya tidak terlihat. Sesudah itu dia yang memainkan animasi ambruknya dan
 * tetap tergeletak di situ, bisa dilihat korbannya sendiri sambil menonton.</p>
 *
 * <p>Ini {@link LivingEntity}, bukan {@link Entity} biasa seperti {@link SeatEntity} —
 * bukan karena butuh nyawa, tapi karena lapisan armor vanilla
 * ({@code HumanoidArmorLayer}) hanya mau menerima {@code LivingEntity}. Lewat jalur
 * itulah seragam korban ikut tergambar di mayatnya tanpa perlu kode render sendiri.
 * Nyawanya dikunci penuh dan semua kerusakan ditolak, jadi bagian "living"-nya tidak
 * pernah benar-benar jalan.</p>
 *
 * <p>Umur animasi disinkronkan lewat {@link #DATA_AGE}, bukan dihitung dari
 * {@code tickCount} client. Bedanya penting: {@code tickCount} client mulai dari nol
 * setiap kali entitasnya masuk jangkauan, jadi pemain yang baru mendekat akan melihat
 * mayat yang sudah lama tergeletak tiba-tiba mengulang animasi jatuhnya. Nilai yang
 * disinkronkan berhenti bertambah begitu animasinya selesai, jadi yang datang
 * belakangan langsung melihat pose akhirnya.</p>
 */
public class CorpseEntity extends LivingEntity {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> DATA_TOPPLE =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_AGE =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);

    /** Panjang animasi ambruk. Sesudah tick ini mayatnya diam selamanya. */
    public static final int ANIM_TICKS = 30;

    /**
     * Jarak antar penyegaran genangan darah, dalam tick.
     *
     * <p>Partikel debu umurnya sekitar satu detik, jadi genangannya harus dikirim
     * ulang terus supaya tidak menghilang. Jaraknya sengaja lebih rapat dari umur
     * partikelnya agar yang lama masih ada saat yang baru muncul — kalau tidak,
     * genangannya berkedip mati-hidup.</p>
     */
    private static final int POOL_INTERVAL = 15;

    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
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
        this.entityData.define(DATA_TOPPLE, 90.0F);
        this.entityData.define(DATA_AGE, 0);
    }

    // ------------------------------------------------------------------ pemasangan

    /**
     * Menyalin korban ke mayatnya: posisi, arah hadap, skin, seragam, dan arah roboh.
     *
     * <p>Harus dipanggil <b>sebelum</b> seragam korban dilepas, karena isi slot armor
     * disalin apa adanya dari pemain.</p>
     *
     * @param laserOrigin titik asal sinar; mayatnya roboh ke arah menjauh dari sini.
     *                    Boleh null kalau tidak diketahui — mayatnya roboh ke belakang.
     */
    public void copyFrom(LivingEntity victim, @Nullable Vec3 laserOrigin) {
        this.moveTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0.0F);
        // Rotasi badan dan kepala dikunci ke nilai yang sama di dua-duanya (nilai
        // sekarang dan nilai tick sebelumnya). Kalau yang lama dibiarkan nol, render
        // frame pertama menginterpolasi dari yaw 0 dan mayatnya terlihat berputar
        // sekejap sebelum diam.
        this.yBodyRot = victim.getYRot();
        this.yBodyRotO = victim.getYRot();
        this.yHeadRot = victim.getYRot();
        this.yHeadRotO = victim.getYRot();
        this.yRotO = victim.getYRot();
        this.setYHeadRot(victim.getYRot());

        this.entityData.set(DATA_OWNER, Optional.of(victim.getUUID()));
        this.entityData.set(DATA_TOPPLE, toppleAngle(victim, laserOrigin));

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setItemSlot(slot, victim.getItemBySlot(slot).copy());
        }
        this.setHealth(this.getMaxHealth());
    }

    /**
     * Sudut yang dipakai renderer untuk memutar sumbu robohnya.
     *
     * <p>Rotasi jatuh vanilla selalu menjatuhkan model ke sisi <b>kiri</b>-nya. Supaya
     * mayat bisa roboh ke arah mana pun, sumbu itu diputar dulu sebesar sudut ini.
     * Pemetaannya: 270 berarti roboh ke depan, 180 ke kanan, 90 ke belakang, 0 ke kiri
     * — makanya rumusnya {@code 270 - (arah dorong - arah hadap)}.</p>
     *
     * <p>Arah dorongnya menjauh dari sumber laser, jadi korban terlihat terdorong oleh
     * tembakannya. Kalau lasernya turun tegak lurus dari langit, arah mendatarnya tidak
     * terdefinisi dan mayatnya dijatuhkan ke belakang — itu yang paling masuk akal,
     * karena korban memang sedang mendongak ke langit saat tertembak.</p>
     */
    private static float toppleAngle(LivingEntity victim, @Nullable Vec3 laserOrigin) {
        if (laserOrigin == null) {
            return 90.0F;
        }
        double dx = victim.getX() - laserOrigin.x;
        double dz = victim.getZ() - laserOrigin.z;
        if (dx * dx + dz * dz < 1.0E-4D) {
            return 90.0F;
        }
        float pushYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        return Mth.wrapDegrees(270.0F - (pushYaw - victim.getYRot()));
    }

    // ------------------------------------------------------------------ state

    @Nullable
    public UUID getOwner() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public float getToppleAngle() {
        return this.entityData.get(DATA_TOPPLE);
    }

    /** Umur animasi dalam tick, sudah dibatasi di ujungnya. */
    public float animationTime(float partialTick) {
        int age = this.entityData.get(DATA_AGE);
        return age >= ANIM_TICKS ? ANIM_TICKS : Math.min(age + partialTick, ANIM_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level) {
            int age = this.entityData.get(DATA_AGE);
            if (age < ANIM_TICKS) {
                this.entityData.set(DATA_AGE, age + 1);
                bleedWhileFalling(level, age);
            } else if (this.tickCount % POOL_INTERVAL == 0) {
                Blood.pool(level, poolCenter());
            }
        }
    }

    /**
     * Tetesan yang mengikuti badan selama ambruk: titik keluarnya turun dari kepala ke
     * tanah sambil bergeser ke arah robohnya, jadi darahnya menyusuri jalur jatuhnya
     * badan, bukan menyembur di satu titik tetap.
     */
    private void bleedWhileFalling(ServerLevel level, int age) {
        if (age % 2 != 0) {
            return;
        }
        float progress = age / (float) ANIM_TICKS;
        Vec3 at = this.position()
                .add(lyingDirection().scale(progress * 0.9D))
                .add(0.0D, Mth.lerp(progress, 1.55D, 0.35D), 0.0D);
        Blood.drip(level, at);
    }

    /**
     * Genangannya ditaruh di bawah <b>badan</b>, bukan di bawah kaki. Titik nol mayat
     * ada di kakinya, sedangkan setelah rebah badannya menjulur hampir satu setengah
     * blok ke arah robohnya — genangan di titik nol akan muncul di dekat sepatunya.
     */
    private Vec3 poolCenter() {
        return this.position().add(lyingDirection().scale(0.9D)).add(0.0D, 0.05D, 0.0D);
    }

    /**
     * Arah mendatar tempat badannya rebah, dihitung balik dari sudut roboh — kebalikan
     * dari rumus di {@link #toppleAngle}.
     */
    private Vec3 lyingDirection() {
        float yaw = (this.getYRot() + 270.0F - getToppleAngle()) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    /**
     * Kotak yang dipakai untuk memutuskan mayatnya masih terlihat atau tidak. Kotak
     * tabrakannya seukuran pemain berdiri, sedangkan badan yang sudah rebah menjulur
     * hampir dua blok ke samping — tanpa pelebaran ini mayatnya berkedip hilang begitu
     * kotak sempit itu keluar layar padahal badannya masih terlihat.
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(2.0D);
    }

    // ------------------------------------------------------------------ mayat tidak ikut campur

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
        // Mayatnya ditembus, bukan menghalangi: tidak mendorong siapa pun.
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
        // Tetap "bergerak" supaya gravitasi LivingEntity jalan dan mayatnya jatuh ke
        // tanah kalau korban tertembak sambil melompat atau berdiri di tepi.
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
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

    @Override
    protected void dropAllDeathLoot(DamageSource source) {
        // Mayat ini murni tampilan. Barang aslinya tetap di tangan pemiliknya yang
        // sekarang jadi penonton, jadi tidak ada yang boleh jatuh dari sini.
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
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return attacker instanceof Player;
    }
}
