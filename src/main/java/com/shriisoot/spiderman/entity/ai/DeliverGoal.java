package com.shriisoot.spiderman.entity.ai;

import com.shriisoot.spiderman.entity.SpiderManEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Brings the gathered loot back and hands it over. */
public class DeliverGoal extends Goal {
    private final SpiderManEntity spidey;
    private int repathTimer;

    public DeliverGoal(SpiderManEntity spidey) {
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
        return spidey.getMode() == SpiderManEntity.Mode.DELIVER && validOwner() != null;
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
        spidey.getLookControl().setLookAt(owner, 30.0F, spidey.getMaxHeadXRot());
        if (spidey.distanceToSqr(owner) < 2.8 * 2.8) {
            spidey.getNavigation().stop();
            spidey.handOverTo(owner);
            return;
        }
        TravelHelper.travelTo(spidey, owner, () -> {
            if (--repathTimer <= 0) {
                repathTimer = adjustedTickDelay(10);
                return true;
            }
            return false;
        });
    }
}
