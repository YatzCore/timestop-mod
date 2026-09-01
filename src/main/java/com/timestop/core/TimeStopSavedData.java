package com.timestop.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

public class TimeStopSavedData extends SavedData {
    private static final String DATA_NAME = "timestop_server_config";
    private boolean serverForceGlobalMode = false;

    public TimeStopSavedData() {
    }

    public static TimeStopSavedData load(CompoundTag tag) {
        TimeStopSavedData data = new TimeStopSavedData();
        if (tag.contains("ServerForceGlobalMode")) {
            data.serverForceGlobalMode = tag.getBoolean("ServerForceGlobalMode");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("ServerForceGlobalMode", this.serverForceGlobalMode);
        return tag;
    }

    public boolean isServerForceGlobalMode() {
        return this.serverForceGlobalMode;
    }

    public void setServerForceGlobalMode(boolean global) {
        this.serverForceGlobalMode = global;
        this.setDirty();
    }

    public static TimeStopSavedData get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new TimeStopSavedData();
        return server.overworld().getDataStorage().computeIfAbsent(TimeStopSavedData::load, TimeStopSavedData::new, DATA_NAME);
    }
}