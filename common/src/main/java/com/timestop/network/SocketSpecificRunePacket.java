package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.RuneType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class SocketSpecificRunePacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "socket_specific_rune");
    private final InteractionHand hand;
    private final int slotIndex;
    private final RuneType requestedType;

    public SocketSpecificRunePacket(InteractionHand hand, int slotIndex, RuneType requestedType) {
        this.hand = hand;
        this.slotIndex = slotIndex;
        this.requestedType = requestedType != null ? requestedType : RuneType.BLANK;
    }

    public SocketSpecificRunePacket(FriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
        this.slotIndex = buf.readVarInt();
        this.requestedType = buf.readEnum(RuneType.class);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeVarInt(this.slotIndex);
        buf.writeEnum(this.requestedType);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            ItemStack watchStack = player.getItemInHand(this.hand);
            com.timestop.item.RuneSocketTransactions.apply(player, watchStack, this.slotIndex, this.requestedType);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            ModMessages.sendToPlayer(new SyncRuneSocketPacket(this.hand, AbstractWatchItem.getSocketedRune(watchStack)), player);
        }
    }
}
