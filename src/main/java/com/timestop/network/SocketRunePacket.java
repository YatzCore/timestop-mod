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

import java.util.function.Supplier;

public class SocketRunePacket {
    private final InteractionHand hand;

    public SocketRunePacket(InteractionHand hand) {
        this.hand = hand;
    }

    public SocketRunePacket(FriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack watchStack = player.getItemInHand(this.hand);
                int slot = -1;
                RuneType type = RuneType.BLANK;
                if (AbstractWatchItem.getSocketedRune(watchStack).isEmpty()) {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack candidate = player.getInventory().getItem(i);
                        if (!candidate.isEmpty() && candidate.getItem() instanceof TemporalRuneItem rune
                                && rune.getType() != RuneType.BLANK) {
                            slot = i;
                            type = rune.getType();
                            break;
                        }
                    }
                }
                com.timestop.item.RuneSocketTransactions.apply(player, watchStack, slot, type);
                player.inventoryMenu.broadcastChanges();
                player.containerMenu.broadcastChanges();
                ModMessages.sendToPlayer(new SyncRuneSocketPacket(this.hand,
                        AbstractWatchItem.getSocketedRune(watchStack)), player);
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
