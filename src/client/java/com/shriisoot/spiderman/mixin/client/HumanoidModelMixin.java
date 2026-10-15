package com.shriisoot.spiderman.mixin.client;

import com.shriisoot.spiderman.client.ClientWebCache;
import com.shriisoot.spiderman.entity.WebLineEntity;
import com.shriisoot.spiderman.registry.ModItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Players holding a web point their shooting arm straight up the strand in third person. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void spiderman$aimWebArm(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                     float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }
        WebLineEntity web = ClientWebCache.get(player);
        if (web == null) {
            return;
        }
        float partialTick = Mth.clamp(ageInTicks - player.tickCount, 0F, 1F);
        boolean mainHand = player.getMainHandItem().is(ModItems.WEB_SHOOTER);
        boolean right = (player.getMainArm() == HumanoidArm.RIGHT) == mainHand;
        ModelPart arm = right ? rightArm : leftArm;

        Vec3 shoulder = player.getPosition(partialTick).add(0, 1.35, 0);
        Vec3 d = web.getPosition(partialTick).subtract(shoulder);
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float yawToWeb = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float relativeYaw = Mth.clamp(Mth.wrapDegrees(yawToWeb - bodyYaw), -85F, 85F);
        float pitch = (float) -(Mth.atan2(d.y, horizontal) * Mth.RAD_TO_DEG);

        arm.xRot = -Mth.HALF_PI + pitch * Mth.DEG_TO_RAD;
        arm.yRot = relativeYaw * Mth.DEG_TO_RAD;
        arm.zRot = 0F;
    }
}
