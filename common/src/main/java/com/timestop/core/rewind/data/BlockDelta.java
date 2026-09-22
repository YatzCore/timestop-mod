package com.timestop.core.rewind.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Stores a block state transition at a specific BlockPos within a dimension.
 */
public record BlockDelta(
        ResourceKey<Level> dimension,
        long packedPos,
        BlockState oldState,
        BlockState newState,
        @Nullable CompoundTag oldBlockEntityNbt,
        @Nullable CompoundTag newBlockEntityNbt
) {
    public static BlockDelta create(
            ResourceKey<Level> dimension,
            BlockPos pos,
            BlockState oldState,
            BlockState newState,
            @Nullable CompoundTag oldBENbt,
            @Nullable CompoundTag newBENbt
    ) {
        return new BlockDelta(dimension, pos.asLong(), oldState, newState, oldBENbt, newBENbt);
    }

    public BlockPos getBlockPos() {
        return BlockPos.of(packedPos);
    }

    public int estimateMemoryBytes() {
        int bytes = 48; // Base object overhead + primitives + references
        if (oldBlockEntityNbt != null) bytes += oldBlockEntityNbt.sizeInBytes();
        if (newBlockEntityNbt != null) bytes += newBlockEntityNbt.sizeInBytes();
        return bytes;
    }
}
