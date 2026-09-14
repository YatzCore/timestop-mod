package com.timestop.network;

public interface IClientboundPacket extends IModPacket {
    void handleClient();
}