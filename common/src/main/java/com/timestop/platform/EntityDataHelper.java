package com.timestop.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public final class EntityDataHelper {
    private EntityDataHelper() {}

    public static CompoundTag getPersistentData(Entity entity) {
        return Services.PLATFORM.getPersistentData(entity);
    }
}