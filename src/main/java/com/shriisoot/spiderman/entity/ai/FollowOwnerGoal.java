package com.shriisoot.spiderman.entity.ai;

import com.shriisoot.spiderman.entity.SpiderManEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class FollowOwnerGoal extends Goal {
    private final SpiderManEntity spidey;
    private int repathTimer;

    public FollowOwnerGoal(SpiderManEntity spidey) {
        this.spidey = spidey;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private Player validOwner() {
        Player owner = spidey.getOwner();
        if (owner == null || owner.isSpectator() || owner.level() != spidey.level()) {
            return null;
        }
        return owner;
    }

    @Override
    public boolean canUse() {
        Player owner = validOwner();
        return spidey.getMode() == SpiderManEntity.Mode.FOLLOW && spidey.getTarget() == null
                && owner != null && spidey.distanceToSqr(owner) > 5 * 5;
    }

    @Override
    public boolean canContinueUse() {
        Player owner = validOwner();
        return spidey.getMode() == SpiderManEntity.Mode.FOLLOW && spidey.getTarget() == null
                && owner != null && spidey.distanceToSqr(owner) > 2.5 * 2.5;
    }

    @Override
    public void start() {
        repathTimer = 0;
    }

    @Override
    public void stop() {
        spidey.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Player owner = validOwner();
        if (owner == null) {
            return;
        }
        spidey.getLookControl().setLookAt(owner, 10.0F, spidey.getMaxHeadXRot());
        TravelHelper.travelTo(spidey, owner, this::nextRepath);
    }

    private boolean nextRepath() {
        if (--repathTimer <= 0) {
            repathTimer = adjustedTickDelay(10);
            return true;
        }
        return false;
    }
}
