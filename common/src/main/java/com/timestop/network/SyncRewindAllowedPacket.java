package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.TimeStopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class SyncRewindAllowedPacket implements IClientboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "sync_rewind_allowed");

    private final boolean allowed;

    public SyncRewindAllowedPacket(boolean allowed) {
        this.allowed = allowed;
    }

    public SyncRewindAllowedPacket(FriendlyByteBuf buf) {
        this.allowed = buf.readBoolean();
    }

    public boolean isAllowed() {
        return allowed;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.allowed);
    }

    @Override
    public void handleClient() {
        TimeStopManager.setClientRewindAllowed(this.allowed);
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.timestop.client.gui.TimeModeSelectionScreen screen) {
            screen.refreshState();
        }
    }
}
