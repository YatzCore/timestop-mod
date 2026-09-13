package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.ClientTimeStopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class SuperhotActivitySyncPacket implements IClientboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "superhot_activity_sync");
    private final UUID bubbleId;
    private final float activity;

    public SuperhotActivitySyncPacket(float activity) {
        this(null, activity);
    }

    public SuperhotActivitySyncPacket(UUID bubbleId, float activity) {
        this.bubbleId = bubbleId;
        this.activity = activity;
    }

    public SuperhotActivitySyncPacket(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            this.bubbleId = buf.readUUID();
        } else {
            this.bubbleId = null;
        }
        this.activity = buf.readFloat();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.bubbleId != null);
        if (this.bubbleId != null) {
            buf.writeUUID(this.bubbleId);
        }
        buf.writeFloat(this.activity);
    }

    @Override
    public void handleClient() {
        if (bubbleId == null) ClientTimeStopManager.setServerSyncedSuperhotActivity(this.activity);
        else com.timestop.core.ClientBubbleManager.setSuperhotActivity(bubbleId, activity);
    }
}
