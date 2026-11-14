package com.timestop.core.rewind;

import com.timestop.item.AbstractWatchItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WatchPreferences {
    private static final String ID = "TimeStopWatchId";

    public static ItemStack snapshot(ItemStack stack) {
        com.timestop.combat.RewindRuneManager.identifyRunes(stack);
        if (stack.getItem() instanceof AbstractWatchItem) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null || !customData.contains(ID)) {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(ID, UUID.randomUUID()));
            }
        }
        return stack.copy();
    }

    public static Map<UUID, ItemStack> capture(ServerPlayer player) {
        Map<UUID, ItemStack> result = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            add(result, player.getInventory().getItem(i));
        }
        for (var stack : com.timestop.platform.Services.PLATFORM.getEquippedWatches(player)) add(result, stack);
        add(result, player.containerMenu.getCarried());
        return result;
    }

    private static void add(Map<UUID, ItemStack> result, ItemStack stack) {
        if (stack.getItem() instanceof AbstractWatchItem) {
            snapshot(stack);
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.contains(ID)) {
                result.put(customData.copyTag().getUUID(ID), stack.copy());
            }
        }
    }

    public static ItemStack restore(ItemStack historical, Map<UUID, ItemStack> current) {
        ItemStack result = historical.copy();
        if (result.getItem() instanceof AbstractWatchItem) {
            CustomData customData = result.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.contains(ID)) {
                var latest = current.get(customData.copyTag().getUUID(ID));
                if (latest != null && latest.is(result.getItem())) {
                    AbstractWatchItem.setMode(result, AbstractWatchItem.getMode(latest));
                    AbstractWatchItem.setGlobalScope(result, AbstractWatchItem.isGlobalScope(latest));
                    if (result.getItem() instanceof AbstractWatchItem watch && watch.getTier() == com.timestop.item.WatchTier.CREATIVE) {
                        AbstractWatchItem.setBubbleRadius(result, (int) AbstractWatchItem.getBubbleRadius(latest));
                        AbstractWatchItem.setRewindInitiatorExempt(result, AbstractWatchItem.isRewindInitiatorExempt(latest));
                        for (var type : com.timestop.item.rune.RuneType.values()) AbstractWatchItem.setMatrixRune(result, type, AbstractWatchItem.isMatrixRuneActive(latest, type));
                    }
                }
            }
        }
        return result;
    }

    public static UUID idOf(ItemStack stack) {
        if (!(stack.getItem() instanceof AbstractWatchItem)) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().hasUUID(ID) ? data.copyTag().getUUID(ID) : null;
    }

    /** Worn watches stay in their native slots; older inventory copies must not be recreated. */
    public static java.util.Set<UUID> equippedIds(ServerPlayer player) {
        var ids = new java.util.HashSet<UUID>();
        for (var stack : com.timestop.platform.Services.PLATFORM.getEquippedWatches(player)) {
            snapshot(stack);
            UUID id = idOf(stack);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    public static ItemStack restore(ItemStack historical, Map<UUID, ItemStack> current, java.util.Set<UUID> equippedIds) {
        UUID id = idOf(historical);
        return id != null && equippedIds.contains(id) ? ItemStack.EMPTY : restore(historical, current);
    }
}
