package com.timestop.fabric.network;

import com.timestop.fabric.TimeStopFabricMod;
import com.timestop.network.*;
import com.timestop.platform.INetworkHelper;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class FabricNetworkHelper implements INetworkHelper {

    public static void registerServerReceivers() {
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
    }

    public static void registerClientReceivers() {
        registerClient(TimeStopSyncPacket.ID, TimeStopSyncPacket::new);
        registerClient(SyncSpeedConfigPacket.ID, SyncSpeedConfigPacket::new);
        registerClient(SyncOrbitCountPacket.ID, SyncOrbitCountPacket::new);
        registerClient(SyncOrbitalEntityPacket.ID, SyncOrbitalEntityPacket::new);
        registerClient(SyncRuneSocketPacket.ID, SyncRuneSocketPacket::new);
        registerClient(TemporalBubbleSyncPacket.ID, TemporalBubbleSyncPacket::new);
        registerClient(SuperhotActivitySyncPacket.ID, SuperhotActivitySyncPacket::new);
        registerClient(SyncCoinChargesPacket.ID, SyncCoinChargesPacket::new);
        registerClient(KineticCaptureSyncPacket.ID, KineticCaptureSyncPacket::new);
        registerClient(DeadEyeGunFeedbackPacket.ID, DeadEyeGunFeedbackPacket::new);
        registerClient(RewindBlocksPacket.ID, RewindBlocksPacket::new);
        registerClient(RewindMobPacket.ID, RewindMobPacket::new);
        registerClient(RewindPistonPacket.ID, RewindPistonPacket::new);
        registerClient(RewindFadePacket.ID, RewindFadePacket::new);
    }

    private static <T extends IServerboundPacket> void registerServer(ResourceLocation id, net.minecraft.network.FriendlyByteBuf.Reader<T> decoder) {
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buf, responseSender) -> {
            T packet = decoder.apply(buf);
            server.execute(() -> packet.handle(player));
        });
    }

    private static <T extends IClientboundPacket> void registerClient(ResourceLocation id, net.minecraft.network.FriendlyByteBuf.Reader<T> decoder) {
        ClientPlayNetworking.registerGlobalReceiver(id, (client, handler, buf, responseSender) -> {
            T packet = decoder.apply(buf);
            client.execute(packet::handleClient);
        });
    }

    @Override
    public void sendToServer(Object message) {
        if (message instanceof IModPacket packet) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            packet.toBytes(buf);
            ClientPlayNetworking.send(packet.getId(), buf);
        }
    }

    @Override
    public void sendToClients(Object message) {
        if (message instanceof IModPacket packet) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            packet.toBytes(buf);
            MinecraftServer server = TimeStopFabricMod.getServer();
            if (server != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, packet.getId(), new FriendlyByteBuf(buf.copy()));
                }
            }
        }
    }

    @Override
    public void sendToPlayer(Object message, ServerPlayer player) {
        if (message instanceof IModPacket packet && player != null) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            packet.toBytes(buf);
            ServerPlayNetworking.send(player, packet.getId(), buf);
        }
    }

    @Override
    public void sendToTracking(Object message, Entity entity) {
        if (message instanceof IModPacket packet && entity.level() instanceof ServerLevel serverLevel) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            packet.toBytes(buf);
            for (ServerPlayer player : PlayerLookup.tracking(entity)) {
                ServerPlayNetworking.send(player, packet.getId(), new FriendlyByteBuf(buf.copy()));
            }
        }
    }
}