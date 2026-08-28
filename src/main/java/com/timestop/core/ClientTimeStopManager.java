package com.timestop.core;

import com.timestop.network.ModMessages;
import com.timestop.network.SuperhotSyncPacket;
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

    // SUPERHOT dynamic motion tracking
    private static float superhotActivity = 0.0F;
    private static double prevMouseX = 0.0;
    private static double prevMouseY = 0.0;
    private static boolean wasFastLastFrame = false;

    private static final ResourceLocation DESATURATE_SHADER = new ResourceLocation("minecraft", "shaders/post/desaturate.json");

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

        // Direct mouse aiming tracking
        double curX = mc.mouseHandler.xpos();
        double curY = mc.mouseHandler.ypos();
        double mouseDelta = Math.abs(curX - prevMouseX) + Math.abs(curY - prevMouseY);
        prevMouseX = curX;
        prevMouseY = curY;
        boolean hasMouseLook = mouseDelta > 1.2;

        float target = (hasMovementKey || hasAction || hasMouseLook) ? 1.0F : 0.0F;

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
