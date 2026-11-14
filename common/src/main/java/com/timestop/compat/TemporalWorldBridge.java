package com.timestop.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/** No optional mod types cross this boundary. Unloaded references must not resolve to another body. */
public interface TemporalWorldBridge {
    Vec3 toWorld(Level level, Vec3 position);
    TemporalPosition anchor(Level level, Vec3 position);
    Optional<Vec3> resolve(Level level, TemporalPosition position);

    default BlockEntity getBlockEntityAtWorld(Level level, Vec3 worldPos) {
        if (level == null) return null;
        BlockPos pos = BlockPos.containing(worldPos);
        return level.hasChunkAt(pos) ? level.getBlockEntity(pos) : null;
    }

    default void updateStasisEntityTransport(Entity entity) {
        if (entity.getVehicle() != null) {
            entity.getVehicle().positionRider(entity);
        }
    }

    default void clearStasisEntityTransport(Entity entity) {}

    default java.util.UUID getContainingSubLevelId(Level level, BlockPos pos) { return null; }

    default java.util.Optional<net.minecraft.world.phys.Vec3> getSubLevelCenterOfMassWorld(Level level, java.util.UUID bodyId) {
        return java.util.Optional.empty();
    }

    default java.util.Optional<net.minecraft.world.phys.Vec3> getLastLiveCenterOfMassWorld(Level level, java.util.UUID bodyId) {
        return java.util.Optional.empty();
    }

    default java.util.Set<java.util.UUID> getConnectedSubLevels(java.util.UUID bodyId) {
        return java.util.Collections.emptySet();
    }

    default boolean isSubLevelPlotBlock(Level level, BlockPos pos, com.timestop.core.rewind.data.SubLevelSnapshot target) {
        return false;
    }

    default BlockPos remapBlockPos(Level level, BlockPos pos) {
        return pos;
    }

    default void captureSubLevelsBegin(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.data.TickFrame frame) {}

    default void captureSubLevelsFinish(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.data.TickFrame frame) {}

    default void captureSubLevels(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.data.TickFrame frame) {
        captureSubLevelsBegin(server, frame);
    }

    /** Validate dependencies without changing world state. Throw if the plan is incomplete. */
    default void validateRewind(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.RewindPlan plan) {}

    default void preRewind(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.RewindPlan plan) {}

    default void applySubLevelRewind(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.RewindPlan plan) {}

    default void postRewind(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.RewindPlan plan) {}

    TemporalWorldBridge VANILLA = new TemporalWorldBridge() {
        public Vec3 toWorld(Level level, Vec3 position) { return position; }
        public TemporalPosition anchor(Level level, Vec3 position) {
            return new TemporalPosition(level.dimension(), null, position);
        }
        public Optional<Vec3> resolve(Level level, TemporalPosition position) {
            return position.bodyId() == null && level.dimension().equals(position.dimension())
                    ? Optional.of(position.position()) : Optional.empty();
        }
    };
}

