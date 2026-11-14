package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.RuneType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.timestop.item.WatchTarget;
import net.minecraft.world.item.ItemStack;

public class SocketSpecificRunePacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "socket_specific_rune");
    private final WatchTarget target;
    private final int slotIndex;
    private final RuneType requestedType;

    public SocketSpecificRunePacket(InteractionHand hand, int slotIndex, RuneType requestedType) {
        this(WatchTarget.inHand(hand), slotIndex, requestedType);
    }

    public SocketSpecificRunePacket(WatchTarget target, int slotIndex, RuneType requestedType) {
        this.target = target;
        this.slotIndex = slotIndex;
        this.requestedType = requestedType != null ? requestedType : RuneType.BLANK;
    }

    public SocketSpecificRunePacket(FriendlyByteBuf buf) {
        this.target = WatchTarget.read(buf);
        this.slotIndex = buf.readVarInt();
        this.requestedType = buf.readEnum(RuneType.class);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        this.target.write(buf);
        buf.writeVarInt(this.slotIndex);
        buf.writeEnum(this.requestedType);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            ItemStack watchStack = this.target.resolve(player);
            com.timestop.item.RuneSocketTransactions.apply(player, watchStack, this.slotIndex, this.requestedType);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            ModMessages.sendToPlayer(new SyncRuneSocketPacket(this.target, AbstractWatchItem.getSocketedRune(watchStack)), player);
        }
    }
}
