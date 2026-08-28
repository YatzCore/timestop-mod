package com.timestop.network;

import com.timestop.client.CapturedProjectilesOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                CapturedProjectilesOverlay.setOrbitCount(this.count);
            });
        });
        return true;
    }
}
