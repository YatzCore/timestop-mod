package com.timestop.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public interface INetworkHelper {
    void sendToServer(Object message);
    void sendToClients(Object message);
    void sendToPlayer(Object message, ServerPlayer player);
    void sendToTracking(Object message, Entity entity);
}