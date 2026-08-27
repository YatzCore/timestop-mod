package com.timestop.core;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

public class ClientTimeStopManager {
    private static boolean clientTimeStopped = false;
    private static int clientRemainingTicks = 0;
    private static int clientTotalDuration = 0;
    @Nullable
    private static UUID clientInitiatorUuid = null;
    private static TimeMode clientMode = TimeMode.TIME_STOP;
    private static boolean shaderActive = false;

    private static final ResourceLocation DESATURATE_SHADER = new ResourceLocation("minecraft", "shaders/post/desaturate.json");

    public static boolean isTimeStopped() {
        return clientTimeStopped;
    }

    public static TimeMode getCurrentMode() {
        return clientMode;
    }

    public static float getClientTickMs() {
        if (!clientTimeStopped) return 50.0F;
        switch (clientMode) {
            case FAST_FORWARD:
                return 10.0F; // 10ms = 100 TPS (5x speed for everything!)
            case SLOW_MOTION:
            case MATRIX:
                return 200.0F; // 200ms = 5 TPS (0.25x speed for everything!)
            default:
                return 50.0F; // 50ms = 20 TPS (normal)
        }
    }

    public static boolean isEntityExempt(Entity entity) {
        if (!clientTimeStopped) return true;

        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return true;
            }
            if (clientInitiatorUuid != null && player.getUUID().equals(clientInitiatorUuid)) {
                return true;
            }
            Player localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null && player.getUUID().equals(localPlayer.getUUID())) {
                if (clientInitiatorUuid != null && clientInitiatorUuid.equals(localPlayer.getUUID())) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void handleSync(boolean active, int duration, @Nullable UUID initiator, TimeMode mode) {
        clientTimeStopped = active;
        clientTotalDuration = duration;
        clientRemainingTicks = duration;
        clientInitiatorUuid = initiator;
        clientMode = mode;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            if (active && mode == TimeMode.TIME_STOP) {
                applyShader();
            } else {
                removeShader();
            }
        }
    }

    public static void clientTick() {
        if (clientTimeStopped && clientRemainingTicks > 0) {
            clientRemainingTicks--;
        }
    }

    public static void applyShader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && !shaderActive) {
            try {
                mc.gameRenderer.loadEffect(DESATURATE_SHADER);
                shaderActive = true;
            } catch (Exception ignored) {
            }
        }
    }

    public static void removeShader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && shaderActive) {
            try {
                mc.gameRenderer.shutdownEffect();
                shaderActive = false;
            } catch (Exception ignored) {
            }
        }
    }

    public static int getRemainingTicks() {
        return clientRemainingTicks;
    }

    public static int getTotalDuration() {
        return clientTotalDuration;
    }
}
