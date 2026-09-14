package com.timestop.fabric.platform;

import com.timestop.fabric.TimeStopFabricMod;
import com.timestop.platform.IEntityDataSaver;
import com.timestop.platform.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public CompoundTag getPersistentData(Entity entity) {
        if (entity instanceof IEntityDataSaver saver) {
            return saver.timestop$getPersistentData();
        }
        return new CompoundTag();
    }

    @Override
    public MinecraftServer getCurrentServer() {
        return TimeStopFabricMod.getServer();
    }
}
