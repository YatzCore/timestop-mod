package com.timestop.forge.network;

import com.timestop.TimeStopMod;
import com.timestop.network.*;
import com.timestop.platform.INetworkHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.function.Function;

public class ForgeNetworkHelper implements INetworkHelper {
    private static final int PROTOCOL_VERSION = 1;
    public static final SimpleChannel INSTANCE = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "main"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .acceptedVersions(Channel.VersionTest.exact(PROTOCOL_VERSION))
            .simpleChannel();

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
        registerClient(RewindBlocksPacket.class, RewindBlocksPacket::new);
        registerClient(RewindMobPacket.class, RewindMobPacket::new);
        registerClient(RewindPistonPacket.class, RewindPistonPacket::new);
        registerClient(RewindFadePacket.class, RewindFadePacket::new);
    }

    private static <T extends IServerboundPacket> void registerServer(Class<T> type, Function<FriendlyByteBuf, T> decoder) {
        INSTANCE.messageBuilder(type, NetworkDirection.PLAY_TO_SERVER)
                .decoder(decoder::apply)
                .encoder(IModPacket::toBytes)
                .consumerMainThread((msg, ctx) -> {
                    msg.handle(ctx.getSender());
                })
                .add();
    }

    private static <T extends IClientboundPacket> void registerClient(Class<T> type, Function<FriendlyByteBuf, T> decoder) {
        INSTANCE.messageBuilder(type, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(decoder::apply)
                .encoder(IModPacket::toBytes)
                .consumerMainThread((msg, ctx) -> {
                    msg.handleClient();
                })
                .add();
    }

    @Override
    public void sendToServer(Object message) {
        INSTANCE.send(message, PacketDistributor.SERVER.noArg());
    }

    @Override
    public void sendToClients(Object message) {
        INSTANCE.send(message, PacketDistributor.ALL.noArg());
    }

    @Override
    public void sendToPlayer(Object message, ServerPlayer player) {
        if (player != null) {
            INSTANCE.send(message, PacketDistributor.PLAYER.with(player));
        }
    }

    @Override
    public void sendToTracking(Object message, Entity entity) {
        if (entity != null) {
            INSTANCE.send(message, PacketDistributor.TRACKING_ENTITY.with(entity));
        }
    }
}
