package com.timestop.network;

import com.timestop.core.TimeMode;
import com.timestop.item.ChronosWatchItem;
import com.timestop.item.CreativeWatchItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SelectTimeModePacket {
    private final TimeMode mode;
    private final InteractionHand hand;

    public SelectTimeModePacket(TimeMode mode, InteractionHand hand) {
        this.mode = mode;
        this.hand = hand;
    }

    public SelectTimeModePacket(FriendlyByteBuf buf) {
        this.mode = buf.readEnum(TimeMode.class);
        this.hand = buf.readEnum(InteractionHand.class);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.mode);
        buf.writeEnum(this.hand);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack stack = player.getItemInHand(this.hand);
                if (stack.getItem() instanceof com.timestop.item.AbstractWatchItem watch) {
                    if (watch.getTier().isModeUnlocked(this.mode)) {
                        com.timestop.item.AbstractWatchItem.setMode(stack, this.mode);

                        // If this player is currently the initiator of global time stop, dynamically update mode!
                        if (com.timestop.core.TimeStopManager.isGlobalTimeStopActive() && player.getUUID().equals(com.timestop.core.TimeStopManager.getInitiatorUuid())) {
                            com.timestop.core.TimeStopManager.setMode(this.mode);
                        }

                        // If this player currently has an active bubble, dynamically update mode!
                        com.timestop.core.TemporalBubble bubble = com.timestop.core.TemporalBubbleManager.getPlayerBubble(player.getUUID());
                        if (bubble != null) {
                            bubble.setMode(this.mode);
                            com.timestop.core.TemporalBubbleManager.syncBubbleToClients(bubble);
                        }

                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
                    }
                }
            }
        });
        return true;
    }
}
