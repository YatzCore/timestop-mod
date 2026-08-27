package com.timestop.item;

import com.timestop.TimeStopMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TimeStopMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TimeStopMod.MOD_ID);

    public static final RegistryObject<Item> CHRONOS_WATCH = ITEMS.register("chronos_watch",
            () -> new ChronosWatchItem(new Item.Properties().stacksTo(1).durability(100)));

    public static final RegistryObject<Item> CREATIVE_WATCH = ITEMS.register("creative_watch",
            () -> new CreativeWatchItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> TIME_STOP_TAB = CREATIVE_MODE_TABS.register("timestop_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(CREATIVE_WATCH.get()))
                    .title(Component.translatable("itemGroup.timestop_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(CHRONOS_WATCH.get());
                        output.accept(CREATIVE_WATCH.get());
                    })
                    .build()
    );
}
