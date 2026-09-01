package com.timestop.network;

import com.timestop.core.ClientTimeStopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SuperhotActivitySyncPacket {
    private final float activity;

    public SuperhotActivitySyncPacket(float activity) {
        this.activity = activity;
    }

    public SuperhotActivitySyncPacket(FriendlyByteBuf buf) {
        this.activity = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(this.activity);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ClientTimeStopManager.setServerSyncedSuperhotActivity(this.activity);
        });
        return true;
    }
}