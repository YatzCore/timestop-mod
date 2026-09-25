package com.timestop.pedestal;

import com.timestop.item.ModItems;
import com.timestop.item.WatchTier;
import com.timestop.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModPedestals {
    public static final Map<ResourceLocation, RegistryEntry<Block>> BLOCKS = new LinkedHashMap<>();
    public static final RegistryEntry<Block> COPPER = block("copper_pedestal", WatchTier.COPPER);
    public static final RegistryEntry<Block> GOLDEN = block("golden_pedestal", WatchTier.GILDED);
    public static final RegistryEntry<Block> DIAMOND = block("diamond_pedestal", WatchTier.DIAMOND);
    public static final RegistryEntry<Block> NETHERITE = block("netherite_pedestal", WatchTier.NETHERITE);
    public static final RegistryEntry<Block> CREATIVE = block("creative_pedestal", WatchTier.CREATIVE);
    public static final RegistryEntry<BlockEntityType<PedestalBlockEntity>> ENTITY = new RegistryEntry<>(
            new ResourceLocation("timestop", "pedestal"), null);
    public static final RegistryEntry<MenuType<PedestalMenu>> MENU = new RegistryEntry<>(
            new ResourceLocation("timestop", "pedestal"), null);
    private static RegistryEntry<Block> block(String name, WatchTier tier) {
        ResourceLocation id = new ResourceLocation("timestop", name);
        RegistryEntry<Block> entry = new RegistryEntry<>(id, () -> new PedestalBlock(tier));
        BLOCKS.put(id, entry);
        return entry;
    }
    public static void bootstrap() {
        BLOCKS.forEach((id, block) -> ModItems.ITEMS.computeIfAbsent(id, key -> new RegistryEntry<>(key,
                () -> new BlockItem(block.get(), new Item.Properties()))));
    }
    private ModPedestals() {}
}
