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
                com.timestop.core.TemporalBubble bubble = com.timestop.core.TemporalBubbleManager.getPlayerBubble(player.getUUID());
                if (bubble == null) {
                    bubble = com.timestop.core.TemporalBubbleManager.getDominantBubble(player.level().dimension(), player.position());
                }
                if (bubble != null && bubble.getMode() == TimeMode.SUPERHOT) {
                    bubble.setPlayerActivity(player.getUUID(), this.activity);
                    float collectiveActivity = bubble.getSuperhotActivity();
                    long tickMs = (long) (500.0F - (Math.max(0.0F, Math.min(1.0F, collectiveActivity)) * 450.0F));
                    TimeStopManager.setSuperhotTickMs(tickMs);
                    ModMessages.sendToClients(new SuperhotActivitySyncPacket(collectiveActivity));
                } else if (TimeStopManager.isGlobalTimeStopped() && TimeStopManager.getCurrentMode() == TimeMode.SUPERHOT) {
                    long tickMs = (long) (500.0F - (Math.max(0.0F, Math.min(1.0F, this.activity)) * 450.0F));
                    TimeStopManager.setSuperhotTickMs(tickMs);
                    ModMessages.sendToClients(new SuperhotActivitySyncPacket(this.activity));
                }
            }
        });
        return true;
    }
}
