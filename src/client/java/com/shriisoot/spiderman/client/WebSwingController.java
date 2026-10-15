package com.shriisoot.spiderman.client;

import com.shriisoot.spiderman.entity.WebLineEntity;
import com.shriisoot.spiderman.network.ReleaseWebPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side rope physics for the local player. Movement is client-authoritative in Minecraft,
 * so the swing has to be simulated here to feel responsive.
 */
public final class WebSwingController {
    private static final double MAX_SPEED = 2.6;

    private static int webId = -1;
    private static int releasedWebId = -1;
    private static double ropeLength;
    private static boolean jumpWasDown;
    private static boolean wasOnGround = true;
    private static final double AIR_DRAG_XZ = 0.91;
    private static final double AIR_DRAG_Y = 0.98;

    private WebSwingController() {
    }

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            webId = -1;
            return;
        }
        // Nothing moves while paused; applying rope forces anyway would store them up and fling the player on resume.
        if (client.isPaused() || (client.level != null && !client.level.tickRateManager().runsNormally())) {
            return;
        }
        boolean groundedBefore = wasOnGround;
        wasOnGround = player.onGround();
        boolean jumpDown = player.input.jumping;
        WebLineEntity web = ClientWebCache.get(player);
        if (web == null || !web.isAnchored() || web.getHookedEntity() != null || web.getId() == releasedWebId || web.releasedLocally) {
            webId = -1;
            jumpWasDown = jumpDown;
            return;
        }

        Vec3 anchor = web.position();
        Vec3 body = player.position().add(0, player.getBbHeight() * 0.55, 0);
        if (web.getId() != webId) {
            webId = web.getId();
            ropeLength = Math.max(2.0, body.distanceTo(anchor));
            jumpWasDown = jumpDown;
        }

        Vec3 toAnchor = anchor.subtract(body);
        double dist = toAnchor.length();
        if (dist < 1.0E-3) {
            return;
        }
        Vec3 dir = toAnchor.scale(1.0 / dist);
        Vec3 velocity = player.getDeltaMovement();

        // Jump in mid-air: let go and get flung forward. (A jump started from the ground is just a jump.)
        if (jumpDown && !jumpWasDown && !groundedBefore && !player.onGround()) {
            player.setDeltaMovement(velocity.add(player.getLookAngle().scale(0.35)).add(0, 0.55, 0));
            letGo(web);
            jumpWasDown = true;
            return;
        }
        jumpWasDown = jumpDown;

        if (web.getMode() == WebLineEntity.MODE_ZIP) {
            Vec3 pull = dir.scale(Math.min(1.9, 0.6 + dist * 0.12));
            if (dist < 2.2) {
                pull = pull.scale(0.6).add(0, 0.5, 0);
                letGo(web);
            }
            player.setDeltaMovement(pull);
            player.resetFallDistance();
            return;
        }

        if (player.input.shiftKeyDown) {
            ropeLength = Math.max(1.5, ropeLength - 0.6);
        }

        if (!player.onGround()) {
            // Undo the air drag travel() is about to apply, so a swing keeps its momentum like a real pendulum.
            velocity = new Vec3(velocity.x / AIR_DRAG_XZ, velocity.y / AIR_DRAG_Y, velocity.z / AIR_DRAG_XZ).scale(0.995);
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
            velocity = velocity.add(forward.scale(player.input.forwardImpulse * 0.045))
                    .add(right.scale(-player.input.leftImpulse * 0.03));
        }

        if (dist > ropeLength) {
            double radial = velocity.dot(dir);
            if (radial < 0) {
                velocity = velocity.subtract(dir.scale(radial));
            }
            velocity = velocity.add(dir.scale(Math.min(0.6, (dist - ropeLength) * 0.3)));
        }

        double speed = velocity.length();
        if (speed > MAX_SPEED) {
            velocity = velocity.scale(MAX_SPEED / speed);
        }
        player.setDeltaMovement(velocity);
        player.resetFallDistance();
    }

    private static void letGo(WebLineEntity web) {
        webId = -1;
        releasedWebId = web.getId();
        web.releasedLocally = true;
        ClientPlayNetworking.send(new ReleaseWebPayload(web.getId()));
    }
}
