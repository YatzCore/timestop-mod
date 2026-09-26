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

        com.timestop.pedestal.ModPedestals.bootstrap();

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
        if (event.getRegistryKey().equals(Registries.STRUCTURE_TYPE)) {
            event.register(Registries.STRUCTURE_TYPE, com.timestop.worldgen.ModObservatories.ID, () -> com.timestop.worldgen.ModObservatories.TYPE);
        } else if (event.getRegistryKey().equals(Registries.STRUCTURE_PIECE)) {
            event.register(Registries.STRUCTURE_PIECE, com.timestop.worldgen.ModObservatories.ID, () -> com.timestop.worldgen.ModObservatories.PIECE);
        }
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            com.timestop.pedestal.ModPedestals.BLOCKS.forEach((id, entry) -> event.register(Registries.BLOCK, id, entry));
        } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            var entry = com.timestop.pedestal.ModPedestals.ENTITY;
            entry.bind(net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(com.timestop.pedestal.PedestalBlockEntity::new,
                    com.timestop.pedestal.ModPedestals.BLOCKS.values().stream().map(com.timestop.registry.RegistryEntry::get)
                            .toArray(net.minecraft.world.level.block.Block[]::new)).build(null));
            event.register(Registries.BLOCK_ENTITY_TYPE, entry.getId(), entry::get);
        } else if (event.getRegistryKey().equals(Registries.MENU)) {
            var entry = com.timestop.pedestal.ModPedestals.MENU;
            entry.bind(net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inventory, data) -> new com.timestop.pedestal.PedestalMenu(id, inventory)));
            event.register(Registries.MENU, entry.getId(), entry::get);
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
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
        } else if (event.getRegistryKey().equals(Registries.SOUND_EVENT)) {
            TimeStopMod.LOGGER.info("[TimeStop] Registering {} sounds...", com.timestop.sound.ModSounds.SOUNDS.size());
            com.timestop.sound.ModSounds.SOUNDS.forEach((id, entry) -> {
                var sound = entry.get();
                event.register(Registries.SOUND_EVENT, id, () -> sound);
                entry.bind(sound);
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
