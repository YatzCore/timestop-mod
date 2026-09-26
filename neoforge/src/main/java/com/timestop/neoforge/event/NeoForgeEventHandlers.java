package com.timestop.neoforge.event;

import com.timestop.command.SyncCommand;
import com.timestop.command.TimeStopCommand;
import com.timestop.combat.*;
import com.timestop.core.SuperhotActivityManager;
import com.timestop.core.TemporalBubbleManager;
import com.timestop.core.TimeStopManager;
import com.timestop.item.rune.RuneType;
import com.timestop.network.ModMessages;
import com.timestop.network.SyncSpeedConfigPacket;
import com.timestop.network.TimeStopSyncPacket;
import com.timestop.sync.SyncManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class NeoForgeEventHandlers {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TimeStopCommand.register(event.getDispatcher());
        SyncCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        TemporalBubbleManager.stopAllBubbles(level);
        TimeStopManager.resumeTime(level);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            KineticPalmManager.setGuarding(player, false);
            KineticPalmManager.dischargeDrop(player);
        }
        TimeStopManager.reset();
        KineticPalmManager.clearAll();
        OrbitalProjectileManager.clearAll();
        TemporalBubbleManager.reset();
        TemporalKineticBlockManager.clearAll();
        SyncManager.resetCache();
        TemporalDamageBuffer.clearAll();
        RuneManager.clearAllCooldowns();
        TranspositionManager.clearAllCooldowns();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SuperhotActivityManager.onServerStopped();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TimeStopManager.serverTick();
        TemporalBubbleManager.serverTick();
        SuperhotActivityManager.serverTick();
        DeadEyeManager.serverTick();
        VoltaicRicochetHandler.serverTick();
        KineticPalmManager.serverTick();
        OrbitalProjectileManager.serverTick();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SyncManager.cachePlayerName(player);
            TemporalBubbleManager.syncAllToPlayer(player);
            ModMessages.sendToPlayer(new TimeStopSyncPacket(
                    TimeStopManager.isGlobalTimeStopActive(), TimeStopManager.getRemainingTicks(),
                    TimeStopManager.getInitiatorUuid(), TimeStopManager.getCurrentMode(),
                    TimeStopManager.getExemptPlayers()), player);
            ModMessages.sendToPlayer(SyncSpeedConfigPacket.current(), player);
            ModMessages.sendToPlayer(new com.timestop.network.SyncRewindAllowedPacket(TimeStopManager.isRewindAllowed()), player);
            CoinManager.onPlayerLoggedIn(player);
            OrbitalProjectileManager.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TemporalBubbleManager.stopPlayerBubble(player.serverLevel(), player.getUUID());
            if (player.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                TimeStopManager.resumeTime(player.serverLevel());
            }
            KineticPalmManager.setGuarding(player, false);
            KineticPalmManager.dischargeDrop(player);
            TimeStopManager.removeMatrixAttributes(player);
            RuneManager.clearPlayerCooldowns(player.getUUID());
            com.timestop.combat.RewindRuneManager.clearPlayer(player.getUUID());
            TranspositionManager.clearPlayerCooldown(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CoinManager.onPlayerRespawn(player);
            OrbitalProjectileManager.onPlayerRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (com.timestop.combat.RewindRuneManager.tryTriggerDeathRewind(player, event.getSource())) {
                event.setCanceled(true);
                return;
            }
        }
        CoinManager.onLivingDeath(event.getEntity(), event.getSource());
        if (event.getEntity() instanceof ServerPlayer player) {
            TemporalBubbleManager.stopPlayerBubble(player.serverLevel(), player.getUUID());
            if (player.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                TimeStopManager.resumeTime(player.serverLevel());
            }
            KineticPalmManager.setGuarding(player, false);
            KineticPalmManager.dischargeDrop(player);
            TimeStopManager.removeMatrixAttributes(player);
            com.timestop.combat.RewindRuneManager.clearPlayer(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && com.timestop.combat.RewindRuneManager.isPlayerInvulnerable(player)) {
            event.setCanceled(true);
            return;
        }
        if (RuneManager.onLivingAttack(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (TemporalInteractionEvents.onAttackEntity(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = TemporalInteractionEvents.onEntityInteract(event.getEntity(), event.getTarget(), event.getHand());
        if (result.consumesAction()) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Projectile projectile) {
            TaczPrecision.onBulletSpawn(projectile);
        }
        KineticPalmManager.onProjectileLoaded(event.getEntity(), event.getLevel());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KineticPalmManager.onStartTracking(player, event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        HitResult hitResult = event.getRayTraceResult();

        if (OrbitalProjectileManager.onProjectileImpact(projectile, hitResult)) {
            event.setCanceled(true);
            return;
        }
        VolatileStasisHandler.onProjectileImpact(projectile);
        if (KineticPalmManager.onDroppedProjectileImpact(projectile)) {
            event.setCanceled(true);
            return;
        }

        if (projectile instanceof AbstractArrow arrow && projectile.level() instanceof ServerLevel level
                && hitResult instanceof EntityHitResult
                && arrow.getOwner() instanceof Player player
                && RuneManager.hasRune(player, RuneType.RICOCHET)) {
            var copy = arrow.getType().create(level);
            if (copy instanceof AbstractArrow next) {
                CompoundTag data = arrow.saveWithoutId(new CompoundTag());
                data.remove("UUID");
                next.load(data);
                next.setOwner(player);
                if (VoltaicRicochetHandler.onProjectileImpact(next, hitResult)) {
                    arrow.discard();
                    if (next.isAlive()) level.addFreshEntity(next);
                } else {
                    next.discard();
                }
            }
        }
    }
}
