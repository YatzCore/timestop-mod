package com.timestop.item;

import com.timestop.core.rewind.WatchPreferences;
import com.timestop.platform.Services;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/** A player's own watch location. Accessory slots retain their native inventory identity. */
public record WatchTarget(String slot, @Nullable UUID watchId) {
    public static WatchTarget inHand(InteractionHand hand) {
        return new WatchTarget(hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand", null);
    }

    /** The interface key prefers the worn watch, then falls back to the normal activation watch. */
    @Nullable
    /** The watch V (time-stop key) activates: held first, then the best worn watch, then inventory. */
    public static WatchTarget forActivation(@Nullable Player player) {
        if (player == null) return null;
        ItemStack selected = AbstractWatchItem.findActivationWatch(player);
        if (selected.isEmpty()) return null;
        if (selected == player.getMainHandItem()) return inHand(InteractionHand.MAIN_HAND);
        if (selected == player.getOffhandItem()) return inHand(InteractionHand.OFF_HAND);
        for (var entry : Services.PLATFORM.getEquippedWatchSlots(player).entrySet()) {
            if (entry.getValue() == selected) return new WatchTarget("accessory:" + entry.getKey(), WatchPreferences.idOf(selected));
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i) == selected) return new WatchTarget("inventory:" + i, WatchPreferences.idOf(selected));
        }
        return forInterface(player);
    }

    public static WatchTarget forInterface(@Nullable Player player) {
        if (player == null) return null;
        String selectedSlot = null;
        ItemStack selected = ItemStack.EMPTY;
        int tier = -1;
        for (var entry : Services.PLATFORM.getEquippedWatchSlots(player).entrySet()) {
            if (entry.getValue().getItem() instanceof AbstractWatchItem watch && watch.getTier().getTierLevel() > tier) {
                selectedSlot = "accessory:" + entry.getKey();
                selected = entry.getValue();
                tier = watch.getTier().getTierLevel();
            }
        }
        if (selectedSlot != null) return new WatchTarget(selectedSlot, WatchPreferences.idOf(selected));
        selected = AbstractWatchItem.findActivationWatch(player);
        if (selected.isEmpty()) return null;
        if (selected == player.getMainHandItem()) return inHand(InteractionHand.MAIN_HAND);
        if (selected == player.getOffhandItem()) return inHand(InteractionHand.OFF_HAND);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i) == selected) return new WatchTarget("inventory:" + i, WatchPreferences.idOf(selected));
        }
        return null;
    }

    public ItemStack resolve(@Nullable Player player) {
        if (player == null) return ItemStack.EMPTY;
        ItemStack stack = ItemStack.EMPTY;
        if (slot.equals("main_hand")) stack = player.getMainHandItem();
        else if (slot.equals("off_hand")) stack = player.getOffhandItem();
        else if (slot.startsWith("accessory:")) stack = Services.PLATFORM.getEquippedWatchSlots(player).getOrDefault(slot.substring(10), ItemStack.EMPTY);
        else if (slot.startsWith("inventory:")) {
            try {
                int index = Integer.parseInt(slot.substring(10));
                if (index >= 0 && index < player.getInventory().getContainerSize()) stack = player.getInventory().getItem(index);
            } catch (NumberFormatException ignored) {}
        }
        if (!(stack.getItem() instanceof AbstractWatchItem) || (watchId != null && !watchId.equals(WatchPreferences.idOf(stack)))) return ItemStack.EMPTY;
        return stack;
    }

    public static WatchTarget read(FriendlyByteBuf buffer) {
        String slot = buffer.readUtf(256);
        return new WatchTarget(slot, buffer.readBoolean() ? buffer.readUUID() : null);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(slot, 256);
        buffer.writeBoolean(watchId != null);
        if (watchId != null) buffer.writeUUID(watchId);
    }
}
