package com.timestop.network;

import net.minecraft.server.level.ServerPlayer;

public interface IServerboundPacket extends IModPacket {
    void handle(ServerPlayer player);
}