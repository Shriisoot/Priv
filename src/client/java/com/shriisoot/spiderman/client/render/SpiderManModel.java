package com.shriisoot.spiderman.client.render;

import com.shriisoot.spiderman.SpiderManMod;
import com.shriisoot.spiderman.entity.SpiderManEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * Player-shaped model with procedural, blended poses: the classic perch, wall-crawling,
 * web-zipping and the "thwip" web-shot. Every pose eases in and out so nothing snaps.
 */
public class SpiderManModel extends PlayerModel<SpiderManEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SpiderManMod.id("spider_man"), "main");

    public SpiderManModel(ModelPart root) {
        super(root, false);
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
    }

    static float ease(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    @Override
    public void setupAnim(SpiderManEntity e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);
        float pt = Mth.clamp(age - e.tickCount, 0F, 1F);

        athleticRun(limbSwing, limbSwingAmount);

        float perch = ease(Mth.lerp(pt, e.perchPoseO, e.perchPose));
        float climb = ease(Mth.lerp(pt, e.climbPoseO, e.climbPose));
        float zip = ease(Mth.lerp(pt, e.zipPoseO, e.zipPose));
        float web = ease(Mth.lerp(pt, e.webPoseO, e.webPose));

        if (perch > 0) perch(perch, age);
        if (climb > 0) climb(climb, age);
        if (zip > 0) zip(zip, age);
        if (web > 0) webShot(web, age);

        hat.copyFrom(head);
        jacket.copyFrom(body);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
    }

    /** Bigger, snappier arm drive when sprinting, plus a slight forward lean of the head. */
    private void athleticRun(float limbSwing, float amount) {
        // Fade out while a punch is playing, or the run cycle would overwrite the swing.
        float run = Mth.clamp((amount - 0.45F) / 0.4F, 0F, 1F) * (1F - Mth.sin(attackTime * Mth.PI));
        if (run <= 0) {
            return;
        }
        float swing = Mth.cos(limbSwing * 0.6662F);
        rightArm.xRot = Mth.lerp(run, rightArm.xRot, -swing * 1.25F - 0.15F);
        leftArm.xRot = Mth.lerp(run, leftArm.xRot, swing * 1.25F - 0.15F);
        rightArm.zRot = Mth.lerp(run, rightArm.zRot, 0.12F);
        leftArm.zRot = Mth.lerp(run, leftArm.zRot, -0.12F);
        rightLeg.xRot = Mth.lerp(run, rightLeg.xRot, swing * 1.2F);
        leftLeg.xRot = Mth.lerp(run, leftLeg.xRot, -swing * 1.2F);
    }

    /** The iconic low crouch: leaning in, arms forward and ready, knees out, eyes up. */
    private void perch(float w, float age) {
        float breathe = Mth.sin(age * 0.08F) * 0.03F;
        float lean = 0.72F + breathe;
        float hipY = 2.0F + 12.0F * Mth.cos(lean);
        float hipZ = 12.0F * Mth.sin(lean);

        body.xRot = Mth.lerp(w, body.xRot, lean);
        body.y = Mth.lerp(w, body.y, 2.0F);

        head.y = Mth.lerp(w, head.y, 2.0F);
        head.xRot = Mth.lerp(w, head.xRot, head.xRot * 0.4F - 0.45F);

        float shoulderY = 2.0F + 2.0F * Mth.cos(lean);
        float shoulderZ = 2.0F * Mth.sin(lean);
        rightArm.y = Mth.lerp(w, rightArm.y, shoulderY);
        rightArm.z = Mth.lerp(w, rightArm.z, shoulderZ);
        leftArm.y = Mth.lerp(w, leftArm.y, shoulderY);
        leftArm.z = Mth.lerp(w, leftArm.z, shoulderZ);
        rightArm.xRot = Mth.lerp(w, rightArm.xRot, -0.55F - breathe);
        rightArm.yRot = Mth.lerp(w, rightArm.yRot, 0.2F);
        rightArm.zRot = Mth.lerp(w, rightArm.zRot, 0.15F);
        leftArm.xRot = Mth.lerp(w, leftArm.xRot, -1.05F + breathe);
        leftArm.yRot = Mth.lerp(w, leftArm.yRot, -0.35F);
        leftArm.zRot = Mth.lerp(w, leftArm.zRot, -0.2F);

        rightLeg.y = Mth.lerp(w, rightLeg.y, hipY);
        rightLeg.z = Mth.lerp(w, rightLeg.z, hipZ);
        leftLeg.y = Mth.lerp(w, leftLeg.y, hipY);
        leftLeg.z = Mth.lerp(w, leftLeg.z, hipZ);
        rightLeg.xRot = Mth.lerp(w, rightLeg.xRot, -0.85F);
        rightLeg.yRot = Mth.lerp(w, rightLeg.yRot, 0.25F);
        rightLeg.zRot = Mth.lerp(w, rightLeg.zRot, 0.3F);
        leftLeg.xRot = Mth.lerp(w, leftLeg.xRot, -0.8F);
        leftLeg.yRot = Mth.lerp(w, leftLeg.yRot, -0.25F);
        leftLeg.zRot = Mth.lerp(w, leftLeg.zRot, -0.3F);
    }

    /** Hand-over-hand wall crawl. */
    private void climb(float w, float age) {
        float cycle = age * 0.45F;
        float s = Mth.sin(cycle);
        rightArm.xRot = Mth.lerp(w, rightArm.xRot, -2.7F + s * 0.55F);
        leftArm.xRot = Mth.lerp(w, leftArm.xRot, -2.7F - s * 0.55F);
        rightArm.yRot = Mth.lerp(w, rightArm.yRot, 0F);
        leftArm.yRot = Mth.lerp(w, leftArm.yRot, 0F);
        rightArm.zRot = Mth.lerp(w, rightArm.zRot, 0.28F);
        leftArm.zRot = Mth.lerp(w, leftArm.zRot, -0.28F);
        rightLeg.xRot = Mth.lerp(w, rightLeg.xRot, -s * 0.6F - 0.25F);
        leftLeg.xRot = Mth.lerp(w, leftLeg.xRot, s * 0.6F - 0.25F);
        rightLeg.zRot = Mth.lerp(w, rightLeg.zRot, 0.18F);
        leftLeg.zRot = Mth.lerp(w, leftLeg.zRot, -0.18F);
        head.xRot = Mth.lerp(w, head.xRot, -0.55F);
    }

    /** Mid-zip: both hands on the line, legs tucked and trailing. */
    private void zip(float w, float age) {
        float flutter = Mth.sin(age * 0.9F) * 0.12F;
        rightArm.xRot = Mth.lerp(w, rightArm.xRot, -2.95F);
        rightArm.yRot = Mth.lerp(w, rightArm.yRot, -0.1F);
        rightArm.zRot = Mth.lerp(w, rightArm.zRot, 0.08F);
        leftArm.xRot = Mth.lerp(w, leftArm.xRot, -2.6F);
        leftArm.yRot = Mth.lerp(w, leftArm.yRot, 0.15F);
        leftArm.zRot = Mth.lerp(w, leftArm.zRot, -0.1F);
        rightLeg.xRot = Mth.lerp(w, rightLeg.xRot, 0.45F + flutter);
        leftLeg.xRot = Mth.lerp(w, leftLeg.xRot, -0.7F - flutter);
        rightLeg.zRot = Mth.lerp(w, rightLeg.zRot, 0.08F);
        leftLeg.zRot = Mth.lerp(w, leftLeg.zRot, -0.08F);
        body.xRot = Mth.lerp(w, body.xRot, -0.12F);
    }

    /** "Thwip": shooting arm locked onto the target along the eye line, off hand up in a guard. */
    private void webShot(float w, float age) {
        float tug = Mth.sin(age * 1.4F) * 0.07F;
        rightArm.xRot = Mth.lerp(w, rightArm.xRot, -Mth.HALF_PI + head.xRot + tug);
        rightArm.yRot = Mth.lerp(w, rightArm.yRot, head.yRot - 0.06F);
        rightArm.zRot = Mth.lerp(w, rightArm.zRot, 0F);
        leftArm.xRot = Mth.lerp(w, leftArm.xRot, -0.85F);
        leftArm.yRot = Mth.lerp(w, leftArm.yRot, 0.55F);
        leftArm.zRot = Mth.lerp(w, leftArm.zRot, -0.1F);
    }
}
