package com.timestop.core;

import com.timestop.combat.TemporalDamageBuffer;
import com.timestop.combat.TemporalKineticBlockManager;
import com.timestop.item.ModItems;
import com.timestop.network.ModMessages;
import com.timestop.network.TimeStopSyncPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
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
    @Nullable
    private static net.minecraft.world.item.Item initiatorWatchItem = null;
    private static int initiatorCooldownTicks = 300;
    private static int accumulatedVampirismBonus = 0;
    private static TimeMode currentMode = TimeMode.TIME_STOP;
    private static final Set<UUID> exemptPlayers = ConcurrentHashMap.newKeySet();

    // Map of suspended projectiles: Projectile UUID -> stored velocity & kinetic data
    public static class ProjectileKineticData {
        public Vec3 originalVelocity = Vec3.ZERO;
        public Vec3 direction = Vec3.ZERO;
        public int hitCount = 0;
        public double totalDamageBonus = 0.0;
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
            if (this.hitCount == 0) {
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
            double initialSpeed = Math.max(1.8, this.originalVelocity.length());
            // Each punch increases exit speed by +40%
            double multiplier = 1.0 + (this.hitCount * 0.40);
            return this.direction.scale(initialSpeed * multiplier);
        }
    }

    private static final Map<UUID, ProjectileKineticData> projectileData = new ConcurrentHashMap<>();
    private static final Map<UUID, Projectile> projectileEntities = new ConcurrentHashMap<>();

    // Dynamic tick duration for SUPERHOT mode (250ms = 4 TPS idle, 50ms = 20 TPS moving)
    private static volatile long superhotTickMs = 250L;

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

    public static void setSuperhotTickMs(long ms) {
        superhotTickMs = Math.max(50L, Math.min(250L, ms));
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
            case SUPERHOT:
                return superhotTickMs; // Dynamically scaled by player motion
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
        accumulatedVampirismBonus = 0;
        currentMode = mode;
        initiatorUuid = initiator != null ? initiator.getUUID() : null;
        initiatorWatchItem = null;
        initiatorCooldownTicks = 300;
        if (initiator != null) {
            net.minecraft.world.item.ItemStack main = initiator.getMainHandItem();
            net.minecraft.world.item.ItemStack off = initiator.getOffhandItem();
            if (main.getItem() instanceof com.timestop.item.AbstractWatchItem w) {
                initiatorWatchItem = w;
                initiatorCooldownTicks = w.getTier().getCooldownTicks();
            } else if (off.getItem() instanceof com.timestop.item.AbstractWatchItem w) {
                initiatorWatchItem = w;
                initiatorCooldownTicks = w.getTier().getCooldownTicks();
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
            }
            Component msg = Component.literal("[Temporal Engine] ").withStyle(net.minecraft.ChatFormatting.GOLD)
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
        ModMessages.sendToClients(new TimeStopSyncPacket(true, durationTicks, initiatorUuid, mode));
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
        ModMessages.sendToClients(new TimeStopSyncPacket(true, remainingTicks, initiatorUuid, currentMode));
        return true;
    }

    public static int getAccumulatedVampirismBonus() {
        return accumulatedVampirismBonus;
    }

    public static void resumeTime(ServerLevel level) {
        if (!timeStopped) return;

        timeStopped = false;
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
        TemporalKineticBlockManager.serverTick();

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

    public static boolean isProjectileSuspended(Projectile projectile) {
        return projectileData.containsKey(projectile.getUUID());
    }

    public static Vec3 getSuspendedVelocity(Projectile projectile) {
        ProjectileKineticData data = projectileData.get(projectile.getUUID());
        return data != null ? data.getDischargeVelocity() : Vec3.ZERO;
    }

    public static int punchSuspendedProjectile(Projectile projectile, Player player) {
        ProjectileKineticData data = projectileData.computeIfAbsent(projectile.getUUID(),
                k -> new ProjectileKineticData(projectile.getDeltaMovement(), projectile.getOwner()));
        data.addPunch(projectile, player);
        projectileEntities.put(projectile.getUUID(), projectile);

        com.timestop.item.rune.RuneType rune = com.timestop.combat.RuneManager.getSocketedRuneType(player);
        if (rune == com.timestop.item.rune.RuneType.KINETIC) {
            data.totalDamageBonus += 8.0;
            data.originalVelocity = data.originalVelocity.scale(1.5);
        } else if (rune == com.timestop.item.rune.RuneType.VOLATILE) {
            projectile.getPersistentData().putBoolean("VolatileStasis", true);
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
            hurting.xPower = dir.x * 0.1D;
            hurting.yPower = dir.y * 0.1D;
            hurting.zPower = dir.z * 0.1D;
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
        Entity shooter = projectile.getOwner();
        Vec3 returnDir;
        if (shooter != null && shooter.isAlive()) {
            returnDir = shooter.getEyePosition().subtract(projectile.position()).normalize();
        } else if (projectile.getDeltaMovement().lengthSqr() > 1e-5) {
            returnDir = projectile.getDeltaMovement().reverse().normalize();
        } else {
            returnDir = player.getLookAngle().normalize();
        }

        double speed = Math.max(1.8, projectile.getDeltaMovement().length() * 1.35);
        projectile.setDeltaMovement(returnDir.scale(speed));
        projectile.setOwner(player);

        if (projectile instanceof AbstractHurtingProjectile hurting) {
            hurting.xPower = returnDir.x * 0.1D;
            hurting.yPower = returnDir.y * 0.1D;
            hurting.zPower = returnDir.z * 0.1D;
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
            arrow.setPierceLevel((byte) Math.min(3, arrow.getPierceLevel() + 1));
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
                k -> new ProjectileKineticData(newVelocity, player));
        data.direction = newVelocity.lengthSqr() > 1e-5 ? newVelocity.normalize() : data.direction;
        projectileEntities.put(projectile.getUUID(), projectile);
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setNoGravity(true);
        if (player != null) {
            projectile.setOwner(player);
        }
    }

    public static void removeSuspendedProjectile(Projectile projectile) {
        projectileData.remove(projectile.getUUID());
        projectileEntities.remove(projectile.getUUID());
    }

    public static void registerSuspendedProjectile(Projectile projectile, Vec3 originalVelocity) {
        if (!timeStopped || currentMode != TimeMode.TIME_STOP) return;
        projectileData.put(projectile.getUUID(), new ProjectileKineticData(originalVelocity, projectile.getOwner()));
        projectileEntities.put(projectile.getUUID(), projectile);

        // Lock in place
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setNoGravity(true);
    }

    private static void resumeProjectiles(ServerLevel level) {
        for (Map.Entry<UUID, ProjectileKineticData> entry : projectileData.entrySet()) {
            Projectile projectile = projectileEntities.get(entry.getKey());
            if (projectile != null && projectile.isAlive()) {
                ProjectileKineticData data = entry.getValue();
                Vec3 velocity = data.getDischargeVelocity();

                projectile.setNoGravity(false);
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
                        arrow.setPierceLevel((byte) Math.min(5, arrow.getPierceLevel() + data.hitCount));
                    }
                }

                if (projectile instanceof AbstractHurtingProjectile hurting) {
                    Vec3 norm = velocity.normalize();
                    hurting.xPower = norm.x * 0.1D;
                    hurting.yPower = norm.y * 0.1D;
                    hurting.zPower = norm.z * 0.1D;
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
        projectileData.clear();
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
