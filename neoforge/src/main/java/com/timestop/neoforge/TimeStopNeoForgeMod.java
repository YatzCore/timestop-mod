package com.timestop.neoforge;

import com.timestop.TimeStopMod;
import com.timestop.config.TimeStopConfig;
import com.timestop.entity.ModEntities;
import com.timestop.item.ModItems;
import com.timestop.neoforge.client.TimeStopNeoForgeClient;
import com.timestop.neoforge.event.NeoForgeEventHandlers;
import com.timestop.neoforge.network.NeoForgeNetworkHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(TimeStopMod.MOD_ID)
public class TimeStopNeoForgeMod {

    public TimeStopNeoForgeMod(IEventBus modEventBus) {
        TimeStopMod.LOGGER.info("[TimeStop] Initializing NeoForge module for Minecraft 1.21.1...");

        // Config setup
        TimeStopConfig.setConfigFile(FMLPaths.CONFIGDIR.get().resolve("timestop.json").toFile());
        TimeStopConfig.load();

        // Mod bus events
        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(NeoForgeNetworkHelper::registerPayloadHandlers);

        // NeoForge game event bus
        NeoForge.EVENT_BUS.register(NeoForgeEventHandlers.class);

        // Client setup
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TimeStopNeoForgeClient.init(modEventBus);
        }

        TimeStopMod.LOGGER.info("[TimeStop] NeoForge module initialized successfully!");
    }

    private void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            TimeStopMod.LOGGER.info("[TimeStop] Registering {} items...", ModItems.ITEMS.size());
            ModItems.ITEMS.forEach((id, entry) -> {
                var item = entry.get();
                event.register(Registries.ITEM, id, () -> item);
                entry.bind(item);
            });
        } else if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
            TimeStopMod.LOGGER.info("[TimeStop] Registering {} entities...", ModEntities.ENTITIES.size());
            ModEntities.ENTITIES.forEach((id, entry) -> {
                var entity = entry.get();
                event.register(Registries.ENTITY_TYPE, id, () -> entity);
                entry.bind(entity);
            });
        } else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            ResourceLocation tabId = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "main");
            CreativeModeTab tab = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.timestop"))
                    .icon(() -> new ItemStack(ModItems.CHRONOS_WATCH.get()))
                    .displayItems((params, output) -> {
                        ModItems.ITEMS.values().forEach(entry -> output.accept(entry.get()));
                    })
                    .build();
            event.register(Registries.CREATIVE_MODE_TAB, tabId, () -> tab);
        }
    }
}
