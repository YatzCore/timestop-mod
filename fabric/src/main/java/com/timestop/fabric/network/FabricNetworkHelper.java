package com.timestop.fabric.network;

import com.timestop.fabric.TimeStopFabricMod;
import com.timestop.network.*;
import com.timestop.platform.INetworkHelper;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class FabricNetworkHelper implements INetworkHelper {

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

    public static void initPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(RawModPayload.TYPE, RawModPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RawModPayload.TYPE, RawModPayload.CODEC);
    }

    public static void registerServerReceivers() {
        initPayloadTypes();

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

        ServerPlayNetworking.registerGlobalReceiver(RawModPayload.TYPE, (payload, context) -> {
            Function<FriendlyByteBuf, IServerboundPacket> decoder = SERVER_DECODERS.get(payload.packetId());
            if (decoder != null) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
                IServerboundPacket packet = decoder.apply(buf);
                context.server().execute(() -> packet.handle(context.player()));
            }
        });
    }

    public static void registerClientReceivers() {
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

        ClientPlayNetworking.registerGlobalReceiver(RawModPayload.TYPE, (payload, context) -> {
            Function<FriendlyByteBuf, IClientboundPacket> decoder = CLIENT_DECODERS.get(payload.packetId());
            if (decoder != null) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
                IClientboundPacket packet = decoder.apply(buf);
                context.client().execute(packet::handleClient);
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
            ClientPlayNetworking.send(wrap(packet));
        }
    }

    @Override
    public void sendToClients(Object message) {
        if (message instanceof IModPacket packet) {
            MinecraftServer server = TimeStopFabricMod.getServer();
            if (server != null) {
                RawModPayload payload = wrap(packet);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }
    }

    @Override
    public void sendToPlayer(Object message, ServerPlayer player) {
        if (message instanceof IModPacket packet && player != null) {
            ServerPlayNetworking.send(player, wrap(packet));
        }
    }

    @Override
    public void sendToTracking(Object message, Entity entity) {
        if (message instanceof IModPacket packet && entity.level() instanceof ServerLevel serverLevel) {
            RawModPayload payload = wrap(packet);
            for (ServerPlayer tracker : PlayerLookup.tracking(entity)) {
                ServerPlayNetworking.send(tracker, payload);
            }
        }
    }
}
