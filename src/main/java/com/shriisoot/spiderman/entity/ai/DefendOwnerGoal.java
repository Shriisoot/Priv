package com.shriisoot.spiderman.entity.ai;

import com.shriisoot.spiderman.entity.SpiderManEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Goes after whatever hurts the owner, and whatever the owner is fighting. */
public class DefendOwnerGoal extends TargetGoal {
    private final SpiderManEntity spidey;
    private LivingEntity pending;
    private int lastHurtByTimestamp;
    private int lastHurtTimestamp;

    public DefendOwnerGoal(SpiderManEntity spidey) {
        super(spidey, false);
        this.spidey = spidey;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    private boolean valid(LivingEntity e) {
        return e != null && e != spidey && e.isAlive() && !(e instanceof Player) && !(e instanceof SpiderManEntity)
                && canAttack(e, TargetingConditions.DEFAULT);
    }

    @Override
    public boolean canUse() {
        Player owner = spidey.getOwner();
        if (owner == null || owner.level() != spidey.level()) {
            return false;
        }
        LivingEntity attacker = owner.getLastHurtByMob();
        if (owner.getLastHurtByMobTimestamp() != lastHurtByTimestamp && valid(attacker)) {
            pending = attacker;
            lastHurtByTimestamp = owner.getLastHurtByMobTimestamp();
            return true;
        }
        LivingEntity victim = owner.getLastHurtMob();
        if (owner.getLastHurtMobTimestamp() != lastHurtTimestamp && valid(victim)) {
            pending = victim;
            lastHurtTimestamp = owner.getLastHurtMobTimestamp();
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        mob.setTarget(pending);
        super.start();
    }
}
