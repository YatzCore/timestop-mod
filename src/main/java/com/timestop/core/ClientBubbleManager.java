package com.timestop.core;

import com.timestop.item.WatchTier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientBubbleManager {

    private static final ResourceLocation SUPERHOT_SHADER = new ResourceLocation("minecraft", "shaders/post/superhot.json");

    public static class ClientBubble {
        public final UUID bubbleId;
        @Nullable
        public final UUID ownerUuid;
        public final String dimensionId;
        public Vec3 center;
        public final double radius;
        public final double radiusSq;
        public final TimeMode mode;
        public int remainingTicks;
        public final int totalDuration;
        public final WatchTier tier;
        public final Set<UUID> exemptPlayers = ConcurrentHashMap.newKeySet();

        public ClientBubble(UUID bubbleId, @Nullable UUID ownerUuid, String dimensionId, Vec3 center,
                            double radius, TimeMode mode, int remainingTicks, int totalDuration,
                            WatchTier tier, Set<UUID> exempt) {
            this.bubbleId = bubbleId;
            this.ownerUuid = ownerUuid;
            this.dimensionId = dimensionId;
            this.center = center;
            this.radius = radius;
            this.radiusSq = radius * radius;
            this.mode = mode;
            this.remainingTicks = remainingTicks;
            this.totalDuration = totalDuration;
            this.tier = tier;
            if (exempt != null) {
                this.exemptPlayers.addAll(exempt);
            }
        }

        public Vec3 getCenter(float partialTick) {
            if (ownerUuid != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    if (mc.player != null && ownerUuid.equals(mc.player.getUUID())) {
                        Vec3 pPos = mc.player.getPosition(partialTick);
                        return pPos.add(0, mc.player.getBbHeight() * 0.5, 0);
                    }
                    Player other = mc.level.getPlayerByUUID(ownerUuid);
                    if (other != null) {
                        Vec3 oPos = other.getPosition(partialTick);
                        return oPos.add(0, other.getBbHeight() * 0.5, 0);
                    }
                }
            }
            return this.center;
        }

        public Vec3 getCenter() {
            return this.center;
        }

        public boolean contains(Vec3 pos) {
            return contains(pos.x, pos.y, pos.z);
        }

        public boolean contains(double px, double py, double pz) {
            double dx = px - this.center.x;
            double dy = py - this.center.y;
            double dz = pz - this.center.z;
            return (dx * dx + dy * dy + dz * dz) <= this.radiusSq;
        }

        public boolean canEntityAct(Entity entity) {
            if (!(entity instanceof Player player)) {
                return false;
            }

            if (player.isCreative() || player.isSpectator()) return true;
            if (ownerUuid != null && ownerUuid.equals(player.getUUID())) return true;
            if (exemptPlayers.contains(player.getUUID())) return true;

            // Self-Generated Temporal Field Shielding:
            // If player is inside ANY active bubble they own, they are free to act!
            for (ClientBubble cb : clientBubbles.values()) {
                if (cb.ownerUuid != null && cb.ownerUuid.equals(player.getUUID()) && cb.contains(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ())) {
                    return true;
                }
            }

            // Temporal Resonance / Parity in client prediction
            WatchTier tier = TemporalBubble.getBestEquippedTier(player);
            if (tier != null && tier.getTierLevel() >= this.tier.getTierLevel()) {
                return true;
            }
            return false;
        }

        public float getTimeDilationFactor(Entity entity) {
            if (canEntityAct(entity)) return 1.0F;

            if (entity instanceof Player player) {
                WatchTier playerTier = TemporalBubble.getBestEquippedTier(player);
                if (playerTier != null) {
                    return Math.max(0.20F, (float) playerTier.getTierLevel() / (float) this.tier.getTierLevel());
                }
            }

            switch (mode) {
                case TIME_STOP:
                    return 0.0F;
                case SLOW_MOTION:
                case MATRIX:
                    return 0.25F;
                case FAST_FORWARD:
                    return 5.0F;
                case SUPERHOT:
                    return 0.20F;
                default:
                    return 1.0F;
            }
        }
    }

    private static final Map<UUID, ClientBubble> clientBubbles = new ConcurrentHashMap<>();
    private static boolean wasInsideBubble = false;

    public static Collection<ClientBubble> getActiveBubbles() {
        return Collections.unmodifiableCollection(clientBubbles.values());
    }

    public static boolean hasActiveBubbles() {
        return !clientBubbles.isEmpty();
    }

    public static void handleSyncBubble(UUID bubbleId, @Nullable UUID ownerUuid, String dimensionId,
                                        double x, double y, double z, double radius, TimeMode mode,
                                        int remainingTicks, int totalDuration, WatchTier tier, Set<UUID> exempt) {
        Vec3 center = new Vec3(x, y, z);
        ClientBubble existing = clientBubbles.get(bubbleId);
        if (existing != null) {
            existing.center = center;
            existing.remainingTicks = remainingTicks;
            existing.exemptPlayers.clear();
            existing.exemptPlayers.addAll(exempt);
        } else {
            clientBubbles.put(bubbleId, new ClientBubble(bubbleId, ownerUuid, dimensionId, center,
                    radius, mode, remainingTicks, totalDuration, tier, exempt));
        }
    }

    public static void handleRemoveBubble(UUID bubbleId) {
        clientBubbles.remove(bubbleId);
        if (clientBubbles.isEmpty()) {
            wasInsideBubble = false;
            if (!ClientTimeStopManager.isGlobalTimeStopActive()) {
                ClientTimeStopManager.removeShader();
            }
        }
    }

    public static void reset() {
        clientBubbles.clear();
        wasInsideBubble = false;
        if (!ClientTimeStopManager.isGlobalTimeStopActive()) {
            ClientTimeStopManager.removeShader();
        }
    }

    @Nullable
    public static ClientBubble getDominantBubble(Vec3 pos) {
        return getDominantBubble(pos.x, pos.y, pos.z);
    }

    @Nullable
    public static ClientBubble getDominantBubble(double px, double py, double pz) {
        ClientBubble dominant = null;
        for (ClientBubble b : clientBubbles.values()) {
            if (b.contains(px, py, pz)) {
                if (dominant == null) {
                    dominant = b;
                } else if (b.tier.getTierLevel() > dominant.tier.getTierLevel()) {
                    dominant = b;
                } else if (b.tier.getTierLevel() == dominant.tier.getTierLevel() && b.mode == TimeMode.TIME_STOP) {
                    dominant = b;
                }
            }
        }
        return dominant;
    }

    public static boolean isPositionInStasis(Vec3 pos) {
        return isPositionInStasis(pos.x, pos.y, pos.z);
    }

    public static boolean isPositionInStasis(double px, double py, double pz) {
        for (ClientBubble b : clientBubbles.values()) {
            if (b.mode == TimeMode.TIME_STOP && b.contains(px, py, pz)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCameraInsideStasis() {
        ClientBubble b = getCameraBubble();
        return b != null && b.mode == TimeMode.TIME_STOP;
    }

    public static boolean isCameraInsideBubble() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity == null) return false;
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        return getDominantBubble(camPos) != null;
    }

    @Nullable
    public static ClientBubble getCameraBubble() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity == null) return null;
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        if (mc.player != null) {
            for (ClientBubble b : clientBubbles.values()) {
                if (b.ownerUuid != null && b.ownerUuid.equals(mc.player.getUUID()) && b.contains(camPos)) {
                    return b;
                }
            }
        }
        return getDominantBubble(camPos);
    }

    public static void clientTick() {
        for (ClientBubble b : clientBubbles.values()) {
            if (b.remainingTicks > 0) {
                b.remainingTicks--;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.isDeadOrDying()) {
            reset();
            return;
        }

        // Dynamically evaluate if local player / camera is inside any temporal bubble
        ClientBubble current = getCameraBubble();
        if (current != null) {
            wasInsideBubble = true;
            // Activate shader strictly while inside the bubble
            if (current.mode == TimeMode.TIME_STOP) {
                ClientTimeStopManager.applyShader();
            } else if (current.mode == TimeMode.SUPERHOT) {
                ClientTimeStopManager.applyShader(SUPERHOT_SHADER);
            } else {
                ClientTimeStopManager.removeShader();
            }
        } else {
            if (wasInsideBubble) {
                wasInsideBubble = false;
            }
            // If global server time stop is active, maintain global mode shader!
            if (ClientTimeStopManager.isTimeStopped()) {
                TimeMode gMode = ClientTimeStopManager.getCurrentMode();
                if (gMode == TimeMode.TIME_STOP) {
                    ClientTimeStopManager.applyShader();
                } else if (gMode == TimeMode.SUPERHOT) {
                    ClientTimeStopManager.applyShader(SUPERHOT_SHADER);
                } else {
                    ClientTimeStopManager.removeShader();
                }
            } else {
                // Left all bubbles and no global time stop: return to normal world visuals immediately!
                ClientTimeStopManager.removeShader();
            }
        }
    }
}
