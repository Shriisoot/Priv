package com.shriisoot.spiderman.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shriisoot.spiderman.SpiderManMod;
import com.shriisoot.spiderman.entity.SpiderManEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class SpiderManRenderer extends HumanoidMobRenderer<SpiderManEntity, SpiderManModel> {
    private static final ResourceLocation TEXTURE = SpiderManMod.id("textures/entity/spider_man.png");
    private static final float PLAYER_SCALE = 0.9375F;

    public SpiderManRenderer(EntityRendererProvider.Context context) {
        super(context, new SpiderManModel(context.bakeLayer(SpiderManModel.LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiderManEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(SpiderManEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
    }

    @Override
    public Vec3 getRenderOffset(SpiderManEntity entity, float partialTick) {
        float perch = SpiderManModel.ease(Mth.lerp(partialTick, entity.perchPoseO, entity.perchPose));
        return new Vec3(0, -0.3 * perch * PLAYER_SCALE, 0);
    }
}
