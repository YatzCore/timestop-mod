package com.timestop.network;

import com.timestop.client.CapturedProjectilesOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class SyncOrbitCountPacket {

    private final int count;

    public SyncOrbitCountPacket(int count) {
        this.count = count;
    }

    public SyncOrbitCountPacket(FriendlyByteBuf buf) {
        this.count = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.count);
    }

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                CapturedProjectilesOverlay.setOrbitCount(this.count);
            });
        });
        context.setPacketHandled(true);
    }
}
