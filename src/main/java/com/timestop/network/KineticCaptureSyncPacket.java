package com.timestop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;
import java.util.UUID;

public record KineticCaptureSyncPacket(int entityId, UUID owner, boolean captured, net.minecraft.world.phys.Vec3 position) {
    public KineticCaptureSyncPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUUID(), buf.readBoolean(), new net.minecraft.world.phys.Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUUID(owner);
        buf.writeBoolean(captured);
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
    }

    public void handle(CustomPayloadEvent.Context context) {       context.enqueueWork(() -> com.timestop.client.ClientPacketHandlers.syncKineticCapture(entityId, owner, captured, position));
        context.setPacketHandled(true);
    }
}
