package com.timestop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.timestop.sync.SyncManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SyncCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerTree(dispatcher, "sync");
        registerTree(dispatcher, "timesync");
    }

    public static void registerTree(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root)
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
                                        for (UUID u : SyncManager.getResonators(p.getUUID())) {
                                            builder.suggest(SyncManager.getPlayerName(u));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> removeSyncByName(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listSync(ctx.getSource())))
                .then(Commands.literal("clear")
                        .executes(ctx -> clearSync(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> showHelp(ctx.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> sendRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                .executes(ctx -> showHelp(ctx.getSource()))
        );
    }

    public static int sendRequest(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null) {
            source.sendFailure(Component.literal("Only players can use Time Sync commands!"));
            return 0;
        }

        SyncManager.sendRequest(sender, target);
        return 1;
    }

    public static int acceptRequest(CommandSourceStack source, ServerPlayer requester) {
        ServerPlayer accepter = source.getPlayer();
        if (accepter == null) {
            source.sendFailure(Component.literal("Only players can use Time Sync commands!"));
            return 0;
        }

        SyncManager.acceptRequest(accepter, requester);
        return 1;
    }

    public static int declineRequest(CommandSourceStack source, ServerPlayer requester) {
        ServerPlayer decliner = source.getPlayer();
        if (decliner == null) {
            source.sendFailure(Component.literal("Only players can use Time Sync commands!"));
            return 0;
        }

        SyncManager.declineRequest(decliner, requester);
        return 1;
    }

    public static int removeSync(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer remover = source.getPlayer();
        if (remover == null) {
            source.sendFailure(Component.literal("Only players can use Time Sync commands!"));
            return 0;
        }

        SyncManager.removeSync(remover, target.getUUID());
        return 1;
    }

    public static int removeSyncByName(CommandSourceStack source, String name) {
        ServerPlayer remover = source.getPlayer();
        if (remover == null) {
            source.sendFailure(Component.literal("Only players can use Time Sync commands!"));
            return 0;
        }

        Set<UUID> resonators = SyncManager.getResonators(remover.getUUID());
        for (UUID uuid : resonators) {
            String knownName = SyncManager.getPlayerName(uuid);
            if (knownName.equalsIgnoreCase(name)) {
                SyncManager.removeSync(remover, uuid);
                return 1;
            }
        }

        source.sendFailure(Component.literal("No active Resonator found with name '" + name + "'!"));
        return 0;
    }

    public static int clearSync(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use Time Sync commands!"));
            return 0;
        }

        SyncManager.clearAllSync(player);
        return 1;
    }

    public static int listSync(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use Time Sync commands!"));
            return 0;
        }

        Set<UUID> resonators = SyncManager.getResonators(player.getUUID());
        Map<UUID, Long> incoming = SyncManager.getIncomingRequests(player.getUUID());

        source.sendSuccess(() -> Component.literal("======= [ Time Sync / Active Resonators ] =======").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);

        if (resonators.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You currently have no active Resonators. Use /sync <player> to link!").withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("Active Resonators (" + resonators.size() + "):").withStyle(ChatFormatting.YELLOW), false);
            for (UUID uuid : resonators) {
                String name = SyncManager.getPlayerName(uuid);
                ServerPlayer online = source.getServer().getPlayerList().getPlayer(uuid);
                boolean isOnline = online != null;

                MutableComponent entry = Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(name).withStyle(isOnline ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                        .append(Component.literal(isOnline ? " [ONLINE]" : " [OFFLINE]").withStyle(isOnline ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY));

                MutableComponent removeBtn = Component.literal(" [DISCONNECT]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sync remove " + name))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to disconnect from " + name))));

                entry.append(Component.literal(" ")).append(removeBtn);
                source.sendSuccess(() -> entry, false);
            }
        }

        if (!incoming.isEmpty()) {
            source.sendSuccess(() -> Component.literal("\nPending Incoming Sync Invitations:").withStyle(ChatFormatting.AQUA), false);
            for (UUID requesterUuid : incoming.keySet()) {
                String name = SyncManager.getPlayerName(requesterUuid);
                MutableComponent reqEntry = Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                        .append(Component.literal(" "))
                        .append(Component.literal("[ACCEPT]")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.GREEN)
                                        .withBold(true)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sync accept " + name))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to accept")))))
                        .append(Component.literal(" "))
                        .append(Component.literal("[DECLINE]")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.RED)
                                        .withBold(true)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sync decline " + name))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to decline")))));

                source.sendSuccess(() -> reqEntry, false);
            }
        }

        source.sendSuccess(() -> Component.literal("=================================================").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        return 1;
    }

    public static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== Time Sync Command Reference ===").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("/sync <player> - Send a Time Sync request"), false);
        source.sendSuccess(() -> Component.literal("/sync accept <player> - Accept incoming Time Sync request"), false);
        source.sendSuccess(() -> Component.literal("/sync decline <player> - Decline incoming request"), false);
        source.sendSuccess(() -> Component.literal("/sync remove <player> - Disconnect a Resonator"), false);
        source.sendSuccess(() -> Component.literal("/sync list - View active Resonators and pending requests"), false);
        source.sendSuccess(() -> Component.literal("/sync clear - Disconnect all active Resonators"), false);
        source.sendSuccess(() -> Component.literal("Note: Resonators move freely within each other's temporal fields!"), false);
        return 1;
    }
}
