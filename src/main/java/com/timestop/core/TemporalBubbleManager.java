package com.timestop.core;

import com.timestop.combat.TemporalDamageBuffer;
import com.timestop.combat.TemporalKineticBlockManager;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import com.timestop.network.ModMessages;
import com.timestop.network.TemporalBubbleSyncPacket;
import com.timestop.network.TimeStopSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TemporalBubbleManager {

    private static final Map<UUID, TemporalBubble> activeBubbles = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> playerToBubble = new ConcurrentHashMap<>();

    private static final UUID MATRIX_SPEED_UUID = UUID.fromString("c0a80101-0000-0000-0000-000000000001");
    private static final UUID MATRIX_ATTACK_UUID = UUID.fromString("c0a80101-0000-0000-0000-000000000002");
    private static final AttributeModifier MATRIX_SPEED_MOD = new AttributeModifier(
            MATRIX_SPEED_UUID, "Matrix Speed", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier MATRIX_ATTACK_MOD = new AttributeModifier(
            MATRIX_ATTACK_UUID, "Matrix Attack Speed", 3.0, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static Map<UUID, TemporalBubble> getActiveBubbles() {
        return Collections.unmodifiableMap(activeBubbles);
    }

    public static boolean hasActiveBubbles() {
        return !activeBubbles.isEmpty();
    }

    @Nullable
    public static TemporalBubble getPlayerBubble(UUID playerUuid) {
        UUID bubbleId = playerToBubble.get(playerUuid);
        return bubbleId != null ? activeBubbles.get(bubbleId) : null;
    }

    @Nullable
    public static TemporalBubble getBubble(UUID bubbleId) {
        return activeBubbles.get(bubbleId);
    }

    public static List<TemporalBubble> getBubblesAt(ResourceKey<Level> dim, Vec3 pos) {
        List<TemporalBubble> list = new ArrayList<>();
        for (TemporalBubble b : activeBubbles.values()) {
            if (b.contains(dim, pos)) {
                list.add(b);
            }
        }
        return list;
    }

    @Nullable
    public static TemporalBubble getDominantBubble(ResourceKey<Level> dim, Vec3 pos) {
        return getDominantBubble(dim, pos.x, pos.y, pos.z);
    }

    @Nullable
    public static TemporalBubble getDominantBubble(ResourceKey<Level> dim, double px, double py, double pz) {
        TemporalBubble dominant = null;
        for (TemporalBubble b : activeBubbles.values()) {
            if (b.contains(dim, px, py, pz)) {
                if (dominant == null) {
                    dominant = b;
                } else {
                    // Precedence: 1. Higher watch tier; 2. TIME_STOP mode over slow-mo
                    if (b.getTier().getTierLevel() > dominant.getTier().getTierLevel()) {
                        dominant = b;
                    } else if (b.getTier().getTierLevel() == dominant.getTier().getTierLevel()) {
                        if (b.getMode() == TimeMode.TIME_STOP && dominant.getMode() != TimeMode.TIME_STOP) {
                            dominant = b;
                        }
                    }
                }
            }
        }
        return dominant;
    }

    public static boolean isPositionInStasis(ResourceKey<Level> dim, Vec3 pos) {
        return isPositionInStasis(dim, pos.x, pos.y, pos.z);
    }

    public static boolean isPositionInStasis(ResourceKey<Level> dim, double px, double py, double pz) {
        for (TemporalBubble b : activeBubbles.values()) {
            if (b.getMode() == TimeMode.TIME_STOP && b.contains(dim, px, py, pz)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEntityInStasis(Entity entity) {
        if (entity == null) return false;
        if (hasActiveBubbles()) {
            TemporalBubble dominant = getDominantBubble(entity.level().dimension(), entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
            if (dominant != null) {
                return dominant.getMode() == TimeMode.TIME_STOP && !dominant.canEntityAct(entity);
            }
        }
        if (TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            return !TimeStopManager.isEntityExempt(entity);
        }
        return false;
    }

    public static boolean hasCreativeBubble() {
        for (TemporalBubble b : activeBubbles.values()) {
            if (b.getTier() == WatchTier.CREATIVE) {
                return true;
            }
        }
        return false;
    }

    public static TemporalBubble startBubble(ServerLevel level, Player player, int durationTicks, TimeMode mode) {
        if (TimeStopManager.isGlobalTimeStopActive() || hasCreativeBubble()) {
            if (!player.isCreative() && !player.hasPermissions(2) && !player.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("The temporal continuum is locked by an almighty force (Admin/Creative Clock)!").withStyle(net.minecraft.ChatFormatting.RED), true);
                return null;
            }
        }

        // If player already has an active bubble, stop it first
        TemporalBubble existing = getPlayerBubble(player.getUUID());
        if (existing != null) {
            stopBubble(level, existing);
        }

        // Determine watch tier
        WatchTier tier = WatchTier.COPPER;
        Item watchItem = null;
        int cooldown = 300;

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (main.getItem() instanceof AbstractWatchItem w) {
            tier = w.getTier();
            watchItem = w;
            cooldown = w.getTier().getCooldownTicks();
        } else if (off.getItem() instanceof AbstractWatchItem w) {
            tier = w.getTier();
            watchItem = w;
            cooldown = w.getTier().getCooldownTicks();
        } else if (player.isCreative()) {
            tier = WatchTier.CREATIVE;
            cooldown = 0;
        }

        double radius = tier.getBubbleRadius();
        UUID bubbleId = UUID.randomUUID();
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);

        // Auto-whitelist teammates on vanilla scoreboard and Time Sync Resonators
        Set<UUID> exempt = new HashSet<>();
        if (player.getTeam() != null) {
            for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
                if (other.getTeam() != null && other.getTeam().isAlliedTo(player.getTeam())) {
                    exempt.add(other.getUUID());
                }
            }
        }
        exempt.addAll(com.timestop.sync.SyncManager.getResonators(player.getUUID()));

        TemporalBubble bubble = new TemporalBubble(bubbleId, player.getUUID(), level.dimension(), center,
                radius, mode, durationTicks, tier, watchItem, cooldown, exempt);

        activeBubbles.put(bubbleId, bubble);
        playerToBubble.put(player.getUUID(), bubbleId);

        // Apply Matrix attributes if mode is MATRIX
        if (mode == TimeMode.MATRIX) {
            applyMatrixAttributes(player);
        }

        // Play activation audio cues
        playActivationSounds(level, player, mode, center);

        Component msg = Component.literal("[Temporal Domain] ").withStyle(ChatFormatting.GOLD)
                .append(mode.getFormattedComponent())
                .append(Component.literal(" deployed! (" + (int) radius + "m radius)").withStyle(ChatFormatting.GREEN));
        player.displayClientMessage(msg, true);

        // Broadcast to clients
        syncBubbleToClients(bubble);

        return bubble;
    }

    public static void stopBubble(ServerLevel level, TemporalBubble bubble) {
        activeBubbles.remove(bubble.getBubbleId());
        playerToBubble.remove(bubble.getOwnerUuid());

        // Discharge damage buffer, projectiles, and kinetic blocks within this bubble's domain
        if (bubble.getMode() == TimeMode.TIME_STOP) {
            TemporalDamageBuffer.dischargeInArea(level, bubble.getCenter(), bubble.getRadius());
            TimeStopManager.resumeProjectilesInArea(level, bubble.getCenter(), bubble.getRadius());
            TemporalKineticBlockManager.dischargeInArea(level, bubble.getCenter(), bubble.getRadius());

            if (activeBubbles.isEmpty() && !TimeStopManager.isGlobalTimeStopActive()) {
                TemporalDamageBuffer.dischargeAll(level);
                TimeStopManager.resumeProjectiles(level);
                TemporalKineticBlockManager.dischargeAll(level);
            }
        }

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bubble.getOwnerUuid());
        if (owner != null) {
            removeMatrixAttributes(owner);

            // Trigger cooldown
            if (!owner.isCreative() && bubble.getCooldownTicks() > 0 && bubble.getWatchItem() != null) {
                owner.getCooldowns().addCooldown(bubble.getWatchItem(), bubble.getCooldownTicks());
            }

            owner.displayClientMessage(Component.literal("[Temporal Domain] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    .append(Component.literal("Field collapsed. Cooldown engaged.").withStyle(ChatFormatting.WHITE)), true);
        }

        // Play collapse sound at bubble center
        Vec3 c = bubble.getCenter();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 1.2F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 1.8F);

        // Broadcast removal packet
        ModMessages.sendToClients(TemporalBubbleSyncPacket.remove(bubble.getBubbleId()));
    }

    public static boolean stopPlayerBubble(ServerLevel level, UUID playerUuid) {
        TemporalBubble b = getPlayerBubble(playerUuid);
        if (b != null) {
            stopBubble(level, b);
            return true;
        }
        return false;
    }

    public static void stopAllBubbles(ServerLevel level) {
        for (TemporalBubble b : new ArrayList<>(activeBubbles.values())) {
            stopBubble(level, b);
        }
    }

    public static boolean extendPlayerBubble(UUID playerUuid, int bonusTicks) {
        TemporalBubble b = getPlayerBubble(playerUuid);
        if (b != null && b.extendDuration(bonusTicks)) {
            syncBubbleToClients(b);
            TimeStopManager.syncLegacyState();
            return true;
        }
        return false;
    }

    public static boolean doesBubbleIntersectChunk(net.minecraft.resources.ResourceKey<Level> dim, int chunkX, int chunkZ, int minY, int maxY) {
        if (activeBubbles.isEmpty()) return false;
        double cxMin = chunkX << 4;
        double cxMax = cxMin + 16;
        double czMin = chunkZ << 4;
        double czMax = czMin + 16;
        for (TemporalBubble b : activeBubbles.values()) {
            if (!b.getDimension().equals(dim) || b.getMode() != TimeMode.TIME_STOP) continue;
            Vec3 c = b.getCenter();
            double r = b.getRadius();
            double closestX = Math.max(cxMin, Math.min(c.x, cxMax));
            double closestY = Math.max((double) minY, Math.min(c.y, (double) maxY));
            double closestZ = Math.max(czMin, Math.min(c.z, czMax));
            double dx = c.x - closestX;
            double dy = c.y - closestY;
            double dz = c.z - closestZ;
            if ((dx * dx + dy * dy + dz * dz) <= (r * r)) {
                return true;
            }
        }
        return false;
    }

    public static void serverTick() {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        if (!activeBubbles.isEmpty()) {
            for (TemporalBubble bubble : new ArrayList<>(activeBubbles.values())) {
                ServerLevel level = server.getLevel(bubble.getDimension());
                if (level == null) {
                    activeBubbles.remove(bubble.getBubbleId());
                    playerToBubble.remove(bubble.getOwnerUuid());
                    ModMessages.sendToClients(TemporalBubbleSyncPacket.remove(bubble.getBubbleId()));
                    continue;
                }

                // Check owner liveness
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bubble.getOwnerUuid());
                if (owner == null || !owner.isAlive() || owner.level() != level) {
                    // Owner died, logged out, or changed dimension: cleanly dissolve
                    stopBubble(level, bubble);
                    continue;
                }

                boolean expired = bubble.tick(level);
                if (expired) {
                    stopBubble(level, bubble);
                } else {
                    // Periodic position / duration sync to clients every 4 ticks (0.2s)
                    if (bubble.getRemainingTicks() % 4 == 0) {
                        syncBubbleToClients(bubble);
                    }
                }
            }
        }

        // Discharge buffered mobs that have fallen outside all stasis bubbles (e.g. player walked away, mob pushed out)
        if (!com.timestop.combat.TemporalDamageBuffer.getRecords().isEmpty()) {
            for (UUID victimUuid : new ArrayList<>(com.timestop.combat.TemporalDamageBuffer.getRecords().keySet())) {
                LivingEntity victim = com.timestop.combat.TemporalDamageBuffer.getVictim(victimUuid);
                if (victim != null && victim.isAlive() && victim.level() instanceof ServerLevel sl) {
                    if (!isEntityInStasis(victim)) {
                        com.timestop.combat.TemporalDamageBuffer.dischargeEntity(sl, victimUuid);
                    }
                } else {
                    // Fallback level cleanup
                    ServerLevel fallback = com.timestop.combat.TemporalDamageBuffer.getLastKnownLevel();
                    if (fallback == null) fallback = server.overworld();
                    com.timestop.combat.TemporalDamageBuffer.dischargeEntity(fallback, victimUuid);
                }
            }
        }

        // Resume suspended projectiles that have fallen outside all stasis bubbles
        if (!TimeStopManager.getSuspendedProjectiles().isEmpty()) {
            for (Map.Entry<UUID, net.minecraft.world.entity.projectile.Projectile> entry : new ArrayList<>(TimeStopManager.getSuspendedProjectiles().entrySet())) {
                UUID pUuid = entry.getKey();
                net.minecraft.world.entity.projectile.Projectile p = entry.getValue();
                if (p != null && p.isAlive() && p.level() instanceof ServerLevel sl) {
                    TemporalBubble dominant = getDominantBubble(sl.dimension(), p.getX(), p.getY(), p.getZ());
                    if (dominant == null || dominant.getMode() != TimeMode.TIME_STOP) {
                        TimeStopManager.resumeSingleProjectile(sl, p);
                    }
                } else if (p != null && p.level() instanceof ServerLevel sl) {
                    TimeStopManager.resumeSingleProjectile(sl, p);
                } else {
                    TimeStopManager.removeSuspendedProjectile(pUuid);
                }
            }
        }
    }

    public static void syncBubbleToClients(TemporalBubble b) {
        Vec3 c = b.getCenter();
        ModMessages.sendToClients(new TemporalBubbleSyncPacket(
                TemporalBubbleSyncPacket.Action.CREATE_OR_UPDATE,
                b.getBubbleId(),
                b.getOwnerUuid(),
                b.getDimension().location().toString(),
                c.x, c.y, c.z,
                b.getRadius(),
                b.getMode(),
                b.getRemainingTicks(),
                b.getTotalDuration(),
                b.getTier(),
                b.getExemptPlayers()
        ));
    }

    public static void syncAllToPlayer(ServerPlayer player) {
        for (TemporalBubble b : activeBubbles.values()) {
            Vec3 c = b.getCenter();
            ModMessages.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new TemporalBubbleSyncPacket(
                            TemporalBubbleSyncPacket.Action.CREATE_OR_UPDATE,
                            b.getBubbleId(),
                            b.getOwnerUuid(),
                            b.getDimension().location().toString(),
                            c.x, c.y, c.z,
                            b.getRadius(),
                            b.getMode(),
                            b.getRemainingTicks(),
                            b.getTotalDuration(),
                            b.getTier(),
                            b.getExemptPlayers()
                    ));
        }
    }

    private static void applyMatrixAttributes(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(MATRIX_SPEED_MOD)) {
            speed.addTransientModifier(MATRIX_SPEED_MOD);
        }
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attack != null && !attack.hasModifier(MATRIX_ATTACK_MOD)) {
            attack.addTransientModifier(MATRIX_ATTACK_MOD);
        }
    }

    private static void removeMatrixAttributes(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(MATRIX_SPEED_MOD)) {
            speed.removeModifier(MATRIX_SPEED_MOD);
        }
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attack != null && attack.hasModifier(MATRIX_ATTACK_MOD)) {
            attack.removeModifier(MATRIX_ATTACK_MOD);
        }
    }

    private static void playActivationSounds(ServerLevel level, Player player, TimeMode mode, Vec3 pos) {
        switch (mode) {
            case TIME_STOP:
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0F, 0.5F);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.5F, 0.6F);
                break;
            case SLOW_MOTION:
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
                break;
            case MATRIX:
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 2.0F, 0.4F);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 1.2F);
                break;
            case FAST_FORWARD:
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.5F, 1.8F);
                break;
            case SUPERHOT:
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 2.0F, 1.4F);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.8F);
                break;
            case DECELERATION_FIELD:
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 1.8F);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0F, 0.6F);
                break;
        }
    }
}
