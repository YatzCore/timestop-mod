package com.timestop.forge.network;

import com.timestop.TimeStopMod;
import com.timestop.network.*;
import com.timestop.platform.INetworkHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ForgeNetworkHelper implements INetworkHelper {
    private static final String PROTOCOL_VERSION = "6";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TimeStopMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int nextId() { return packetId++; }

    public static void register() {
        // Serverbound packets
        registerServer(ToggleTimeStopPacket.class, ToggleTimeStopPacket::new);
        registerServer(SuperhotSyncPacket.class, SuperhotSyncPacket::new);
        registerServer(SlapProjectilePacket.class, SlapProjectilePacket::new);
        registerServer(KineticBlockPunchPacket.class, KineticBlockPunchPacket::new);
        registerServer(SelectTimeModePacket.class, SelectTimeModePacket::new);
        registerServer(SnatchProjectilePacket.class, SnatchProjectilePacket::new);
        registerServer(SocketRunePacket.class, SocketRunePacket::new);
        registerServer(SocketSpecificRunePacket.class, SocketSpecificRunePacket::new);
        registerServer(DeadEyeStatePacket.class, DeadEyeStatePacket::new);
        registerServer(DeadEyeExecutePacket.class, DeadEyeExecutePacket::new);
        registerServer(CycleRuneModePacket.class, CycleRuneModePacket::new);
        registerServer(ReleaseProjectilesPacket.class, ReleaseProjectilesPacket::new);
        registerServer(SingleFireProjectilePacket.class, SingleFireProjectilePacket::new);
        registerServer(TranspositionSwapPacket.class, TranspositionSwapPacket::new);
        registerServer(SetWatchScopePacket.class, SetWatchScopePacket::new);
        registerServer(KineticPalmActionPacket.class, KineticPalmActionPacket::new);
        registerServer(FlipCoinPacket.class, FlipCoinPacket::new);
        registerServer(ToggleProjectileFlowPacket.class, ToggleProjectileFlowPacket::new);
        registerServer(UpdateSpeedConfigPacket.class, UpdateSpeedConfigPacket::new);

        // Clientbound packets
        registerClient(TimeStopSyncPacket.class, TimeStopSyncPacket::new);
        registerClient(SyncSpeedConfigPacket.class, SyncSpeedConfigPacket::new);
        registerClient(SyncOrbitCountPacket.class, SyncOrbitCountPacket::new);
        registerClient(SyncOrbitalEntityPacket.class, SyncOrbitalEntityPacket::new);
        registerClient(SyncRuneSocketPacket.class, SyncRuneSocketPacket::new);
        registerClient(TemporalBubbleSyncPacket.class, TemporalBubbleSyncPacket::new);
        registerClient(SuperhotActivitySyncPacket.class, SuperhotActivitySyncPacket::new);
        registerClient(SyncCoinChargesPacket.class, SyncCoinChargesPacket::new);
        registerClient(KineticCaptureSyncPacket.class, KineticCaptureSyncPacket::new);
        registerClient(DeadEyeGunFeedbackPacket.class, DeadEyeGunFeedbackPacket::new);
    }

    private static <T extends IServerboundPacket> void registerServer(Class<T> type, net.minecraft.network.FriendlyByteBuf.Reader<T> decoder) {
        INSTANCE.messageBuilder(type, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(decoder::apply)
                .encoder(IModPacket::toBytes)
                .consumerMainThread((msg, ctx) -> {
                    msg.handle(ctx.get().getSender());
                })
                .add();
    }

    private static <T extends IClientboundPacket> void registerClient(Class<T> type, net.minecraft.network.FriendlyByteBuf.Reader<T> decoder) {
        INSTANCE.messageBuilder(type, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(decoder::apply)
                .encoder(IModPacket::toBytes)
                .consumerMainThread((msg, ctx) -> {
                    msg.handleClient();
                })
                .add();
    }

    @Override
    public void sendToServer(Object message) {
        INSTANCE.send(PacketDistributor.SERVER.noArg(), message);
    }

    @Override
    public void sendToClients(Object message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }

    @Override
    public void sendToPlayer(Object message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    @Override
    public void sendToTracking(Object message, Entity entity) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
    }
}