package com.timestop.item;

import net.minecraft.world.item.ItemStack;

public class CreativeWatchItem extends AbstractWatchItem {
    public CreativeWatchItem(Properties properties) {
        super(properties, WatchTier.CREATIVE);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
