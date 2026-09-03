package com.timestop.core;

import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TemporalBubble {
    private final UUID bubbleId;
    private final UUID ownerUuid;
    private final ResourceKey<Level> dimension;
    private Vec3 center;
    private final double radius;
    private final double radiusSq;
    private TimeMode mode;
    private int remainingTicks;
    private final int totalDuration;
    private final WatchTier tier;
    private final Set<UUID> exemptPlayers = ConcurrentHashMap.newKeySet();
    @Nullable
    private final Item watchItem;
    private final int cooldownTicks;
    private int accumulatedVampirismBonus = 0;
    private final Map<UUID, Float> playerActivities = new ConcurrentHashMap<>();

    public TemporalBubble(UUID bubbleId, UUID ownerUuid, ResourceKey<Level> dimension, Vec3 center,
                          double radius, TimeMode mode, int durationTicks, WatchTier tier,
                          @Nullable Item watchItem, int cooldownTicks, @Nullable Set<UUID> exempt) {
        this.bubbleId = bubbleId;
        this.ownerUuid = ownerUuid;
        this.dimension = dimension;
        this.center = center;
        this.radius = radius;
        this.radiusSq = radius * radius;
        this.mode = mode;
        this.totalDuration = durationTicks;
        this.remainingTicks = durationTicks;
        this.tier = tier;
        this.watchItem = watchItem;
        this.cooldownTicks = cooldownTicks;
        if (exempt != null) {
            this.exemptPlayers.addAll(exempt);
        }
    }

    public UUID getId() {
        return bubbleId;
    }

    public UUID getBubbleId() {
        return bubbleId;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public Vec3 getCenter() {
        return this.center;
    }

    public void setCenter(Vec3 center) {
        this.center = center;
    }

    public double getRadius() {
        return radius;
    }

    public double getRadiusSq() {
        return radiusSq;
    }

    public TimeMode getMode() {
        return mode;
    }

    public void setMode(TimeMode mode) {
        this.mode = mode;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public int getTotalDuration() {
        return totalDuration;
    }

    public WatchTier getTier() {
        return tier;
    }

    public Set<UUID> getExemptPlayers() {
        return Collections.unmodifiableSet(exemptPlayers);
    }

    public void addExemptPlayer(UUID uuid) {
        exemptPlayers.add(uuid);
    }

    public void removeExemptPlayer(UUID uuid) {
        exemptPlayers.remove(uuid);
    }

    public void addExempt(UUID uuid) {
        exemptPlayers.add(uuid);
    }

    public void removeExempt(UUID uuid) {
        exemptPlayers.remove(uuid);
    }

    public void clearExemptions() {
        exemptPlayers.clear();
    }

    @Nullable
    public Item getWatchItem() {
        return watchItem;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public boolean contains(ResourceKey<Level> dim, Vec3 pos) {
        if (!this.dimension.equals(dim)) return false;
        double dx = pos.x - this.center.x;
        double dy = pos.y - this.center.y;
        double dz = pos.z - this.center.z;
        return (dx * dx + dy * dy + dz * dz) <= this.radiusSq;
    }

    public boolean contains(ResourceKey<Level> dim, double px, double py, double pz) {
        if (!this.dimension.equals(dim)) return false;
        double dx = px - this.center.x;
        double dy = py - this.center.y;
        double dz = pz - this.center.z;
        return (dx * dx + dy * dy + dz * dz) <= this.radiusSq;
    }

    public boolean contains(Entity entity) {
        return contains(entity.level().dimension(), entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
    }

    /**
     * Siphons bonus ticks via Chrono-Vampirism.
     */
    public boolean extendDuration(int bonusTicks) {
        if (totalDuration <= 0) return false;
        int maxBonus = totalDuration;
        if (accumulatedVampirismBonus >= maxBonus) return false;
        int actualAdd = Math.min(bonusTicks, maxBonus - accumulatedVampirismBonus);
        if (actualAdd <= 0) return false;
        remainingTicks += actualAdd;
        accumulatedVampirismBonus += actualAdd;
        return true;
    }

    public void setPlayerActivity(UUID playerUuid, float activity) {
        playerActivities.put(playerUuid, activity);
    }

    public void setSuperhotActivity(float superhotActivity) {
        playerActivities.put(this.ownerUuid, superhotActivity);
    }

    public float getSuperhotActivity() {
        float max = 0.0F;
        for (float val : playerActivities.values()) {
            if (val > max) max = val;
        }
        return max;
    }

    /**
     * Resolves whether an entity can act freely inside this temporal bubble without time distortion.
     */
    public boolean canEntityAct(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        if (player.isCreative() || player.isSpectator()) return true;
        if (player.getUUID().equals(this.ownerUuid)) return true;
        if (exemptPlayers.contains(player.getUUID())) return true;

        // Time Sync Resonators (Fast In-Memory Cache)
        if (this.ownerUuid != null && com.timestop.sync.SyncManager.isSynced(this.ownerUuid, player.getUUID())) {
            return true;
        }

        // Scoreboard team parity (allies share freedom)
        if (player.getTeam() != null && entity.level() instanceof ServerLevel sl) {
            ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(this.ownerUuid);
            if (owner != null && owner.getTeam() != null && owner.getTeam().isAlliedTo(player.getTeam())) {
                return true;
            }
        }

        // Self-Generated Temporal Field Shielding:
        // If the player has activated their OWN temporal bubble and is inside it, they can act freely!
        TemporalBubble playerBubble = TemporalBubbleManager.getPlayerBubble(player.getUUID());
        if (playerBubble != null && playerBubble.contains(entity)) {
            return true;
        }

        // Temporal Resonance: Having an equal or higher tier watch grants full freedom (Temporal Parity)
        WatchTier playerTier = getBestEquippedTier(player);
        if (playerTier != null && playerTier.getTierLevel() >= this.tier.getTierLevel()) {
            return true;
        }

        return false;
    }

    /**
     * Calculates the time dilation speed factor for an entity inside this bubble.
     */
    public float getTimeDilationFactor(Entity entity) {
        if (canEntityAct(entity)) return 1.0F;

        if (entity instanceof Player player) {
            WatchTier playerTier = getBestEquippedTier(player);
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

    @Nullable
    public static WatchTier getBestEquippedTier(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof AbstractWatchItem w) return w.getTier();
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof AbstractWatchItem w) return w.getTier();

        int best = -1;
        WatchTier bestTier = null;
        int invSize = player.getInventory().items.size();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.getItem() instanceof AbstractWatchItem w) {
                if (w.getTier().getTierLevel() > best) {
                    best = w.getTier().getTierLevel();
                    bestTier = w.getTier();
                }
            }
        }
        return bestTier;
    }

    public boolean tick(ServerLevel level) {
        // Mobile center updates directly to caster position if caster is online and in this level
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.ownerUuid);
        if (owner != null && owner.level() == level && owner.isAlive()) {
            this.center = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        }

        if (remainingTicks > 0) {
            remainingTicks--;
            return remainingTicks <= 0;
        }
        return false;
    }
}
