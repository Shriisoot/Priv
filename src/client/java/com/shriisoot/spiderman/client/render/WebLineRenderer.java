package com.shriisoot.spiderman.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shriisoot.spiderman.SpiderManMod;
import com.shriisoot.spiderman.entity.SpiderManEntity;
import com.shriisoot.spiderman.entity.WebLineEntity;
import com.shriisoot.spiderman.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Draws the web strand from the shooter's hand to the anchor as two crossed ribbons, plus a splat where it sticks. */
public class WebLineRenderer extends EntityRenderer<WebLineEntity> {
    private static final ResourceLocation TEXTURE = SpiderManMod.id("textures/entity/web_line.png");
    private static final float HALF_WIDTH = 0.032F;
    private static final int SEGMENTS = 16;

    public WebLineRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(WebLineEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return entity.getOwnerEntity() != null;
    }

    @Override
    public ResourceLocation getTextureLocation(WebLineEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(WebLineEntity web, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Entity owner = web.getOwnerEntity();
        if (owner == null) {
            return;
        }
        Vec3 tip = web.getPosition(partialTick);
        Vec3 hand = handPosition(owner, tip, partialTick);

        // Webs that spawn already stuck (Spider-Man's yanks/zips) visibly shoot out over a few frames.
        float extend = web.getMode() == WebLineEntity.MODE_YANK ? Mth.clamp((web.tickCount + partialTick) / 3.0F, 0F, 1F) : 1F;
        Vec3 start = hand.subtract(tip);
        Vec3 end = start.scale(1.0 - extend);
        float slack = web.isAnchored() || web.getHookedEntity() != null ? (1F - extend) * 0.4F : 0.12F;

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        strand(consumer, pose, start, end, slack, light);

        if (web.isAnchored() && extend >= 1F && web.getHookedEntity() == null) {
            splat(consumer, pose, start.normalize(), light);
        }
        super.render(web, yaw, partialTick, poseStack, buffers, light);
    }

    private Vec3 handPosition(Entity owner, Vec3 tip, float partialTick) {
        int side = 1;
        if (owner instanceof Player player) {
            boolean mainHand = player.getMainHandItem().is(ModItems.WEB_SHOOTER);
            side = (player.getMainArm() == HumanoidArm.RIGHT) == mainHand ? 1 : -1;
            Minecraft mc = Minecraft.getInstance();
            if (player == mc.player && entityRenderDispatcher.options.getCameraType().isFirstPerson()) {
                double fovScale = 960.0 / entityRenderDispatcher.options.fov().get().intValue();
                Vec3 offset = entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(side * 0.525F, -0.1F).scale(fovScale);
                return player.getEyePosition(partialTick).add(offset);
            }
        }
        if (owner instanceof LivingEntity living) {
            float bodyYaw = Mth.lerp(partialTick, living.yBodyRotO, living.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0, -Mth.sin(bodyYaw));
            double shoulderHeight = living.getBbHeight() * 0.74 - (living.isCrouching() ? 0.2 : 0.0);
            Vec3 shoulder = living.getPosition(partialTick).add(0, shoulderHeight, 0).add(right.scale(side * 0.33 * living.getScale()));
            if (living instanceof SpiderManEntity spidey && spidey.zipPose > 0.5F) {
                return shoulder.add(0, 0.7, 0);
            }
            Vec3 toTip = tip.subtract(shoulder);
            double length = toTip.length();
            return length < 0.01 ? shoulder : shoulder.add(toTip.scale(Math.min(0.72, length) / length));
        }
        return owner.getPosition(partialTick).add(0, owner.getBbHeight() * 0.5, 0);
    }

    private static void strand(VertexConsumer consumer, PoseStack.Pose pose, Vec3 from, Vec3 to, float slack, int light) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) {
            return;
        }
        dir = dir.normalize();
        Vec3 a = Math.abs(dir.y) > 0.98 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 b = dir.cross(a).normalize();
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = i / (float) SEGMENTS;
            float t1 = (i + 1) / (float) SEGMENTS;
            Vec3 p0 = point(from, to, t0, slack);
            Vec3 p1 = point(from, to, t1, slack);
            ribbon(consumer, pose, p0, p1, a, 255, light);
            ribbon(consumer, pose, p0, p1, b, 225, light);
        }
    }

    private static Vec3 point(Vec3 from, Vec3 to, float t, float slack) {
        Vec3 p = from.add(to.subtract(from).scale(t));
        return slack == 0F ? p : p.add(0, -Mth.sin(t * Mth.PI) * slack, 0);
    }

    private static void ribbon(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 side, int shade, int light) {
        Vec3 o = side.scale(HALF_WIDTH);
        vertex(consumer, pose, p0.subtract(o), 0F, 0F, shade, light);
        vertex(consumer, pose, p0.add(o), 0F, 1F, shade, light);
        vertex(consumer, pose, p1.add(o), 1F, 1F, shade, light);
        vertex(consumer, pose, p1.subtract(o), 1F, 0F, shade, light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, float u, float v, int shade, int light) {
        consumer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(shade, shade, shade, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0F, 1F, 0F);
    }

    /** A little starburst of strands where the web sticks. */
    private static void splat(VertexConsumer consumer, PoseStack.Pose pose, Vec3 back, int light) {
        Vec3 a = Math.abs(back.y) > 0.98 ? new Vec3(1, 0, 0) : back.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 b = back.cross(a).normalize();
        for (int k = 0; k < 6; k++) {
            double angle = k * Math.PI / 3.0 + 0.3;
            Vec3 ray = a.scale(Math.cos(angle)).add(b.scale(Math.sin(angle))).scale(0.28 + (k % 2) * 0.1).add(back.scale(0.04));
            strand(consumer, pose, Vec3.ZERO, ray, 0F, light);
        }
    }
}
