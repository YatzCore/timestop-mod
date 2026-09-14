package com.timestop.core;

import com.timestop.network.ModMessages;
import com.timestop.network.SuperhotActivitySyncPacket;
import com.timestop.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Input belongs to players; each bubble aggregates only its current occupants. */
public final class SuperhotActivityManager {
    private record Activity(float value, long received) {}
    private static final Map<UUID, Activity> reports = new HashMap<>();

    public static void report(ServerPlayer player, float activity) {
        if (!player.isAlive() || player.isSpectator() || !Float.isFinite(activity)) return;
        reports.put(player.getUUID(), new Activity(Math.max(0.0F, Math.min(1.0F, activity)), System.currentTimeMillis()));
        update();
    }

    public static void serverTick() {
        update();
    }

    private static void update() {
        var server = Services.PLATFORM.getCurrentServer();
        if (server == null) return;
        long now = System.currentTimeMillis();
        reports.entrySet().removeIf(e -> now - e.getValue().received() > 1500
                || server.getPlayerList().getPlayer(e.getKey()) == null);

        for (var bubble : TemporalBubbleManager.getActiveBubbles().values()) {
            if (bubble.getMode() != TimeMode.SUPERHOT) continue;
            Set<UUID> occupants = new HashSet<>();
            for (var player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive() || player.isSpectator() || !bubble.contains(player)) continue;
                occupants.add(player.getUUID());
                Activity report = reports.get(player.getUUID());
                bubble.setPlayerActivity(player.getUUID(), report == null ? 0 : report.value());
            }
            bubble.retainActivePlayers(occupants);
            for (var player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension().equals(bubble.getDimension()))
                    ModMessages.sendToPlayer(new SuperhotActivitySyncPacket(bubble.getId(), bubble.getSuperhotActivity()), player);
            }
        }
        float global = 0;
        if (TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.SUPERHOT) {
            for (var player : server.getPlayerList().getPlayers()) {
                Activity report = reports.get(player.getUUID());
                if (player.isAlive() && !player.isSpectator() && report != null) global = Math.max(global, report.value());
            }
            float idleRate = Math.max(0.20F, com.timestop.config.TimeStopConfig.COMMON.superhotIdleRate.get().floatValue());
            long maxMs = Math.min(250L, Math.max(50L, Math.round(50.0F / idleRate)));
            long tickMs = (long) (maxMs - global * (maxMs - 50L));
            TimeStopManager.setSuperhotTickMs(tickMs);
            ModMessages.sendToClients(new SuperhotActivitySyncPacket(global));
        }
    }

    public static void onServerStopped() {
        reports.clear();
    }
}
