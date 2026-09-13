package com.timestop.network;

import com.timestop.combat.TranspositionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class TranspositionSwapPacket {

    private final boolean isSneaking;

    public TranspositionSwapPacket(boolean isSneaking) {
        this.isSneaking = isSneaking;
    }

    public TranspositionSwapPacket(FriendlyByteBuf buf) {
        this.isSneaking = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.isSneaking);
    }

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.isAlive()) {
                TranspositionManager.executeSwap(player, this.isSneaking);
            }
        });
        context.setPacketHandled(true);
    }
}
