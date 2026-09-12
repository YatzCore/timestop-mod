package com.timestop.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

public class TimeStopSavedData extends SavedData {
    private static final String DATA_NAME = "timestop_server_config";
    public enum WatchScope { WATCH, GLOBAL, SPHERE }
    private WatchScope watchScope = WatchScope.WATCH;
    private boolean redirectToLook;
    private TimeStopManager.ProjectileStasisMode projectileStasisMode = TimeStopManager.ProjectileStasisMode.FLOWING;

    public TimeStopSavedData() {
    }

    public static TimeStopSavedData load(CompoundTag tag) {
        TimeStopSavedData data = new TimeStopSavedData();
        if (tag.contains("ServerForceGlobalMode")) {
            data.watchScope = tag.getBoolean("ServerForceGlobalMode") ? WatchScope.GLOBAL : WatchScope.WATCH;
        }
        if (tag.contains("WatchScope")) {
            try { data.watchScope = WatchScope.valueOf(tag.getString("WatchScope")); }
            catch (IllegalArgumentException ignored) { data.watchScope = WatchScope.WATCH; }
        }
        data.redirectToLook = tag.getBoolean("RedirectToLook");
        if (tag.contains("ProjectileStasisMode")) {
            try { data.projectileStasisMode = TimeStopManager.ProjectileStasisMode.valueOf(tag.getString("ProjectileStasisMode")); }
            catch (IllegalArgumentException ignored) { data.projectileStasisMode = TimeStopManager.ProjectileStasisMode.FLOWING; }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("ServerForceGlobalMode", isServerForceGlobalMode());
        tag.putString("WatchScope", watchScope.name());
        tag.putBoolean("RedirectToLook", redirectToLook);
        tag.putString("ProjectileStasisMode", projectileStasisMode.name());
        return tag;
    }

    public boolean isServerForceGlobalMode() {
        return watchScope == WatchScope.GLOBAL;
    }

    public void setServerForceGlobalMode(boolean global) {
        setWatchScope(global ? WatchScope.GLOBAL : WatchScope.SPHERE);
    }

    public WatchScope getWatchScope() { return watchScope; }
    public void setWatchScope(WatchScope scope) { watchScope = scope; setDirty(); }
    public boolean isRedirectToLook() { return redirectToLook; }
    public void setRedirectToLook(boolean look) { redirectToLook = look; setDirty(); }
    public TimeStopManager.ProjectileStasisMode getProjectileStasisMode() { return projectileStasisMode; }
    public void setProjectileStasisMode(TimeStopManager.ProjectileStasisMode mode) { this.projectileStasisMode = mode; setDirty(); }

    public static TimeStopSavedData get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new TimeStopSavedData();
        return server.overworld().getDataStorage().computeIfAbsent(TimeStopSavedData::load, TimeStopSavedData::new, DATA_NAME);
    }
}
