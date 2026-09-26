package com.timestop.neoforge.network;

import com.timestop.network.*;
import com.timestop.platform.INetworkHelper;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class NeoForgeNetworkHelper implements INetworkHelper {

    public record RawModPayload(ResourceLocation packetId, byte[] data) implements CustomPacketPayload {
        public static final Type<RawModPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("timestop", "raw_payload"));
        public static final StreamCodec<FriendlyByteBuf, RawModPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeResourceLocation(payload.packetId);
                    buf.writeByteArray(payload.data);
                },
                buf -> new RawModPayload(buf.readResourceLocation(), buf.readByteArray())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final Map<ResourceLocation, Function<FriendlyByteBuf, IServerboundPacket>> SERVER_DECODERS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Function<FriendlyByteBuf, IClientboundPacket>> CLIENT_DECODERS = new ConcurrentHashMap<>();

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("timestop").versioned("1.6.1");

        registerServer(SetPedestalRadiusPacket.ID, SetPedestalRadiusPacket::new);
        registerClient(SyncRewindAllowedPacket.ID, SyncRewindAllowedPacket::new);
        // Serverbound packets
        registerServer(ToggleTimeStopPacket.ID, ToggleTimeStopPacket::new);
        registerServer(SuperhotSyncPacket.ID, SuperhotSyncPacket::new);
        registerServer(SlapProjectilePacket.ID, SlapProjectilePacket::new);
        registerServer(KineticBlockPunchPacket.ID, KineticBlockPunchPacket::new);
        registerServer(SelectTimeModePacket.ID, SelectTimeModePacket::new);
        registerServer(SnatchProjectilePacket.ID, SnatchProjectilePacket::new);
        registerServer(SocketRunePacket.ID, SocketRunePacket::new);
        registerServer(SocketSpecificRunePacket.ID, SocketSpecificRunePacket::new);
        registerServer(DeadEyeStatePacket.ID, DeadEyeStatePacket::new);
        registerServer(DeadEyeExecutePacket.ID, DeadEyeExecutePacket::new);
        registerServer(CycleRuneModePacket.ID, CycleRuneModePacket::new);
        registerServer(ReleaseProjectilesPacket.ID, ReleaseProjectilesPacket::new);
        registerServer(SingleFireProjectilePacket.ID, SingleFireProjectilePacket::new);
        registerServer(TranspositionSwapPacket.ID, TranspositionSwapPacket::new);
        registerServer(SetWatchScopePacket.ID, SetWatchScopePacket::new);
        registerServer(KineticPalmActionPacket.ID, KineticPalmActionPacket::new);
        registerServer(FlipCoinPacket.ID, FlipCoinPacket::new);
        registerServer(ToggleProjectileFlowPacket.ID, ToggleProjectileFlowPacket::new);
        registerServer(UpdateSpeedConfigPacket.ID, UpdateSpeedConfigPacket::new);

        // Clientbound packets
        registerClient(TimeStopSyncPacket.ID, TimeStopSyncPacket::new);
        registerClient(SyncOrbitCountPacket.ID, SyncOrbitCountPacket::new);
        registerClient(SyncOrbitalEntityPacket.ID, SyncOrbitalEntityPacket::new);
        registerClient(SyncRuneSocketPacket.ID, SyncRuneSocketPacket::new);
        registerClient(TemporalBubbleSyncPacket.ID, TemporalBubbleSyncPacket::new);
        registerClient(SuperhotActivitySyncPacket.ID, SuperhotActivitySyncPacket::new);
        registerClient(SyncCoinChargesPacket.ID, SyncCoinChargesPacket::new);
        registerClient(KineticCaptureSyncPacket.ID, KineticCaptureSyncPacket::new);
        registerClient(DeadEyeGunFeedbackPacket.ID, DeadEyeGunFeedbackPacket::new);
        registerClient(SyncSpeedConfigPacket.ID, SyncSpeedConfigPacket::new);
        registerClient(RewindBlocksPacket.ID, RewindBlocksPacket::new);
        registerClient(RewindMobPacket.ID, RewindMobPacket::new);
        registerClient(RewindPistonPacket.ID, RewindPistonPacket::new);
        registerClient(RewindFadePacket.ID, RewindFadePacket::new);

        registrar.playBidirectional(
                RawModPayload.TYPE,
                RawModPayload.CODEC,
                NeoForgeNetworkHelper::handlePayload
        );
    }

    private static void handlePayload(RawModPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isServerbound()) {
                Function<FriendlyByteBuf, IServerboundPacket> decoder = SERVER_DECODERS.get(payload.packetId());
                if (decoder != null && context.player() instanceof ServerPlayer player) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
                    IServerboundPacket packet = decoder.apply(buf);
                    packet.handle(player);
                }
            } else {
                Function<FriendlyByteBuf, IClientboundPacket> decoder = CLIENT_DECODERS.get(payload.packetId());
                if (decoder != null) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
                    IClientboundPacket packet = decoder.apply(buf);
                    packet.handleClient();
                }
            }
        });
    }

    private static <T extends IServerboundPacket> void registerServer(ResourceLocation id, Function<FriendlyByteBuf, T> decoder) {
        SERVER_DECODERS.put(id, (Function<FriendlyByteBuf, IServerboundPacket>) decoder);
    }

    private static <T extends IClientboundPacket> void registerClient(ResourceLocation id, Function<FriendlyByteBuf, T> decoder) {
        CLIENT_DECODERS.put(id, (Function<FriendlyByteBuf, IClientboundPacket>) decoder);
    }

    private static RawModPayload wrap(IModPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.toBytes(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return new RawModPayload(packet.getId(), bytes);
    }

    @Override
    public void sendToServer(Object message) {
        if (message instanceof IModPacket packet) {
            PacketDistributor.sendToServer(wrap(packet));
        }
    }

    @Override
    public void sendToClients(Object message) {
        if (message instanceof IModPacket packet) {
            PacketDistributor.sendToAllPlayers(wrap(packet));
        }
    }

    @Override
    public void sendToPlayer(Object message, ServerPlayer player) {
        if (message instanceof IModPacket packet && player != null) {
            PacketDistributor.sendToPlayer(player, wrap(packet));
        }
    }

    @Override
    public void sendToTracking(Object message, Entity entity) {
        if (message instanceof IModPacket packet && entity != null) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, wrap(packet));
        }
    }
}
