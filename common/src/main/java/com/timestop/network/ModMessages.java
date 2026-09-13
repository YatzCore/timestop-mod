package com.timestop.network;

import com.timestop.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class ModMessages {
    public static <MSG> void sendToServer(MSG message) {
        Services.NETWORK.sendToServer(message);
    }

    public static <MSG> void sendToClients(MSG message) {
        Services.NETWORK.sendToClients(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        Services.NETWORK.sendToPlayer(message, player);
    }

    public static <MSG> void sendToTracking(MSG message, Entity entity) {
        Services.NETWORK.sendToTracking(message, entity);
    }
}