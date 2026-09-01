package com.timestop;

import com.mojang.logging.LogUtils;
import com.timestop.client.ClientSetup;
import com.timestop.command.TimeStopCommand;
import com.timestop.core.TimeStopManager;
import com.timestop.item.ModItems;
import com.timestop.network.ModMessages;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(TimeStopMod.MOD_ID)
public class TimeStopMod {
    public static final String MOD_ID = "timestop";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TimeStopMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, com.timestop.config.TimeStopConfig.CLIENT_SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, com.timestop.config.TimeStopConfig.COMMON_SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modEventBus);
        }

        LOGGER.info("[TimeStop] Ultimate Time Stop Mod initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModMessages::register);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.COPPER_WATCH);
            event.accept(ModItems.CHRONOS_WATCH);
            event.accept(ModItems.DIAMOND_WATCH);
            event.accept(ModItems.NETHERITE_WATCH);
            event.accept(ModItems.CREATIVE_WATCH);
        }
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(ModItems.CREATIVE_WATCH);
        }
    }

    @Mod.EventBusSubscriber(modid = TimeStopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                TimeStopManager.serverTick();
                com.timestop.core.TemporalBubbleManager.serverTick();
            }
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            TimeStopCommand.register(event.getDispatcher());
            com.timestop.command.FriendCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.timestop.friend.FriendManager.cachePlayerName(serverPlayer);
                com.timestop.core.TemporalBubbleManager.syncAllToPlayer(serverPlayer);
            }
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.timestop.core.TemporalBubbleManager.stopPlayerBubble(serverPlayer.serverLevel(), serverPlayer.getUUID());
            }
        }

        @SubscribeEvent
        public static void onLivingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.timestop.core.TemporalBubbleManager.stopPlayerBubble(serverPlayer.serverLevel(), serverPlayer.getUUID());
            }
        }
    }
}
