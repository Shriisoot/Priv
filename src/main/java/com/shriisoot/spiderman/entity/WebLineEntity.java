package com.shriisoot.spiderman.entity;

import com.shriisoot.spiderman.item.WebShooterItem;
import com.shriisoot.spiderman.mixin.ServerGamePacketListenerImplAccessor;
import com.shriisoot.spiderman.registry.ModEntities;
import com.shriisoot.spiderman.registry.ModSounds;
import com.shriisoot.spiderman.util.FallProtection;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A strand of web between an owner (player or Spider-Man) and an anchor point.
 * Flies like a projectile until it sticks to a block or yanks an entity.
 */
public class WebLineEntity extends Projectile {
    public static final byte MODE_SWING = 0;
    public static final byte MODE_ZIP = 1;
    public static final byte MODE_YANK = 2;
    public static final byte MODE_STRIKE = 3;

    private static final EntityDataAccessor<Integer> DATA_OWNER_ID = SynchedEntityData.defineId(WebLineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ANCHORED = SynchedEntityData.defineId(WebLineEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(WebLineEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_HOOKED_ID = SynchedEntityData.defineId(WebLineEntity.class, EntityDataSerializers.INT);

    private static final int MAX_FLIGHT_TICKS = 22;
    private static final int MAX_ANCHORED_TICKS = 20 * 60;
    private static final double MAX_LENGTH = 72.0;

    private int anchoredTicks;
    private int hookedTicks;
    private int lifetime = -1;
    private boolean hookPull = true;
    /** Client-only: the local player already let go; ignore this web until the server removes it. */
    public boolean releasedLocally;

    public WebLineEntity(EntityType<? extends WebLineEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    private WebLineEntity(Level level, LivingEntity owner, byte mode) {
        this(ModEntities.WEB_LINE, level);
        this.setOwner(owner);
        this.entityData.set(DATA_OWNER_ID, owner.getId());
        this.entityData.set(DATA_MODE, mode);
    }

    /** Fires a web from {@code from} along {@code direction}. */
    public static WebLineEntity shoot(Level level, LivingEntity owner, byte mode, Vec3 from, Vec3 direction, double speed) {
        WebLineEntity web = new WebLineEntity(level, owner, mode);
        web.setPos(from);
        web.setDeltaMovement(direction.normalize().scale(speed));
        level.addFreshEntity(web);
        return web;
    }

    /** Creates a web that is already stuck at {@code point}; the renderer animates it shooting out. */
    public static WebLineEntity anchoredAt(Level level, LivingEntity owner, byte mode, Vec3 point, int lifetime) {
        WebLineEntity web = new WebLineEntity(level, owner, mode);
        web.setPos(point);
        web.setDeltaMovement(Vec3.ZERO);
        web.entityData.set(DATA_ANCHORED, true);
        web.lifetime = lifetime;
        level.addFreshEntity(web);
        return web;
    }

    @Nullable
    public static WebLineEntity findFor(Entity owner) {
        List<WebLineEntity> webs = owner.level().getEntitiesOfClass(WebLineEntity.class,
                owner.getBoundingBox().inflate(MAX_LENGTH + 8), w -> w.isAlive() && w.getOwnerId() == owner.getId());
        return webs.isEmpty() ? null : webs.get(0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_ID, -1);
        builder.define(DATA_ANCHORED, false);
        builder.define(DATA_MODE, MODE_SWING);
        builder.define(DATA_HOOKED_ID, -1);
    }

    public int getOwnerId() {
        return this.entityData.get(DATA_OWNER_ID);
    }

    @Nullable
    public Entity getOwnerEntity() {
        int id = getOwnerId();
        return id < 0 ? null : level().getEntity(id);
    }

    public boolean hasLostHook() {
        return this.entityData.get(DATA_HOOKED_ID) >= 0 && getHookedEntity() == null;
    }

    public boolean isAnchored() {
        return this.entityData.get(DATA_ANCHORED);
    }

    public byte getMode() {
        return this.entityData.get(DATA_MODE);
    }

    @Nullable
    public Entity getHookedEntity() {
        int id = this.entityData.get(DATA_HOOKED_ID);
        return id < 0 ? null : level().getEntity(id);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = getOwnerEntity();

        if (!level().isClientSide) {
            if (owner == null || !owner.isAlive() || owner.level() != level() || owner.distanceTo(this) > MAX_LENGTH) {
                discard();
                return;
            }
            if (owner instanceof Player player && (getMode() == MODE_SWING || getMode() == MODE_ZIP)
                    && !WebShooterItem.isHolding(player)) {
                release();
                return;
            }
            if (lifetime >= 0 && tickCount >= lifetime) {
                discard();
                return;
            }
        }

        Entity hooked = getHookedEntity();
        if (hooked != null) {
            tickHooked(hooked, owner);
            return;
        }
        if (this.entityData.get(DATA_HOOKED_ID) >= 0) {
            // Whatever it was hooked to is gone (picked up, killed, unloaded).
            if (!level().isClientSide) {
                discard();
            }
            return;
        }

        if (isAnchored()) {
            anchoredTicks++;
            if (!level().isClientSide && owner instanceof ServerPlayer player) {
                FallProtection.grant(player, 100);
                ((ServerGamePacketListenerImplAccessor) player.connection).spiderman$setAboveGroundTickCount(0);
                if (getMode() == MODE_ZIP && anchoredTicks > 3 && player.getEyePosition().distanceTo(position()) < 1.8) {
                    release();
                    return;
                }
            }
            if (!level().isClientSide && anchoredTicks > MAX_ANCHORED_TICKS) {
                release();
            }
            return;
        }

        if (!level().isClientSide && tickCount > MAX_FLIGHT_TICKS) {
            discard();
            return;
        }

        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                this.onHit(hit);
                if (isAnchored() || getHookedEntity() != null || isRemoved()) {
                    return;
                }
            }
        }
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
    }

    private void tickHooked(Entity hooked, @Nullable Entity owner) {
        setPos(hooked.getX(), hooked.getY(0.6), hooked.getZ());
        if (level().isClientSide) {
            return;
        }
        hookedTicks++;
        if (!hooked.isAlive() || (hookPull && hookedTicks > 8)) {
            discard();
            return;
        }
        if (hookPull && hookedTicks == 2 && owner != null) {
            Vec3 pull = owner.position().subtract(hooked.position());
            double dist = pull.length();
            if (dist > 0.5) {
                Vec3 v = pull.normalize().scale(Math.min(1.6, 0.35 + dist * 0.11)).add(0, 0.35, 0);
                hooked.setDeltaMovement(v);
                hooked.hurtMarked = true;
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = getOwnerEntity();
        if (target == owner || target instanceof WebLineEntity || !target.isAlive() || !target.isPickable() || target.isSpectator()) {
            return false;
        }
        if (owner != null && owner.getRootVehicle() == target.getRootVehicle()) {
            return false;
        }
        if (target instanceof Player victim && owner instanceof Player shooter
                && (!shooter.canHarmPlayer(victim) || victim.getAbilities().invulnerable)) {
            return false;
        }
        if (owner instanceof SpiderManEntity spidey) {
            return getMode() == MODE_STRIKE ? target == spidey.getTarget() : !spidey.isFriendly(target);
        }
        if (target instanceof SpiderManEntity spidey && owner instanceof Player player && spidey.isOwnedBy(player)) {
            return false;
        }
        return true;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        Vec3 at = result.getLocation();
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        if (getMode() == MODE_STRIKE) {
            puff(at, 6);
            discard();
            return;
        }
        this.entityData.set(DATA_ANCHORED, true);
        level().playSound(null, at.x, at.y, at.z, ModSounds.WEB_HIT, SoundSource.PLAYERS, 0.8F, 0.9F + random.nextFloat() * 0.2F);
        puff(at, 8);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = getOwnerEntity();
        this.entityData.set(DATA_HOOKED_ID, target.getId());
        setDeltaMovement(Vec3.ZERO);
        level().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.WEB_HIT, SoundSource.PLAYERS, 0.9F, 1.1F);
        puff(target.position().add(0, target.getBbHeight() * 0.6, 0), 12);
        if (target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3), owner);
            if (owner instanceof LivingEntity attacker) {
                living.hurt(damageSources().mobProjectile(this, attacker), getMode() == MODE_STRIKE ? 3.0F : 1.0F);
            }
        }
    }

    /** Re-attaches the strand to an entity (e.g. the log it just yanked free) so it visibly reels it in. */
    public void hookOnto(Entity target, int ticks) {
        this.entityData.set(DATA_HOOKED_ID, target.getId());
        this.hookedTicks = 0;
        this.hookPull = false;
        this.lifetime = tickCount + ticks;
    }

    /** Lets go of the web. Players keep their momentum and get a moment of fall-damage grace. */
    public void release() {
        if (level().isClientSide) {
            return;
        }
        if (getOwnerEntity() instanceof ServerPlayer player) {
            FallProtection.grant(player, 100);
            level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
        discard();
    }

    private void puff(Vec3 at, int count) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
                    at.x, at.y, at.z, count, 0.15, 0.15, 0.15, 0.08);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }
}
