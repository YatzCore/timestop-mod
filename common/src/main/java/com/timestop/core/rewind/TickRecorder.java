package com.timestop.core.rewind;

import com.timestop.config.TimeStopConfig;
import com.timestop.core.rewind.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;
import java.util.*;

/** One interval per SERVER tick, including packet work between world ticks. */
public class TickRecorder {
    private static final TickRecorder INSTANCE = new TickRecorder();
    public static TickRecorder getInstance() { return INSTANCE; }
    private TimelineBuffer timelineBuffer = new TimelineBuffer();
    private MinecraftServer owner;
    private TickFrame currentFrame;
    private final Map<UUID, EntityDelta> previousEntities = new HashMap<>();
    private final Map<UUID, KinematicState> previousKinematics = new HashMap<>();
    private final Map<UUID, SharedEntitySnapshot> entityBaselines = new HashMap<>();
    private final Map<UUID, SharedInventory> lastPlayerInventories = new HashMap<>();
    private final Set<UUID> spawnedEntities = new HashSet<>();
    private final Set<UUID> removedEntities = new HashSet<>();
    private final Map<BlockEntity, CompoundTag> blockEntityBaselines = new WeakHashMap<>();

    public record KinematicState(
            double posX, double posY, double posZ,
            double motionX, double motionY, double motionZ,
            float yaw, float pitch,
            float health,
            float fallDistance,
            short fireTicks,
            short airSupply,
            boolean onGround,
            short hurtTime,
            short deathTime,
            int fuse
    ) {
        public static KinematicState capture(Entity entity) {
            var motion = entity.getDeltaMovement();
            float health = entity instanceof LivingEntity living ? living.getHealth() : 1.0F;
            short hurtTime = entity instanceof LivingEntity living ? (short) living.hurtTime : 0;
            short deathTime = entity instanceof LivingEntity living ? (short) living.deathTime : 0;
            int fuse = entity instanceof PrimedTnt tnt ? tnt.getFuse() : -1;
            return new KinematicState(
                    entity.getX(), entity.getY(), entity.getZ(),
                    motion.x, motion.y, motion.z,
                    entity.getYRot(), entity.getXRot(),
                    health,
                    entity.fallDistance,
                    (short) entity.getRemainingFireTicks(),
                    (short) entity.getAirSupply(),
                    entity.onGround(),
                    hurtTime,
                    deathTime,
                    fuse
            );
        }

        public boolean matches(Entity entity) {
            var motion = entity.getDeltaMovement();
            float h = entity instanceof LivingEntity living ? living.getHealth() : 1.0F;
            short ht = entity instanceof LivingEntity living ? (short) living.hurtTime : 0;
            short dt = entity instanceof LivingEntity living ? (short) living.deathTime : 0;
            int f = entity instanceof PrimedTnt tnt ? tnt.getFuse() : -1;
            return posX == entity.getX() && posY == entity.getY() && posZ == entity.getZ()
                    && motionX == motion.x && motionY == motion.y && motionZ == motion.z
                    && yaw == entity.getYRot() && pitch == entity.getXRot()
                    && health == h
                    && fallDistance == entity.fallDistance
                    && fireTicks == (short) entity.getRemainingFireTicks()
                    && airSupply == (short) entity.getAirSupply()
                    && onGround == entity.onGround()
                    && hurtTime == ht && deathTime == dt && fuse == f;
        }
    }

    public TimelineBuffer getTimelineBuffer() { return timelineBuffer; }
    public void setTimelineBuffer(TimelineBuffer buffer) { timelineBuffer = buffer; clearTrackingData(); }
    public TickFrame getCurrentFrame() { return currentFrame; }
    public boolean isRecording() {
        return !RewindExecutor.isApplyingPlan() && timelineBuffer.isRecording() && !timelineBuffer.isRewinding() && !timelineBuffer.isFrozen();
    }

    public long getBaselineMemoryBytes() {
        long bytes = 0;
        for (CompoundTag tag : blockEntityBaselines.values()) {
            if (tag != null) bytes += tag.sizeInBytes();
        }
        for (SharedEntitySnapshot snapshot : entityBaselines.values()) {
            if (snapshot != null) bytes += snapshot.estimatedBytes();
        }
        for (SharedInventory inv : lastPlayerInventories.values()) {
            if (inv != null) bytes += inv.estimatedBytes();
        }
        return bytes;
    }

    public void serverTick(MinecraftServer server) {
        if (owner != server) {
            reset();
            owner = server;
            timelineBuffer = new TimelineBuffer(Math.max(1, Math.min(60, TimeStopConfig.COMMON.rewindHistorySeconds.get())) * 20,
                    Math.max(1L, TimeStopConfig.COMMON.rewindMemoryCapMB.get()) * 1024L * 1024L);
        }
        if (!isRecording()) return;
        finishFrame(server);
        beginFrame(server);
    }

    public void beginFrame(MinecraftServer server) {
        currentFrame = new TickFrame(server.overworld().getGameTime());
        currentFrame.captureEnvironment(server.overworld());
        previousEntities.clear();
        previousKinematics.clear();
        removedEntities.clear();
        spawnedEntities.clear();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (shouldTrack(entity)) {
                    UUID id = entity.getUUID();
                    KinematicState k = KinematicState.capture(entity);
                    previousKinematics.put(id, k);
                    var typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                    SharedEntitySnapshot base = entityBaselines.get(id);
                    if (base == null) {
                        base = SharedEntitySnapshot.of(typeKey, snapshot(entity));
                        entityBaselines.put(id, base);
                    }
                    previousEntities.put(id, EntityDelta.compactDespawn(
                            level.dimension(), id, typeKey, base,
                            k.posX(), k.posY(), k.posZ(),
                            k.motionX(), k.motionY(), k.motionZ(),
                            k.yaw(), k.pitch(), k.health(),
                            k.fallDistance(), k.fireTicks(), k.airSupply(),
                            k.onGround(), k.hurtTime(), k.deathTime(),
                            k.fuse(), null));
                }
            }
            for (var player : level.players()) {
                if (player.isAlive() && !LocalRewind.contains(player)) {
                    SharedInventory cached = lastPlayerInventories.get(player.getUUID());
                    PlayerDelta pDelta = PlayerDelta.fromPlayer(player, cached);
                    if (pDelta.sharedInventory() != null) {
                        lastPlayerInventories.put(player.getUUID(), pDelta.sharedInventory());
                    }
                    currentFrame.putPlayerDelta(pDelta);
                }
            }
        }
    }

    public void finishFrame(MinecraftServer server) {
        if (!isRecording() || currentFrame == null) return;
        Set<UUID> present = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!shouldTrack(entity)) continue;
                UUID id = entity.getUUID();
                present.add(id);
                var typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                EntityDelta previous = previousEntities.get(id);
                KinematicState prevK = previousKinematics.get(id);

                if (previous == null && spawnedEntities.contains(id)) {
                    CompoundTag state = snapshot(entity);
                    SharedEntitySnapshot base = SharedEntitySnapshot.of(typeKey, state);
                    entityBaselines.put(id, base);
                    currentFrame.addEntityDelta(EntityDelta.spawn(level.dimension(), id, typeKey, state));
                } else if (prevK != null) {
                    boolean moved = !prevK.matches(entity);
                    boolean dimChanged = previous != null && !previous.dimension().equals(level.dimension());
                    if (moved || dimChanged) {
                        SharedEntitySnapshot base = entityBaselines.get(id);
                        if (base == null) {
                            base = SharedEntitySnapshot.of(typeKey, snapshot(entity));
                            entityBaselines.put(id, base);
                        }
                        currentFrame.addEntityDelta(EntityDelta.compactUpdate(
                                previous != null ? previous.dimension() : level.dimension(),
                                id, typeKey, base,
                                prevK.posX(), prevK.posY(), prevK.posZ(),
                                prevK.motionX(), prevK.motionY(), prevK.motionZ(),
                                prevK.yaw(), prevK.pitch(), prevK.health(),
                                prevK.fallDistance(), prevK.fireTicks(), prevK.airSupply(),
                                prevK.onGround(), prevK.hurtTime(), prevK.deathTime(),
                                prevK.fuse(), null
                        ));
                    }
                }
            }
        }
        for (UUID id : removedEntities) {
            EntityDelta previous = previousEntities.get(id);
            if (previous != null && !present.contains(id)) {
                currentFrame.addEntityDelta(previous);
                entityBaselines.remove(id);
            }
        }
        timelineBuffer.pushFrame(currentFrame);
        currentFrame = null;
    }

    private void ensureFrame(@Nullable Level level) {
        if (currentFrame == null && level != null && level.getServer() != null) {
            currentFrame = new TickFrame(level.getServer().overworld().getGameTime());
            currentFrame.captureEnvironment(level.getServer().overworld());
        }
    }

    public void recordBlockChange(Level level, BlockPos pos, BlockState oldState, BlockState newState,
                                  @Nullable BlockEntity oldBE, @Nullable BlockEntity newBE) {
        if (level.isClientSide || !isRecording() || LocalRewind.contains(level.dimension(), pos) || oldState.equals(newState)) return;
        ensureFrame(level);
        if (currentFrame == null) return;
        com.timestop.combat.RewindRuneManager.identifyInventory(oldBE);
        CompoundTag oldNbt = oldBE == null ? null : oldBE.saveWithFullMetadata(level.registryAccess());
        currentFrame.addBlockDelta(BlockDelta.create(level.dimension(), pos, oldState, newState, oldNbt, null));
        if (oldBE != null) blockEntityBaselines.remove(oldBE);
    }

    /** Seed before callers mutate inventories; setChanged itself is called AFTER mutation. */
    public void observeBlockEntity(BlockEntity be) {
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide || !isRecording()) return;
        if (!blockEntityBaselines.containsKey(be)) {
            com.timestop.combat.RewindRuneManager.identifyInventory(be);
            blockEntityBaselines.put(be, be.saveWithFullMetadata(be.getLevel().registryAccess()));
        }
    }

    public void blockEntityChanged(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide || !isRecording() || LocalRewind.contains(level.dimension(), be.getBlockPos())) return;
        ensureFrame(level);
        if (currentFrame == null) return;
        com.timestop.combat.RewindRuneManager.identifyInventory(be);
        CompoundTag now = be.saveWithFullMetadata(level.registryAccess());
        CompoundTag before = blockEntityBaselines.put(be, now);
        if (before != null && !before.equals(now)) {
            currentFrame.addBlockEntityDelta(BlockEntityDelta.create(level.dimension(), be.getBlockPos(),
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()), before, now));
        }
    }

    public void recordExplosion(Level level, double x, double y, double z, float radius) {
        if (!level.isClientSide && isRecording()) {
            ensureFrame(level);
            if (currentFrame != null) {
                currentFrame.addExplosion(new TickFrame.ExplosionMoment(level.dimension(), x, y, z, radius));
            }
        }
    }

    public void recordEntitySpawn(Entity entity) {
        if (isRecording() && shouldTrack(entity)) {
            ensureFrame(entity.level());
            if (currentFrame != null) spawnedEntities.add(entity.getUUID());
        }
    }
    public void recordEntityRemoval(Entity entity) {
        if (!entity.level().isClientSide && isRecording() && shouldTrack(entity)) removedEntities.add(entity.getUUID());
    }
    public void clearTrackingData() {
        currentFrame = null;
        previousEntities.clear();
        previousKinematics.clear();
        removedEntities.clear();
        spawnedEntities.clear();
        blockEntityBaselines.clear();
        entityBaselines.clear();
        lastPlayerInventories.clear();
    }
    public void reset() {
        RewindExplosionEffects.clear();
        clearTrackingData();
        timelineBuffer.clear();
        timelineBuffer.setRecording(true);
        timelineBuffer.setRewinding(false);
        timelineBuffer.setFrozen(false);
        owner = null;
    }
    private static boolean shouldTrack(Entity entity) {
        return !(entity instanceof Player) && !LocalRewind.contains(entity) && !entity.isRemoved()
                && !BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("minecraft:marker");
    }
    private static CompoundTag snapshot(Entity entity) {
        com.timestop.combat.RewindRuneManager.identifyInventory(entity);
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity item)
            com.timestop.combat.RewindRuneManager.identifyRunes(item.getItem());
        if (entity instanceof net.minecraft.world.entity.LivingEntity living)
            for (var stack : living.getAllSlots()) com.timestop.combat.RewindRuneManager.identifyRunes(stack);
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        tag.putString("id", typeId.toString());
        // Gun bullets commonly keep required ballistic/spawn data only in memory (e.g. TACZ).
        // Their existing motion can be restored; constructing replacements from NBT is unsafe.
        if (!entity.getType().canSerialize() || entity instanceof net.minecraft.world.entity.projectile.Projectile
                && !typeId.getNamespace().equals("minecraft")) tag.putBoolean("TimeStopTransient", true);
        tag.remove("Passengers"); // Each passenger has its own timeline entry; do not duplicate nested snapshots.
        return tag;
    }
}
