package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.timestop.item.WatchTarget;

public record SetWatchRadiusPacket(WatchTarget target, int radius) implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "set_watch_radius");

    public SetWatchRadiusPacket(InteractionHand hand, int radius) {
        this(WatchTarget.inHand(hand), radius);
    }

    public SetWatchRadiusPacket(FriendlyByteBuf buffer) {
        this(WatchTarget.read(buffer), buffer.readVarInt());
    }

    @Override public ResourceLocation getId() { return ID; }
    @Override public void toBytes(FriendlyByteBuf buffer) {
        target.write(buffer);
        buffer.writeVarInt(radius);
    }
    @Override public void handle(ServerPlayer player) {
        if (player == null) return;
        var stack = target.resolve(player);
        if (stack.getItem() instanceof AbstractWatchItem watch && watch.getTier() == WatchTier.CREATIVE) {
            AbstractWatchItem.setBubbleRadius(stack, radius);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }
}
