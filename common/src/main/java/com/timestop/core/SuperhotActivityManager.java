package com.timestop.core;

import com.timestop.network.ModMessages;
import com.timestop.network.SuperhotActivitySyncPacket;
import com.timestop.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Input belongs to players; each bubble aggregates only its current occupants. */
public final class SuperhotActivityManager {
    private record Activity(float value, long received) {}
    private static final Map<UUID, Activity> reports = new HashMap<>();

    public static void report(ServerPlayer player, float activity) {
        if (!player.isAlive() || player.isSpectator() || !Float.isFinite(activity)) return;
        reports.put(player.getUUID(), new Activity(activity > 0.15f ? 1 : 0, System.currentTimeMillis()));
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

        // 1. Process localized temporal bubbles
        for (var bubble : TemporalBubbleManager.getActiveBubbles().values()) {
            if (bubble.getMode() != TimeMode.SUPERHOT) continue;
            Set<UUID> occupants = new HashSet<>();
            for (var player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive() || player.isSpectator() || !bubble.contains(player)) continue;
                occupants.add(player.getUUID());
                Activity report = reports.get(player.getUUID());
                bubble.setPlayerActivity(player.getUUID(), report == null ? 0.0f : report.value());
            }
            bubble.retainActivePlayers(occupants);

            for (var player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension().equals(bubble.getDimension())) {
                    // Send occupants the activity of other occupants in the bubble to avoid echoing their own input.
                    // Send outside players total bubble activity so they observe correct dilation.
                    float activityForPlayer = bubble.contains(player)
                            ? bubble.getOtherPlayersSuperhotActivity(player.getUUID())
                            : bubble.getSuperhotActivity();
                    ModMessages.sendToPlayer(new SuperhotActivitySyncPacket(bubble.getId(), activityForPlayer), player);
                }
            }
        }

        // 2. Process global SUPERHOT
        if (TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.SUPERHOT) {
            ServerLevel activeLevel = TimeStopManager.getActiveServerLevel();
            float global = 0.0f;
            for (var player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive() || player.isSpectator()) continue;
                if (activeLevel != null && !player.level().dimension().equals(activeLevel.dimension())) continue;
                Activity report = reports.get(player.getUUID());
                if (report != null) {
                    global = Math.max(global, report.value());
                }
            }
            double idleRate = com.timestop.config.TimeStopConfig.COMMON.superhotIdleRate.get();
            long maxMs = (long) Math.max(50.0, Math.round(50.0 / idleRate));
            TimeStopManager.setSuperhotTickMs((long) (maxMs - global * (maxMs - 50L)));

            for (var player : server.getPlayerList().getPlayers()) {
                if (activeLevel != null && !player.level().dimension().equals(activeLevel.dimension())) continue;
                float othersGlobal = 0.0f;
                for (var other : server.getPlayerList().getPlayers()) {
                    if (other.equals(player)) continue;
                    if (!other.isAlive() || other.isSpectator()) continue;
                    if (activeLevel != null && !other.level().dimension().equals(activeLevel.dimension())) continue;
                    Activity rep = reports.get(other.getUUID());
                    if (rep != null) {
                        othersGlobal = Math.max(othersGlobal, rep.value());
                    }
                }
                ModMessages.sendToPlayer(new SuperhotActivitySyncPacket(othersGlobal), player);
            }
        }
    }

    public static void onServerStopped() {
        reports.clear();
    }
}