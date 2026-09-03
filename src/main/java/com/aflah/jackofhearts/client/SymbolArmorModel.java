package com.aflah.jackofhearts.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Model seragam, layout UV mengikuti tekstur armor vanilla 64x32.
 *
 * <p>Simbol punggung dicetak langsung di muka belakang kotak badan — tanpa geometri
 * tambahan — jadi tidak ada tonjolan yang menambah tebal.</p>
 *
 * <p>Lengannya dipendekkan jadi {@link #SLEEVE_HEIGHT} unit (vanilla 12) supaya ujung
 * lengan dan tangan pemain tetap kelihatan.</p>
 *
 * <p><b>Kalau {@link #SLEEVE_HEIGHT} diubah</b>, tekstur lengan di UV (40,16) harus ikut
 * diubah tingginya: kotak lengan memakai {@code 4 + SLEEVE_HEIGHT} baris piksel. Dengan
 * tinggi 9 berarti area (40,16) sampai (56,29). Baris di bawahnya tidak terpakai; kalau
 * lengan dipanjangkan tanpa memperluas tekstur, ujung lengan jadi transparan.</p>
 */
public class SymbolArmorModel extends HumanoidModel<LivingEntity> {

    /**
     * Tebal badan baju. Patokan vanilla: chestplate 1.0, leggings 0.5.
     *
     * <p><b>Batas bawahnya 0.25</b>, karena lapisan luar skin pemain (jacket) sudah
     * duduk di 0.25. Apa pun yang lebih tipis dari itu tenggelam di dalam skin dan
     * permukaannya kedip-kedip. Praktisnya jangan turun di bawah ~0.3.</p>
     */
    private static final CubeDeformation BODY_DEFORMATION = new CubeDeformation(0.4F);
    /**
     * Tebal lengan, sengaja dipisah dari badan. Inflate itu menambah ukuran dalam
     * satuan absolut, bukan persentase: lengan pemain lebarnya cuma 4, jadi inflate
     * 0.5 membuatnya jadi 5 (+25%), sementara badan yang lebarnya 8 cuma naik 12%.
     * Kalau dua-duanya pakai angka yang sama, lengannya yang kelihatan menggembung.
     */
    private static final CubeDeformation SLEEVE_DEFORMATION = new CubeDeformation(0.3F);
    /**
     * Celana harus lebih tipis dari badan baju. Kotak "body" dirender oleh baju
     * <b>dan</b> celana, jadi kalau angkanya sama persis permukaannya bertabrakan
     * dan tekstur di pinggang kedip-kedip (z-fighting).
     */
    private static final CubeDeformation PANTS_DEFORMATION = new CubeDeformation(0.3F);
    /**
     * Panjang lengan baju dalam unit model. Kotak lengan vanilla tingginya 12 dan
     * menutupi sampai tangan; 9 menyisakan tepat 3 piksel kulit di ujung lengan.
     */
    private static final float SLEEVE_HEIGHT = 9.0F;

    public SymbolArmorModel(ModelPart root) {
        super(root, RenderType::armorCutoutNoCull);
    }

    /** Baju: badan + lengan. Simbol ada di tekstur muka belakang badan. */
    public static LayerDefinition createShirtLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(BODY_DEFORMATION, 0.0F);
        PartDefinition root = mesh.getRoot();

        // Ganti lengan bawaan: dipotong dari 12 jadi SLEEVE_HEIGHT, dan pakai inflate
        // sendiri yang lebih kecil supaya tidak menggembung. Pivot bahu tidak diubah.
        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(40, 16)
                        .addBox(-3.0F, -2.0F, -2.0F, 4.0F, SLEEVE_HEIGHT, 4.0F, SLEEVE_DEFORMATION),
                PartPose.offset(-5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(40, 16).mirror()
                        .addBox(-1.0F, -2.0F, -2.0F, 4.0F, SLEEVE_HEIGHT, 4.0F, SLEEVE_DEFORMATION),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Celana: pinggang + kedua kaki, setebal leggings vanilla. */
    public static LayerDefinition createPantsLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(PANTS_DEFORMATION, 0.0F);
        return LayerDefinition.create(mesh, 64, 32);
    }
}
