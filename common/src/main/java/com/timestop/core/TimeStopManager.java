package com.timestop.core;

import com.timestop.combat.TemporalDamageBuffer;
import com.timestop.combat.TemporalKineticBlockManager;
import com.timestop.item.ModItems;
import com.timestop.network.ModMessages;
import com.timestop.network.TimeStopSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
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
    @Nullable
    private static net.minecraft.world.item.Item initiatorWatchItem = null;
    private static int initiatorCooldownTicks = 300;
    private static int accumulatedVampirismBonus = 0;
    private static final Set<UUID> exemptPlayers = ConcurrentHashMap.newKeySet();
    private static java.lang.ref.WeakReference<ServerLevel> activeServerLevel = new java.lang.ref.WeakReference<>(null);

    public static Set<UUID> getExemptPlayers() {
        return Collections.unmodifiableSet(exemptPlayers);
    }

    // Map of suspended projectiles: Projectile UUID -> stored velocity & kinetic data
    public static class ProjectileKineticData {
        public Vec3 originalVelocity = Vec3.ZERO;
        public Vec3 direction = Vec3.ZERO;
        public int hitCount = 0;
        public double totalDamageBonus = 0.0;
        public boolean originalNoGravity;
        @Nullable
        public UUID originalShooterUuid = null;
        @Nullable
        public UUID lastAttacker = null;

        public ProjectileKineticData(Vec3 initialVelocity, @Nullable Entity shooter) {
            this.originalVelocity = initialVelocity;
            if (shooter != null) {
                this.originalShooterUuid = shooter.getUUID();
            }
            if (initialVelocity.lengthSqr() > 1e-5) {
                this.direction = initialVelocity.reverse().normalize();
            } else {
                this.direction = new Vec3(0, 0, 1);
            }
        }

        public void addPunch(Projectile projectile, Player player) {
            if (com.timestop.combat.ProjectileRedirection.usesLook(player)) {
                this.direction = player.getLookAngle().normalize();
            } else if (this.hitCount == 0) {
                // 1st time hit: lock return trajectory towards original shooter!
                Level level = projectile.level();
                Entity shooter = null;
                if (this.originalShooterUuid != null && level instanceof ServerLevel serverLevel) {
                    shooter = serverLevel.getEntity(this.originalShooterUuid);
                } else {
                    shooter = projectile.getOwner();
                }

                if (shooter != null && shooter.isAlive()) {
                    Vec3 targetPos = shooter.getEyePosition().subtract(0, 0.2, 0);
                    Vec3 returnVec = targetPos.subtract(projectile.position());
                    if (returnVec.lengthSqr() > 1e-5) {
                        this.direction = returnVec.normalize();
                    }
                } else if (this.originalVelocity.lengthSqr() > 1e-5) {
                    this.direction = this.originalVelocity.reverse().normalize();
                } else {
                    this.direction = player.getLookAngle().normalize();
                }
            }

            // Subsequent hits: DO NOT change vector! Only increase power (speed & damage)
            this.hitCount++;
            this.totalDamageBonus += 4.5; // +4.5 damage per kinetic punch
            this.lastAttacker = player.getUUID();
        }

        public Vec3 getDischargeVelocity() {
            if (hitCount == 0) return originalVelocity;
            double initialSpeed = Math.max(1.8, this.originalVelocity.length());
            // Each punch increases exit speed by +40%
            double multiplier = 1.0 + (this.hitCount * 0.40);
            return this.direction.scale(initialSpeed * multiplier);
        }
    }

    private static final Map<UUID, ProjectileKineticData> projectileData = new ConcurrentHashMap<>();
    private static final Map<UUID, java.lang.ref.WeakReference<Projectile>> projectileEntities = new ConcurrentHashMap<>();

    // Dynamic tick duration for SUPERHOT mode (500ms = 2 TPS idle extreme slow-mo, 50ms = 20 TPS moving)
    private static volatile long superhotTickMs = 500L;

    // Attribute modifiers for Matrix mode: ZERO potion effects, pure engine attribute boost!
    private static final ResourceLocation MATRIX_SPEED_RL = ResourceLocation.fromNamespaceAndPath("timestop", "matrix_speed");
    private static final ResourceLocation MATRIX_ATTACK_RL = ResourceLocation.fromNamespaceAndPath("timestop", "matrix_attack_speed");
    private static final AttributeModifier MATRIX_SPEED_MOD = new AttributeModifier(
            MATRIX_SPEED_RL, 3.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeModifier MATRIX_ATTACK_MOD = new AttributeModifier(
            MATRIX_ATTACK_RL, 3.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    public static boolean isGlobalTimeStopActive() {
        return timeStopped;
    }

    public static boolean isGlobalTimeStopped() {
        return timeStopped || TemporalBubbleManager.hasActiveBubbles();
    }

    public static boolean isTimeStopped(@Nullable Level level) {
        return timeStopped || TemporalBubbleManager.hasActiveBubbles();
    }

    public static TimeMode getCurrentMode() {
        if (timeStopped) {
            return currentMode;
        }
        if (TemporalBubbleManager.hasActiveBubbles()) {
            TemporalBubble dominant = null;
            for (TemporalBubble b : TemporalBubbleManager.getActiveBubbles().values()) {
                if (dominant == null || b.getTier().getTierLevel() > dominant.getTier().getTierLevel()) {
                    dominant = b;
                }
            }
            if (dominant != null) return dominant.getMode();
        }
        return currentMode;
    }

    public static void setSuperhotTickMs(long ms) {
        superhotTickMs = Math.max(50L, Math.min(1200L, ms));
    }

    /**
     * Governs the server's core tick interval in milliseconds.
     * Note: Only global server-wide time stop modulates nextTickTime.
     * Local temporal bubbles never degrade dedicated server tick performance.
     */
    public static long getServerTickMs() {
        if (timeStopped) {
            switch (currentMode) {
                case FAST_FORWARD:
                    return (long) Math.max(10L, Math.round(50.0 / com.timestop.config.TimeStopConfig.COMMON.fastForwardRate.get()));
                case SLOW_MOTION:
                    return (long) Math.max(50, Math.round(50.0 / com.timestop.config.TimeStopConfig.COMMON.slowMotionRate.get()));
                case MATRIX:
                    return (long) Math.max(50, Math.round(50.0 / com.timestop.config.TimeStopConfig.COMMON.matrixRate.get()));
                case SUPERHOT:
                    return superhotTickMs;
                default:
                    return 50L;
            }
        }
        if (TemporalBubbleManager.hasActiveBubbles()) {
            for (TemporalBubble bubble : TemporalBubbleManager.getActiveBubbles().values()) {
                if (bubble.isStationary()) continue;
                TimeMode bMode = bubble.getMode();
                if (bMode == TimeMode.SLOW_MOTION) {
                    return (long) Math.max(50, Math.round(50.0 / com.timestop.config.TimeStopConfig.COMMON.slowMotionRate.get()));
                } else if (bMode == TimeMode.MATRIX) {
                    return (long) Math.max(50, Math.round(50.0 / com.timestop.config.TimeStopConfig.COMMON.matrixRate.get()));
                } else if (bMode == TimeMode.SUPERHOT) {
                    float act = Math.max(0.0F, Math.min(1.0F, bubble.getSuperhotActivity()));
                    float idleRate = com.timestop.config.TimeStopConfig.COMMON.superhotIdleRate.get().floatValue();
                    long maxMs = (long) Math.max(50.0F, Math.round(50.0F / idleRate));
                    return (long) (maxMs - act * (maxMs - 50L));
                }
            }
        }
        return 50L;
    }

    public enum ProjectileStasisMode {
        FLOWING,
        SUSPENDED
    }

    private static ProjectileStasisMode projectileStasisMode = ProjectileStasisMode.FLOWING;

    public static ProjectileStasisMode getProjectileStasisMode() {
        net.minecraft.server.MinecraftServer server = com.timestop.platform.Services.PLATFORM.getCurrentServer();
        if (server != null) {
            return TimeStopSavedData.get().getProjectileStasisMode();
        }
        return ClientTimeStopManager.getProjectileMode();
    }

    public static void setProjectileStasisMode(ProjectileStasisMode mode) {
        projectileStasisMode = mode;
        net.minecraft.server.MinecraftServer server = com.timestop.platform.Services.PLATFORM.getCurrentServer();
        if (server != null) {
            TimeStopSavedData.get().setProjectileStasisMode(mode);
        }
        syncLegacyState();
    }

    private static boolean clientRewindAllowed = true;

    public static boolean isRewindAllowed() {
        net.minecraft.server.MinecraftServer server = com.timestop.platform.Services.PLATFORM.getCurrentServer();
        if (server != null) {
            return TimeStopSavedData.get().isRewindModeAllowed();
        }
        return clientRewindAllowed;
    }

    public static void setClientRewindAllowed(boolean allowed) {
        clientRewindAllowed = allowed;
    }

    public static void setRewindAllowed(boolean allowed) {
        net.minecraft.server.MinecraftServer server = com.timestop.platform.Services.PLATFORM.getCurrentServer();
        if (server != null) {
            TimeStopSavedData.get().setRewindModeAllowed(allowed);
            if (!allowed) {
                if (currentMode == TimeMode.REWIND && isTimeStopped(server.overworld())) {
                    resumeTime(server.overworld());
                }
                com.timestop.core.rewind.LocalRewind.clear();
            }
            ModMessages.sendToClients(new com.timestop.network.SyncRewindAllowedPacket(allowed));
        }
    }

    public static boolean isProjectileExempt(Projectile projectile) {
        if (!com.timestop.config.TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.get()) {
            return false;
        }
        if (getProjectileStasisMode() != ProjectileStasisMode.FLOWING) {
            return false;
        }
        if (!timeStopped && TemporalBubbleManager.hasActiveBubbles()) {
            TemporalBubble bubble = TemporalBubbleManager.getDominantBubble(projectile.level().dimension(),
                    projectile.getX(), projectile.getY() + projectile.getBbHeight() * 0.5, projectile.getZ());
            return bubble == null || bubble.canEntityAct(projectile);
        }
        Entity owner = projectile.getOwner();
        if (owner instanceof Player player) {
            return isEntityExempt(player);
        }
        return false;
    }

    public static boolean isEntityExempt(Entity entity) {
        if (entity instanceof Projectile p && isProjectileExempt(p)) {
            return true;
        }
        if (timeStopped) {
            // Global time stop is active across the server!
            if (entity instanceof Player player) {
                if (player.isCreative() || player.isSpectator()) {
                    return true;
                }
                if (initiatorUuid != null && player.getUUID().equals(initiatorUuid)) {
                    return true;
                }
                if (exemptPlayers.contains(player.getUUID())) {
                    return true;
                }
            }
            // Check if entity is inside a localized bubble that explicitly exempts it
            if (TemporalBubbleManager.hasActiveBubbles()) {
                TemporalBubble dominant = TemporalBubbleManager.getDominantBubble(entity.level().dimension(), entity.position());
                if (dominant != null) {
                    return dominant.canEntityAct(entity);
                }
            }
            return false;
        }

        // Global time stop is NOT active: check localized bubbles
        if (TemporalBubbleManager.hasActiveBubbles()) {
            TemporalBubble dominant = TemporalBubbleManager.getDominantBubble(entity.level().dimension(), entity.position());
            if (dominant == null) {
                return true; // Outside all bubbles = free to act!
            }
            return dominant.canEntityAct(entity);
        }

        return true;
    }

    public static void setMode(TimeMode mode) {
        TimeMode previous = currentMode;
        currentMode = mode;
        ServerLevel level = activeServerLevel.get();
        if (timeStopped && level != null && previous != mode) {
            if (previous == TimeMode.TIME_STOP) {
                TemporalDamageBuffer.dischargeAll(level);
                resumeProjectiles(level);
                TemporalKineticBlockManager.dischargeAll(level);
            }
            ServerPlayer initiator = initiatorUuid == null ? null : level.getServer().getPlayerList().getPlayer(initiatorUuid);
            if (initiator != null) {
                if (previous == TimeMode.MATRIX) removeMatrixAttributes(initiator);
                if (mode == TimeMode.MATRIX) applyMatrixAttributes(initiator);
            }
            superhotTickMs = 500L;
        }
        syncLegacyState();
    }

    public static void syncLegacyState() {
        if (timeStopped) {
            ModMessages.sendToClients(new TimeStopSyncPacket(true, remainingTicks, initiatorUuid, currentMode, exemptPlayers));
        } else {
            ModMessages.sendToClients(new TimeStopSyncPacket(false, 0, null, TimeMode.TIME_STOP, Collections.emptySet()));
        }
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

    public static boolean isServerForceGlobalMode() {
        return TimeStopSavedData.get().isServerForceGlobalMode();
    }

    public static void setServerForceGlobalMode(boolean global) {
        TimeStopSavedData.get().setServerForceGlobalMode(global);
    }

    public static boolean isWatchConfiguredGlobal(@Nullable Player player) {
        return com.timestop.item.AbstractWatchItem.isGlobalScope(
                com.timestop.item.AbstractWatchItem.findActivationWatch(player));
    }

    public static boolean usesGlobalWatchScope(Player player) {
        return switch (TimeStopSavedData.get().getWatchScope()) {
            case GLOBAL -> true;
            case SPHERE -> false;
            case WATCH -> isWatchConfiguredGlobal(player);
        };
    }

    public static void startTimeStop(ServerLevel level, @Nullable Player initiator, int durationTicks, TimeMode mode) {
        if (mode == TimeMode.REWIND && !isRewindAllowed()) {
            if (initiator != null) {
                initiator.displayClientMessage(Component.literal("Rewind mode has been disabled by the server administrator!").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        if (timeStopped) {
            if (initiator != null && !initiator.isCreative() && !initiator.hasPermissions(2) && (initiatorUuid == null || !initiatorUuid.equals(initiator.getUUID()))) {
                initiator.displayClientMessage(Component.literal("The universe is locked in global temporal stasis!").withStyle(ChatFormatting.RED), true);
                return;
            }
        }

        if (mode == TimeMode.REWIND && initiator instanceof ServerPlayer player && !usesGlobalWatchScope(player)) {
            var scope = com.timestop.core.rewind.RewindScope.forPlayer(player);
            net.minecraft.world.item.ItemStack watch = com.timestop.item.AbstractWatchItem.findActivationWatch(player);
            com.timestop.item.AbstractWatchItem watchItem = watch.getItem() instanceof com.timestop.item.AbstractWatchItem w ? w : null;
            int cooldownTicks = watchItem != null ? watchItem.getTier().getCooldownTicks() : 300;

            if ("CONTINUOUS".equalsIgnoreCase(com.timestop.config.TimeStopConfig.COMMON.rewindMode.get())) {
                int ticks = com.timestop.config.TimeStopConfig.COMMON.rewindHistorySeconds.get() * 20;
                com.timestop.core.rewind.LocalRewind.start(player, scope, ticks, () -> {
                    if (!player.isCreative() && watchItem != null && cooldownTicks > 0) {
                        player.getCooldowns().addCooldown(watchItem, cooldownTicks);
                    }
                });
            } else {
                var old = TemporalBubbleManager.getPlayerBubble(player.getUUID());
                if (old != null) TemporalBubbleManager.stopBubble(level, old);

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 2.0F, 0.6F);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 1.5F, 1.5F);
                com.timestop.network.ModMessages.sendToPlayer(new com.timestop.network.RewindFadePacket(true), player);

                var result = com.timestop.core.rewind.RewindExecutor.execute(level.getServer(),
                        com.timestop.config.TimeStopConfig.rewindDurationSeconds(),
                        player, com.timestop.config.TimeStopConfig.COMMON.rollbackPlayerInventory.get(), scope);
                player.displayClientMessage(result.toComponent(), false);

                if (result.success() && !player.isCreative() && watchItem != null && cooldownTicks > 0) {
                    player.getCooldowns().addCooldown(watchItem, cooldownTicks);
                }
            }
            return;
        }
        if (mode == TimeMode.REWIND && initiator instanceof ServerPlayer player && !usesGlobalWatchScope(player)) {
            var scope = com.timestop.core.rewind.RewindScope.forPlayer(player);
            net.minecraft.world.item.ItemStack watch = com.timestop.item.AbstractWatchItem.findActivationWatch(player);
            com.timestop.item.AbstractWatchItem watchItem = watch.getItem() instanceof com.timestop.item.AbstractWatchItem w ? w : null;
            int cooldownTicks = watchItem != null ? watchItem.getTier().getCooldownTicks() : 300;

            if ("CONTINUOUS".equalsIgnoreCase(com.timestop.config.TimeStopConfig.COMMON.rewindMode.get())) {
                int ticks = com.timestop.config.TimeStopConfig.COMMON.rewindHistorySeconds.get() * 20;
                com.timestop.core.rewind.LocalRewind.start(player, scope, ticks, () -> {
                    if (!player.isCreative() && watchItem != null && cooldownTicks > 0) {
                        player.getCooldowns().addCooldown(watchItem, cooldownTicks);
                    }
                });
            } else {
                var old = TemporalBubbleManager.getPlayerBubble(player.getUUID());
                if (old != null) TemporalBubbleManager.stopBubble(level, old);

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 2.0F, 0.6F);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 1.5F, 1.5F);
                com.timestop.network.ModMessages.sendToPlayer(new com.timestop.network.RewindFadePacket(true), player);

                var result = com.timestop.core.rewind.RewindExecutor.execute(level.getServer(),
                        com.timestop.config.TimeStopConfig.rewindDurationSeconds(),
                        player, com.timestop.config.TimeStopConfig.COMMON.rollbackPlayerInventory.get(), scope);
                player.displayClientMessage(result.toComponent(), false);

                if (result.success() && !player.isCreative() && watchItem != null && cooldownTicks > 0) {
                    player.getCooldowns().addCooldown(watchItem, cooldownTicks);
                }
            }
            return;
        }
        if (initiator != null && !usesGlobalWatchScope(initiator)) {
            if (mode != TimeMode.REWIND) {
                if (timeStopped) {
                    initiator.displayClientMessage(Component.literal("Cannot spawn localized bubbles while global server stasis is active!").withStyle(ChatFormatting.RED), true);
                    return;
                }
                TemporalBubbleManager.startBubble(level, initiator, durationTicks, mode);
                return;
            }
        }

        startGlobalTimeStop(level, initiator, durationTicks, mode);
    }

    public static void startGlobalTimeStop(ServerLevel level, @Nullable Player initiator, int durationTicks, TimeMode mode) {
        if (mode == TimeMode.REWIND && "CONTINUOUS".equalsIgnoreCase(com.timestop.config.TimeStopConfig.COMMON.rewindMode.get())) {
            startContinuousRewind(level, initiator, durationTicks, null);
            return;
        }
        if (timeStopped) {
            if (initiator != null && !initiator.isCreative() && !initiator.hasPermissions(2) && (initiatorUuid == null || !initiatorUuid.equals(initiator.getUUID()))) {
                return;
            }
            resumeTime(level);
        }

        com.timestop.core.rewind.LocalRewind.clear();
        // Collapse all localized bubbles because global server time stop takes absolute precedence!
        TemporalBubbleManager.stopAllBubbles(level);

        timeStopped = true;
        activeServerLevel = new java.lang.ref.WeakReference<>(level);
        totalDuration = durationTicks;
        remainingTicks = durationTicks;
        accumulatedVampirismBonus = 0;
        currentMode = mode;
        initiatorUuid = initiator != null ? initiator.getUUID() : null;
        initiatorWatchItem = null;
        initiatorCooldownTicks = 300;
        exemptPlayers.clear();

        if (initiator != null) {
            net.minecraft.world.item.ItemStack watch = com.timestop.item.AbstractWatchItem.findActivationWatch(initiator);
            if (watch.getItem() instanceof com.timestop.item.AbstractWatchItem w) {
                initiatorWatchItem = w;
                initiatorCooldownTicks = w.getTier().getCooldownTicks();
            }

            // Whitelist teammates and Time Sync Resonators
            exemptPlayers.addAll(com.timestop.sync.SyncManager.getResonators(initiator.getUUID()));
            if (initiator.getTeam() != null) {
                for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
                    if (other.getTeam() != null && other.getTeam().isAlliedTo(initiator.getTeam())) {
                        exemptPlayers.add(other.getUUID());
                    }
                }
            }
        }

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
                case SUPERHOT:
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 2.0F, 1.4F);
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.8F);
                    break;
                case DECELERATION_FIELD:
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 1.8F);
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0F, 0.6F);
                    break;
                case REWIND:
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 2.0F, 0.6F);
                    level.playSound(null, initiator.getX(), initiator.getY(), initiator.getZ(),
                            SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 1.5F, 1.5F);
                    break;
            }
            Component msg = Component.literal("[Global Temporal Engine] ").withStyle(net.minecraft.ChatFormatting.GOLD)
                    .append(mode.getFormattedComponent())
                    .append(Component.literal(" activated!").withStyle(net.minecraft.ChatFormatting.GREEN));
            initiator.displayClientMessage(msg, true);
        } else {
            for (ServerPlayer player : level.players()) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.5F, 0.6F);
            }
        }

        // Broadcast to all clients
        ModMessages.sendToClients(new TimeStopSyncPacket(true, durationTicks, initiatorUuid, mode, exemptPlayers));

        // Burst Rewind Execution
        if (mode == TimeMode.REWIND) {
            String rewindMode = com.timestop.config.TimeStopConfig.COMMON.rewindMode.get();
            if ("BURST".equalsIgnoreCase(rewindMode)) {
                int burstSec = com.timestop.config.TimeStopConfig.rewindDurationSeconds();
                ServerPlayer sPlayer = initiator instanceof ServerPlayer sp ? sp : null;
                boolean rollbackInv = com.timestop.config.TimeStopConfig.COMMON.rollbackPlayerInventory.get();
                if (sPlayer != null) {
                    com.timestop.network.ModMessages.sendToPlayer(new com.timestop.network.RewindFadePacket(true), sPlayer);
                }
                com.timestop.core.rewind.RewindExecutor.RewindResult result =
                        com.timestop.core.rewind.RewindExecutor.execute(level.getServer(), burstSec, sPlayer, rollbackInv, null);
                if (initiator != null) {
                    initiator.displayClientMessage(result.toComponent(), false);
                }
                resumeTime(level);
            }
        }
    }

    public static void startContinuousRewind(ServerLevel level, @Nullable Player initiator, int durationTicks) {
        startContinuousRewind(level, initiator, durationTicks, com.timestop.core.rewind.RewindScope.forPlayer(initiator));
    }

    public static void startContinuousRewind(ServerLevel level, @Nullable Player initiator, int durationTicks,
            @Nullable com.timestop.core.rewind.RewindScope scope) {
        if (!isRewindAllowed()) {
            if (initiator != null) {
                initiator.displayClientMessage(Component.literal("§c[TimeStop] Rewind mode has been disabled by the server administrator!"), true);
            }
            return;
        }
        var recorder = com.timestop.core.rewind.TickRecorder.getInstance();
        recorder.finishFrame(level.getServer());
        int available = recorder.getTimelineBuffer().getFrameCount();
        if (available <= 0) {
            if (initiator != null) {
                initiator.displayClientMessage(Component.literal("§c[TimeStop] Rewind buffer is empty. History records as the world runs."), true);
            }
            return;
        }
        int requested = (durationTicks > 0) ? durationTicks : com.timestop.config.TimeStopConfig.rewindDurationSeconds() * 20;
        durationTicks = Math.min(requested, available);

        if (scope != null && initiator instanceof ServerPlayer player) {
            com.timestop.core.rewind.LocalRewind.start(player, scope, durationTicks, () -> {});
            return;
        }
        if (timeStopped) {
            resumeTime(level);
        }

        com.timestop.core.rewind.LocalRewind.clear();
        // Collapse all localized bubbles because global rewind takes precedence
        TemporalBubbleManager.stopAllBubbles(level);

        timeStopped = true;
        activeServerLevel = new java.lang.ref.WeakReference<>(level);
        totalDuration = durationTicks;
        remainingTicks = durationTicks;
        accumulatedVampirismBonus = 0;
        currentMode = TimeMode.REWIND;
        initiatorUuid = initiator != null ? initiator.getUUID() : null;
        initiatorWatchItem = null;
        initiatorCooldownTicks = 0;
        exemptPlayers.clear();

        if (initiator != null) {
            exemptPlayers.addAll(com.timestop.sync.SyncManager.getResonators(initiator.getUUID()));
            if (initiator.getTeam() != null) {
                for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
                    if (other.getTeam() != null && other.getTeam().isAlliedTo(initiator.getTeam())) {
                        exemptPlayers.add(other.getUUID());
                    }
                }
            }
        }

        // Broadcast to all clients
        ModMessages.sendToClients(new TimeStopSyncPacket(true, durationTicks, initiatorUuid, TimeMode.REWIND, exemptPlayers));

        // Start continuous recording playback
        com.timestop.core.rewind.RewindPlaybackFeedback.started(initiator, durationTicks, available, "global");
        recorder.getTimelineBuffer().setRewinding(true);
        recorder.getTimelineBuffer().setRecording(false);
        recorder.clearTrackingData();
    }

    public static void startTimeStop(ServerLevel level, @Nullable Player initiator, int durationTicks) {
        startTimeStop(level, initiator, durationTicks, TimeMode.TIME_STOP);
    }

    /**
     * Siphons temporal duration to extend the active session, capped at double the default duration.
     */
    public static boolean extendTimeStop(int bonusTicks) {
        if (!timeStopped || totalDuration <= 0) return false;

        // Dynamic cap: maximum total extension is double the default maximum duration
        int maxBonus = totalDuration;
        if (accumulatedVampirismBonus >= maxBonus) return false;

        int actualAdd = Math.min(bonusTicks, maxBonus - accumulatedVampirismBonus);
        if (actualAdd <= 0) return false;

        remainingTicks += actualAdd;
        accumulatedVampirismBonus += actualAdd;

        // Sync updated duration to all clients
        ModMessages.sendToClients(new TimeStopSyncPacket(true, remainingTicks, initiatorUuid, currentMode, exemptPlayers));
        return true;
    }

    public static int getAccumulatedVampirismBonus() {
        return accumulatedVampirismBonus;
    }

    public static void resumeTime(ServerLevel level) {
        if (!timeStopped) return;

        if (currentMode == TimeMode.REWIND) {
            var recorder = com.timestop.core.rewind.TickRecorder.getInstance();
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().setRewinding(false);
            recorder.getTimelineBuffer().setRecording(true);
            com.timestop.combat.RewindRuneManager.onRewindFinished(level.getServer());
        }
        timeStopped = false;
        activeServerLevel = new java.lang.ref.WeakReference<>(null);
        remainingTicks = 0;
        totalDuration = 0;
        accumulatedVampirismBonus = 0;

        // Discharge all accumulated damage and knockback on living entities & kinetic blocks (only if TIME_STOP was active)
        if (currentMode == TimeMode.TIME_STOP) {
            TemporalDamageBuffer.dischargeAll(level);
            resumeProjectiles(level);
            TemporalKineticBlockManager.dischargeAll(level);
        }

        superhotTickMs = 250L;

        // Clean up Matrix attributes from initiator
        if (initiatorUuid != null) {
            ServerPlayer initiator = level.getServer().getPlayerList().getPlayer(initiatorUuid);
            if (initiator != null) {
                removeMatrixAttributes(initiator);

                // Cooldown begins WHEN time stop ends!
                if (!initiator.isCreative() && initiatorCooldownTicks > 0) {
                    if (initiatorWatchItem != null) {
                        initiator.getCooldowns().addCooldown(initiatorWatchItem, initiatorCooldownTicks);
                    }
                }
                initiator.displayClientMessage(Component.literal("[Temporal Engine] ").withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD)
                        .append(Component.literal("Time normalized. Cooldown started.").withStyle(net.minecraft.ChatFormatting.WHITE)), true);
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
        initiatorWatchItem = null;
        exemptPlayers.clear();

        // Broadcast to all clients
        ModMessages.sendToClients(new TimeStopSyncPacket(false, 0, null, TimeMode.TIME_STOP, Collections.emptySet()));
    }

    public static void toggleTimeStop(ServerLevel level, Player player, int durationTicks, TimeMode mode) {
        if (com.timestop.core.rewind.LocalRewind.cancel(player.getUUID())) return;
        TemporalBubble existing = TemporalBubbleManager.getPlayerBubble(player.getUUID());
        if (existing != null) {
            TemporalBubbleManager.stopBubble(level, existing);
        } else if (timeStopped) {
            resumeTime(level);
        } else {
            TemporalBubbleManager.startBubble(level, player, durationTicks, mode);
        }
    }

    public static void serverTick() {
        com.timestop.core.rewind.RewindExplosionEffects.tick();
        TemporalKineticBlockManager.serverTick();

        var currentServer = com.timestop.platform.Services.PLATFORM.getCurrentServer();
        if (currentServer != null) {
            com.timestop.core.rewind.LocalRewind.tick(currentServer);
            com.timestop.combat.RewindRuneManager.serverTick(currentServer);
        }

        if (!timeStopped) return;

        // Continuous Rewind handling
        if (currentMode == TimeMode.REWIND) {
            ServerLevel level = activeServerLevel.get();
            if (level == null) {
                var server = com.timestop.platform.Services.PLATFORM.getCurrentServer();
                if (server != null) resumeTime(server.overworld());
                else reset();
                return;
            }
            if (level != null) {
                var buffer = com.timestop.core.rewind.TickRecorder.getInstance().getTimelineBuffer();
                if (buffer.getFrameCount() == 0) {
                    if (initiatorUuid != null) {
                        ServerPlayer initiator = level.getServer().getPlayerList().getPlayer(initiatorUuid);
                        if (initiator != null) {
                            initiator.displayClientMessage(Component.literal("§e[TimeStop] Rewind buffer exhausted; normal time resumed."), true);
                        }
                    }
                    resumeTime(level);
                    return;
                }
                boolean rollbackInv = com.timestop.config.TimeStopConfig.COMMON.rollbackPlayerInventory.get();
                var frames = buffer.getFramesForRewind(1);
                if (!frames.isEmpty()) {
                    var plan = com.timestop.core.rewind.RewindExecutor.buildPlan(frames);
                    buffer.setRewinding(true);
                    buffer.setRecording(false);
                    try {
                        com.timestop.core.rewind.RewindExecutor.applyPlan(level.getServer(), plan, rollbackInv);
                        buffer.removeRecentFrames(1);
                    } catch (Exception error) {
                        org.slf4j.LoggerFactory.getLogger("TimeStopRewind").error("Continuous rewind failed", error);
                        buffer.clear();
                        resumeTime(level);
                        return;
                    }
                    if (buffer.getFrameCount() == 0) {
                        if (initiatorUuid != null) {
                            ServerPlayer initiator = level.getServer().getPlayerList().getPlayer(initiatorUuid);
                            if (initiator != null) {
                                initiator.displayClientMessage(Component.literal("§e[TimeStop] Rewind complete: all recorded history restored."), true);
                            }
                        }
                        resumeTime(level);
                        return;
                    }
                } else {
                    if (initiatorUuid != null) {
                        ServerPlayer initiator = level.getServer().getPlayerList().getPlayer(initiatorUuid);
                        if (initiator != null) {
                            initiator.displayClientMessage(Component.literal("§e[TimeStop] Rewind buffer exhausted; normal time resumed."), true);
                        }
                    }
                    resumeTime(level);
                    return;
                }
            }
        }

        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks <= 0) {
                ServerLevel anyLevel = activeServerLevel.get();
                if (anyLevel == null) {
                    anyLevel = TemporalDamageBuffer.getLastKnownLevel();
                }
                if (anyLevel == null) {
                    net.minecraft.server.MinecraftServer s = com.timestop.platform.Services.PLATFORM.getCurrentServer();
                    if (s != null) {
                        anyLevel = s.overworld();
                    }
                }
                if (anyLevel != null) {
                    resumeTime(anyLevel);
                } else {
                    timeStopped = false;
                    initiatorUuid = null;
                    ModMessages.sendToClients(new TimeStopSyncPacket(false, 0, null, TimeMode.TIME_STOP, Collections.emptySet()));
                }
            }
        }
    }

    public static void reset() {
        com.timestop.core.rewind.LocalRewind.clear();
        com.timestop.combat.RewindRuneManager.clearAllConsumptions();
        com.timestop.core.rewind.TickRecorder.getInstance().reset();
        timeStopped = false;
        remainingTicks = 0;
        totalDuration = 0;
        accumulatedVampirismBonus = 0;
        initiatorUuid = null;
        initiatorWatchItem = null;
        initiatorCooldownTicks = 300;
        currentMode = TimeMode.TIME_STOP;
        superhotTickMs = 500L;
        projectileStasisMode = ProjectileStasisMode.FLOWING;
        exemptPlayers.clear();
        projectileData.clear();
        projectileEntities.clear();
        clientRewindAllowed = true;
        activeServerLevel = new java.lang.ref.WeakReference<>(null);
    }

    private static void applyMatrixAttributes(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(MATRIX_SPEED_RL)) {
            speed.addTransientModifier(MATRIX_SPEED_MOD);
        }
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attack != null && !attack.hasModifier(MATRIX_ATTACK_RL)) {
            attack.addTransientModifier(MATRIX_ATTACK_MOD);
        }
    }

    public static void removeMatrixAttributes(Player player) {
        if (player == null) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(MATRIX_SPEED_RL)) {
            speed.removeModifier(MATRIX_SPEED_RL);
        }
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attack != null && attack.hasModifier(MATRIX_ATTACK_RL)) {
            attack.removeModifier(MATRIX_ATTACK_RL);
        }
    }

    public static boolean isProjectileSuspended(Projectile projectile) {
        return projectileData.containsKey(projectile.getUUID());
    }

    public static Vec3 getSuspendedVelocity(Projectile projectile) {
        ProjectileKineticData data = projectileData.get(projectile.getUUID());
        return data != null ? data.getDischargeVelocity() : Vec3.ZERO;
    }

    public static int punchSuspendedProjectile(Projectile projectile, Player player) {
        ProjectileKineticData data = projectileData.computeIfAbsent(projectile.getUUID(),
                k -> createProjectileData(projectile, projectile.getDeltaMovement()));
        data.addPunch(projectile, player);
        com.timestop.combat.ProjectileRedirection.clearGuidance(projectile);
        projectileEntities.put(projectile.getUUID(), new java.lang.ref.WeakReference<>(projectile));

        com.timestop.item.rune.RuneType rune = com.timestop.combat.RuneManager.getSocketedRuneType(player);
        if (rune == com.timestop.item.rune.RuneType.KINETIC) {
            data.totalDamageBonus += 8.0;
            data.originalVelocity = data.originalVelocity.scale(1.5);
        } else if (rune == com.timestop.item.rune.RuneType.VOLATILE) {
            com.timestop.platform.EntityDataHelper.getPersistentData(projectile).putBoolean("VolatileStasis", true);
            if (projectile.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.FLAME, projectile.getX(), projectile.getY(), projectile.getZ(), 8, 0.15, 0.15, 0.15, 0.05);
            }
        }

        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setNoGravity(true);
        projectile.setOwner(player);

        Vec3 dir = data.direction;
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yRot = (float) (Mth.atan2(dir.x, dir.z) * (180.0D / Math.PI));
        float xRot = (float) (Mth.atan2(dir.y, horiz) * (180.0D / Math.PI));
        projectile.setYRot(yRot);
        projectile.setXRot(xRot);
        projectile.yRotO = yRot;
        projectile.xRotO = xRot;

        if (projectile instanceof AbstractHurtingProjectile hurting) {
            hurting.setDeltaMovement(dir.scale(0.1D));
            hurting.accelerationPower = 0.1D;
        }

        if (projectile.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcast(projectile, new ClientboundTeleportEntityPacket(projectile));
            serverLevel.getChunkSource().broadcast(projectile, new ClientboundSetEntityMotionPacket(projectile));
            projectile.hasImpulse = true;
            playProjectileDeflectionEffects(projectile, dir, serverLevel, data.hitCount);
        }

        return data.hitCount;
    }

    public static void playProjectileDeflectionEffects(Projectile projectile, Vec3 dir, ServerLevel serverLevel, int hits) {
        double x = projectile.getX();
        double y = projectile.getY();
        double z = projectile.getZ();

        if (projectile instanceof DragonFireball) {
            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 14, dir.x * 0.25, dir.y * 0.25, dir.z * 0.25, 0.15);
            serverLevel.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.0F, 1.6F);
            serverLevel.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4F, 0.9F);
        } else if (projectile instanceof WitherSkull) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 12, dir.x * 0.2, dir.y * 0.2, dir.z * 0.2, 0.12);
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 8, 0.1, 0.1, 0.1, 0.05);
            serverLevel.playSound(null, x, y, z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.2F, 1.3F);
        } else if (projectile instanceof Fireball || projectile instanceof SmallFireball || projectile instanceof LargeFireball) {
            serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 14, dir.x * 0.25, dir.y * 0.25, dir.z * 0.25, 0.15);
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 6, 0.1, 0.1, 0.1, 0.05);
            serverLevel.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4F, 1.1F);
        } else if (projectile instanceof Snowball) {
            serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL, x, y, z, 12, dir.x * 0.2, dir.y * 0.2, dir.z * 0.2, 0.1);
            serverLevel.playSound(null, x, y, z, SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.2F, 1.2F);
        } else if (projectile instanceof ThrownEnderpearl) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, x, y, z, 16, dir.x * 0.2, dir.y * 0.2, dir.z * 0.2, 0.2);
            serverLevel.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2F, 1.4F);
        } else if (projectile instanceof ShulkerBullet) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, x, y, z, 12, dir.x * 0.2, dir.y * 0.2, dir.z * 0.2, 0.12);
            serverLevel.playSound(null, x, y, z, SoundEvents.SHULKER_BULLET_HIT, SoundSource.HOSTILE, 1.4F, 1.3F);
        } else {
            float pitch = Math.min(2.0F, 1.1F + (hits * 0.15F));
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    8 + Math.min(16, hits * 3), dir.x * 0.3, dir.y * 0.3 + 0.05, dir.z * 0.3, 0.18);
            serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z, 8, 0.1, 0.1, 0.1, 0.1);
            serverLevel.playSound(null, x, y, z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, pitch);
            serverLevel.playSound(null, x, y, z, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.2F, pitch);
        }
    }

    public static void deflectDynamicProjectile(Projectile projectile, Player player) {
        Vec3 returnDir = com.timestop.combat.ProjectileRedirection.direction(projectile, player,
                projectile.getOwner(), projectile.getDeltaMovement());
        com.timestop.combat.ProjectileRedirection.clearGuidance(projectile);

        double speed = Math.max(1.8, projectile.getDeltaMovement().length() * 1.35);
        projectile.setDeltaMovement(returnDir.scale(speed));
        projectile.setOwner(player);

        if (projectile instanceof AbstractHurtingProjectile hurting) {
            hurting.setDeltaMovement(returnDir.scale(0.1D));
            hurting.accelerationPower = 0.1D;
        }

        double horiz = Math.sqrt(returnDir.x * returnDir.x + returnDir.z * returnDir.z);
        float yRot = (float) (Mth.atan2(returnDir.x, returnDir.z) * (180.0D / Math.PI));
        float xRot = (float) (Mth.atan2(returnDir.y, horiz) * (180.0D / Math.PI));
        projectile.setYRot(yRot);
        projectile.setXRot(xRot);
        projectile.yRotO = yRot;
        projectile.xRotO = xRot;

        if (projectile instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(arrow.getBaseDamage() + 5.0);
            arrow.setCritArrow(true);
            if (arrow instanceof com.timestop.mixin.AbstractArrowAccessor accessor) {
                accessor.timestop$setPierceLevel((byte) Math.min(3, arrow.getPierceLevel() + 1));
            }
        }

        if (projectile.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcast(projectile, new ClientboundTeleportEntityPacket(projectile));
            serverLevel.getChunkSource().broadcast(projectile, new ClientboundSetEntityMotionPacket(projectile));
            projectile.hasImpulse = true;
            playProjectileDeflectionEffects(projectile, returnDir, serverLevel, 1);
        }
    }

    public static int punchSuspendedProjectile(Projectile projectile, Vec3 lookDirection, Player player) {
        return punchSuspendedProjectile(projectile, player);
    }

    public static void redirectProjectile(Projectile projectile, Vec3 newVelocity, @Nullable Player player) {
        ProjectileKineticData data = projectileData.computeIfAbsent(projectile.getUUID(),
                k -> createProjectileData(projectile, newVelocity));
        data.originalVelocity = newVelocity;
        data.direction = newVelocity.lengthSqr() > 1e-5 ? newVelocity.normalize() : data.direction;
        projectileEntities.put(projectile.getUUID(), new java.lang.ref.WeakReference<>(projectile));
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setNoGravity(true);
        if (player != null) {
            projectile.setOwner(player);
        }
    }

    public static void removeSuspendedProjectile(UUID uuid) {
        if (uuid != null) {
            projectileData.remove(uuid);
            projectileEntities.remove(uuid);
        }
    }

    public static void removeSuspendedProjectile(Projectile projectile) {
        if (projectile != null) {
            removeSuspendedProjectile(projectile.getUUID());
        }
    }

    public static Map<UUID, Projectile> getSuspendedProjectiles() {
        Map<UUID, Projectile> map = new HashMap<>();
        for (Map.Entry<UUID, java.lang.ref.WeakReference<Projectile>> entry : projectileEntities.entrySet()) {
            Projectile p = entry.getValue() != null ? entry.getValue().get() : null;
            if (p != null && p.isAlive()) {
                map.put(entry.getKey(), p);
            } else {
                removeSuspendedProjectile(entry.getKey());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public static void registerSuspendedProjectile(Projectile projectile, Vec3 originalVelocity) {
        if (com.timestop.combat.ProjectileCombatHelper.isStuckOrDead(projectile)) return;
        boolean isStasis = false;
        if (TemporalBubbleManager.hasActiveBubbles()) {
            TemporalBubble dominant = TemporalBubbleManager.getDominantBubble(projectile.level().dimension(),
                    projectile.getX(), projectile.getY() + projectile.getBbHeight() * .5, projectile.getZ());
            if (dominant != null && dominant.getMode() == TimeMode.TIME_STOP) {
                isStasis = true;
            }
        } else if (timeStopped && currentMode == TimeMode.TIME_STOP) {
            isStasis = true;
        }

        if (!isStasis) return;
        projectileData.computeIfAbsent(projectile.getUUID(), k -> createProjectileData(projectile, originalVelocity));
        projectileEntities.put(projectile.getUUID(), new java.lang.ref.WeakReference<>(projectile));

        // Lock in place
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setNoGravity(true);
    }

    public static void resumeSingleProjectile(ServerLevel level, Projectile projectile) {
        if (projectile == null) return;
        UUID uuid = projectile.getUUID();
        ProjectileKineticData data = projectileData.remove(uuid);
        projectileEntities.remove(uuid);
        if (data == null) return;

        if (com.timestop.combat.ProjectileCombatHelper.isStuckOrDead(projectile)) {
            projectile.setNoGravity(data.originalNoGravity);
            return;
        }

        if (projectile.isAlive()) {
            Vec3 velocity = data.getDischargeVelocity();

            projectile.setNoGravity(data.originalNoGravity);
            projectile.setDeltaMovement(velocity);

            double horiz = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            float yRot = (float) (Mth.atan2(velocity.x, velocity.z) * (180.0D / Math.PI));
            float xRot = (float) (Mth.atan2(velocity.y, horiz) * (180.0D / Math.PI));
            projectile.setYRot(yRot);
            projectile.setXRot(xRot);
            projectile.yRotO = yRot;
            projectile.hasImpulse = true;
            level.getChunkSource().broadcast(projectile, new ClientboundTeleportEntityPacket(projectile));
            level.getChunkSource().broadcast(projectile, new ClientboundSetEntityMotionPacket(projectile));

            // If it's an arrow, apply kinetic bonus damage, crit particles, and piercing!
            if (projectile instanceof AbstractArrow arrow) {
                if (data.hitCount > 0) {
                    arrow.setBaseDamage(arrow.getBaseDamage() + data.totalDamageBonus);
                    arrow.setCritArrow(true);
                    if (arrow instanceof com.timestop.mixin.AbstractArrowAccessor accessor) {
                        accessor.timestop$setPierceLevel((byte) Math.min(5, arrow.getPierceLevel() + data.hitCount));
                    }
                }
            }

            if (data.hitCount > 0 && projectile instanceof AbstractHurtingProjectile hurting) {
                Vec3 norm = velocity.normalize();
                hurting.setDeltaMovement(norm.scale(0.1D));
                hurting.accelerationPower = 0.1D;
            }

            // Launch puff at discharge origin
            level.sendParticles(ParticleTypes.POOF,
                    projectile.getX(), projectile.getY(), projectile.getZ(),
                    4, 0.08, 0.08, 0.08, 0.02);

            Vec3 vDir = velocity.normalize();
            int particleCount = 8 + Math.min(20, data.hitCount * 4);
            level.sendParticles(ParticleTypes.CRIT,
                    projectile.getX(), projectile.getY(), projectile.getZ(),
                    particleCount, vDir.x * 0.2, vDir.y * 0.2, vDir.z * 0.2, 0.15);

            if (data.hitCount > 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    projectile.getX(), projectile.getY(), projectile.getZ(),
                    8 + Math.min(12, data.hitCount * 3), vDir.x * 0.25, vDir.y * 0.25, vDir.z * 0.25, 0.15);
            }

            float pitch = Math.min(2.0F, 1.0F + (data.hitCount * 0.15F));
            level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.5F, pitch);
        }
    }

    public static void resumeProjectiles(ServerLevel level) {
        net.minecraft.server.MinecraftServer server = level.getServer();
        for (UUID uuid : new ArrayList<>(projectileData.keySet())) {
            java.lang.ref.WeakReference<Projectile> ref = projectileEntities.get(uuid);
            Projectile projectile = ref != null ? ref.get() : null;
            ServerLevel targetLevel = level;

            if (projectile != null && projectile.isAlive() && projectile.level() instanceof ServerLevel sl) {
                targetLevel = sl;
            } else {
                for (ServerLevel sl : server.getAllLevels()) {
                    Entity found = sl.getEntity(uuid);
                    if (found instanceof Projectile p && p.isAlive()) {
                        projectile = p;
                        targetLevel = sl;
                        break;
                    }
                }
            }

            if (projectile != null && projectile.isAlive()) {
                resumeSingleProjectile(targetLevel, projectile);
            } else {
                removeSuspendedProjectile(uuid);
            }
        }
        projectileData.clear();
        projectileEntities.clear();
    }

    public static void resumeProjectilesInArea(ServerLevel level, Vec3 center, double radius) {
        double rSq = radius * radius;
        List<UUID> toResume = new ArrayList<>();
        for (UUID uuid : projectileData.keySet()) {
            java.lang.ref.WeakReference<Projectile> ref = projectileEntities.get(uuid);
            Projectile p = ref != null ? ref.get() : null;
            if (p == null || !p.isAlive()) {
                Entity found = level.getEntity(uuid);
                if (found instanceof Projectile proj) p = proj;
            }
            if (p != null && p.level() == level && p.position().add(0, p.getBbHeight() * .5, 0).distanceToSqr(center) <= rSq
                    && !TemporalBubbleManager.isEntityInStasis(p)) {
                toResume.add(uuid);
            }
        }
        for (UUID uuid : toResume) {
            java.lang.ref.WeakReference<Projectile> ref = projectileEntities.get(uuid);
            Projectile p = ref != null ? ref.get() : null;
            if (p == null || !p.isAlive()) {
                Entity found = level.getEntity(uuid);
                if (found instanceof Projectile proj) p = proj;
            }
            if (p != null && p.isAlive()) {
                resumeSingleProjectile(level, p);
            } else {
                removeSuspendedProjectile(uuid);
            }
        }
    }

    public static int getRemainingTicks() {
        return remainingTicks;
    }

    private static ProjectileKineticData createProjectileData(Projectile projectile, Vec3 velocity) {
        ProjectileKineticData data = new ProjectileKineticData(velocity, projectile.getOwner());
        data.originalNoGravity = projectile.isNoGravity();
        return data;
    }

    public static int getTotalDuration() {
        return totalDuration;
    }

    @Nullable
    public static UUID getInitiatorUuid() {
        return initiatorUuid;
    }
}
