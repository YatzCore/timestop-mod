package com.timestop.friend;

import com.timestop.core.TemporalBubble;
import com.timestop.core.TemporalBubbleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FriendManager {
    private static final long REQUEST_TIMEOUT_MS = 60_000L; // 60s request expiration

    // targetUuid -> (senderUuid -> timestamp)
    private static final Map<UUID, Map<UUID, Long>> pendingRequests = new ConcurrentHashMap<>();

    // In-memory cache to eliminate massive overhead of repeated DataStorage lookups during entity tick loops
    private static final Map<UUID, Set<UUID>> friendCache = new ConcurrentHashMap<>();
    private static final Map<UUID, String> nameCache = new ConcurrentHashMap<>();
    private static volatile boolean cacheInitialized = false;

    private static FriendSavedData getSavedData() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new FriendSavedData();
        FriendSavedData data = server.overworld().getDataStorage().computeIfAbsent(FriendSavedData::load, FriendSavedData::new, FriendSavedData.getDataName());
        if (!cacheInitialized) {
            friendCache.clear();
            for (Map.Entry<UUID, Set<UUID>> entry : data.getFriends().entrySet()) {
                friendCache.put(entry.getKey(), ConcurrentHashMap.newKeySet());
                friendCache.get(entry.getKey()).addAll(entry.getValue());
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

    public static boolean isFriend(UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        ensureCacheInitialized();
        Set<UUID> set = friendCache.get(a);
        return set != null && set.contains(b);
    }

    public static Set<UUID> getFriends(UUID player) {
        ensureCacheInitialized();
        Set<UUID> set = friendCache.get(player);
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
        FriendSavedData data = getSavedData();
        return data.getLastKnownNames().getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    public static void cachePlayerName(ServerPlayer player) {
        nameCache.put(player.getUUID(), player.getGameProfile().getName());
        FriendSavedData data = getSavedData();
        data.getLastKnownNames().put(player.getUUID(), player.getGameProfile().getName());
        data.setDirty();
    }

    public static boolean sendRequest(ServerPlayer sender, ServerPlayer target) {
        if (sender.getUUID().equals(target.getUUID())) {
            sender.displayClientMessage(Component.literal("You cannot send a friend request to yourself!").withStyle(ChatFormatting.RED), false);
            return false;
        }

        if (isFriend(sender.getUUID(), target.getUUID())) {
            sender.displayClientMessage(Component.literal(target.getGameProfile().getName() + " is already your Chrono-Ally!").withStyle(ChatFormatting.YELLOW), false);
            return false;
        }

        // Cache names
        cachePlayerName(sender);
        cachePlayerName(target);

        // Check if target already sent request to sender (auto-accept!)
        Map<UUID, Long> myIncoming = pendingRequests.get(sender.getUUID());
        if (myIncoming != null && myIncoming.containsKey(target.getUUID())) {
            long time = myIncoming.get(target.getUUID());
            if (System.currentTimeMillis() - time < REQUEST_TIMEOUT_MS) {
                acceptRequest(sender, target);
                return true;
            }
        }

        pendingRequests.computeIfAbsent(target.getUUID(), k -> new ConcurrentHashMap<>())
                .put(sender.getUUID(), System.currentTimeMillis());

        // Notify sender
        sender.displayClientMessage(Component.literal("Sent a Chrono-Ally request to ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal("! (Expires in 60s)").withStyle(ChatFormatting.GRAY)), false);

        // Interactive message for target
        MutableComponent acceptBtn = Component.literal("[ACCEPT]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friend accept " + sender.getGameProfile().getName()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to accept Chrono-Ally request from " + sender.getGameProfile().getName()))));

        MutableComponent declineBtn = Component.literal("[DECLINE]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friend decline " + sender.getGameProfile().getName()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to decline request from " + sender.getGameProfile().getName()))));

        MutableComponent inviteMsg = Component.literal("⌛ ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(sender.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" wants to be your Chrono-Ally! You will both be able to move freely in each other's time spheres. ")
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
            accepter.displayClientMessage(Component.literal("No pending friend request from " + requester.getGameProfile().getName() + "!").withStyle(ChatFormatting.RED), false);
            return false;
        }

        long timestamp = incoming.remove(requester.getUUID());
        if (System.currentTimeMillis() - timestamp > REQUEST_TIMEOUT_MS) {
            accepter.displayClientMessage(Component.literal("The friend request from " + requester.getGameProfile().getName() + " has expired!").withStyle(ChatFormatting.RED), false);
            return false;
        }

        friendCache.computeIfAbsent(accepter.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(requester.getUUID());
        friendCache.computeIfAbsent(requester.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(accepter.getUUID());
        nameCache.put(accepter.getUUID(), accepter.getGameProfile().getName());
        nameCache.put(requester.getUUID(), requester.getGameProfile().getName());

        FriendSavedData data = getSavedData();
        data.getFriends().computeIfAbsent(accepter.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(requester.getUUID());
        data.getFriends().computeIfAbsent(requester.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(accepter.getUUID());
        data.getLastKnownNames().put(accepter.getUUID(), accepter.getGameProfile().getName());
        data.getLastKnownNames().put(requester.getUUID(), requester.getGameProfile().getName());
        data.setDirty();

        syncActiveBubblesForFriends(accepter, requester);

        Component successMsg = Component.literal("✦ Chrono-Alliance established! You and ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(requester.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" can now freely traverse each other's time spheres!").withStyle(ChatFormatting.GREEN));

        accepter.displayClientMessage(successMsg, false);

        Component requesterMsg = Component.literal("✦ ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(accepter.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" accepted your Chrono-Ally request! You can now freely traverse each other's time spheres!").withStyle(ChatFormatting.GREEN));

        requester.displayClientMessage(requesterMsg, false);
        return true;
    }

    public static boolean declineRequest(ServerPlayer decliner, ServerPlayer requester) {
        Map<UUID, Long> incoming = pendingRequests.get(decliner.getUUID());
        if (incoming != null && incoming.remove(requester.getUUID()) != null) {
            decliner.displayClientMessage(Component.literal("Declined friend request from " + requester.getGameProfile().getName() + ".").withStyle(ChatFormatting.GRAY), false);
            requester.displayClientMessage(Component.literal(decliner.getGameProfile().getName() + " declined your Chrono-Ally request.").withStyle(ChatFormatting.GRAY), false);
            return true;
        }
        decliner.displayClientMessage(Component.literal("No pending request from " + requester.getGameProfile().getName() + ".").withStyle(ChatFormatting.RED), false);
        return false;
    }

    public static boolean removeFriend(ServerPlayer remover, UUID targetUuid) {
        if (friendCache.containsKey(remover.getUUID())) {
            friendCache.get(remover.getUUID()).remove(targetUuid);
        }
        if (friendCache.containsKey(targetUuid)) {
            friendCache.get(targetUuid).remove(remover.getUUID());
        }

        FriendSavedData data = getSavedData();
        String targetName = getPlayerName(targetUuid);

        boolean removedA = data.getFriends().getOrDefault(remover.getUUID(), Collections.emptySet()).remove(targetUuid);
        boolean removedB = data.getFriends().getOrDefault(targetUuid, Collections.emptySet()).remove(remover.getUUID());

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

            remover.displayClientMessage(Component.literal("Removed ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(targetName).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal(" from your Chrono-Allies.").withStyle(ChatFormatting.YELLOW)), false);

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
                if (target != null) {
                    target.displayClientMessage(Component.literal(remover.getGameProfile().getName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            .append(Component.literal(" removed you from their Chrono-Allies.").withStyle(ChatFormatting.YELLOW)), false);
                }
            }
            return true;
        }

        remover.displayClientMessage(Component.literal(targetName + " is not in your friends list!").withStyle(ChatFormatting.RED), false);
        return false;
    }

    public static void clearAllFriends(ServerPlayer player) {
        FriendSavedData data = getSavedData();
        Set<UUID> myFriends = new HashSet<>(data.getFriends().getOrDefault(player.getUUID(), Collections.emptySet()));

        for (UUID friendUuid : myFriends) {
            removeFriend(player, friendUuid);
        }

        player.displayClientMessage(Component.literal("Cleared all Chrono-Allies.").withStyle(ChatFormatting.YELLOW), false);
    }

    public static void syncActiveBubblesForFriends(ServerPlayer p1, ServerPlayer p2) {
        TemporalBubble b1 = TemporalBubbleManager.getPlayerBubble(p1.getUUID());
        if (b1 != null) {
            b1.addExempt(p2.getUUID());
            TemporalBubbleManager.syncBubbleToClients(b1);
        }
        TemporalBubble b2 = TemporalBubbleManager.getPlayerBubble(p2.getUUID());
        if (b2 != null) {
            b2.addExempt(p1.getUUID());
            TemporalBubbleManager.syncBubbleToClients(b2);
        }
    }

    public static Map<UUID, Long> getIncomingRequests(UUID playerUuid) {
        Map<UUID, Long> map = pendingRequests.get(playerUuid);
        if (map == null) return Collections.emptyMap();
        // Filter expired
        map.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > REQUEST_TIMEOUT_MS);
        return Collections.unmodifiableMap(map);
    }
}