package com.aflah.jackofhearts.client;

import com.aflah.jackofhearts.entity.SeatEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer kosong. {@link SeatEntity} memang tidak boleh terlihat, tapi Forge tetap
 * mewajibkan setiap entity type punya renderer terdaftar — tanpa ini client akan
 * error "No renderer registered" begitu kursinya muncul.
 */
public class SeatEntityRenderer extends EntityRenderer<SeatEntity> {

    public SeatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(SeatEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(SeatEntity entity) {
        // shouldRender() di atas selalu false, jadi ini praktis tidak pernah dipanggil;
        // nilainya cuma perlu ada karena method-nya abstract.
        return MissingTextureAtlasSprite.getLocation();
    }
}
