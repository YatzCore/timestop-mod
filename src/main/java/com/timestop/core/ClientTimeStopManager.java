package com.timestop.core;

import com.timestop.network.ModMessages;
import com.timestop.network.SuperhotSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientTimeStopManager {
    private static boolean clientTimeStopped = false;
    private static int clientRemainingTicks = 0;
    private static int clientTotalDuration = 0;
    @Nullable
    private static UUID clientInitiatorUuid = null;
    private static TimeMode clientMode = TimeMode.TIME_STOP;
    private static boolean shaderActive = false;

    // SUPERHOT dynamic motion tracking
    private static float superhotActivity = 0.0F;
    private static double prevMouseX = 0.0;
    private static double prevMouseY = 0.0;
    private static boolean wasFastLastFrame = false;
    private static final ResourceLocation DESATURATE_SHADER = new ResourceLocation("minecraft", "shaders/post/desaturate.json");
    private static final ResourceLocation SUPERHOT_SHADER = new ResourceLocation("minecraft", "shaders/post/superhot.json");
    private static ResourceLocation currentShader = null;

    public static boolean isTimeStopped() {
        return clientTimeStopped;
    }

    public static TimeMode getCurrentMode() {
        return clientMode;
    }

    public static float getSuperhotActivity() {
        return superhotActivity;
    }

    public static float getClientTickMs() {
        if (!clientTimeStopped) return 50.0F;
        switch (clientMode) {
            case FAST_FORWARD:
                return 10.0F; // 10ms = 100 TPS (5x speed for everything!)
            case SLOW_MOTION:
            case MATRIX:
                return 200.0F; // 200ms = 5 TPS (0.25x speed for everything!)
            case SUPERHOT:
                // 250ms = 4 TPS at rest, smoothly scaling to 50ms = 20 TPS when moving
                return 250.0F - (superhotActivity * 200.0F);
            default:
                return 50.0F; // 50ms = 20 TPS (normal)
        }
    }

    private static final Set<UUID> clientExemptPlayers = ConcurrentHashMap.newKeySet();

    public static boolean isEntityExempt(Entity entity) {
        if (!clientTimeStopped) return true;

        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return true;
            }
            if (clientInitiatorUuid != null && player.getUUID().equals(clientInitiatorUuid)) {
                return true;
            }
            if (clientExemptPlayers.contains(player.getUUID())) {
                return true;
            }
        }

        return false;
    }

    public static void handleSync(boolean active, int duration, @Nullable UUID initiator, TimeMode mode, Set<UUID> exempt) {
        clientTimeStopped = active;
        clientTotalDuration = duration;
        clientRemainingTicks = duration;
        clientInitiatorUuid = initiator;
        clientMode = mode;
        clientExemptPlayers.clear();
        if (exempt != null) {
            clientExemptPlayers.addAll(exempt);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            if (active) {
                if (mode == TimeMode.TIME_STOP) {
                    applyShader(DESATURATE_SHADER);
                } else if (mode == TimeMode.SUPERHOT) {
                    applyShader(SUPERHOT_SHADER);
                } else {
                    removeShader();
                }
            } else {
                removeShader();
            }
        }
    }

    public static void handleSync(boolean active, int duration, @Nullable UUID initiator, TimeMode mode) {
        handleSync(active, duration, initiator, mode, Collections.emptySet());
    }

    public static void clientTick() {
        if (clientTimeStopped && clientRemainingTicks > 0) {
            clientRemainingTicks--;
        }
    }

    public static void onRenderFrameMotion() {
        if (!clientTimeStopped || clientMode != TimeMode.SUPERHOT) {
            superhotActivity = 0.0F;
            wasFastLastFrame = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || mc.screen != null) {
            return;
        }

        // Direct key state queries on client options: instantaneous responsiveness!
        boolean hasMovementKey = mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown()
                || mc.options.keyShift.isDown()
                || mc.options.keySprint.isDown();

        boolean hasAction = mc.options.keyAttack.isDown()
                || mc.options.keyUse.isDown()
                || mc.player.swinging
                || mc.player.isUsingItem();

        // In Superhot: Moving (WASD, jump, sprint) or attacking/using item advances time.
        // Mouse look allows aiming freely in slow motion without speeding time to normal!
        float target = (hasMovementKey || hasAction) ? 1.0F : 0.0F;

        if (target >= 0.9F) {
            // Immediate real-time acceleration!
            superhotActivity = 1.0F;
        } else {
            // Smooth decay to standstill (approx 0.3s)
            superhotActivity = Math.max(0.0F, superhotActivity - 0.045F);
        }

        // Send sync packet when state transitions between active motion and rest
        boolean isFastNow = superhotActivity > 0.25F;
        if (isFastNow != wasFastLastFrame) {
            wasFastLastFrame = isFastNow;
            ModMessages.sendToServer(new SuperhotSyncPacket(superhotActivity));
        }
    }

    public static void applyShader() {
        applyShader(DESATURATE_SHADER);
    }

    public static void applyShader(ResourceLocation shader) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) {
            try {
                if (!shaderActive || !shader.equals(currentShader)) {
                    mc.gameRenderer.loadEffect(shader);
                    shaderActive = true;
                    currentShader = shader;
                }
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
                currentShader = null;
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
