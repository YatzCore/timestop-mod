package com.timestop.network;

import com.timestop.TimeStopMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class SuperhotSyncPacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "superhot_sync");
    private final float activity;

    public SuperhotSyncPacket(float activity) {
        this.activity = activity;
    }

    public SuperhotSyncPacket(FriendlyByteBuf buf) {
        this.activity = buf.readFloat();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(this.activity);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            com.timestop.core.SuperhotActivityManager.report(player, activity);
        }
    }
}
