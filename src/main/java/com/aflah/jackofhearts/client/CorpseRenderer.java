package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.entity.CorpseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Menggambar mayat memakai skin pemiliknya.
 *
 * <p>Skinnya diambil dari daftar pemain yang sudah dipegang client, bukan diunduh
 * sendiri: korban laser cuma dipindah ke mode penonton, tidak keluar dari server, jadi
 * dia pasti masih ada di daftar itu dan skinnya sudah termuat. Kalau ternyata tidak
 * ketemu — misalnya dia keluar setelah mati — dipakai skin bawaan.</p>
 */
public class CorpseRenderer extends LivingEntityRenderer<CorpseEntity, CorpseModel> {

    /** Lengan ramping dan lengan lebar butuh model terpisah; dipilih per mayat saat digambar. */
    private final CorpseModel wideModel;
    private final CorpseModel slimModel;

    public CorpseRenderer(EntityRendererProvider.Context context) {
        super(context, new CorpseModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new CorpseModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        // Lapisan armor vanilla. Seragam Jack punya modelnya sendiri lewat
        // IClientItemExtensions di SymbolArmorItem, dan Forge yang menyalin pose dari
        // model mayat ke model seragam itu — jadi seragamnya ikut ambruk tanpa kode
        // tambahan di sini.
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public void render(CorpseEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        this.model = isSlim(entity) ? this.slimModel : this.wideModel;
        super.render(entity, entityYaw, partialTick, pose, buffer, light);
    }

    /**
     * Memiringkan seluruh badan sampai rebah.
     *
     * <p>Pada titik ini titik nolnya masih di kaki mayat, jadi rotasi di sini berputar
     * bertumpu pada kakinya — persis seperti orang tumbang, bukan berputar di
     * pinggang.</p>
     *
     * <p>Memutar pada sumbu Z saja selalu menjatuhkan model ke sisi kirinya. Supaya
     * bisa roboh ke arah mana pun, sumbunya diapit: diputar dulu sebesar sudut roboh,
     * dimiringkan, lalu diputar balik. Yaw badannya sendiri jadi tidak ikut berubah —
     * mayatnya tetap menghadap ke arah terakhir korban berdiri, cuma tumbangnya yang
     * mengarah menjauhi laser.</p>
     */
    @Override
    protected void setupRotations(CorpseEntity entity, PoseStack pose, float ageInTicks,
                                  float bodyRot, float partialTick) {
        float t = entity.animationTime(partialTick);

        // Naik-turun badan dipasang sebelum rotasi apa pun supaya arahnya tetap tegak
        // lurus terhadap dunia, bukan ikut miring bersama badannya.
        pose.translate(0.0F, CorpseAnimation.bodyOffsetY(t), 0.0F);

        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));

        float topple = entity.getToppleAngle();
        pose.mulPose(Axis.YP.rotationDegrees(topple));
        pose.mulPose(Axis.ZP.rotationDegrees(CorpseAnimation.fallDegrees(t)));
        pose.mulPose(Axis.YP.rotationDegrees(-topple));
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity entity) {
        return PlayerSkins.texture(entity.getOwner());
    }

    private static boolean isSlim(CorpseEntity entity) {
        return PlayerSkins.isSlim(entity.getOwner());
    }

    @Override
    protected boolean shouldShowName(CorpseEntity entity) {
        return false;
    }
}
