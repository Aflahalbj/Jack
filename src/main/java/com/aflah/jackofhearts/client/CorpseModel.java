package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.entity.CorpseEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Model pemain yang seluruh sendinya diatur sendiri, bukan oleh animasi vanilla.
 *
 * <p>{@code setupAnim} bawaan menghitung pose dari jalan, ayunan tangan, dan arah
 * pandang — semuanya tidak relevan untuk mayat, dan kalau dibiarkan justru melawan
 * pose yang kita pasang. Jadi tidak ada panggilan {@code super} sama sekali di sini:
 * posenya ditulis penuh oleh {@link CorpseAnimation}.</p>
 */
public class CorpseModel extends PlayerModel<CorpseEntity> {

    public CorpseModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(CorpseEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // ageInTicks itu tickCount + partialTick, dan tickCount selalu bulat — jadi
        // bagian pecahannya persis partialTick. Dipungut dari sini karena setupAnim
        // tidak menerima partialTick, sementara umur animasinya harus dibaca dari
        // nilai yang disinkronkan server, bukan dari tickCount client.
        float partialTick = ageInTicks - Mth.floor(ageInTicks);
        float t = entity.animationTime(partialTick);

        CorpseAnimation.pose(this, t, CorpseAnimation.variance(entity.getUUID()));

        // Lapisan luar skin tidak punya pose sendiri; semuanya menempel ke bagian di
        // bawahnya. Biasanya PlayerModel.setupAnim yang mengurus ini.
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);

        // Jubah dan telinga sengaja tidak diurus: dua-duanya bukan bagian dari badan
        // model, melainkan digambar lewat method sendiri yang hanya dipanggil oleh
        // layer khusus. Renderer mayat tidak memasang layer itu, jadi keduanya memang
        // tidak pernah muncul.
    }
}
