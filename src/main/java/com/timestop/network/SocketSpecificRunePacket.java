package com.timestop.network;

import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class SocketSpecificRunePacket {
    private final InteractionHand hand;
    private final int slotIndex; // -1 to eject
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

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeVarInt(this.slotIndex);
        buf.writeEnum(this.requestedType);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack watchStack = player.getItemInHand(this.hand);
                com.timestop.item.RuneSocketTransactions.apply(player, watchStack, this.slotIndex, this.requestedType);
                player.inventoryMenu.broadcastChanges();
                player.containerMenu.broadcastChanges();
                // Acknowledge rejected requests too, so the menu can accept the next operation.
                ModMessages.sendToPlayer(new SyncRuneSocketPacket(this.hand,
                        AbstractWatchItem.getSocketedRune(watchStack)), player);
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
