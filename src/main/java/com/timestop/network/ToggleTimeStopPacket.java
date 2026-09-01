package com.timestop.network;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.AbstractWatchItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleTimeStopPacket {
    public ToggleTimeStopPacket() {
    }

    public ToggleTimeStopPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.level() instanceof ServerLevel serverLevel) {
                boolean isOmnipotentActive = TimeStopManager.isGlobalTimeStopActive() || com.timestop.core.TemporalBubbleManager.hasCreativeBubble();
                if (isOmnipotentActive) {
                    if (!player.isCreative() && !player.hasPermissions(2) && !player.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                        player.displayClientMessage(Component.literal("The temporal continuum is locked by an almighty force (Admin/Creative Clock)!").withStyle(net.minecraft.ChatFormatting.RED), true);
                        return;
                    }
                }

                // If this player already has an active bubble, pressing V stops their own bubble!
                com.timestop.core.TemporalBubble existing = com.timestop.core.TemporalBubbleManager.getPlayerBubble(player.getUUID());
                if (existing != null) {
                    com.timestop.core.TemporalBubbleManager.stopBubble(serverLevel, existing);
                    return;
                }

                // If global time stop is active, check permissions
                if (TimeStopManager.isGlobalTimeStopActive()) {
                    if (player.isCreative() || player.hasPermissions(2) || (TimeStopManager.getInitiatorUuid() != null && TimeStopManager.getInitiatorUuid().equals(player.getUUID()))) {
                        TimeStopManager.resumeTime(serverLevel);
                    } else {
                        player.displayClientMessage(Component.literal("The global temporal field is locked by an almighty force (Creative/Command)!").withStyle(net.minecraft.ChatFormatting.RED), true);
                    }
                    return;
                }

                // If time is NOT active, V key starts the mode from the best equipped watch
                ItemStack watchStack = findBestWatch(player);

                if (player.isCreative() && watchStack.isEmpty()) {
                    TimeStopManager.startTimeStop(serverLevel, player, 0, TimeMode.TIME_STOP);
                    return;
                }

                if (!watchStack.isEmpty() && watchStack.getItem() instanceof AbstractWatchItem watchItem) {
                    if (player.getCooldowns().isOnCooldown(watchItem)) {
                        player.displayClientMessage(Component.literal("Your " + watchItem.getTier().getDisplayName() + " is recharging!").withStyle(net.minecraft.ChatFormatting.RED), true);
                        return;
                    }

                    TimeMode mode = AbstractWatchItem.getMode(watchStack);
                    if (!watchItem.getTier().isModeUnlocked(mode)) {
                        mode = watchItem.getTier().getUnlockedModes().iterator().next();
                        AbstractWatchItem.setMode(watchStack, mode);
                    }

                    int duration = (player.isCreative() || watchItem.getTier().getDurationTicks() == 0) ? 0 : watchItem.getTier().getDurationTicks();
                    TimeStopManager.startTimeStop(serverLevel, player, duration, mode);
                } else {
                    player.displayClientMessage(Component.literal("You need a Chronos Watch to control time!").withStyle(net.minecraft.ChatFormatting.RED), true);
                }
            }
        });
        return true;
    }

    private static ItemStack findBestWatch(ServerPlayer player) {
        // Priority 1: Main Hand
        if (player.getMainHandItem().getItem() instanceof AbstractWatchItem) {
            return player.getMainHandItem();
        }
        // Priority 2: Off-Hand
        if (player.getOffhandItem().getItem() instanceof AbstractWatchItem) {
            return player.getOffhandItem();
        }
        // Priority 3: Search Inventory for highest tier watch
        ItemStack best = ItemStack.EMPTY;
        int bestTier = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractWatchItem watch) {
                if (watch.getTier().getTierLevel() > bestTier) {
                    best = stack;
                    bestTier = watch.getTier().getTierLevel();
                }
            }
        }
        return best;
    }
}
