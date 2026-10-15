package com.shriisoot.spiderman.entity;

import com.shriisoot.spiderman.entity.ai.DefendOwnerGoal;
import com.shriisoot.spiderman.entity.ai.DeliverGoal;
import com.shriisoot.spiderman.entity.ai.FollowOwnerGoal;
import com.shriisoot.spiderman.entity.ai.GatherGoal;
import com.shriisoot.spiderman.entity.ai.HoldPositionGoal;
import com.shriisoot.spiderman.entity.work.Resource;
import com.shriisoot.spiderman.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpiderManEntity extends PathfinderMob {
    public enum Mode { FOLLOW, STAY, WORK, DELIVER }

    public static final byte ANIM_NONE = 0;
    public static final byte ANIM_WEB = 1;
    public static final byte ANIM_ZIP = 2;

    public static final double WEB_REACH = 9.0;
    public static final double PUNCH_REACH = 3.2;

    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(SpiderManEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(SpiderManEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_ANIM = SynchedEntityData.defineId(SpiderManEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(SpiderManEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,3})\\b");

    private final SimpleContainer inventory = new SimpleContainer(27);

    @Nullable private Resource orderResource;
    private int orderTarget;
    private int orderProgress;
    private int orderGeneration;
    @Nullable private BlockPos workCenter;
    @Nullable private BlockPos stayPos;

    private int animTicks;
    private int webCooldown;
    private int zipCooldown;

    // Client-side pose blending, eased in the model.
    public float webPose, webPoseO;
    public float zipPose, zipPoseO;
    public float climbPose, climbPoseO;
    public float perchPose, perchPoseO;

    public SpiderManEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.SAFE_FALL_DISTANCE, 40.0)
                .add(Attributes.JUMP_STRENGTH, 0.6);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.35, true));
        this.goalSelector.addGoal(2, new GatherGoal(this));
        this.goalSelector.addGoal(3, new DeliverGoal(this));
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this));
        this.goalSelector.addGoal(4, new HoldPositionGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new DefendOwnerGoal(this));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return super.canUse() && !(SpiderManEntity.this.getLastHurtByMob() instanceof Player);
            }
        });
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                e -> !(e instanceof Creeper) && !(e instanceof NeutralMob) && !(e instanceof AbstractPiglin)
                        && e.distanceToSqr(this) < 16 * 16));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS, (byte) 0);
        builder.define(DATA_MODE, (byte) Mode.FOLLOW.ordinal());
        builder.define(DATA_ANIM, ANIM_NONE);
        builder.define(DATA_OWNER, Optional.empty());
    }

    // ---------------------------------------------------------------- state

    public boolean isClimbing() {
        return (this.entityData.get(DATA_FLAGS) & 1) != 0;
    }

    private void setClimbing(boolean climbing) {
        byte flags = this.entityData.get(DATA_FLAGS);
        this.entityData.set(DATA_FLAGS, climbing ? (byte) (flags | 1) : (byte) (flags & ~1));
    }

    @Override
    public boolean onClimbable() {
        return isClimbing();
    }

    public Mode getMode() {
        return Mode.values()[Mth.clamp(this.entityData.get(DATA_MODE), 0, Mode.values().length - 1)];
    }

    public void setMode(Mode mode) {
        this.entityData.set(DATA_MODE, (byte) mode.ordinal());
        if (mode == Mode.STAY) {
            stayPos = blockPosition();
        }
        if (mode != Mode.WORK) {
            orderResource = null;
        }
        getNavigation().stop();
    }

    public byte getAnim() {
        return this.entityData.get(DATA_ANIM);
    }

    public void playAnim(byte anim, int ticks) {
        this.entityData.set(DATA_ANIM, anim);
        this.animTicks = ticks;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(@Nullable Player player) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(player == null ? null : player.getUUID()));
    }

    @Nullable
    public Player getOwner() {
        UUID uuid = getOwnerUUID();
        return uuid == null ? null : level().getPlayerByUUID(uuid);
    }

    public boolean isOwnedBy(Player player) {
        return player.getUUID().equals(getOwnerUUID());
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    @Nullable
    public Resource getOrderResource() {
        return orderResource;
    }

    public int getOrderGeneration() {
        return orderGeneration;
    }

    public boolean orderComplete() {
        return orderResource == null || orderProgress >= orderTarget;
    }

    @Nullable
    public BlockPos getWorkCenter() {
        return workCenter;
    }

    @Nullable
    public BlockPos getStayPos() {
        return stayPos;
    }

    public boolean isWebReady() {
        return webCooldown <= 0;
    }

    public boolean canZip() {
        return zipCooldown <= 0;
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickClientPoses();
        } else {
            setClimbing(this.horizontalCollision);
        }
    }

    private void tickClientPoses() {
        webPoseO = webPose;
        zipPoseO = zipPose;
        climbPoseO = climbPose;
        perchPoseO = perchPose;
        byte anim = getAnim();
        webPose = approach(webPose, anim == ANIM_WEB ? 1F : 0F, 0.3F);
        zipPose = approach(zipPose, anim == ANIM_ZIP ? 1F : 0F, 0.22F);
        climbPose = approach(climbPose, isClimbing() && !onGround() ? 1F : 0F, 0.2F);
        boolean perching = getMode() == Mode.STAY && onGround() && walkAnimation.speed() < 0.08F && getAnim() == ANIM_NONE;
        perchPose = approach(perchPose, perching ? 1F : 0F, 0.08F);
    }

    private static float approach(float current, float target, float step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (webCooldown > 0) webCooldown--;
        if (zipCooldown > 0) zipCooldown--;
        if (animTicks > 0 && --animTicks == 0) {
            this.entityData.set(DATA_ANIM, ANIM_NONE);
        }
        if (tickCount % 30 == 0 && getHealth() < getMaxHealth()) {
            heal(1.0F);
        }
        if (getMode() == Mode.WORK || getMode() == Mode.DELIVER) {
            pickUpNearbyItems();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && webCooldown <= 0) {
            double dist = distanceTo(target);
            if (dist > 3.5 && dist < 14 && getSensing().hasLineOfSight(target)) {
                shootWebAt(target);
                webCooldown = 45 + random.nextInt(25);
            }
        }
    }

    private void pickUpNearbyItems() {
        List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1.8, 1.2, 1.8),
                e -> e.isAlive() && !e.getItem().isEmpty() && !e.hasPickUpDelay() && e.getOwner() == null);
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            ItemStack remainder = inventory.addItem(stack.copy());
            int taken = stack.getCount() - remainder.getCount();
            if (taken > 0) {
                take(item, taken);
                if (remainder.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(remainder);
                }
            }
        }
    }

    public boolean hasInventorySpace() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- web abilities

    public Vec3 webHandPosition() {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        return getEyePosition().add(right.scale(0.36)).add(forward.scale(0.35)).add(0, -0.3, 0);
    }

    public void faceTowards(Vec3 point) {
        double dx = point.x - getX();
        double dz = point.z - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        getLookControl().setLookAt(point.x, point.y, point.z, 360.0F, 360.0F);
    }

    private void shootWebAt(LivingEntity target) {
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
        faceTowards(aim);
        playAnim(ANIM_WEB, 12);
        Vec3 from = webHandPosition();
        WebLineEntity.shoot(level(), this, WebLineEntity.MODE_STRIKE, from, aim.subtract(from), 2.6);
        playSound(ModSounds.THWIP, 1.0F, 1.0F + (random.nextFloat() - 0.5F) * 0.2F);
    }

    /** Shoots a web at a block to tug it free. Returns the web so the caller can time the yank. */
    public WebLineEntity webBlock(BlockPos pos, Vec3 hitPoint) {
        faceTowards(Vec3.atCenterOf(pos));
        playAnim(ANIM_WEB, 16);
        playSound(ModSounds.THWIP, 1.0F, 1.0F + (random.nextFloat() - 0.5F) * 0.2F);
        webCooldown = 6;
        return WebLineEntity.anchoredAt(level(), this, WebLineEntity.MODE_YANK, hitPoint, 14);
    }

    /** Web-zips (or leaps) toward a far-away destination. */
    public void webZipToward(Vec3 destination) {
        getNavigation().stop();
        Vec3 flat = new Vec3(destination.x - getX(), 0, destination.z - getZ());
        double horizontal = flat.length();
        if (horizontal < 0.01) {
            return;
        }
        Vec3 dir = flat.scale(1.0 / horizontal);
        faceTowards(destination);

        Vec3 eye = getEyePosition();
        Vec3 aim = eye.add(dir.scale(14)).add(0, 9, 0);
        BlockHitResult hit = level().clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            WebLineEntity.anchoredAt(level(), this, WebLineEntity.MODE_YANK, hit.getLocation(), 14);
        }

        double speed = Math.min(1.6, 0.25 + horizontal * 0.075);
        double lift = 0.55 + Math.max(0, destination.y - getY()) * 0.05;
        setDeltaMovement(dir.scale(speed).add(0, Math.min(lift, 1.0), 0));
        this.hasImpulse = true;
        playAnim(ANIM_ZIP, 20);
        playSound(ModSounds.THWIP, 1.0F, 1.1F);
        zipCooldown = 28;
    }

    public void teleportNear(Entity target) {
        for (int i = 0; i < 12; i++) {
            double x = target.getX() + (random.nextDouble() - 0.5) * 5;
            double z = target.getZ() + (random.nextDouble() - 0.5) * 5;
            if (randomTeleport(x, target.getY() + 1, z, false)) {
                getNavigation().stop();
                puff(position().add(0, 1, 0), 16);
                playSound(ModSounds.THWIP, 1.0F, 0.9F);
                return;
            }
        }
    }

    private void puff(Vec3 at, int count) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
                    at.x, at.y, at.z, count, 0.3, 0.4, 0.3, 0.05);
        }
    }

    /** Breaks a block like a pro and flings the drops back to himself. */
    public boolean harvest(BlockPos pos, Resource resource, boolean viaWeb) {
        if (!(level() instanceof ServerLevel server)) {
            return false;
        }
        BlockState state = server.getBlockState(pos);
        if (!resource.matches(state)) {
            return false;
        }
        BlockEntity blockEntity = server.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, server, pos, blockEntity, this, resource.tool());
        server.destroyBlock(pos, false, this);
        Vec3 from = Vec3.atCenterOf(pos);
        Vec3 to = position().add(0, 0.9, 0);
        int flightTicks = viaWeb ? 10 : 5;
        for (ItemStack drop : drops) {
            ItemEntity item = new ItemEntity(server, from.x, from.y, from.z, drop);
            item.setPickUpDelay(2);
            Vec3 delta = to.subtract(from);
            item.setDeltaMovement(delta.x / flightTicks, delta.y / flightTicks + 0.04 * flightTicks / 2.0, delta.z / flightTicks);
            server.addFreshEntity(item);
        }
        orderProgress++;
        return true;
    }

    // ---------------------------------------------------------------- orders & dialogue

    public void say(String message) {
        Player owner = getOwner();
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("[Spider-Man] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal(message).withStyle(style -> style.withBold(false).withColor(ChatFormatting.WHITE))));
        }
    }

    private String pick(String... lines) {
        return lines[random.nextInt(lines.length)];
    }

    public void receiveOrder(Player from, String text) {
        if (getOwnerUUID() == null) {
            setOwner(from);
        }
        if (!isOwnedBy(from)) {
            return;
        }
        String t = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim() + " ";

        if (containsAny(t, " dismiss ", " go home ", " bye ", " goodbye ", " leave ")) {
            dismiss();
            return;
        }
        if (containsAny(t, " status ", " report ", " what are you doing ", " how many ")) {
            reportStatus();
            return;
        }
        Resource resource = Resource.fromText(t);
        if (resource != null) {
            startOrder(resource, parseAmount(t));
            return;
        }
        if (containsAny(t, " give ", " deliver ", " hand ", " drop ", " bring it ", " return ", " what you got ")) {
            if (inventoryIsEmpty()) {
                say(pick("My web-pockets are empty right now!", "Nothing to hand over yet, boss."));
            } else {
                setMode(Mode.DELIVER);
                say(pick("Special delivery, coming right up!", "On my way with the goods!"));
            }
            return;
        }
        if (containsAny(t, " stay ", " wait ", " stop ", " sit ", " guard ", " hold ", " chill ", " stand ")) {
            setMode(Mode.STAY);
            say(pick("Holding this spot. Spider-sense is on.", "I'll hang around right here.", "Guarding this spot!"));
            return;
        }
        if (containsAny(t, " follow ", " come ", " here ", " with me ", " lets go ", " let s go ")) {
            setMode(Mode.FOLLOW);
            say(pick("Right behind you!", "Swinging your way!", "Lead the way, I've got your back."));
            return;
        }
        say("Not sure what you mean. Try \"spidey get 16 wood\", \"spidey follow\", \"spidey stay\" or \"spidey give\".");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static int parseAmount(String t) {
        Matcher m = NUMBER.matcher(t);
        if (m.find()) {
            return Mth.clamp(Integer.parseInt(m.group(1)), 1, 640);
        }
        if (t.contains(" stack ") || t.contains(" lots ") || t.contains(" a lot ") || t.contains(" tons ")) return 64;
        if (t.contains(" few ") || t.contains(" couple ")) return 5;
        if (t.contains(" all ")) return 128;
        return 16;
    }

    public void startOrder(Resource resource, int amount) {
        setMode(Mode.WORK);
        this.orderResource = resource;
        this.orderTarget = amount;
        this.orderProgress = 0;
        this.orderGeneration++;
        this.workCenter = blockPosition();
        say(pick(
                "On it! " + amount + " " + resource.displayName() + ", coming right up.",
                amount + " " + resource.displayName() + "? Easy. Thwip!",
                "Friendly neighborhood " + resource.displayName() + " delivery service, at your service. Getting " + amount + "!"));
    }

    /** Ends the current job and brings back whatever was gathered. */
    public void finishOrder(String reason) {
        Resource done = orderResource;
        int got = orderProgress;
        orderResource = null;
        if (inventoryIsEmpty()) {
            setMode(Mode.FOLLOW);
            say(reason.isEmpty() ? "Couldn't find any " + (done == null ? "of that" : done.displayName()) + " I can reach around here. Point me somewhere else?" : reason);
            return;
        }
        setMode(Mode.DELIVER);
        if (!reason.isEmpty()) {
            say(reason);
        } else if (done != null && got >= orderTarget) {
            say(pick("Got your " + done.displayName() + "! Swinging back.", "Job done! Bringing you the " + done.displayName() + ".", "That's " + got + " " + done.displayName() + ". Heading back!"));
        } else if (done != null) {
            say("Only found " + got + " " + done.displayName() + " in reach. Bringing what I've got!");
        }
    }

    public void handOverTo(Player owner) {
        Map<Item, Integer> summary = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.removeItemNoUpdate(i);
            if (stack.isEmpty()) {
                continue;
            }
            summary.merge(stack.getItem(), stack.getCount(), Integer::sum);
            owner.getInventory().placeItemBackInInventory(stack);
        }
        inventory.setChanged();
        swing(InteractionHand.MAIN_HAND);
        level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        StringBuilder list = new StringBuilder();
        summary.forEach((item, count) -> {
            if (!list.isEmpty()) list.append(", ");
            list.append(count).append("x ").append(new ItemStack(item).getHoverName().getString());
        });
        say(pick("Special delivery! ", "Here you go: ", "Fresh from the neighborhood: ") + list);
        setMode(Mode.FOLLOW);
    }

    private boolean inventoryIsEmpty() {
        return inventory.isEmpty();
    }

    private void reportStatus() {
        String doing = switch (getMode()) {
            case FOLLOW -> "Following you around.";
            case STAY -> "Guarding my spot.";
            case WORK -> orderResource == null ? "Working." : "Gathering " + orderResource.displayName() + ": " + orderProgress + "/" + orderTarget + ".";
            case DELIVER -> "Bringing you your stuff.";
        };
        int items = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) items += inventory.getItem(i).getCount();
        say(doing + " Carrying " + items + " items. Health " + (int) getHealth() + "/" + (int) getMaxHealth() + ".");
    }

    private void dismiss() {
        say("See you around, neighbor! Thwip!");
        Containers.dropContents(level(), this, inventory);
        puff(position().add(0, 1, 0), 24);
        setDeltaMovement(0, 1.4, 0);
        playSound(ModSounds.THWIP, 1.0F, 1.0F);
        discard();
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (getOwnerUUID() == null) {
            setOwner(player);
            say("Friendly neighborhood Spider-Man, at your service! Just tell me what you need in chat, like \"spidey get 16 wood\".");
            return InteractionResult.SUCCESS;
        }
        if (!isOwnedBy(player)) {
            player.sendSystemMessage(Component.literal("[Spider-Man] Sorry, I'm already helping someone else.").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            if (getMode() == Mode.STAY) {
                setMode(Mode.FOLLOW);
                say("Right behind you!");
            } else {
                setMode(Mode.STAY);
                say("Holding this spot.");
            }
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new SimpleMenuProvider((id, playerInventory, p) -> ChestMenu.threeRows(id, playerInventory, inventory),
                Component.literal("Spider-Man's Web-Pockets")));
        return InteractionResult.SUCCESS;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        if (getOwnerUUID() == null) {
            Player nearest = level.getNearestPlayer(this, 10.0);
            if (nearest != null) {
                setOwner(nearest);
                say(pick("Friendly neighborhood Spider-Man, reporting for duty!", "Hey there! Need a hand? Or eight?") +
                        " Tell me what you need in chat, like \"spidey get 16 wood\".");
            }
        }
        puff(position().add(0, 1, 0), 20);
        return data;
    }

    // ---------------------------------------------------------------- combat & survival

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player && isOwnedBy(player)) {
            return false;
        }
        if (!level().isClientSide && attacker != null && onGround() && random.nextFloat() < 0.3F && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            spiderSenseDodge(attacker);
            return false;
        }
        return super.hurt(source, amount);
    }

    private void spiderSenseDodge(Entity attacker) {
        Vec3 away = position().subtract(attacker.position());
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(1, 0, 0);
        }
        away = away.normalize();
        Vec3 side = new Vec3(-away.z, 0, away.x).scale(random.nextBoolean() ? 1 : -1);
        setDeltaMovement(side.scale(0.65).add(away.scale(0.35)).add(0, 0.45, 0));
        this.hasImpulse = true;
        playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F, 1.4F);
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        if (!state.is(Blocks.COBWEB)) {
            super.makeStuckInBlock(state, motionMultiplier);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            Containers.dropContents(level(), this, inventory);
            say("Ugh... I'll be back, neighbor...");
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("Mode", getMode().name());
        tag.put("Inventory", inventory.createTag(registryAccess()));
        if (orderResource != null) {
            tag.putString("OrderResource", orderResource.name());
            tag.putInt("OrderTarget", orderTarget);
            tag.putInt("OrderProgress", orderProgress);
        }
        if (workCenter != null) {
            tag.putLong("WorkCenter", workCenter.asLong());
        }
        if (stayPos != null) {
            tag.putLong("StayPos", stayPos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            this.entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
        }
        inventory.fromTag(tag.getList("Inventory", Tag.TAG_COMPOUND), registryAccess());
        if (tag.contains("WorkCenter")) {
            workCenter = BlockPos.of(tag.getLong("WorkCenter"));
        }
        if (tag.contains("StayPos")) {
            stayPos = BlockPos.of(tag.getLong("StayPos"));
        }
        Mode mode = Mode.FOLLOW;
        try {
            mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException ignored) {
        }
        this.entityData.set(DATA_MODE, (byte) mode.ordinal());
        if (tag.contains("OrderResource")) {
            try {
                orderResource = Resource.valueOf(tag.getString("OrderResource"));
                orderTarget = tag.getInt("OrderTarget");
                orderProgress = tag.getInt("OrderProgress");
            } catch (IllegalArgumentException ignored) {
                orderResource = null;
            }
        }
        if (mode == Mode.WORK && orderResource == null) {
            this.entityData.set(DATA_MODE, (byte) Mode.FOLLOW.ordinal());
        }
    }
}
