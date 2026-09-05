package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.entity.MannequinEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Model pemain yang benar-benar diam.
 *
 * <p>{@code setupAnim} bawaan tidak cuma mengurus jalan dan ayunan tangan: ada juga
 * goyangan halus yang selalu berjalan — lengan yang bergerak pelan mengikuti waktu,
 * dan kepala yang menoleh mengikuti arah pandang. Untuk badan pengganti, gerakan
 * sekecil apa pun justru masalahnya: yang berdiri di situ harus terbaca sebagai orang
 * yang sedang tidak ada, bukan orang yang diam menunggu. Jadi tidak ada panggilan
 * {@code super} sama sekali — semua sendinya dinolkan.</p>
 */
public class MannequinModel extends PlayerModel<MannequinEntity> {

    public MannequinModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(MannequinEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.setRotation(0.0F, 0.0F, 0.0F);
        this.body.setRotation(0.0F, 0.0F, 0.0F);
        this.rightArm.setRotation(0.0F, 0.0F, 0.0F);
        this.leftArm.setRotation(0.0F, 0.0F, 0.0F);
        this.rightLeg.setRotation(0.0F, 0.0F, 0.0F);
        this.leftLeg.setRotation(0.0F, 0.0F, 0.0F);

        // Lapisan luar skin tidak punya pose sendiri; semuanya menempel ke bagian di
        // bawahnya. Biasanya PlayerModel.setupAnim yang mengurus ini.
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
    }
}
