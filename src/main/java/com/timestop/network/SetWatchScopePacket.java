package com.timestop.network;

import com.timestop.item.AbstractWatchItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetWatchScopePacket {
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

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.hand == InteractionHand.MAIN_HAND);
        buf.writeBoolean(this.globalScope);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack stack = player.getItemInHand(this.hand);
                if (stack.getItem() instanceof AbstractWatchItem) {
                    AbstractWatchItem.setGlobalScope(stack, this.globalScope);
                }
            }
        });
        return true;
    }
}