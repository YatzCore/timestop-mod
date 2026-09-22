package com.timestop.item;

import com.timestop.TimeStopMod;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import com.timestop.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ModItems {
    public static final Map<ResourceLocation, RegistryEntry<Item>> ITEMS = new LinkedHashMap<>();

    private static RegistryEntry<Item> register(String name, Supplier<Item> supplier) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, name);
        RegistryEntry<Item> entry = new RegistryEntry<>(id, supplier);
        ITEMS.put(id, entry);
        return entry;
    }

    // Tier 1: Copper Watch
    public static final RegistryEntry<Item> COPPER_WATCH = register("copper_watch",
            () -> new CopperWatchItem(new Item.Properties().stacksTo(1).durability(100)));

    // Tier 2: Golden Watch
    public static final RegistryEntry<Item> CHRONOS_WATCH = register("chronos_watch",
            () -> new ChronosWatchItem(new Item.Properties().stacksTo(1).durability(250)));

    // Tier 3: Diamond Watch
    public static final RegistryEntry<Item> DIAMOND_WATCH = register("diamond_watch",
            () -> new DiamondWatchItem(new Item.Properties().stacksTo(1).durability(500)));

    // Tier 4: Netherite Watch (Fire Resistant!)
    public static final RegistryEntry<Item> NETHERITE_WATCH = register("netherite_watch",
            () -> new NetheriteWatchItem(new Item.Properties().stacksTo(1).fireResistant().durability(1000)));

    // Tier 5: Creative Watch (Creative)
    public static final RegistryEntry<Item> CREATIVE_WATCH = register("creative_watch",
            () -> new CreativeWatchItem(new Item.Properties().stacksTo(1).fireResistant()));

    // Runes
    public static final RegistryEntry<Item> BLANK_RUNE = register("blank_rune",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(16), RuneType.BLANK));

    public static final RegistryEntry<Item> RUNE_DEFLECTION = register("rune_deflection",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.DEFLECTION));

    public static final RegistryEntry<Item> RUNE_SNATCHING = register("rune_snatching",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.SNATCHING));

    public static final RegistryEntry<Item> RUNE_PHASING = register("rune_phasing",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.PHASING));

    public static final RegistryEntry<Item> RUNE_KINETIC = register("rune_kinetic",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.KINETIC));

    public static final RegistryEntry<Item> RUNE_VAMPIRISM = register("rune_vampirism",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.VAMPIRISM));

    public static final RegistryEntry<Item> RUNE_VOLATILE = register("rune_volatile",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.VOLATILE));

    public static final RegistryEntry<Item> RUNE_TACHYON = register("rune_tachyon",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.TACHYON));

    public static final RegistryEntry<Item> RUNE_DEAD_EYE = register("rune_deadeye",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.DEAD_EYE));

    public static final RegistryEntry<Item> RUNE_RICOCHET = register("rune_ricochet",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.RICOCHET));

    public static final RegistryEntry<Item> RUNE_ORBITAL = register("rune_orbital",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.ORBITAL));

    public static final RegistryEntry<Item> RUNE_TRANSPOSITION = register("rune_transposition",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.TRANSPOSITION));

    public static final RegistryEntry<Item> RUNE_VECTOR = register("rune_vector",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.VECTOR));

    public static final RegistryEntry<Item> RUNE_BARRIER = register("rune_barrier",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.KINETIC_BARRIER));

    public static final RegistryEntry<Item> RUNE_COIN = register("rune_coin",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.RICOSHOT));

    public static final RegistryEntry<Item> RUNE_REWIND = register("rune_rewind",
            () -> new TemporalRuneItem(new Item.Properties().stacksTo(1), RuneType.REWIND));

    public static final RegistryEntry<Item> CHRONO_COIN = register("chrono_coin",
            () -> new ChronoCoinItem(new Item.Properties().stacksTo(64)));

    public static Item getRuneItem(RuneType type) {
        if (type == null) return BLANK_RUNE.get();
        return switch (type) {
            case BLANK -> BLANK_RUNE.get();
            case DEFLECTION -> RUNE_DEFLECTION.get();
            case SNATCHING -> RUNE_SNATCHING.get();
            case PHASING -> RUNE_PHASING.get();
            case KINETIC -> RUNE_KINETIC.get();
            case VAMPIRISM -> RUNE_VAMPIRISM.get();
            case VOLATILE -> RUNE_VOLATILE.get();
            case TACHYON -> RUNE_TACHYON.get();
            case DEAD_EYE -> RUNE_DEAD_EYE.get();
            case RICOCHET -> RUNE_RICOCHET.get();
            case ORBITAL -> RUNE_ORBITAL.get();
            case TRANSPOSITION -> RUNE_TRANSPOSITION.get();
            case VECTOR -> RUNE_VECTOR.get();
            case KINETIC_BARRIER -> RUNE_BARRIER.get();
            case RICOSHOT -> RUNE_COIN.get();
            case REWIND -> RUNE_REWIND.get();
        };
    }
}
