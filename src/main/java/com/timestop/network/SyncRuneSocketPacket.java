package com.timestop.network;

import com.timestop.client.gui.TimeModeSelectionScreen;
import com.timestop.item.AbstractWatchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
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
        this.socketedRune = socketedRune;
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
        context.enqueueWork(() -> {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                ItemStack watchStack = player.getItemInHand(this.hand);
                if (watchStack.getItem() instanceof AbstractWatchItem) {
                    AbstractWatchItem.setSocketedRune(watchStack, this.socketedRune);
                }

                if (Minecraft.getInstance().screen instanceof TimeModeSelectionScreen screen) {
                    screen.onServerSync(this.socketedRune);
                }
            }
        });
        return true;
    }
}
