package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.combat.ChainTargetFilter;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class CycleRuneModePacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "cycle_rune_mode");

    public CycleRuneModePacket() {}

    public CycleRuneModePacket(FriendlyByteBuf buf) {}

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {}

    @Override
    public void handle(ServerPlayer player) {
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
    }
}
