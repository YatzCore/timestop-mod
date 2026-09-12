package com.timestop.network;

import com.timestop.core.ClientTimeStopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SuperhotActivitySyncPacket {
    private final float activity;
    private final java.util.UUID bubbleId;

    public SuperhotActivitySyncPacket(float activity) {
        this(null, activity);
    }

    public SuperhotActivitySyncPacket(java.util.UUID bubbleId, float activity) {
        this.bubbleId = bubbleId;
        this.activity = activity;
    }

    public SuperhotActivitySyncPacket(FriendlyByteBuf buf) {
        this.bubbleId = buf.readBoolean() ? buf.readUUID() : null;
        this.activity = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(bubbleId != null);
        if (bubbleId != null) buf.writeUUID(bubbleId);
        buf.writeFloat(this.activity);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (bubbleId == null) ClientTimeStopManager.setServerSyncedSuperhotActivity(this.activity);
            else com.timestop.core.ClientBubbleManager.setSuperhotActivity(bubbleId, activity);
        });
        return true;
    }
}