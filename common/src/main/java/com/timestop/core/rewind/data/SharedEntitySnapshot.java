package com.timestop.core.rewind.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Immutable shared snapshot holding the heavyweight, slowly changing NBT
 * (attributes, brain, equipment, capabilities, and entity metadata) for an entity.
 */
public record SharedEntitySnapshot(
        @Nullable ResourceLocation entityType,
        CompoundTag baseTag,
        int estimatedBytes
) {
    public static SharedEntitySnapshot of(@Nullable ResourceLocation entityType, CompoundTag tag) {
        CompoundTag copy = tag.copy();
        int bytes = copy.sizeInBytes() + 48;
        return new SharedEntitySnapshot(entityType, copy, bytes);
    }
}
