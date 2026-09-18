package com.timestop.core.rewind.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Records an in-place block entity data change without a block state change.
 */
public record BlockEntityDelta(
        ResourceKey<Level> dimension,
        long packedPos,
        ResourceLocation blockEntityType,
        CompoundTag oldNbt,
        @Nullable CompoundTag newNbt
) {
    public static BlockEntityDelta create(
            ResourceKey<Level> dimension,
            BlockPos pos,
            ResourceLocation blockEntityType,
            CompoundTag oldNbt,
            @Nullable CompoundTag newNbt
    ) {
        // Discard forward newNbt to avoid holding redundant memory during backward rewind
        return new BlockEntityDelta(dimension, pos.asLong(), blockEntityType, oldNbt, null);
    }

    public BlockPos getBlockPos() {
        return BlockPos.of(packedPos);
    }

    public int estimateMemoryBytes() {
        return 48 + (oldNbt != null ? oldNbt.sizeInBytes() : 0);
    }
}

