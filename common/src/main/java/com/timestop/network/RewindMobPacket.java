package com.timestop.network;

import com.timestop.TimeStopMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RewindMobPacket(ResourceLocation dimension, UUID entity) implements IClientboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "rewind_mob");

    public RewindMobPacket(FriendlyByteBuf buf) {
        this(buf.readResourceLocation(), buf.readUUID());
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeUUID(entity);
    }

    @Override
    public void handleClient() {
        com.timestop.client.RewindMobAnimation.begin(this);
    }
}
