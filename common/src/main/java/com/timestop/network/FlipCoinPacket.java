package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.item.ChronoCoinItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class FlipCoinPacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "flip_coin");

    public FlipCoinPacket() {}

    public FlipCoinPacket(FriendlyByteBuf buf) {}

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {}

    @Override
    public void handle(ServerPlayer player) {
        if (player != null && player.isAlive()) {
            if (!com.timestop.combat.RuneManager.hasRune(player, com.timestop.item.rune.RuneType.RICOSHOT)) {
                return;
            }
            if (com.timestop.combat.CoinManager.consumeCharge(player)) {
                ChronoCoinItem.flipCoinFromInventory(player);
            }
        }
    }
}
