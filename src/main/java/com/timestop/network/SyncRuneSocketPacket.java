package com.timestop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client packet to synchronize the socketed rune in a watch and update open GUIs.
 */
public class SyncRuneSocketPacket {
    private final InteractionHand hand;
    private final ItemStack socketedRune;

    public SyncRuneSocketPacket(InteractionHand hand, ItemStack socketedRune) {
        this.hand = hand;
        this.socketedRune = socketedRune.copy();
    }

    public SyncRuneSocketPacket(FriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
        this.socketedRune = buf.readItem();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeItem(this.socketedRune);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> com.timestop.client.ClientPacketHandlers.syncRune(hand, socketedRune));
        return true;
    }
}
