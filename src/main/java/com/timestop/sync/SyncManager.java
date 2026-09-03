package com.timestop.sync;

import com.timestop.core.TemporalBubble;
import com.timestop.core.TemporalBubbleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SyncManager {
    private static final long REQUEST_TIMEOUT_MS = 60_000L; // 60s request expiration

    // targetUuid -> (senderUuid -> timestamp)
    private static final Map<UUID, Map<UUID, Long>> pendingRequests = new ConcurrentHashMap<>();

    // In-memory cache for O(1) stasis checks during entity tick loops
    private static final Map<UUID, Set<UUID>> syncCache = new ConcurrentHashMap<>();
    private static final Map<UUID, String> nameCache = new ConcurrentHashMap<>();
    private static volatile boolean cacheInitialized = false;

    private static SyncSavedData getSavedData() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new SyncSavedData();
        ServerLevel overworld = server.overworld();

        SyncSavedData data = overworld.getDataStorage().get(SyncSavedData::load, SyncSavedData.getDataName());
        if (data == null) {
            // Check legacy timestop_friends for seamless world migration
            SyncSavedData legacyData = overworld.getDataStorage().get(SyncSavedData::load, SyncSavedData.LEGACY_DATA_NAME);
            if (legacyData != null) {
                data = legacyData;
                data.setDirty();
                overworld.getDataStorage().set(SyncSavedData.getDataName(), data);
            } else {
                data = overworld.getDataStorage().computeIfAbsent(SyncSavedData::load, SyncSavedData::new, SyncSavedData.getDataName());
            }
        }

        if (!cacheInitialized) {
            syncCache.clear();
            for (Map.Entry<UUID, Set<UUID>> entry : data.getSyncedPlayers().entrySet()) {
                syncCache.put(entry.getKey(), ConcurrentHashMap.newKeySet());
                syncCache.get(entry.getKey()).addAll(entry.getValue());
            }
            nameCache.putAll(data.getLastKnownNames());
            cacheInitialized = true;
        }
        return data;
    }

    public static void ensureCacheInitialized() {
        if (!cacheInitialized) {
            getSavedData();
        }
    }

    public static void resetCache() {
        syncCache.clear();
        nameCache.clear();
        pendingRequests.clear();
        cacheInitialized = false;
    }

    public static boolean isSynced(UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        ensureCacheInitialized();
        Set<UUID> set = syncCache.get(a);
        return set != null && set.contains(b);
    }

    public static Set<UUID> getResonators(UUID player) {
        ensureCacheInitialized();
        Set<UUID> set = syncCache.get(player);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    public static String getPlayerName(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) {
                return online.getGameProfile().getName();
            }
        }
        SyncSavedData data = getSavedData();
        return data.getLastKnownNames().getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    public static void cachePlayerName(ServerPlayer player) {
        nameCache.put(player.getUUID(), player.getGameProfile().getName());
        SyncSavedData data = getSavedData();
        data.getLastKnownNames().put(player.getUUID(), player.getGameProfile().getName());
        data.setDirty();
    }

    public static boolean sendRequest(ServerPlayer sender, ServerPlayer target) {
        if (sender.getUUID().equals(target.getUUID())) {
            sender.displayClientMessage(Component.literal("You cannot establish Time Sync with yourself!").withStyle(ChatFormatting.RED), false);
            return false;
        }

        if (isSynced(sender.getUUID(), target.getUUID())) {
            sender.displayClientMessage(Component.literal(target.getGameProfile().getName() + " is already an active Resonator!").withStyle(ChatFormatting.YELLOW), false);
            return false;
        }

        pendingRequests.computeIfAbsent(target.getUUID(), k -> new ConcurrentHashMap<>())
                .put(sender.getUUID(), System.currentTimeMillis());

        sender.displayClientMessage(Component.literal("[Time Sync] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal("Sent a Time Sync request to ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(".").withStyle(ChatFormatting.WHITE)), false);

        MutableComponent acceptBtn = Component.literal("[ACCEPT]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sync accept " + sender.getGameProfile().getName()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to accept Time Sync from " + sender.getGameProfile().getName()))));

        MutableComponent declineBtn = Component.literal("[DECLINE]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sync decline " + sender.getGameProfile().getName()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to decline request from " + sender.getGameProfile().getName()))));

        MutableComponent inviteMsg = Component.literal("[Time Sync] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(sender.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" wants to establish Time Sync with you. Both players can move freely within each other's temporal fields. ")
                        .withStyle(ChatFormatting.WHITE))
                .append(acceptBtn)
                .append(Component.literal(" "))
                .append(declineBtn);

        target.displayClientMessage(inviteMsg, false);
        return true;
    }

    public static boolean acceptRequest(ServerPlayer accepter, ServerPlayer requester) {
        Map<UUID, Long> incoming = pendingRequests.get(accepter.getUUID());
        if (incoming == null || !incoming.containsKey(requester.getUUID())) {
            accepter.displayClientMessage(Component.literal("No pending Time Sync request from " + requester.getGameProfile().getName() + "!").withStyle(ChatFormatting.RED), false);
            return false;
        }

        long timestamp = incoming.remove(requester.getUUID());
        if (System.currentTimeMillis() - timestamp > REQUEST_TIMEOUT_MS) {
            accepter.displayClientMessage(Component.literal("The Time Sync request from " + requester.getGameProfile().getName() + " has expired!").withStyle(ChatFormatting.RED), false);
            return false;
        }

        syncCache.computeIfAbsent(accepter.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(requester.getUUID());
        syncCache.computeIfAbsent(requester.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(accepter.getUUID());
        nameCache.put(accepter.getUUID(), accepter.getGameProfile().getName());
        nameCache.put(requester.getUUID(), requester.getGameProfile().getName());

        SyncSavedData data = getSavedData();
        data.getSyncedPlayers().computeIfAbsent(accepter.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(requester.getUUID());
        data.getSyncedPlayers().computeIfAbsent(requester.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(accepter.getUUID());
        data.getLastKnownNames().put(accepter.getUUID(), accepter.getGameProfile().getName());
        data.getLastKnownNames().put(requester.getUUID(), requester.getGameProfile().getName());
        data.setDirty();

        syncActiveBubblesForResonators(accepter, requester);

        Component successMsg = Component.literal("[Time Sync] Time Sync established with ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(requester.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(". You are now resonant.").withStyle(ChatFormatting.GREEN));

        accepter.displayClientMessage(successMsg, false);

        Component requesterMsg = Component.literal("[Time Sync] ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(accepter.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" accepted your Time Sync invitation.").withStyle(ChatFormatting.GREEN));

        requester.displayClientMessage(requesterMsg, false);
        return true;
    }

    public static boolean declineRequest(ServerPlayer decliner, ServerPlayer requester) {
        Map<UUID, Long> incoming = pendingRequests.get(decliner.getUUID());
        if (incoming != null && incoming.remove(requester.getUUID()) != null) {
            decliner.displayClientMessage(Component.literal("[Time Sync] Declined invitation from " + requester.getGameProfile().getName() + ".").withStyle(ChatFormatting.GRAY), false);
            requester.displayClientMessage(Component.literal("[Time Sync] " + decliner.getGameProfile().getName() + " declined your Time Sync invitation.").withStyle(ChatFormatting.GRAY), false);
            return true;
        }
        decliner.displayClientMessage(Component.literal("No pending request from " + requester.getGameProfile().getName() + ".").withStyle(ChatFormatting.RED), false);
        return false;
    }

    public static boolean removeSync(ServerPlayer remover, UUID targetUuid) {
        if (syncCache.containsKey(remover.getUUID())) {
            syncCache.get(remover.getUUID()).remove(targetUuid);
        }
        if (syncCache.containsKey(targetUuid)) {
            syncCache.get(targetUuid).remove(remover.getUUID());
        }

        SyncSavedData data = getSavedData();
        String targetName = getPlayerName(targetUuid);

        boolean removedA = data.getSyncedPlayers().getOrDefault(remover.getUUID(), Collections.emptySet()).remove(targetUuid);
        boolean removedB = data.getSyncedPlayers().getOrDefault(targetUuid, Collections.emptySet()).remove(remover.getUUID());

        if (removedA || removedB) {
            data.setDirty();

            // Desync from active bubbles
            TemporalBubble b1 = TemporalBubbleManager.getPlayerBubble(remover.getUUID());
            if (b1 != null) {
                b1.removeExempt(targetUuid);
                TemporalBubbleManager.syncBubbleToClients(b1);
            }
            TemporalBubble b2 = TemporalBubbleManager.getPlayerBubble(targetUuid);
            if (b2 != null) {
                b2.removeExempt(remover.getUUID());
                TemporalBubbleManager.syncBubbleToClients(b2);
            }

            remover.displayClientMessage(Component.literal("[Time Sync] Removed ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(targetName).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal(" from your resonant network.").withStyle(ChatFormatting.YELLOW)), false);

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
                if (target != null) {
                    target.displayClientMessage(Component.literal("[Time Sync] ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal(remover.getGameProfile().getName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                            .append(Component.literal(" disconnected from your resonant network.").withStyle(ChatFormatting.YELLOW)), false);
                }
            }
            return true;
        }

        remover.displayClientMessage(Component.literal("Player is not in your resonant network.").withStyle(ChatFormatting.RED), false);
        return false;
    }

    public static void clearAllSync(ServerPlayer player) {
        Set<UUID> set = syncCache.remove(player.getUUID());
        SyncSavedData data = getSavedData();
        data.getSyncedPlayers().remove(player.getUUID());

        if (set != null) {
            for (UUID other : set) {
                if (syncCache.containsKey(other)) {
                    syncCache.get(other).remove(player.getUUID());
                }
                data.getSyncedPlayers().getOrDefault(other, Collections.emptySet()).remove(player.getUUID());
            }
        }
        data.setDirty();

        TemporalBubble b = TemporalBubbleManager.getPlayerBubble(player.getUUID());
        if (b != null) {
            b.clearExemptions();
            TemporalBubbleManager.syncBubbleToClients(b);
        }

        player.displayClientMessage(Component.literal("[Time Sync] Disconnected all active Resonators.").withStyle(ChatFormatting.YELLOW), false);
    }

    public static Map<UUID, Long> getIncomingRequests(UUID targetUuid) {
        Map<UUID, Long> incoming = pendingRequests.get(targetUuid);
        if (incoming == null) return Collections.emptyMap();

        long now = System.currentTimeMillis();
        incoming.entrySet().removeIf(entry -> now - entry.getValue() > REQUEST_TIMEOUT_MS);
        return Collections.unmodifiableMap(incoming);
    }

    private static void syncActiveBubblesForResonators(ServerPlayer a, ServerPlayer b) {
        TemporalBubble bubbleA = TemporalBubbleManager.getPlayerBubble(a.getUUID());
        if (bubbleA != null) {
            bubbleA.addExempt(b.getUUID());
            TemporalBubbleManager.syncBubbleToClients(bubbleA);
        }
        TemporalBubble bubbleB = TemporalBubbleManager.getPlayerBubble(b.getUUID());
        if (bubbleB != null) {
            bubbleB.addExempt(a.getUUID());
            TemporalBubbleManager.syncBubbleToClients(bubbleB);
        }
    }
}
