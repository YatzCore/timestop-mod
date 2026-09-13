package com.timestop.network;

import com.timestop.TimeStopMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class SyncRuneSocketPacket implements IClientboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "sync_rune_socket");
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

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeItem(this.socketedRune);
    }

    @Override
    public void handleClient() {
        com.timestop.client.ClientPacketHandlers.syncRune(hand, socketedRune);
    }
}
