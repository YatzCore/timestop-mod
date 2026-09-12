package com.timestop.core;

import com.timestop.TimeStopMod;
import com.timestop.network.ModMessages;
import com.timestop.network.SuperhotActivitySyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;

/** Input belongs to players; each bubble aggregates only its current occupants. */
@Mod.EventBusSubscriber(modid = TimeStopMod.MOD_ID)
public final class SuperhotActivityManager {
    private record Activity(float value, long received) {}
    private static final Map<UUID, Activity> reports = new HashMap<>();

    public static void report(ServerPlayer player, float activity) {
        if (!player.isAlive() || player.isSpectator() || !Float.isFinite(activity)) return;
        reports.put(player.getUUID(), new Activity(activity > 0.15f ? 1 : 0, System.currentTimeMillis()));
        update();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) update();
    }

    private static void update() {
        var server = ServerLifecycleHooks.getCurrentServer();
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
            TimeStopManager.setSuperhotTickMs((long) (1000 - global * 950));
            ModMessages.sendToClients(new SuperhotActivitySyncPacket(global));
        }
    }

    @SubscribeEvent
    public static void clear(ServerStoppedEvent event) { reports.clear(); }
}
