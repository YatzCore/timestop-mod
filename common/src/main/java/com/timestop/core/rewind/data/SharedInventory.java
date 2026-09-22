package com.timestop.core.rewind.data;

import com.timestop.core.rewind.WatchPreferences;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared immutable snapshot of a player's inventory and carried item.
 * Enables change-based deduplication across consecutive tick frames.
 */
public record SharedInventory(
        List<ItemStack> items,
        ItemStack carried,
        int estimatedBytes
) {
    public static SharedInventory capture(ServerPlayer player) {
        int containerSize = player.getInventory().getContainerSize();
        List<ItemStack> list = new ArrayList<>(containerSize);
        int bytes = 64 + containerSize * 32;

        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = WatchPreferences.snapshot(player.getInventory().getItem(i));
            list.add(stack);
            if (!stack.isEmpty()) {
                bytes += stack.save(player.registryAccess()).sizeInBytes();
            }
        }

        ItemStack carried = WatchPreferences.snapshot(player.containerMenu.getCarried());
        if (!carried.isEmpty()) {
            bytes += carried.save(player.registryAccess()).sizeInBytes();
        }

        return new SharedInventory(Collections.unmodifiableList(list), carried, bytes);
    }

    public boolean matches(ServerPlayer player) {
        if (!ItemStack.matches(carried, player.containerMenu.getCarried())) {
            return false;
        }
        int containerSize = player.getInventory().getContainerSize();
        if (items.size() != containerSize) {
            return false;
        }
        for (int i = 0; i < containerSize; i++) {
            if (!ItemStack.matches(items.get(i), player.getInventory().getItem(i))) {
                return false;
            }
        }
        return true;
    }
}
