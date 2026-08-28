package com.timestop.item;

import net.minecraft.world.item.ItemStack;

public class NetheriteWatchItem extends AbstractWatchItem {
    public NetheriteWatchItem(Properties properties) {
        super(properties, WatchTier.NETHERITE);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Legendary enchantment glint
    }
}
