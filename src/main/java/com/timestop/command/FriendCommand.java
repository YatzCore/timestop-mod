package com.timestop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.timestop.friend.FriendManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FriendCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register /friend
        dispatcher.register(Commands.literal("friend")
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> sendRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> acceptRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> declineRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    if (p != null) {
                                        for (UUID u : FriendManager.getFriends(p.getUUID())) {
                                            builder.suggest(FriendManager.getPlayerName(u));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> removeFriendByName(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listFriends(ctx.getSource())))
                .then(Commands.literal("clear")
                        .executes(ctx -> clearFriends(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> showHelp(ctx.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> sendRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                .executes(ctx -> showHelp(ctx.getSource()))
        );

        // Register /party as alias
        dispatcher.register(Commands.literal("party")
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> sendRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> acceptRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> declineRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    if (p != null) {
                                        for (UUID u : FriendManager.getFriends(p.getUUID())) {
                                            builder.suggest(FriendManager.getPlayerName(u));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> removeFriendByName(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listFriends(ctx.getSource())))
                .then(Commands.literal("clear")
                        .executes(ctx -> clearFriends(ctx.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> sendRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                .executes(ctx -> showHelp(ctx.getSource()))
        );
    }

    public static int sendRequest(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null) {
            source.sendFailure(Component.literal("Only players can use friend commands!"));
            return 0;
        }

        FriendManager.sendRequest(sender, target);
        return 1;
    }

    public static int acceptRequest(CommandSourceStack source, ServerPlayer requester) {
        ServerPlayer accepter = source.getPlayer();
        if (accepter == null) {
            source.sendFailure(Component.literal("Only players can use friend commands!"));
            return 0;
        }

        FriendManager.acceptRequest(accepter, requester);
        return 1;
    }

    public static int declineRequest(CommandSourceStack source, ServerPlayer requester) {
        ServerPlayer decliner = source.getPlayer();
        if (decliner == null) {
            source.sendFailure(Component.literal("Only players can use friend commands!"));
            return 0;
        }

        FriendManager.declineRequest(decliner, requester);
        return 1;
    }

    public static int removeFriend(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer remover = source.getPlayer();
        if (remover == null) {
            source.sendFailure(Component.literal("Only players can use friend commands!"));
            return 0;
        }

        FriendManager.removeFriend(remover, target.getUUID());
        return 1;
    }

    public static int removeFriendByName(CommandSourceStack source, String name) {
        ServerPlayer remover = source.getPlayer();
        if (remover == null) {
            source.sendFailure(Component.literal("Only players can use friend commands!"));
            return 0;
        }

        Set<UUID> friends = FriendManager.getFriends(remover.getUUID());
        for (UUID uuid : friends) {
            String knownName = FriendManager.getPlayerName(uuid);
            if (knownName.equalsIgnoreCase(name)) {
                FriendManager.removeFriend(remover, uuid);
                return 1;
            }
        }

        source.sendFailure(Component.literal("No friend found with name '" + name + "'!"));
        return 0;
    }

    public static int clearFriends(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use friend commands!"));
            return 0;
        }

        FriendManager.clearAllFriends(player);
        return 1;
    }

    public static int listFriends(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use friend commands!"));
            return 0;
        }

        Set<UUID> friends = FriendManager.getFriends(player.getUUID());
        Map<UUID, Long> incoming = FriendManager.getIncomingRequests(player.getUUID());

        source.sendSuccess(() -> Component.literal("======= [ Chrono-Allies & Friends ] =======").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        if (friends.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You currently have no Chrono-Allies. Use /timestop friend <player> to ally!").withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("Active Allies (" + friends.size() + "):").withStyle(ChatFormatting.YELLOW), false);
            for (UUID uuid : friends) {
                String name = FriendManager.getPlayerName(uuid);
                ServerPlayer online = source.getServer().getPlayerList().getPlayer(uuid);
                boolean isOnline = online != null;

                MutableComponent entry = Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(name).withStyle(isOnline ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                        .append(Component.literal(isOnline ? " [ONLINE]" : " [OFFLINE]").withStyle(isOnline ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY));

                MutableComponent removeBtn = Component.literal(" [REMOVE]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/timestop friend remove " + name))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to remove " + name))));

                entry.append(removeBtn);
                source.sendSuccess(() -> entry, false);
            }
        }

        if (!incoming.isEmpty()) {
            source.sendSuccess(() -> Component.literal("\nPending Incoming Requests:").withStyle(ChatFormatting.AQUA), false);
            for (UUID requesterUuid : incoming.keySet()) {
                String name = FriendManager.getPlayerName(requesterUuid);
                MutableComponent reqEntry = Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                        .append(Component.literal(" "))
                        .append(Component.literal("[ACCEPT]")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.GREEN)
                                        .withBold(true)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/timestop friend accept " + name))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to accept")))))
                        .append(Component.literal(" "))
                        .append(Component.literal("[DECLINE]")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.RED)
                                        .withBold(true)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/timestop friend decline " + name))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to decline")))));

                source.sendSuccess(() -> reqEntry, false);
            }
        }

        source.sendSuccess(() -> Component.literal("===========================================").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        return 1;
    }

    public static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== Chrono-Allies Command Help ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("/timestop friend <player> §7- Send a Chrono-Ally request"), false);
        source.sendSuccess(() -> Component.literal("/timestop friend accept <player> §7- Accept incoming request"), false);
        source.sendSuccess(() -> Component.literal("/timestop friend decline <player> §7- Decline incoming request"), false);
        source.sendSuccess(() -> Component.literal("/timestop friend remove <player> §7- Remove player from friends"), false);
        source.sendSuccess(() -> Component.literal("/timestop friend list §7- View your friends & pending requests"), false);
        source.sendSuccess(() -> Component.literal("/timestop friend clear §7- Remove all friends"), false);
        source.sendSuccess(() -> Component.literal("§eNote: Friends can freely move inside each other's time spheres!"), false);
        return 1;
    }
}