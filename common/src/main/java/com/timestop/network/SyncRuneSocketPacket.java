package com.timestop.network;

import com.timestop.TimeStopMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import com.timestop.item.WatchTarget;
import net.minecraft.world.item.ItemStack;

public class SyncRuneSocketPacket implements IClientboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "sync_rune_socket");
    private final WatchTarget target;
    private final ItemStack socketedRune;

    public SyncRuneSocketPacket(InteractionHand hand, ItemStack socketedRune) {
        this(WatchTarget.inHand(hand), socketedRune);
    }

    public SyncRuneSocketPacket(WatchTarget target, ItemStack socketedRune) {
        this.target = target;
        this.socketedRune = socketedRune.copy();
    }

    public SyncRuneSocketPacket(FriendlyByteBuf buf) {
        this.target = WatchTarget.read(buf);
        this.socketedRune = buf.readItem();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        this.target.write(buf);
        buf.writeItem(this.socketedRune);
    }

    @Override
    public void handleClient() {
        com.timestop.client.ClientPacketHandlers.syncRune(target, socketedRune);
    }
}
