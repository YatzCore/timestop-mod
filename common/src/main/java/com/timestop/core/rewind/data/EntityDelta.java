package com.timestop.core.rewind.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Records entity lifecycle (spawn/despawn) and per-tick state transitions (position/health/motion).
 * Supports both standalone CompoundTag states and compact kinematic updates referencing a SharedEntitySnapshot.
 */
public class EntityDelta {
    public enum Type {
        SPAWN,
        DESPAWN,
        UPDATE
    }

    private final ResourceKey<Level> dimension;
    private final UUID entityId;
    private final Type type;
    @Nullable private final ResourceLocation entityType;
    @Nullable private CompoundTag oldState;
    @Nullable private final CompoundTag newState;

    // Compact kinematic fields
    @Nullable private final SharedEntitySnapshot sharedSnapshot;
    private final double posX, posY, posZ;
    private final double motionX, motionY, motionZ;
    private final float yaw, pitch;
    private final float health;
    private final float fallDistance;
    private final short fireTicks;
    private final short airSupply;
    private final boolean onGround;
    private final short hurtTime;
    private final short deathTime;
    private final int fuse;
    @Nullable private final CompoundTag extraTag;

    public EntityDelta(
            ResourceKey<Level> dimension,
            UUID entityId,
            Type type,
            @Nullable ResourceLocation entityType,
            @Nullable CompoundTag oldState,
            @Nullable CompoundTag newState
    ) {
        this.dimension = dimension;
        this.entityId = entityId;
        this.type = type;
        this.entityType = entityType;
        this.oldState = oldState;
        this.newState = newState;
        this.sharedSnapshot = null;
        this.posX = 0; this.posY = 0; this.posZ = 0;
        this.motionX = 0; this.motionY = 0; this.motionZ = 0;
        this.yaw = 0; this.pitch = 0;
        this.health = 0; this.fallDistance = 0;
        this.fireTicks = 0; this.airSupply = 0;
        this.onGround = false;
        this.hurtTime = 0; this.deathTime = 0;
        this.fuse = -1;
        this.extraTag = null;
    }

    private EntityDelta(
            ResourceKey<Level> dimension,
            UUID entityId,
            Type type,
            @Nullable ResourceLocation entityType,
            SharedEntitySnapshot sharedSnapshot,
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
            int fuse,
            @Nullable CompoundTag extraTag
    ) {
        this.dimension = dimension;
        this.entityId = entityId;
        this.type = type;
        this.entityType = entityType;
        this.oldState = null;
        this.newState = null;
        this.sharedSnapshot = sharedSnapshot;
        this.posX = posX; this.posY = posY; this.posZ = posZ;
        this.motionX = motionX; this.motionY = motionY; this.motionZ = motionZ;
        this.yaw = yaw; this.pitch = pitch;
        this.health = health; this.fallDistance = fallDistance;
        this.fireTicks = fireTicks; this.airSupply = airSupply;
        this.onGround = onGround;
        this.hurtTime = hurtTime; this.deathTime = deathTime;
        this.fuse = fuse;
        this.extraTag = extraTag != null && !extraTag.isEmpty() ? extraTag.copy() : null;
    }

    public static EntityDelta spawn(
            ResourceKey<Level> dimension,
            UUID entityId,
            ResourceLocation entityType,
            CompoundTag state
    ) {
        return new EntityDelta(dimension, entityId, Type.SPAWN, entityType, null, state);
    }

    public static EntityDelta despawn(
            ResourceKey<Level> dimension,
            UUID entityId,
            ResourceLocation entityType,
            CompoundTag state
    ) {
        return new EntityDelta(dimension, entityId, Type.DESPAWN, entityType, state, null);
    }

    public static EntityDelta update(
            ResourceKey<Level> dimension,
            UUID entityId,
            CompoundTag oldState,
            CompoundTag newState
    ) {
        return new EntityDelta(dimension, entityId, Type.UPDATE, null, oldState, newState);
    }

    public static EntityDelta compactUpdate(
            ResourceKey<Level> dimension,
            UUID entityId,
            @Nullable ResourceLocation entityType,
            SharedEntitySnapshot sharedSnapshot,
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
            int fuse,
            @Nullable CompoundTag extraTag
    ) {
        return new EntityDelta(
                dimension, entityId, Type.UPDATE, entityType, sharedSnapshot,
                posX, posY, posZ, motionX, motionY, motionZ,
                yaw, pitch, health, fallDistance, fireTicks, airSupply,
                onGround, hurtTime, deathTime, fuse, extraTag
        );
    }

    public static EntityDelta compactDespawn(
            ResourceKey<Level> dimension,
            UUID entityId,
            @Nullable ResourceLocation entityType,
            SharedEntitySnapshot sharedSnapshot,
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
            int fuse,
            @Nullable CompoundTag extraTag
    ) {
        return new EntityDelta(
                dimension, entityId, Type.DESPAWN, entityType, sharedSnapshot,
                posX, posY, posZ, motionX, motionY, motionZ,
                yaw, pitch, health, fallDistance, fireTicks, airSupply,
                onGround, hurtTime, deathTime, fuse, extraTag
        );
    }

    public ResourceKey<Level> dimension() { return dimension; }
    public UUID entityId() { return entityId; }
    public Type type() { return type; }
    @Nullable public ResourceLocation entityType() { return entityType; }
    @Nullable public CompoundTag newState() { return newState; }
    @Nullable public SharedEntitySnapshot sharedSnapshot() { return sharedSnapshot; }
    public boolean isCompact() { return sharedSnapshot != null; }

    public synchronized CompoundTag oldState() {
        if (oldState != null) return oldState;
        if (sharedSnapshot != null) {
            CompoundTag tag = sharedSnapshot.baseTag().copy();
            ListTag posList = new ListTag();
            posList.add(DoubleTag.valueOf(posX));
            posList.add(DoubleTag.valueOf(posY));
            posList.add(DoubleTag.valueOf(posZ));
            tag.put("Pos", posList);

            ListTag motionList = new ListTag();
            motionList.add(DoubleTag.valueOf(motionX));
            motionList.add(DoubleTag.valueOf(motionY));
            motionList.add(DoubleTag.valueOf(motionZ));
            tag.put("Motion", motionList);

            ListTag rotList = new ListTag();
            rotList.add(FloatTag.valueOf(yaw));
            rotList.add(FloatTag.valueOf(pitch));
            tag.put("Rotation", rotList);

            tag.putFloat("Health", health);
            tag.putFloat("FallDistance", fallDistance);
            tag.putShort("Fire", fireTicks);
            tag.putShort("Air", airSupply);
            tag.putBoolean("OnGround", onGround);
            tag.putShort("HurtTime", hurtTime);
            tag.putShort("DeathTime", deathTime);
            if (fuse >= 0) {
                tag.putShort("Fuse", (short) fuse);
            }
            if (extraTag != null) {
                tag.merge(extraTag);
            }
            this.oldState = tag;
            return tag;
        }
        return null;
    }

    public int estimateMemoryBytes() {
        if (sharedSnapshot != null) {
            int bytes = 112;
            if (extraTag != null) bytes += extraTag.sizeInBytes();
            return bytes;
        }
        int bytes = 64;
        if (oldState != null) bytes += oldState.sizeInBytes();
        if (newState != null) bytes += newState.sizeInBytes();
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntityDelta that)) return false;
        return Objects.equals(dimension, that.dimension) &&
                Objects.equals(entityId, that.entityId) &&
                type == that.type &&
                Objects.equals(entityType, that.entityType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, entityId, type, entityType);
    }
}
