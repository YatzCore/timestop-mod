package com.timestop.item;

import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Inventory transfers run only on the server; repeated or stale requests cannot mint items. */
public final class RuneSocketTransactions {
    private RuneSocketTransactions() {}

    public static boolean apply(Player player, ItemStack watchStack, int slot, RuneType type) {
        if (player.level().isClientSide || watchStack.getCount() != 1
                || !(watchStack.getItem() instanceof AbstractWatchItem watch)
                || !watch.getTier().hasRuneSocket()) return false;
        ItemStack previous = AbstractWatchItem.getSocketedRune(watchStack);
        if (slot == -1) {
            if (previous.isEmpty()) return false;
            AbstractWatchItem.setSocketedRune(watchStack, ItemStack.EMPTY);
        } else {
            if (slot < 0 || slot >= player.getInventory().getContainerSize() || type == RuneType.BLANK) return false;
            ItemStack source = player.getInventory().getItem(slot);
            if (source.isEmpty() || !(source.getItem() instanceof TemporalRuneItem rune) || rune.getType() != type) return false;
            ItemStack inserted = source.split(1);
            AbstractWatchItem.setSocketedRune(watchStack, inserted);
        }
        if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false);
        player.getInventory().setChanged();
        return true;
    }
}
