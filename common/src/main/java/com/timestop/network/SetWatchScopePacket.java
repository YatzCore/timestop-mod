package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class SetWatchScopePacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "set_watch_scope");
    private final InteractionHand hand;
    private final boolean globalScope;

    public SetWatchScopePacket(InteractionHand hand, boolean globalScope) {
        this.hand = hand;
        this.globalScope = globalScope;
    }

    public SetWatchScopePacket(FriendlyByteBuf buf) {
        this.hand = buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        this.globalScope = buf.readBoolean();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.hand == InteractionHand.MAIN_HAND);
        buf.writeBoolean(this.globalScope);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            ItemStack stack = player.getItemInHand(this.hand);
            if (stack.getItem() instanceof AbstractWatchItem watch) {
                boolean allowed = player.isCreative() || watch.getTier() == WatchTier.CREATIVE;
                if (this.globalScope && !allowed) {
                    AbstractWatchItem.setGlobalScope(stack, false);
                    return;
                }
                AbstractWatchItem.setGlobalScope(stack, this.globalScope);
            }
        }
    }
}