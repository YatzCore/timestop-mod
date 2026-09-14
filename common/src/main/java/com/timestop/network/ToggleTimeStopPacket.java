package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.AbstractWatchItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ToggleTimeStopPacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "toggle_time_stop");

    public ToggleTimeStopPacket() {}

    public ToggleTimeStopPacket(FriendlyByteBuf buf) {}

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {}

    @Override
    public void handle(ServerPlayer player) {
        if (player != null && player.level() instanceof ServerLevel serverLevel) {
            boolean isOmnipotentActive = TimeStopManager.isGlobalTimeStopActive() || com.timestop.core.TemporalBubbleManager.hasCreativeBubble();
            if (isOmnipotentActive) {
                if (!player.isCreative() && !player.hasPermissions(2) && !player.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                    player.displayClientMessage(Component.literal("The temporal continuum is locked by an almighty force (Admin/Creative Clock)!").withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                }
            }

            com.timestop.core.TemporalBubble existing = com.timestop.core.TemporalBubbleManager.getPlayerBubble(player.getUUID());
            if (existing != null) {
                com.timestop.core.TemporalBubbleManager.stopBubble(serverLevel, existing);
                return;
            }

            if (TimeStopManager.isGlobalTimeStopActive()) {
                if (player.isCreative() || player.hasPermissions(2) || (TimeStopManager.getInitiatorUuid() != null && TimeStopManager.getInitiatorUuid().equals(player.getUUID()))) {
                    TimeStopManager.resumeTime(serverLevel);
                } else {
                    player.displayClientMessage(Component.literal("The global temporal field is locked by an almighty force (Creative/Command)!").withStyle(net.minecraft.ChatFormatting.RED), true);
                }
                return;
            }

            ItemStack watchStack = AbstractWatchItem.findActivationWatch(player);

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
    }
}
