package com.timestop.forge;

import com.timestop.TimeStopMod;
import com.timestop.command.SyncCommand;
import com.timestop.command.TimeStopCommand;
import com.timestop.core.SuperhotActivityManager;
import com.timestop.core.TemporalBubbleManager;
import com.timestop.core.TimeStopManager;
import com.timestop.entity.ModEntities;
import com.timestop.forge.client.ForgeClientSetup;
import com.timestop.forge.network.ForgeNetworkHelper;
import com.timestop.item.ModItems;
import com.timestop.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

@Mod(TimeStopMod.MOD_ID)
public class TimeStopForgeMod {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TimeStopMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> TIME_STOP_TAB = CREATIVE_MODE_TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.timestop"))
                    .icon(() -> new ItemStack(ModItems.CHRONOS_WATCH.get()))
                    .displayItems((params, output) -> {
                        ModItems.ITEMS.values().forEach(entry -> {
                            output.accept(entry.get());
                        });
                    })
                    .build()
    );

    public TimeStopForgeMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        com.timestop.config.TimeStopConfig.load();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClientSetup.init(modEventBus));

        MinecraftForge.EVENT_BUS.register(new ForgeServerEvents());

        TimeStopMod.LOGGER.info("[TimeStop] Forge module initialized successfully!");
    }

    private void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            ModItems.ITEMS.forEach((id, entry) -> {
                Item item = entry.get();
                event.register(Registries.ITEM, id, () -> item);
                entry.bind(item);
            });
        } else if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
            ModEntities.ENTITIES.forEach((id, entry) -> {
                var entity = entry.get();
                event.register(Registries.ENTITY_TYPE, id, () -> entity);
                entry.bind(entity);
            });
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ForgeNetworkHelper::register);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(ModItems.CREATIVE_WATCH.get());
        }
    }

    public static class ForgeServerEvents {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                TimeStopManager.serverTick();
                TemporalBubbleManager.serverTick();
                SuperhotActivityManager.serverTick();
                com.timestop.combat.DeadEyeManager.serverTick();
                com.timestop.combat.VoltaicRicochetHandler.serverTick();
                com.timestop.combat.KineticPalmManager.serverTick();
                com.timestop.combat.OrbitalProjectileManager.serverTick();
            }
        }

        @SubscribeEvent
        public void onRegisterCommands(RegisterCommandsEvent event) {
            TimeStopCommand.register(event.getDispatcher());
            SyncCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                com.timestop.sync.SyncManager.cachePlayerName(serverPlayer);
                TemporalBubbleManager.syncAllToPlayer(serverPlayer);
                com.timestop.network.ModMessages.sendToPlayer(new com.timestop.network.TimeStopSyncPacket(
                        TimeStopManager.isGlobalTimeStopActive(), TimeStopManager.getRemainingTicks(),
                        TimeStopManager.getInitiatorUuid(), TimeStopManager.getCurrentMode(),
                        TimeStopManager.getExemptPlayers()), serverPlayer);
                com.timestop.network.ModMessages.sendToPlayer(com.timestop.network.SyncSpeedConfigPacket.current(), serverPlayer);
                com.timestop.combat.CoinManager.onPlayerLoggedIn(serverPlayer);
                com.timestop.combat.OrbitalProjectileManager.onPlayerLoggedIn(serverPlayer);
            }
        }

        @SubscribeEvent
        public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                TemporalBubbleManager.stopPlayerBubble(serverPlayer.serverLevel(), serverPlayer.getUUID());
                if (serverPlayer.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                    TimeStopManager.resumeTime(serverPlayer.serverLevel());
                }
                com.timestop.combat.KineticPalmManager.setGuarding(serverPlayer, false);
                com.timestop.combat.KineticPalmManager.dischargeDrop(serverPlayer);
                TimeStopManager.removeMatrixAttributes(serverPlayer);
                com.timestop.combat.RuneManager.clearPlayerCooldowns(serverPlayer.getUUID());
                com.timestop.combat.TranspositionManager.clearPlayerCooldown(serverPlayer.getUUID());
            }
        }

        @SubscribeEvent
        public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                com.timestop.combat.CoinManager.onPlayerRespawn(serverPlayer);
                com.timestop.combat.OrbitalProjectileManager.onPlayerRespawn(serverPlayer);
            }
        }

        @SubscribeEvent
        public void onLivingDeath(LivingDeathEvent event) {
            com.timestop.combat.CoinManager.onLivingDeath(event.getEntity(), event.getSource());
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                TemporalBubbleManager.stopPlayerBubble(serverPlayer.serverLevel(), serverPlayer.getUUID());
                if (serverPlayer.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                    TimeStopManager.resumeTime(serverPlayer.serverLevel());
                }
                com.timestop.combat.KineticPalmManager.setGuarding(serverPlayer, false);
                com.timestop.combat.KineticPalmManager.dischargeDrop(serverPlayer);
                TimeStopManager.removeMatrixAttributes(serverPlayer);
            }
        }

        @SubscribeEvent
        public void onLivingAttack(LivingAttackEvent event) {
            if (com.timestop.combat.RuneManager.onLivingAttack(event.getEntity(), event.getSource())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onAttackEntity(AttackEntityEvent event) {
            if (com.timestop.combat.TemporalInteractionEvents.onAttackEntity(event.getEntity(), event.getTarget())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
            InteractionResult result = com.timestop.combat.TemporalInteractionEvents.onEntityInteract(event.getEntity(), event.getTarget(), event.getHand());
            if (result.consumesAction()) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onProjectileImpact(ProjectileImpactEvent event) {
            if (com.timestop.combat.OrbitalProjectileManager.onProjectileImpact(event.getProjectile(), event.getRayTraceResult())) {
                event.setCanceled(true);
                return;
            }
            com.timestop.combat.VoltaicRicochetHandler.onProjectileImpact(event.getProjectile(), event.getRayTraceResult());
            com.timestop.combat.VolatileStasisHandler.onProjectileImpact(event.getProjectile());
            if (com.timestop.combat.KineticPalmManager.onDroppedProjectileImpact(event.getProjectile())) {
                event.setCanceled(true);
                return;
            }
        }

        @SubscribeEvent
        public void onEntityJoinLevel(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof Projectile projectile) {
                com.timestop.combat.TaczPrecision.onBulletSpawn(projectile);
            }
            com.timestop.combat.KineticPalmManager.onProjectileLoaded(event.getEntity(), event.getLevel());
        }

        @SubscribeEvent
        public void onStartTracking(PlayerEvent.StartTracking event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                com.timestop.combat.KineticPalmManager.onStartTracking(serverPlayer, event.getTarget());
            }
        }

        @SubscribeEvent
        public void onServerStopping(ServerStoppingEvent event) {
            ServerLevel level = event.getServer().overworld();
            TemporalBubbleManager.stopAllBubbles(level);
            TimeStopManager.resumeTime(level);
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
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
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            SuperhotActivityManager.onServerStopped();
        }
    }
}