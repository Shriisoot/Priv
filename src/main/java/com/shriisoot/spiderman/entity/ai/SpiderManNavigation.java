package com.shriisoot.spiderman.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

/**
 * Wall-climbing navigation. Same idea as vanilla's WallClimberNavigation (steer straight at the target when
 * no path exists, climbing whatever is in the way), but stop() really stops, and moveTo(Entity) reports
 * whether a real path was found so callers can fall back to web-zipping.
 */
public class SpiderManNavigation extends GroundPathNavigation {
    @Nullable
    private BlockPos directTarget;

    public SpiderManNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    public Path createPath(BlockPos pos, int accuracy) {
        this.directTarget = pos;
        return super.createPath(pos, accuracy);
    }

    @Override
    public Path createPath(Entity entity, int accuracy) {
        this.directTarget = entity.blockPosition();
        return super.createPath(entity, accuracy);
    }

    /** True when a path exists that actually ends at the entity (not just a best-effort partial path). */
    public boolean canReach(Entity entity) {
        Path path = super.createPath(entity, 1);
        return path != null && path.canReach();
    }

    @Override
    public boolean moveTo(Entity entity, double speed) {
        Path path = this.createPath(entity, 0);
        if (path != null) {
            return this.moveTo(path, speed);
        }
        this.directTarget = entity.blockPosition();
        this.speedModifier = speed;
        return false;
    }

    @Override
    public void stop() {
        super.stop();
        this.directTarget = null;
    }

    @Override
    public void tick() {
        if (!this.isDone()) {
            super.tick();
            return;
        }
        if (this.directTarget == null) {
            return;
        }
        BlockPos sameLevel = BlockPos.containing(this.directTarget.getX(), this.mob.getY(), this.directTarget.getZ());
        boolean arrived = this.directTarget.closerToCenterThan(this.mob.position(), this.mob.getBbWidth())
                || (this.mob.getY() > this.directTarget.getY() && sameLevel.closerToCenterThan(this.mob.position(), this.mob.getBbWidth()));
        if (arrived) {
            this.directTarget = null;
        } else {
            // Aim at the block centre: the arrival test is centre-based, so aiming at the corner circles forever.
            this.mob.getMoveControl().setWantedPosition(this.directTarget.getX() + 0.5, this.directTarget.getY(), this.directTarget.getZ() + 0.5, this.speedModifier);
        }
    }
}
