package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.entity.MannequinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Menggambar badan pengganti memakai skin pemiliknya.
 *
 * <p>Skinnya diambil lewat {@link PlayerSkins}, yang sudah tahu cara mencari wajah
 * orang yang tidak ada di daftar pemain — dan itu justru keadaan biasa di sini, karena
 * badan ini memang hanya berdiri selama pemiliknya keluar.</p>
 */
public class MannequinRenderer extends LivingEntityRenderer<MannequinEntity, MannequinModel> {

    /** Lengan ramping dan lengan lebar butuh model terpisah; dipilih saat digambar. */
    private final MannequinModel wideModel;
    private final MannequinModel slimModel;

    public MannequinRenderer(EntityRendererProvider.Context context) {
        super(context, new MannequinModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new MannequinModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        // Lapisan armor vanilla: lewat sinilah seragam pesertanya tergambar, lengkap
        // dengan simbol di punggung. Tanpa ini badan penggantinya berdiri tanpa baju
        // dan simbolnya hilang dari permainan selama pemiliknya pergi.
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public void render(MannequinEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        this.model = PlayerSkins.isSlim(entity.getOwner()) ? this.slimModel : this.wideModel;
        super.render(entity, entityYaw, partialTick, pose, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MannequinEntity entity) {
        return PlayerSkins.texture(entity.getOwner());
    }
}
