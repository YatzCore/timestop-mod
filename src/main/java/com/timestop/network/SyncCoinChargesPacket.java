package com.timestop.network;

import com.timestop.combat.CoinManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                CoinManager.clientCharges = this.charges;
            });
        });
        return true;
    }
}
