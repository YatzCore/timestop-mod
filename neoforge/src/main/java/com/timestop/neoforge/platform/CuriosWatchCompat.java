package com.timestop.neoforge.platform;

import com.timestop.item.AbstractWatchItem;
import top.theillusivec4.curios.api.CuriosApi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loaded only when Curios is present. Cosmetic slots are excluded. */
public final class CuriosWatchCompat {
    private CuriosWatchCompat() {}

    public static Map<String, ItemStack> getEquippedWatchSlots(Player player) {
        Map<String, ItemStack> watches = new LinkedHashMap<>();
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            inventory.getCurios().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                var stacks = entry.getValue().getStacks();
                for (int slot = 0; slot < stacks.getSlots(); slot++) {
                    ItemStack stack = stacks.getStackInSlot(slot);
                    if (stack.getItem() instanceof AbstractWatchItem) watches.put(entry.getKey() + "/" + slot, stack);
                }
            });
        });
        return watches;
    }
}
