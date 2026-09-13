package com.timestop.network;

import com.timestop.combat.DeadEyeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class DeadEyeStatePacket {
    private final boolean active;

    public DeadEyeStatePacket(boolean active) {
        this.active = active;
    }

    public DeadEyeStatePacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.active);
    }

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                DeadEyeManager.handleStateChange(player, this.active);
            }
        });
        context.setPacketHandled(true);
    }
}
