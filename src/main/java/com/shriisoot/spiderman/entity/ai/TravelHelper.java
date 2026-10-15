package com.shriisoot.spiderman.entity.ai;

import com.shriisoot.spiderman.entity.SpiderManEntity;
import net.minecraft.world.entity.Entity;

import java.util.function.BooleanSupplier;

/** Shared "get to that entity, Spider-Man style" movement: run, web-zip, or as a last resort, swing in from off-screen. */
final class TravelHelper {
    private TravelHelper() {
    }

    static void travelTo(SpiderManEntity spidey, Entity target, BooleanSupplier repath) {
        double dist = spidey.distanceTo(target);
        if (dist > 56) {
            spidey.teleportNear(target);
            return;
        }
        if (dist > 16 && spidey.onGround() && spidey.canZip()) {
            spidey.webZipToward(target.position());
            return;
        }
        if (repath.getAsBoolean() && spidey.onGround()) {
            boolean moving = spidey.getNavigation().moveTo(target, dist > 9 ? 1.5 : 1.15);
            if (!moving && dist > 10 && spidey.canZip()) {
                spidey.webZipToward(target.position());
            }
        }
    }
}
