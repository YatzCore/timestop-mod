package com.timestop.network;

import com.timestop.combat.ChainTargetFilter;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CycleRuneModePacket {

    public CycleRuneModePacket() {}

    public CycleRuneModePacket(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack held = player.getMainHandItem();
                if (!(held.getItem() instanceof AbstractWatchItem)) {
                    held = player.getOffhandItem();
                }
                if (held.getItem() instanceof AbstractWatchItem) {
                    ItemStack socketedRune = AbstractWatchItem.getSocketedRune(held);
                    if (socketedRune.getItem() instanceof TemporalRuneItem) {
                        ChainTargetFilter current = TemporalRuneItem.getTargetFilter(socketedRune);
                        ChainTargetFilter next = current.next();
                        TemporalRuneItem.setTargetFilter(socketedRune, next);
                        AbstractWatchItem.setSocketedRune(held, socketedRune);
                    }
                }
            }
        });
        return true;
    }
}
