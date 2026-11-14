package com.timestop.core.rewind.data;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Captures high-precision motion, transform, impulse buffer, structural plot tag,
 * and constraint graph state for a physics sublevel rigid body across rewind intervals.
 */
public record SubLevelSnapshot(
        UUID bodyId,
        ResourceKey<Level> dimension,
        Vector3d position,
        Quaterniond orientation,
        Vector3d linearVelocity,
        Vector3d angularVelocity,
        Vector3d bufferedLinearImpulse,
        Vector3d bufferedAngularImpulse,
        double pendingSeconds,
        boolean removed,
        Vector3d centerOfMass,
        net.minecraft.core.BlockPos plotCenter,
        net.minecraft.nbt.CompoundTag plotData,
        Set<UUID> connectedBodyIds,
        Map<UUID, Object> constraintConfigs // Keyed by constraint identity, not by partner body.
) {
    public interface RetainedConstraint { long estimatedBytes(); }
    public SubLevelSnapshot(
            UUID bodyId,
            ResourceKey<Level> dimension,
            Vector3d position,
            Quaterniond orientation,
            Vector3d linearVelocity,
            Vector3d angularVelocity,
            Vector3d bufferedLinearImpulse,
            Vector3d bufferedAngularImpulse,
            double pendingSeconds,
            boolean removed,
            Vector3d centerOfMass,
            net.minecraft.core.BlockPos plotCenter
    ) {
        this(bodyId, dimension, position, orientation, linearVelocity, angularVelocity, bufferedLinearImpulse, bufferedAngularImpulse,
                pendingSeconds, removed, centerOfMass, plotCenter, null, Collections.emptySet(), Collections.emptyMap());
    }

    public SubLevelSnapshot(
            UUID bodyId,
            ResourceKey<Level> dimension,
            Vector3d position,
            Quaterniond orientation,
            Vector3d linearVelocity,
            Vector3d angularVelocity,
            Vector3d bufferedLinearImpulse,
            Vector3d bufferedAngularImpulse,
            double pendingSeconds,
            boolean removed,
            Vector3d centerOfMass
    ) {
        this(bodyId, dimension, position, orientation, linearVelocity, angularVelocity, bufferedLinearImpulse, bufferedAngularImpulse,
                pendingSeconds, removed, centerOfMass, null, null, Collections.emptySet(), Collections.emptyMap());
    }

    public SubLevelSnapshot(
            UUID bodyId,
            ResourceKey<Level> dimension,
            Vector3d position,
            Quaterniond orientation,
            Vector3d linearVelocity,
            Vector3d angularVelocity,
            Vector3d bufferedLinearImpulse,
            Vector3d bufferedAngularImpulse,
            double pendingSeconds,
            boolean removed
    ) {
        this(bodyId, dimension, position, orientation, linearVelocity, angularVelocity, bufferedLinearImpulse, bufferedAngularImpulse,
                pendingSeconds, removed, new Vector3d(position), null, null, Collections.emptySet(), Collections.emptyMap());
    }

    public long estimateMemoryBytes() {
        long bytes = 256;
        if (plotData != null) {
            bytes += estimateTagBytes(plotData);
        }
        if (connectedBodyIds != null) {
            bytes += (long) connectedBodyIds.size() * 32L;
        }
        if (constraintConfigs != null) {
            for (Object config : constraintConfigs.values()) {
                bytes += 64L + (config instanceof RetainedConstraint retained ? retained.estimatedBytes() : 0);
            }
        }
        return bytes;
    }

    public static long estimateTagBytes(net.minecraft.nbt.Tag tag) {
        if (tag == null) return 0;
        if (tag instanceof net.minecraft.nbt.ByteArrayTag b) {
            return 24L + b.getAsByteArray().length;
        } else if (tag instanceof net.minecraft.nbt.IntArrayTag i) {
            return 24L + ((long) i.getAsIntArray().length * 4L);
        } else if (tag instanceof net.minecraft.nbt.LongArrayTag l) {
            return 24L + ((long) l.getAsLongArray().length * 8L);
        } else if (tag instanceof net.minecraft.nbt.StringTag s) {
            return 32L + ((long) s.getAsString().length() * 2L);
        } else if (tag instanceof net.minecraft.nbt.CompoundTag c) {
            long size = 48L;
            for (String key : c.getAllKeys()) {
                size += 32L + ((long) key.length() * 2L);
                size += estimateTagBytes(c.get(key));
            }
            return size;
        } else if (tag instanceof net.minecraft.nbt.ListTag l) {
            long size = 32L;
            for (var elem : l) {
                size += estimateTagBytes(elem);
            }
            return size;
        } else if (tag instanceof net.minecraft.nbt.ByteTag) {
            return 16L;
        } else if (tag instanceof net.minecraft.nbt.ShortTag) {
            return 18L;
        } else if (tag instanceof net.minecraft.nbt.IntTag || tag instanceof net.minecraft.nbt.FloatTag) {
            return 20L;
        } else if (tag instanceof net.minecraft.nbt.LongTag || tag instanceof net.minecraft.nbt.DoubleTag) {
            return 24L;
        }
        return 16L;
    }
}
