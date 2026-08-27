package com.timestop.network;

import com.timestop.TimeStopMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TimeStopMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        INSTANCE.messageBuilder(TimeStopSyncPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(TimeStopSyncPacket::new)
                .encoder(TimeStopSyncPacket::toBytes)
                .consumerMainThread(TimeStopSyncPacket::handle)
                .add();

        INSTANCE.messageBuilder(ToggleTimeStopPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ToggleTimeStopPacket::new)
                .encoder(ToggleTimeStopPacket::toBytes)
                .consumerMainThread(ToggleTimeStopPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToClients(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }
}
