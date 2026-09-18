package com.timestop.core.rewind;

import com.timestop.core.rewind.data.PlayerDelta;
import com.timestop.core.rewind.data.TickFrame;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable plan describing all block, entity, player, and weather states to restore.
 */
public record RewindPlan(
        int frameCount,
        Map<BlockKey, BlockState> blockTargetStates,
        Map<BlockKey, CompoundTag> blockEntityTargetNbts,
        Map<BlockKey, CompoundTag> standaloneBeTargetNbts,
        Set<UUID> entitiesToRemove,
        Map<UUID, EntitySpawnInfo> entitiesToRespawn,
        Map<UUID, EntitySpawnInfo> entityTargetStates,
        Map<UUID, PlayerDelta> playerTargetStates,
        @Nullable TickFrame oldestFrame,
        java.util.List<TickFrame.ExplosionMoment> explosions
) {
    public record BlockKey(ResourceKey<Level> dimension, long packedPos) {}

    public record EntitySpawnInfo(
            ResourceKey<Level> dimension,
            ResourceLocation entityType,
            CompoundTag state
    ) {}
}
