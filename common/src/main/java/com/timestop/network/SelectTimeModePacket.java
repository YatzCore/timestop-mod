package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.TimeMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.timestop.item.WatchTarget;
import net.minecraft.world.item.ItemStack;

public class SelectTimeModePacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "select_time_mode");
    private final TimeMode selectedMode;
    private final WatchTarget target;

    public SelectTimeModePacket(TimeMode selectedMode, InteractionHand hand) {
        this(selectedMode, WatchTarget.inHand(hand));
    }

    public SelectTimeModePacket(TimeMode selectedMode, WatchTarget target) {
        this.selectedMode = selectedMode;
        this.target = target;
    }

    public SelectTimeModePacket(FriendlyByteBuf buf) {
        this.selectedMode = buf.readEnum(TimeMode.class);
        this.target = WatchTarget.read(buf);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.selectedMode);
        this.target.write(buf);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            ItemStack stack = this.target.resolve(player);
            if (stack.getItem() instanceof com.timestop.item.AbstractWatchItem watch) {
                if (watch.getTier().isModeUnlocked(this.selectedMode)) {
                    com.timestop.item.AbstractWatchItem.setMode(stack, this.selectedMode);
                }
            }
        }
    }
}
