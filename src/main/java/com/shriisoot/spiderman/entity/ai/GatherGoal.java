package com.shriisoot.spiderman.entity.ai;

import com.shriisoot.spiderman.entity.SpiderManEntity;
import com.shriisoot.spiderman.entity.WebLineEntity;
import com.shriisoot.spiderman.entity.work.Resource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The work brain: find the nearest reachable natural block of the ordered resource,
 * punch it when close, web-yank it when it's up a tree or across a gap, and scoop up the drops.
 */
public class GatherGoal extends Goal {
    private static final int SEARCH_RADIUS = 20;
    private static final int SEARCH_DOWN = 8;
    private static final int SEARCH_UP = 14;
    private static final int MAX_WANDER = 40;
    private static final int WEB_TUG_TICKS = 10;

    private enum Phase { SEARCH, APPROACH, PUNCH, WEB_TUG, COLLECT }

    private final SpiderManEntity spidey;
    private final Set<Long> blacklist = new HashSet<>();
    private final Set<UUID> ignoredDrops = new HashSet<>();

    private Phase phase = Phase.SEARCH;
    private int phaseTicks;
    @Nullable private BlockPos target;
    @Nullable private ItemEntity collectTarget;
    @Nullable private WebLineEntity web;
    private int breakTime;
    private int searchCooldown;
    private int failedSearches;
    private int repathTimer;
    private double lastDistance;
    private int stuckChecks;
    private int generation = -1;

    public GatherGoal(SpiderManEntity spidey) {
        this.spidey = spidey;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return spidey.getMode() == SpiderManEntity.Mode.WORK && spidey.getOrderResource() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        setPhase(Phase.SEARCH);
        searchCooldown = 0;
    }

    @Override
    public void stop() {
        clearCrack();
        discardWeb();
        target = null;
        collectTarget = null;
        spidey.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    @Override
    public void tick() {
        Resource resource = spidey.getOrderResource();
        if (resource == null) {
            return;
        }
        if (generation != spidey.getOrderGeneration()) {
            generation = spidey.getOrderGeneration();
            blacklist.clear();
            ignoredDrops.clear();
            failedSearches = 0;
            clearCrack();
            discardWeb();
            target = null;
            setPhase(Phase.SEARCH);
        }
        // Once the quota is met, sweep up any drops still lying around, then head home.
        if (spidey.orderComplete() && phase != Phase.WEB_TUG && phase != Phase.COLLECT
                && !spidey.hasPendingDrops() && findDrop() == null) {
            spidey.finishOrder("");
            return;
        }
        if (!spidey.hasInventorySpace()) {
            spidey.finishOrder("My web-pockets are full! Bringing it all back.");
            return;
        }
        phaseTicks++;
        switch (phase) {
            case SEARCH -> tickSearch(resource);
            case APPROACH -> tickApproach(resource);
            case PUNCH -> tickPunch(resource);
            case WEB_TUG -> tickWebTug(resource);
            case COLLECT -> tickCollect();
        }
    }

    // ---------------------------------------------------------------- phases

    private void tickSearch(Resource resource) {
        ItemEntity drop = findDrop();
        if (drop != null) {
            collectTarget = drop;
            setPhase(Phase.COLLECT);
            return;
        }
        if (spidey.orderComplete()) {
            return;
        }
        if (searchCooldown-- > 0) {
            return;
        }
        target = findBlock(resource);
        if (target == null) {
            failedSearches++;
            searchCooldown = 20;
            if (failedSearches >= 3) {
                spidey.finishOrder("");
            }
            return;
        }
        failedSearches = 0;
        lastDistance = Double.MAX_VALUE;
        stuckChecks = 0;
        repathTimer = 0;
        setPhase(Phase.APPROACH);
    }

    private void tickApproach(Resource resource) {
        if (target == null || !resource.matches(level().getBlockState(target))) {
            target = null;
            setPhase(Phase.SEARCH);
            return;
        }
        Vec3 center = Vec3.atCenterOf(target);
        spidey.getLookControl().setLookAt(center.x, center.y, center.z, 30.0F, 30.0F);
        double eyeDistance = spidey.getEyePosition().distanceTo(center);

        if (eyeDistance <= SpiderManEntity.PUNCH_REACH && webLineOfSight(target) != null) {
            spidey.getNavigation().stop();
            float hardness = level().getBlockState(target).getDestroySpeed(level(), target);
            breakTime = Math.max(6, Math.min(40, (int) (hardness * 10)));
            setPhase(Phase.PUNCH);
            return;
        }
        if (eyeDistance <= SpiderManEntity.WEB_REACH && spidey.isWebReady() && spidey.onGround()) {
            Vec3 hitPoint = webLineOfSight(target);
            if (hitPoint != null) {
                spidey.getNavigation().stop();
                web = spidey.webBlock(target, hitPoint);
                setPhase(Phase.WEB_TUG);
                return;
            }
        }

        if (--repathTimer <= 0) {
            repathTimer = adjustedTickDelay(15);
            spidey.getNavigation().moveTo(center.x, target.getY(), center.z, 1.2);
        }
        if (phaseTicks % 40 == 0) {
            if (eyeDistance > lastDistance - 0.5) {
                stuckChecks++;
            } else {
                stuckChecks = 0;
            }
            lastDistance = eyeDistance;
            if (stuckChecks >= 3 || phaseTicks > 400) {
                blacklist.add(target.asLong());
                target = null;
                setPhase(Phase.SEARCH);
            }
        }
    }

    private void tickPunch(Resource resource) {
        if (target == null || !resource.matches(level().getBlockState(target))) {
            clearCrack();
            target = null;
            setPhase(Phase.SEARCH);
            return;
        }
        Vec3 center = Vec3.atCenterOf(target);
        spidey.getLookControl().setLookAt(center.x, center.y, center.z, 30.0F, 30.0F);
        BlockState state = level().getBlockState(target);
        if (phaseTicks % 5 == 1) {
            spidey.swing(phaseTicks % 10 == 1 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            SoundType sound = state.getSoundType();
            level().playSound(null, target, sound.getHitSound(), SoundSource.NEUTRAL, sound.getVolume() * 0.6F, sound.getPitch() * 0.8F);
        }
        int stage = Math.min(9, (int) (phaseTicks / (float) breakTime * 10));
        level().destroyBlockProgress(spidey.getId(), target, stage);
        if (phaseTicks >= breakTime) {
            clearCrack();
            spidey.harvest(target, resource, false);
            target = null;
            searchCooldown = 0;
            setPhase(Phase.SEARCH);
        }
    }

    private void tickWebTug(Resource resource) {
        if (target == null || !resource.matches(level().getBlockState(target))) {
            clearCrack();
            discardWeb();
            target = null;
            setPhase(Phase.SEARCH);
            return;
        }
        Vec3 center = Vec3.atCenterOf(target);
        spidey.getLookControl().setLookAt(center.x, center.y, center.z, 60.0F, 60.0F);
        level().destroyBlockProgress(spidey.getId(), target, Math.min(9, phaseTicks * 10 / WEB_TUG_TICKS));
        if (phaseTicks >= WEB_TUG_TICKS) {
            clearCrack();
            List<ItemEntity> drops = spidey.harvest(target, resource, true);
            if (web != null && web.isAlive() && !drops.isEmpty()) {
                web.hookOnto(drops.get(0), 30);
                web = null;
            } else {
                discardWeb();
            }
            target = null;
            searchCooldown = 0;
            setPhase(Phase.SEARCH);
        }
    }

    private void tickCollect() {
        ItemEntity drop = collectTarget;
        if (drop == null || !drop.isAlive()) {
            collectTarget = null;
            setPhase(Phase.SEARCH);
            return;
        }
        if (phaseTicks > 160) {
            ignoredDrops.add(drop.getUUID());
            collectTarget = null;
            setPhase(Phase.SEARCH);
            return;
        }
        spidey.getLookControl().setLookAt(drop, 30.0F, 30.0F);
        if (--repathTimer <= 0) {
            repathTimer = adjustedTickDelay(10);
            if (!spidey.getNavigation().moveTo(drop, 1.25) && phaseTicks > 40) {
                ignoredDrops.add(drop.getUUID());
                collectTarget = null;
                setPhase(Phase.SEARCH);
            }
        }
    }

    // ---------------------------------------------------------------- searching

    private Level level() {
        return spidey.level();
    }

    @Nullable
    private ItemEntity findDrop() {
        BlockPos center = spidey.getWorkCenter() != null ? spidey.getWorkCenter() : spidey.blockPosition();
        List<ItemEntity> drops = level().getEntitiesOfClass(ItemEntity.class, spidey.getBoundingBox().inflate(10, 5, 10),
                e -> e.isAlive() && e.onGround() && spidey.isOwnDrop(e) && !ignoredDrops.contains(e.getUUID())
                        && e.blockPosition().distSqr(center) < MAX_WANDER * MAX_WANDER);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity drop : drops) {
            double d = drop.distanceToSqr(spidey);
            if (d < bestDistance) {
                bestDistance = d;
                best = drop;
            }
        }
        return best;
    }

    @Nullable
    private BlockPos findBlock(Resource resource) {
        Level level = level();
        BlockPos origin = spidey.blockPosition();
        BlockPos center = spidey.getWorkCenter() != null ? spidey.getWorkCenter() : origin;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestScore = SEARCH_RADIUS * SEARCH_RADIUS + 1;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                double flat = dx * dx + dz * dz;
                if (flat >= bestScore) {
                    continue;
                }
                cursor.set(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
                if (!level.isLoaded(cursor)) {
                    continue;
                }
                for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
                    double score = flat + dy * dy * (dy < 0 ? 2.0 : 0.6);
                    if (score >= bestScore) {
                        continue;
                    }
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (center.distSqr(cursor) > MAX_WANDER * MAX_WANDER || blacklist.contains(cursor.asLong())) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (!resource.matches(state) || !isExposed(cursor)) {
                        continue;
                    }
                    if (resource.needsNaturalCheck() && !isNaturalTree(cursor)) {
                        continue;
                    }
                    best = cursor.immutable();
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private boolean isExposed(BlockPos pos) {
        Level level = level();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            n.setWithOffset(pos, d);
            BlockState s = level.getBlockState(n);
            if (s.isAir() || !s.isSolidRender(level, n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A log counts as part of a natural tree only if it connects, log to log, to a log that touches natural
     * (non-player-placed) leaves. Cabins at the edge of a forest stay intact.
     */
    private boolean isNaturalTree(BlockPos log) {
        Level level = level();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(log);
        seen.add(log.asLong());
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && seen.size() <= 96) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        n.set(current.getX() + dx, current.getY() + dy, current.getZ() + dz);
                        BlockState s = level.getBlockState(n);
                        if (s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
                            return true;
                        }
                        if (s.is(BlockTags.LOGS) && n.getY() >= log.getY() - 2 && n.getY() <= log.getY() + 32 && seen.add(n.asLong())) {
                            queue.add(n.immutable());
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Where a web from his eyes would land on the target block. Webs pass straight through leaves. */
    @Nullable
    private Vec3 webLineOfSight(BlockPos target) {
        Level level = level();
        Vec3 to = Vec3.atCenterOf(target);
        Vec3 start = spidey.getEyePosition();
        Vec3 dir = to.subtract(start).normalize();
        for (int i = 0; i < 8; i++) {
            BlockHitResult hit = level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, spidey));
            if (hit.getType() == HitResult.Type.MISS) {
                return to;
            }
            BlockPos hitPos = hit.getBlockPos();
            if (hitPos.equals(target)) {
                return hit.getLocation();
            }
            if (!level.getBlockState(hitPos).is(BlockTags.LEAVES)) {
                return null;
            }
            Vec3 p = hit.getLocation();
            for (int guard = 0; guard < 40 && BlockPos.containing(p).equals(hitPos); guard++) {
                p = p.add(dir.scale(0.1));
            }
            start = p;
        }
        return null;
    }

    private void clearCrack() {
        if (target != null && level() instanceof ServerLevel server) {
            server.destroyBlockProgress(spidey.getId(), target, -1);
        }
    }

    private void discardWeb() {
        if (web != null) {
            web.discard();
            web = null;
        }
    }
}
