package com.timestop.compat;

import com.timestop.TimeStopMod;
import com.timestop.platform.Services;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Initialized by the loader entry point, never while mixin configurations are being discovered. */
public final class TemporalWorlds {
    private static TemporalWorldBridge bridge = TemporalWorldBridge.VANILLA;
    private static volatile boolean initialized = false;

    public static synchronized void initialize() {
        if (!initialized) {
            initialized = true;
            if (Services.PLATFORM.isModLoaded("sable") && !"Forge".equals(Services.PLATFORM.getPlatformName())) {
                bridge = new com.timestop.compat.sable.SableWorldBridge();
                TimeStopMod.LOGGER.info("[TimeStop] Sable spatial integration enabled (experimental; see SABLE_HANDOFF.md)");
            }
        }
    }

    public static TemporalWorldBridge bridge() {
        if (!initialized) {
            initialize();
        }
        return bridge;
    }
    public static Vec3 toWorld(Level level, Vec3 position) {
        return level == null ? position : bridge.toWorld(level, position);
    }

    /** Server-only dimension queries. Client callers must pass their actual Level instead. */
    public static Vec3 serverToWorld(ResourceKey<Level> dimension, Vec3 position) {
        if (bridge == TemporalWorldBridge.VANILLA) return position;
        var server = Services.PLATFORM.getCurrentServer();
        return server == null ? position : toWorld(server.getLevel(dimension), position);
    }

    private TemporalWorlds() {}
}
