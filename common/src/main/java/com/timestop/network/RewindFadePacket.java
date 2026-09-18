package com.timestop.network;

import com.timestop.TimeStopMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record RewindFadePacket(boolean playRamielSound) implements IClientboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "rewind_fade");

    public RewindFadePacket(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.playRamielSound);
    }

    @Override
    public void handleClient() {
        com.timestop.client.RewindFadeOverlay.start(this.playRamielSound);
    }
}
