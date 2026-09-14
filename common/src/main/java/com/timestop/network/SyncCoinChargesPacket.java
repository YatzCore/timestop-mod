package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.combat.CoinManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class SyncCoinChargesPacket implements IClientboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "sync_coin_charges");
    private final int charges;

    public SyncCoinChargesPacket(int charges) {
        this.charges = charges;
    }

    public SyncCoinChargesPacket(FriendlyByteBuf buf) {
        this.charges = buf.readVarInt();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.charges);
    }

    @Override
    public void handleClient() {
        CoinManager.clientCharges = this.charges;
    }
}
