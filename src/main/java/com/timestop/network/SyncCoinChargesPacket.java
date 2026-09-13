package com.timestop.network;

import com.timestop.combat.CoinManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class SyncCoinChargesPacket {

    private final int charges;

    public SyncCoinChargesPacket(int charges) {
        this.charges = charges;
    }

    public SyncCoinChargesPacket(FriendlyByteBuf buf) {
        this.charges = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.charges);
    }

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                CoinManager.clientCharges = this.charges;
            });
        });
        context.setPacketHandled(true);
    }
}
