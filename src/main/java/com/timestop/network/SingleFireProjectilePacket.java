package com.timestop.network;

import com.timestop.combat.OrbitalProjectileManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class SingleFireProjectilePacket {

    public SingleFireProjectilePacket() {}

    public SingleFireProjectilePacket(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.isAlive()) {
                OrbitalProjectileManager.launchSingleProjectile(player);
            }
        });
        context.setPacketHandled(true);
    }
}
