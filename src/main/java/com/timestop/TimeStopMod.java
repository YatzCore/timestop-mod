package com.timestop;

import com.mojang.logging.LogUtils;
import com.timestop.client.ClientSetup;
import com.timestop.combat.TemporalInteractionEvents;
import com.timestop.command.TimeStopCommand;
import com.timestop.core.TimeStopManager;
import com.timestop.item.ModItems;
import com.timestop.network.ModMessages;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
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

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new TemporalInteractionEvents());
        MinecraftForge.EVENT_BUS.register(new com.timestop.combat.RuneManager());
        MinecraftForge.EVENT_BUS.register(new com.timestop.combat.VolatileStasisHandler());
        MinecraftForge.EVENT_BUS.register(new com.timestop.combat.TachyonRuneHandler());
        MinecraftForge.EVENT_BUS.register(com.timestop.combat.DeadEyeManager.class);
        MinecraftForge.EVENT_BUS.register(new com.timestop.combat.VoltaicRicochetHandler());
        MinecraftForge.EVENT_BUS.register(new com.timestop.combat.OrbitalProjectileManager());

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
            event.accept(ModItems.CHRONOS_WATCH);
            event.accept(ModItems.CREATIVE_WATCH);
        }
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(ModItems.CREATIVE_WATCH);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TimeStopManager.serverTick();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TimeStopCommand.register(event.getDispatcher());
    }
}
