package com.shriisoot.spiderman.entity.ai;

import com.shriisoot.spiderman.entity.SpiderManEntity;
import net.minecraft.world.entity.Entity;

import java.util.function.BooleanSupplier;

/** Shared "get to that entity, Spider-Man style" movement: run, web-zip, or as a last resort, swing in from off-screen. */
final class TravelHelper {
    private TravelHelper() {
    }

    static void travelTo(SpiderManEntity spidey, Entity target, BooleanSupplier repath) {
        if (spidey.isPassenger()) {
            spidey.stopRiding();
        }
        double dist = spidey.distanceTo(target);
        if (dist > 56) {
            spidey.teleportNear(target);
            return;
        }
        if (dist > 16 && spidey.onGround() && spidey.canZip()) {
            spidey.webZipToward(target.position());
            return;
        }
        // Re-path while swimming or climbing too, or he bobs in place forever once his last path runs out mid-lake.
        if (repath.getAsBoolean() && (spidey.onGround() || spidey.isInWater() || spidey.onClimbable())) {
            // Across lava, ravines or water the pathfinder only returns a partial path: zip over it instead.
            if (dist > 10 && spidey.canZip() && spidey.getNavigation() instanceof SpiderManNavigation nav && !nav.canReach(target)) {
                spidey.webZipToward(target.position());
            } else {
                spidey.getNavigation().moveTo(target, dist > 9 ? 1.5 : 1.15);
            }
        }
    }
}
