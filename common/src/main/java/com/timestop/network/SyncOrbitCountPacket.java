package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.client.CapturedProjectilesOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class SyncOrbitCountPacket implements IClientboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "sync_orbit_count");
    private final int count;

    public SyncOrbitCountPacket(int count) {
        this.count = count;
    }

    public SyncOrbitCountPacket(FriendlyByteBuf buf) {
        this.count = buf.readVarInt();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.count);
    }

    @Override
    public void handleClient() {
        CapturedProjectilesOverlay.setOrbitCount(this.count);
    }
}
