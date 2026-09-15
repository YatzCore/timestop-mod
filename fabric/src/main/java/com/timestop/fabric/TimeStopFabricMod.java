package com.timestop.fabric;

import com.timestop.TimeStopMod;
import com.timestop.command.SyncCommand;
import com.timestop.command.TimeStopCommand;
import com.timestop.core.SuperhotActivityManager;
import com.timestop.core.TemporalBubbleManager;
import com.timestop.core.TimeStopManager;
import com.timestop.entity.ModEntities;
import com.timestop.fabric.network.FabricNetworkHelper;
import com.timestop.item.ModItems;
import com.timestop.platform.Services;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

public class TimeStopFabricMod implements ModInitializer {
    private static MinecraftServer currentServer;

    public static MinecraftServer getServer() {
        return currentServer;
    }

    @Override
    public void onInitialize() {
        // 1. Items & Entities
        TimeStopMod.LOGGER.info("[TimeStop] Registering {} items...", ModItems.ITEMS.size());
        ModItems.ITEMS.forEach((id, entry) -> {
            var item = Registry.register(BuiltInRegistries.ITEM, id, entry.get());
            entry.bind(item);
            TimeStopMod.LOGGER.info("[TimeStop] Registered item: {}", id);
        });
        ModEntities.ENTITIES.forEach((id, entry) -> {
            var entity = Registry.register(BuiltInRegistries.ENTITY_TYPE, id, entry.get());
            entry.bind(entity);
            TimeStopMod.LOGGER.info("[TimeStop] Registered entity: {}", id);
        });

        // 2. Creative Tab
        net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> tabKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                new ResourceLocation(TimeStopMod.MOD_ID, "main")
        );
        net.minecraft.world.item.CreativeModeTab timeStopTab = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.timestop"))
                .icon(() -> new ItemStack(ModItems.CHRONOS_WATCH.get()))
                .displayItems((params, output) -> {
                    ModItems.ITEMS.values().forEach(entry -> {
                        output.accept(entry.get());
                    });
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, timeStopTab);

        // 3. Config
        com.timestop.config.TimeStopConfig.load();

        // 4. Networking
        FabricNetworkHelper.registerServerReceivers();

        // 5. Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TimeStopCommand.register(dispatcher);
            SyncCommand.register(dispatcher);
        });

        // 6. Server Lifecycle
        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerLevel level = server.overworld();
            TemporalBubbleManager.stopAllBubbles(level);
            TimeStopManager.resumeTime(level);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                com.timestop.combat.KineticPalmManager.setGuarding(player, false);
                com.timestop.combat.KineticPalmManager.dischargeDrop(player);
            }
            TimeStopManager.reset();
            com.timestop.combat.KineticPalmManager.clearAll();
            com.timestop.combat.OrbitalProjectileManager.clearAll();
            TemporalBubbleManager.reset();
            com.timestop.combat.TemporalKineticBlockManager.clearAll();
            com.timestop.sync.SyncManager.resetCache();
            com.timestop.combat.TemporalDamageBuffer.clearAll();
            com.timestop.combat.RuneManager.clearAllCooldowns();
            com.timestop.combat.TranspositionManager.clearAllCooldowns();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SuperhotActivityManager.onServerStopped();
            currentServer = null;
        });

        // 7. Server Ticks
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TimeStopManager.serverTick();
            TemporalBubbleManager.serverTick();
            SuperhotActivityManager.serverTick();
            com.timestop.combat.DeadEyeManager.serverTick();
            com.timestop.combat.VoltaicRicochetHandler.serverTick();
            com.timestop.combat.KineticPalmManager.serverTick();
            com.timestop.combat.OrbitalProjectileManager.serverTick();
        });

        // 8. Player Connections
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer serverPlayer = handler.getPlayer();
            com.timestop.sync.SyncManager.cachePlayerName(serverPlayer);
            TemporalBubbleManager.syncAllToPlayer(serverPlayer);
            com.timestop.network.ModMessages.sendToPlayer(new com.timestop.network.TimeStopSyncPacket(
                    TimeStopManager.isGlobalTimeStopActive(), TimeStopManager.getRemainingTicks(),
                    TimeStopManager.getInitiatorUuid(), TimeStopManager.getCurrentMode(),
                    TimeStopManager.getExemptPlayers()), serverPlayer);
            com.timestop.network.ModMessages.sendToPlayer(com.timestop.network.SyncSpeedConfigPacket.current(), serverPlayer);
            com.timestop.network.ModMessages.sendToPlayer(com.timestop.network.SyncMechanicsConfigPacket.current(), serverPlayer);
            com.timestop.combat.CoinManager.onPlayerLoggedIn(serverPlayer);
            com.timestop.combat.OrbitalProjectileManager.onPlayerLoggedIn(serverPlayer);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer serverPlayer = handler.getPlayer();
            TemporalBubbleManager.stopPlayerBubble(serverPlayer.serverLevel(), serverPlayer.getUUID());
            if (serverPlayer.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                TimeStopManager.resumeTime(serverPlayer.serverLevel());
            }
            com.timestop.combat.KineticPalmManager.setGuarding(serverPlayer, false);
            com.timestop.combat.KineticPalmManager.dischargeDrop(serverPlayer);
            TimeStopManager.removeMatrixAttributes(serverPlayer);
            com.timestop.combat.RuneManager.clearPlayerCooldowns(serverPlayer.getUUID());
            com.timestop.combat.TranspositionManager.clearPlayerCooldown(serverPlayer.getUUID());
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            com.timestop.combat.CoinManager.onPlayerRespawn(newPlayer);
            com.timestop.combat.OrbitalProjectileManager.onPlayerRespawn(newPlayer);
        });

        // 9. Combat & Entity events
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            com.timestop.combat.CoinManager.onLivingDeath(entity, damageSource);
            if (entity instanceof ServerPlayer serverPlayer) {
                TemporalBubbleManager.stopPlayerBubble(serverPlayer.serverLevel(), serverPlayer.getUUID());
                if (serverPlayer.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                    TimeStopManager.resumeTime(serverPlayer.serverLevel());
                }
                com.timestop.combat.KineticPalmManager.setGuarding(serverPlayer, false);
                com.timestop.combat.KineticPalmManager.dischargeDrop(serverPlayer);
                TimeStopManager.removeMatrixAttributes(serverPlayer);
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !com.timestop.combat.RuneManager.onLivingAttack(entity, source));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (com.timestop.combat.TemporalInteractionEvents.onAttackEntity(player, entity)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                com.timestop.combat.TemporalInteractionEvents.onEntityInteract(player, entity, hand));

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Projectile projectile) {
                com.timestop.combat.TaczPrecision.onBulletSpawn(projectile);
            }
            com.timestop.combat.KineticPalmManager.onProjectileLoaded(entity, world);
        });

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) ->
                com.timestop.combat.KineticPalmManager.onStartTracking(player, trackedEntity));

        TimeStopMod.LOGGER.info("[TimeStop] Fabric module initialized successfully!");
    }
}