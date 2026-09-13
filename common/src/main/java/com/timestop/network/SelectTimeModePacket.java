package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.TimeMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class SelectTimeModePacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "select_time_mode");
    private final TimeMode selectedMode;
    private final InteractionHand hand;

    public SelectTimeModePacket(TimeMode selectedMode, InteractionHand hand) {
        this.selectedMode = selectedMode;
        this.hand = hand;
    }

    public SelectTimeModePacket(FriendlyByteBuf buf) {
        this.selectedMode = buf.readEnum(TimeMode.class);
        this.hand = buf.readEnum(InteractionHand.class);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.selectedMode);
        buf.writeEnum(this.hand);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            ItemStack stack = player.getItemInHand(this.hand);
            if (stack.getItem() instanceof com.timestop.item.AbstractWatchItem watch) {
                if (watch.getTier().isModeUnlocked(this.selectedMode)) {
                    com.timestop.item.AbstractWatchItem.setMode(stack, this.selectedMode);
                }
            }
        }
    }
}
