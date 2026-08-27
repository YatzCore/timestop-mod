package com.timestop.core;

import com.timestop.combat.TemporalDamageBuffer;
import com.timestop.item.ModItems;
import com.timestop.network.ModMessages;
import com.timestop.network.TimeStopSyncPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TimeStopManager {
    private static boolean timeStopped = false;
    private static int remainingTicks = 0;
    private static int totalDuration = 0;
    @Nullable
    private static UUID initiatorUuid = null;
    private static TimeMode currentMode = TimeMode.TIME_STOP;
    private static final Set<UUID> exemptPlayers = ConcurrentHashMap.newKeySet();

    // Map of suspended projectiles: Projectile UUID -> stored velocity
    private static final Map<UUID, Vec3> suspendedProjectiles = new ConcurrentHashMap<>();
    private static final Map<UUID, Projectile> projectileEntities = new ConcurrentHashMap<>();

    // Attribute modifiers for Matrix mode: ZERO potion effects, pure engine attribute boost!
    private static final UUID MATRIX_SPEED_UUID = UUID.fromString("c0a80101-0000-0000-0000-000000000001");
    private static final UUID MATRIX_ATTACK_UUID = UUID.fromString("c0a80101-0000-0000-0000-000000000002");
    private static final AttributeModifier MATRIX_SPEED_MOD = new AttributeModifier(
            MATRIX_SPEED_UUID, "Matrix Speed", 3.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier MATRIX_ATTACK_MOD = new AttributeModifier(
            MATRIX_ATTACK_UUID, "Matrix Attack Speed", 3.0, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static boolean isGlobalTimeStopped() {
        return timeStopped;
    }

    public static boolean isTimeStopped(@Nullable Level level) {
        return timeStopped;
    }

    public static TimeMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Governs the server's core tick interval in milliseconds.
     */
    public static long getServerTickMs() {
        if (!timeStopped) return 50L;
        switch (currentMode) {
            case FAST_FORWARD:
                return 10L; // 10ms = 100 TPS (5x speed for everything: movement, smelting, dying, daylight)
            case SLOW_MOTION:
            case MATRIX:
                return 200L; // 200ms = 5 TPS (0.25x speed: silky-smooth bullet time)
            default:
                return 50L; // 50ms = 20 TPS (normal)
        }
    }

    public static boolean isEntityExempt(Entity entity) {
        if (!timeStopped) return true;

        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return true;
            }
            if (initiatorUuid != null && player.getUUID().equals(initiatorUuid)) {
                return true;
            }
            return exemptPlayers.contains(player.getUUID());
        }

        return false;
    }

    public static void addExemptPlayer(UUID uuid) {
        exemptPlayers.add(uuid);
    }

    public static void removeExemptPlayer(UUID uuid) {
        exemptPlayers.remove(uuid);
    }

    public static boolean isPlayerExempt(UUID uuid) {
        return (initiatorUuid != null && initiatorUuid.equals(uuid)) || exemptPlayers.contains(uuid);
    }

    public static void startTimeStop(ServerLevel level, @Nullable Player initiator, int durationTicks, TimeMode mode) {
        if (timeStopped) return;

        timeStopped = true;
        totalDuration = durationTicks;
        remainingTicks = durationTicks;
        currentMode = mode;
        initiatorUuid = initiator != null ? initiator.getUUID() : null;

        // Apply Matrix attributes directly without any cheap potion effects
        if (mode == TimeMode.MATRIX && initiator != null) {
            applyMatrixAttributes(initiator);
        }

        // Play mode-specific audio cues
        if (initiator != null) {
            switch (mode) {
                case TIME_STOP:
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0F, 0.5F);
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.5F, 0.6F);
                    break;
                case SLOW_MOTION:
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
                    break;
                case MATRIX:
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 2.0F, 0.4F);
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 1.2F);
                    break;
                case FAST_FORWARD:
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.5F, 1.8F);
                    break;
            }
            initiator.displayClientMessage(Component.literal("§6[Temporal Engine] " + mode.getFormattedName() + " §aactivated!"), true);
        } else {
            for (ServerPlayer player : level.players()) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.5F, 0.6F);
            }
        }

        // Broadcast to all clients
        ModMessages.sendToClients(new TimeStopSyncPacket(true, durationTicks, initiatorUuid, mode));
    }

    public static void startTimeStop(ServerLevel level, @Nullable Player initiator, int durationTicks) {
        startTimeStop(level, initiator, durationTicks, TimeMode.TIME_STOP);
    }

    public static void resumeTime(ServerLevel level) {
        if (!timeStopped) return;

        timeStopped = false;
        remainingTicks = 0;
        totalDuration = 0;

        // Discharge all accumulated damage and knockback on living entities (only if TIME_STOP was active)
        if (currentMode == TimeMode.TIME_STOP) {
            TemporalDamageBuffer.dischargeAll(level);
            resumeProjectiles(level);
        }

        // Clean up Matrix attributes from initiator
        if (initiatorUuid != null) {
            ServerPlayer initiator = level.getServer().getPlayerList().getPlayer(initiatorUuid);
            if (initiator != null) {
                removeMatrixAttributes(initiator);

                // Cooldown begins WHEN time stop ends!
                if (!initiator.isCreative()) {
                    initiator.getCooldowns().addCooldown(ModItems.CHRONOS_WATCH.get(), 300); // 15s cooldown
                }
                initiator.displayClientMessage(Component.literal("§b§l[Temporal Engine] §fTime normalized. Cooldown started."), true);
            }
        }

        // Broadcast resume sound
        for (ServerPlayer player : level.players()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 1.2F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 1.8F);
        }

        initiatorUuid = null;
        currentMode = TimeMode.TIME_STOP;

        // Broadcast to all clients
        ModMessages.sendToClients(new TimeStopSyncPacket(false, 0, null, TimeMode.TIME_STOP));
    }

    public static void toggleTimeStop(ServerLevel level, Player player, int durationTicks, TimeMode mode) {
        if (timeStopped) {
            resumeTime(level);
        } else {
            startTimeStop(level, player, durationTicks, mode);
        }
    }

    public static void serverTick() {
        if (!timeStopped) return;

        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks <= 0) {
                ServerLevel anyLevel = TemporalDamageBuffer.getLastKnownLevel();
                if (anyLevel != null) {
                    resumeTime(anyLevel);
                } else {
                    timeStopped = false;
                    initiatorUuid = null;
                    ModMessages.sendToClients(new TimeStopSyncPacket(false, 0, null, TimeMode.TIME_STOP));
                }
            }
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

    public static void registerSuspendedProjectile(Projectile projectile, Vec3 originalVelocity) {
        if (!timeStopped || currentMode != TimeMode.TIME_STOP) return;
        suspendedProjectiles.put(projectile.getUUID(), originalVelocity);
        projectileEntities.put(projectile.getUUID(), projectile);

        // Lock in place
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setNoGravity(true);
    }

    private static void resumeProjectiles(ServerLevel level) {
        for (Map.Entry<UUID, Vec3> entry : suspendedProjectiles.entrySet()) {
            Projectile projectile = projectileEntities.get(entry.getKey());
            if (projectile != null && projectile.isAlive()) {
                Vec3 velocity = entry.getValue();
                projectile.setNoGravity(false);
                projectile.setDeltaMovement(velocity);
                level.sendParticles(ParticleTypes.CRIT,
                        projectile.getX(), projectile.getY(), projectile.getZ(),
                        10, 0.1, 0.1, 0.1, 0.15);
                level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                        SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.2F, 1.2F);
            }
        }
        suspendedProjectiles.clear();
        projectileEntities.clear();
    }

    public static int getRemainingTicks() {
        return remainingTicks;
    }

    public static int getTotalDuration() {
        return totalDuration;
    }

    @Nullable
    public static UUID getInitiatorUuid() {
        return initiatorUuid;
    }
}
