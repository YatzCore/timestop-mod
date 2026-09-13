package com.timestop.network;

import com.timestop.item.ChronoCoinItem;
import com.timestop.item.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class FlipCoinPacket {

    public FlipCoinPacket() {}

    public FlipCoinPacket(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public void handle(CustomPayloadEvent.Context context) {       context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.isAlive()) {
                if (!com.timestop.combat.RuneManager.hasRune(player, com.timestop.item.rune.RuneType.RICOSHOT)) {
                    return; // Rune not socketed in a clock
                }

                if (com.timestop.combat.CoinManager.consumeCharge(player)) {
                    ChronoCoinItem.flipCoinFromInventory(player);
                }
            }
        });
        context.setPacketHandled(true);
    }
}