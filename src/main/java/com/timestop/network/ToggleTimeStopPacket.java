package com.timestop.network;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.ChronosWatchItem;
import com.timestop.item.CreativeWatchItem;
import com.timestop.item.ModItems;
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
                // If time is active, V key ONLY stops it!
                if (TimeStopManager.isTimeStopped(serverLevel)) {
                    TimeStopManager.resumeTime(serverLevel);
                    return;
                }

                // If time is NOT active, V key starts the currently selected mode on the watch
                ItemStack creativeWatch = findItem(player, ModItems.CREATIVE_WATCH.get().getDefaultInstance());
                ItemStack survivalWatch = findItem(player, ModItems.CHRONOS_WATCH.get().getDefaultInstance());

                if (player.isCreative() || !creativeWatch.isEmpty()) {
                    TimeMode mode = !creativeWatch.isEmpty() ? CreativeWatchItem.getMode(creativeWatch) : TimeMode.TIME_STOP;
                    TimeStopManager.startTimeStop(serverLevel, player, 0, mode); // Unlimited
                } else if (!survivalWatch.isEmpty()) {
                    if (player.getCooldowns().isOnCooldown(ModItems.CHRONOS_WATCH.get())) {
                        player.displayClientMessage(Component.literal("§cYour Chronos Watch is recharging!"), true);
                        return;
                    }
                    TimeMode mode = ChronosWatchItem.getMode(survivalWatch);
                    TimeStopManager.startTimeStop(serverLevel, player, ChronosWatchItem.DEFAULT_SURVIVAL_TICKS, mode);
                } else {
                    player.displayClientMessage(Component.literal("§cYou need a Chronos Pocket Watch to control time!"), true);
                }
            }
        });
        return true;
    }

    private static ItemStack findItem(ServerPlayer player, ItemStack target) {
        if (ItemStack.isSameItem(player.getMainHandItem(), target)) {
            return player.getMainHandItem();
        }
        if (ItemStack.isSameItem(player.getOffhandItem(), target)) {
            return player.getOffhandItem();
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItem(stack, target)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
