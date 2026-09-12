package com.timestop.network;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SuperhotSyncPacket {
    private final float activity;

    public SuperhotSyncPacket(float activity) {
        this.activity = activity;
    }

    public SuperhotSyncPacket(FriendlyByteBuf buf) {
        this.activity = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(this.activity);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                com.timestop.core.SuperhotActivityManager.report(player, activity);
            }
        });
        return true;
    }
}
