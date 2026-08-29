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
            if (player != null && TimeStopManager.isGlobalTimeStopped() && TimeStopManager.getCurrentMode() == TimeMode.SUPERHOT) {
                if (player.isCreative() || player.hasPermissions(2) || (TimeStopManager.getInitiatorUuid() != null && TimeStopManager.getInitiatorUuid().equals(player.getUUID()))) {
                    // Dynamically map activity (0.0 to 1.0) to tick ms (250ms down to 50ms)
                    long tickMs = (long) (250.0F - (Math.max(0.0F, Math.min(1.0F, this.activity)) * 200.0F));
                    TimeStopManager.setSuperhotTickMs(tickMs);
                }
            }
        });
        return true;
    }
}
