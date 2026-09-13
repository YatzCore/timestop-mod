package com.timestop.network;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;


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

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                com.timestop.core.SuperhotActivityManager.report(player, activity);
            }
        });
        context.setPacketHandled(true);
    }
}
