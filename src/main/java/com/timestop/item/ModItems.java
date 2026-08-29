package com.timestop.item;

import com.timestop.TimeStopMod;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
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

    // Tier 1: Copper Watch
    public static final RegistryObject<Item> COPPER_WATCH = ITEMS.register("copper_watch",
            () -> new CopperWatchItem(new Item.Properties().stacksTo(1).durability(100)));

    // Tier 2: Golden Watch
    public static final RegistryObject<Item> CHRONOS_WATCH = ITEMS.register("chronos_watch",
            () -> new ChronosWatchItem(new Item.Properties().stacksTo(1).durability(250)));

    // Tier 3: Diamond Watch
    public static final RegistryObject<Item> DIAMOND_WATCH = ITEMS.register("diamond_watch",
            () -> new DiamondWatchItem(new Item.Properties().stacksTo(1).durability(500)));

    // Tier 4: Netherite Watch (Fire Resistant!)
    public static final RegistryObject<Item> NETHERITE_WATCH = ITEMS.register("netherite_watch",
            () -> new NetheriteWatchItem(new Item.Properties().stacksTo(1).fireResistant().durability(1000)));

    // Tier 5: Creative Watch (Creative)
    public static final RegistryObject<Item> CREATIVE_WATCH = ITEMS.register("creative_watch",
            () -> new CreativeWatchItem(new Item.Properties().stacksTo(1).fireResistant()));

    // Runes
    public static final RegistryObject<Item> BLANK_RUNE = ITEMS.register("blank_rune",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(16), RuneType.BLANK));

    public static final RegistryObject<Item> RUNE_DEFLECTION = ITEMS.register("rune_deflection",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.DEFLECTION));

    public static final RegistryObject<Item> RUNE_SNATCHING = ITEMS.register("rune_snatching",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.SNATCHING));

    public static final RegistryObject<Item> RUNE_PHASING = ITEMS.register("rune_phasing",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.PHASING));

    public static final RegistryObject<Item> RUNE_KINETIC = ITEMS.register("rune_kinetic",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.KINETIC));

    public static final RegistryObject<Item> RUNE_VAMPIRISM = ITEMS.register("rune_vampirism",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.VAMPIRISM));

    public static final RegistryObject<Item> RUNE_VOLATILE = ITEMS.register("rune_volatile",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.VOLATILE));

    public static final RegistryObject<Item> RUNE_TACHYON = ITEMS.register("rune_tachyon",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.TACHYON));

    public static final RegistryObject<Item> RUNE_DEAD_EYE = ITEMS.register("rune_deadeye",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.DEAD_EYE));

    public static final RegistryObject<Item> RUNE_RICOCHET = ITEMS.register("rune_ricochet",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.RICOCHET));

    public static final RegistryObject<Item> RUNE_ORBITAL = ITEMS.register("rune_orbital",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.ORBITAL));

    public static final RegistryObject<Item> RUNE_TRANSPOSITION = ITEMS.register("rune_transposition",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.TRANSPOSITION));

    public static final RegistryObject<CreativeModeTab> TIME_STOP_TAB = CREATIVE_MODE_TABS.register("timestop_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(CHRONOS_WATCH.get()))
                    .title(Component.translatable("itemGroup.timestop_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(COPPER_WATCH.get());
                        output.accept(CHRONOS_WATCH.get());
                        output.accept(DIAMOND_WATCH.get());
                        output.accept(NETHERITE_WATCH.get());
                        output.accept(CREATIVE_WATCH.get());
                        output.accept(BLANK_RUNE.get());
                        output.accept(RUNE_DEFLECTION.get());
                        output.accept(RUNE_SNATCHING.get());
                        output.accept(RUNE_PHASING.get());
                        output.accept(RUNE_KINETIC.get());
                        output.accept(RUNE_VAMPIRISM.get());
                        output.accept(RUNE_VOLATILE.get());
                        output.accept(RUNE_TACHYON.get());
                        output.accept(RUNE_DEAD_EYE.get());
                        output.accept(RUNE_RICOCHET.get());
                        output.accept(RUNE_ORBITAL.get());
                        output.accept(RUNE_TRANSPOSITION.get());
                    })
                    .build()
    );
}
