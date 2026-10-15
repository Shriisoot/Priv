package com.shriisoot.spiderman.entity.ai;

import com.shriisoot.spiderman.entity.SpiderManEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** In STAY mode, walks back to his post after chasing something off. */
public class HoldPositionGoal extends Goal {
    private final SpiderManEntity spidey;

    public HoldPositionGoal(SpiderManEntity spidey) {
        this.spidey = spidey;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        BlockPos post = spidey.getStayPos();
        return spidey.getMode() == SpiderManEntity.Mode.STAY && spidey.getTarget() == null
                && post != null && spidey.blockPosition().distSqr(post) > 4;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos post = spidey.getStayPos();
        return spidey.getMode() == SpiderManEntity.Mode.STAY && spidey.getTarget() == null
                && post != null && spidey.blockPosition().distSqr(post) > 1 && !spidey.getNavigation().isDone();
    }

    @Override
    public void stop() {
        spidey.getNavigation().stop();
    }

    @Override
    public void start() {
        BlockPos post = spidey.getStayPos();
        if (post != null) {
            spidey.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.1);
        }
    }
}
