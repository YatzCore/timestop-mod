package com.timestop.fabric.platform;

import com.timestop.item.AbstractWatchItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loaded only when Trinkets is present. Cosmetic slots are excluded. */
public final class TrinketsWatchCompat {
    private TrinketsWatchCompat() {}

    public static Map<String, ItemStack> getEquippedWatchSlots(Player player) {
        Map<String, ItemStack> watches = new LinkedHashMap<>();
        TrinketsApi.getTrinketComponent(player).ifPresent(component -> {
            component.getInventory().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(group -> {
                group.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    var stacks = entry.getValue();
                    for (int slot = 0; slot < stacks.getContainerSize(); slot++) {
                        ItemStack stack = stacks.getItem(slot);
                        if (stack.getItem() instanceof AbstractWatchItem) watches.put(group.getKey() + "/" + entry.getKey() + "/" + slot, stack);
                    }
                });
            });
        });
        return watches;
    }
}
