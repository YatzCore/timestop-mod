package com.timestop.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** World position, or position relative to a body's plot origin (never its mutable COM). */
public record TemporalPosition(ResourceKey<Level> dimension, @Nullable UUID bodyId, Vec3 position) {
    public TemporalPosition {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(position);
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            throw new IllegalArgumentException("Non-finite temporal position");
        }
    }
}
