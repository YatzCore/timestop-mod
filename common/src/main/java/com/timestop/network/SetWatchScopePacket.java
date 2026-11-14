package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.timestop.item.WatchTarget;
import net.minecraft.world.item.ItemStack;

public class SetWatchScopePacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "set_watch_scope");
    private final WatchTarget target;
    private final boolean globalScope;

    public SetWatchScopePacket(InteractionHand hand, boolean globalScope) {
        this(WatchTarget.inHand(hand), globalScope);
    }

    public SetWatchScopePacket(WatchTarget target, boolean globalScope) {
        this.target = target;
        this.globalScope = globalScope;
    }

    public SetWatchScopePacket(FriendlyByteBuf buf) {
        this.target = WatchTarget.read(buf);
        this.globalScope = buf.readBoolean();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        this.target.write(buf);
        buf.writeBoolean(this.globalScope);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            ItemStack stack = this.target.resolve(player);
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