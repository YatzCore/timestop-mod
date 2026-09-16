package com.timestop.core;

import com.timestop.network.ModMessages;
import com.timestop.network.SuperhotSyncPacket;
import com.timestop.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
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
    private static TimeStopManager.ProjectileStasisMode projectileMode = TimeStopManager.ProjectileStasisMode.FLOWING;
    private static boolean allowPlayerProjectiles = true;

    public static void setProjectileFlow(TimeStopManager.ProjectileStasisMode mode, boolean allowed) {
        projectileMode = mode;
        allowPlayerProjectiles = allowed;
    }

    public static TimeStopManager.ProjectileStasisMode getProjectileMode() {
        return projectileMode;
    }

    public static boolean arePlayerProjectilesFlowing() {
        return allowPlayerProjectiles && projectileMode == TimeStopManager.ProjectileStasisMode.FLOWING;
    }

    // SUPERHOT dynamic motion tracking
    private static float superhotActivity = 0.0F;
    private static volatile float serverSyncedSuperhotActivity = 0.0F;
    private static long lastSuperhotReport;
    private static double prevMouseX = 0.0;
    private static double prevMouseY = 0.0;
    private static float lastYRot = 0.0F;
    private static float lastXRot = 0.0F;
    private static boolean wasFastLastFrame = false;
    private static final ResourceLocation DESATURATE_SHADER = new ResourceLocation("minecraft", "shaders/post/desaturate.json");
    private static final ResourceLocation SUPERHOT_SHADER = new ResourceLocation("minecraft", "shaders/post/superhot.json");
    private static ResourceLocation currentShader = null;

    public static boolean isGlobalTimeStopActive() {
        return clientTimeStopped;
    }

    public static boolean isShaderActive() {
        return shaderActive;
    }

    public static boolean isTimeStopped() {
        if (clientTimeStopped) {
            return true; // Global Server Time Stop is active everywhere!
        }
        if (ClientBubbleManager.hasActiveBubbles()) {
            return ClientBubbleManager.getCameraBubble() != null;
        }
        return false;
    }

    public static TimeMode getCurrentMode() {
        if (ClientBubbleManager.hasActiveBubbles()) {
            ClientBubbleManager.ClientBubble b = ClientBubbleManager.getCameraBubble();
            if (b != null) return b.mode;
        }
        return clientMode;
    }

    public static float getSuperhotActivity() {
        return superhotActivity;
    }

    public static float getClientTickMs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) {
            return 50.0F; // Never accelerate or slow down GUI, death screen, or main menu
        }
        TimeMode mode = clientMode;
        boolean active = clientTimeStopped;
        if (ClientBubbleManager.hasActiveBubbles()) {
            ClientBubbleManager.ClientBubble b = ClientBubbleManager.getCameraBubble();
            if (b != null) {
                active = true;
                mode = b.mode;
            } else if (!clientTimeStopped) {
                return 50.0F; // Camera outside bubble and no global time stop = normal real-time!
            }
        }
        if (!active) return 50.0F;
        switch (mode) {
            case FAST_FORWARD:
                return (float) Math.max(1.0, 50.0 / com.timestop.config.TimeStopConfig.COMMON.fastForwardRate.get());
            case SLOW_MOTION:
                return (float) Math.max(50.0, 50.0 / com.timestop.config.TimeStopConfig.COMMON.slowMotionRate.get());
            case MATRIX:
                return (float) Math.max(50.0, 50.0 / com.timestop.config.TimeStopConfig.COMMON.matrixRate.get());
            case SUPERHOT:
                float idleRate = com.timestop.config.TimeStopConfig.COMMON.superhotIdleRate.get().floatValue();
                float maxMs = Math.max(50.0F, 50.0F / idleRate);
                float act = Math.max(0.0F, Math.min(1.0F, superhotActivity));
                return maxMs - act * (maxMs - 50.0F);
            default:
                return 50.0F;
        }
    }

    public static void setServerSyncedSuperhotActivity(float activity) {
        serverSyncedSuperhotActivity = Float.isFinite(activity) ? Math.max(0, Math.min(1, activity)) : 0;
    }

    private static final Set<UUID> clientExemptPlayers = ConcurrentHashMap.newKeySet();

    public static boolean isEntityExempt(Entity entity) {
        if (ClientBubbleManager.hasActiveBubbles()) {
            ClientBubbleManager.ClientBubble b = ClientBubbleManager.getDominantBubble(entity.position());
            if (b == null) return true; // Outside bubble -> free to act!
            return b.canEntityAct(entity);
        }

        if (!clientTimeStopped) return true;

        if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            if (arePlayerProjectilesFlowing()) {
                Entity owner = projectile.getOwner();
                if (owner instanceof Player player) {
                    return isEntityExempt(player);
                }
            }
            return false;
        }

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

    public static void reset() {
        clientTimeStopped = false;
        clientRemainingTicks = 0;
        clientTotalDuration = 0;
        clientInitiatorUuid = null;
        clientMode = TimeMode.TIME_STOP;
        clientExemptPlayers.clear();
        superhotActivity = 0.0F;
        serverSyncedSuperhotActivity = 0.0F;
        wasFastLastFrame = false;
        setProjectileFlow(TimeStopManager.ProjectileStasisMode.FLOWING, true);
        removeShader();
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
        boolean isSuperhot = false;
        boolean inBubble = false;
        ClientBubbleManager.ClientBubble currentBubble = null;
        if (ClientBubbleManager.hasActiveBubbles()) {
            currentBubble = ClientBubbleManager.getCameraBubble();
            if (currentBubble != null && currentBubble.mode == TimeMode.SUPERHOT) {
                isSuperhot = true;
                inBubble = true;
                serverSyncedSuperhotActivity = ClientBubbleManager.getSuperhotActivity(currentBubble.bubbleId);
            }
        } else if (clientTimeStopped && clientMode == TimeMode.SUPERHOT) {
            isSuperhot = true;
        }

        if (!isSuperhot) {
            superhotActivity = 0.0F;
            serverSyncedSuperhotActivity = 0.0F;
            wasFastLastFrame = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || !mc.player.isAlive()) {
            superhotActivity = 0.0F;
            wasFastLastFrame = false;
            return;
        }

        // Direct key state queries on client options: instantaneous responsiveness!
        // Moving the mouse cursor does NOT advance time (free cursor aiming).
        boolean hasMovementKey = mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown();

        boolean hasAction = mc.options.keyAttack.isDown()
                || mc.options.keyUse.isDown()
                || mc.player.swinging
                || mc.player.isUsingItem();

        boolean myLocalFast = mc.screen == null && (hasMovementKey || hasAction) && !mc.player.isSpectator();
        if (inBubble && currentBubble != null) {
            if (!currentBubble.contains(mc.player.getX(), mc.player.getY() + mc.player.getBbHeight() * 0.5, mc.player.getZ())) {
                myLocalFast = false;
            }
        }

        long now = System.currentTimeMillis();
        if (myLocalFast != wasFastLastFrame || now - lastSuperhotReport >= 250) {
            lastSuperhotReport = now;
            wasFastLastFrame = myLocalFast;
            ModMessages.sendToServer(new SuperhotSyncPacket(myLocalFast ? 1.0F : 0.0F));
        }

        // In Superhot: Moving or acting advances time. If any player on server in this sphere is moving, time also advances!
        float target = (myLocalFast || serverSyncedSuperhotActivity > 0.15F) ? 1.0F : 0.0F;

        if (target >= 0.9F) {
            // Immediate real-time acceleration!
            superhotActivity = 1.0F;
        } else {
            // Smooth decay to standstill (approx 0.3s)
            superhotActivity = Math.max(0.0F, superhotActivity - 0.045F);
        }
    }

    public static void applyShader() {
        applyShader(DESATURATE_SHADER);
    }

    public static void applyShader(ResourceLocation shader) {
        if (!com.timestop.config.TimeStopConfig.CLIENT.enableShaders.get()) {
            removeShader();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) {
            try {
                PostChain activeEffect = ((GameRendererAccessor) mc.gameRenderer).timestop$getPostEffect();
                if (activeEffect == null || !shaderActive || !shader.equals(currentShader)) {
                    ((GameRendererAccessor) mc.gameRenderer).timestop$loadEffect(shader);
                    shaderActive = true;
                    currentShader = shader;
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void removeShader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) {
            try {
                PostChain activeEffect = ((GameRendererAccessor) mc.gameRenderer).timestop$getPostEffect();
                if (activeEffect != null && shaderActive) {
                    mc.gameRenderer.shutdownEffect();
                }
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
