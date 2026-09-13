package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class SocketRunePacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "socket_rune");
    private final InteractionHand hand;

    public SocketRunePacket(InteractionHand hand) {
        this.hand = hand;
    }

    public SocketRunePacket(FriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            ItemStack watchStack = player.getItemInHand(this.hand);
            int slot = -1;
            RuneType type = RuneType.BLANK;

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() != RuneType.BLANK) {
                    slot = i;
                    type = runeItem.getRuneType();
                    break;
                }
            }

            com.timestop.item.RuneSocketTransactions.apply(player, watchStack, slot, type);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            ModMessages.sendToPlayer(new SyncRuneSocketPacket(this.hand, AbstractWatchItem.getSocketedRune(watchStack)), player);
        }
    }
}
