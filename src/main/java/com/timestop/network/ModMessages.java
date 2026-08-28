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

        INSTANCE.messageBuilder(SuperhotSyncPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SuperhotSyncPacket::new)
                .encoder(SuperhotSyncPacket::toBytes)
                .consumerMainThread(SuperhotSyncPacket::handle)
                .add();

        INSTANCE.messageBuilder(SlapProjectilePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SlapProjectilePacket::new)
                .encoder(SlapProjectilePacket::toBytes)
                .consumerMainThread(SlapProjectilePacket::handle)
                .add();

        INSTANCE.messageBuilder(KineticBlockPunchPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(KineticBlockPunchPacket::new)
                .encoder(KineticBlockPunchPacket::toBytes)
                .consumerMainThread(KineticBlockPunchPacket::handle)
                .add();

        INSTANCE.messageBuilder(SelectTimeModePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SelectTimeModePacket::new)
                .encoder(SelectTimeModePacket::toBytes)
                .consumerMainThread(SelectTimeModePacket::handle)
                .add();

        INSTANCE.messageBuilder(SnatchProjectilePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SnatchProjectilePacket::new)
                .encoder(SnatchProjectilePacket::toBytes)
                .consumerMainThread(SnatchProjectilePacket::handle)
                .add();

        INSTANCE.messageBuilder(SocketRunePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SocketRunePacket::new)
                .encoder(SocketRunePacket::toBytes)
                .consumerMainThread(SocketRunePacket::handle)
                .add();

        INSTANCE.messageBuilder(SocketSpecificRunePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SocketSpecificRunePacket::new)
                .encoder(SocketSpecificRunePacket::toBytes)
                .consumerMainThread(SocketSpecificRunePacket::handle)
                .add();

        INSTANCE.messageBuilder(DeadEyeStatePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(DeadEyeStatePacket::new)
                .encoder(DeadEyeStatePacket::toBytes)
                .consumerMainThread(DeadEyeStatePacket::handle)
                .add();

        INSTANCE.messageBuilder(DeadEyeExecutePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(DeadEyeExecutePacket::new)
                .encoder(DeadEyeExecutePacket::toBytes)
                .consumerMainThread(DeadEyeExecutePacket::handle)
                .add();

        INSTANCE.messageBuilder(CycleRuneModePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(CycleRuneModePacket::new)
                .encoder(CycleRuneModePacket::toBytes)
                .consumerMainThread(CycleRuneModePacket::handle)
                .add();

        INSTANCE.messageBuilder(ReleaseProjectilesPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ReleaseProjectilesPacket::new)
                .encoder(ReleaseProjectilesPacket::toBytes)
                .consumerMainThread(ReleaseProjectilesPacket::handle)
                .add();

        INSTANCE.messageBuilder(SyncOrbitCountPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncOrbitCountPacket::new)
                .encoder(SyncOrbitCountPacket::toBytes)
                .consumerMainThread(SyncOrbitCountPacket::handle)
                .add();

        INSTANCE.messageBuilder(SingleFireProjectilePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SingleFireProjectilePacket::new)
                .encoder(SingleFireProjectilePacket::toBytes)
                .consumerMainThread(SingleFireProjectilePacket::handle)
                .add();

        INSTANCE.messageBuilder(TranspositionSwapPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(TranspositionSwapPacket::new)
                .encoder(TranspositionSwapPacket::toBytes)
                .consumerMainThread(TranspositionSwapPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToClients(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }
}
