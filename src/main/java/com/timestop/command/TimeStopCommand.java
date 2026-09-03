package com.timestop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.timestop.core.TimeStopManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class TimeStopCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("timestop")
                // Admin controls (Requires permission level 2)
                .then(Commands.literal("start")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> startTimeStop(ctx.getSource(), 0, com.timestop.core.TimeMode.TIME_STOP))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(ctx -> startTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20, com.timestop.core.TimeMode.TIME_STOP)))
                        .then(Commands.literal("timestop")
                                .executes(ctx -> startTimeStop(ctx.getSource(), 0, com.timestop.core.TimeMode.TIME_STOP))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> startTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20, com.timestop.core.TimeMode.TIME_STOP))))
                        .then(Commands.literal("slowmotion")
                                .executes(ctx -> startTimeStop(ctx.getSource(), 0, com.timestop.core.TimeMode.SLOW_MOTION))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> startTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20, com.timestop.core.TimeMode.SLOW_MOTION))))
                        .then(Commands.literal("matrix")
                                .executes(ctx -> startTimeStop(ctx.getSource(), 0, com.timestop.core.TimeMode.MATRIX))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> startTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20, com.timestop.core.TimeMode.MATRIX))))
                        .then(Commands.literal("superhot")
                                .executes(ctx -> startTimeStop(ctx.getSource(), 0, com.timestop.core.TimeMode.SUPERHOT))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> startTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20, com.timestop.core.TimeMode.SUPERHOT))))
                        .then(Commands.literal("fastforward")
                                .executes(ctx -> startTimeStop(ctx.getSource(), 0, com.timestop.core.TimeMode.FAST_FORWARD))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> startTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20, com.timestop.core.TimeMode.FAST_FORWARD))))
                        .then(Commands.literal("deceleration")
                                .executes(ctx -> startTimeStop(ctx.getSource(), 0, com.timestop.core.TimeMode.DECELERATION_FIELD))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> startTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20, com.timestop.core.TimeMode.DECELERATION_FIELD)))))
                .then(Commands.literal("stop")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> stopTimeStop(ctx.getSource())))
                .then(Commands.literal("toggle")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> toggleTimeStop(ctx.getSource(), 0, com.timestop.core.TimeMode.TIME_STOP))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(ctx -> toggleTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20, com.timestop.core.TimeMode.TIME_STOP))))
                .then(Commands.literal("exempt")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> addExempt(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> removeExempt(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("servermode")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("global")
                                .executes(ctx -> setServerMode(ctx.getSource(), true)))
                        .then(Commands.literal("bubble")
                                .executes(ctx -> setServerMode(ctx.getSource(), false))))
                .then(Commands.literal("scope")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("global")
                                .executes(ctx -> setServerMode(ctx.getSource(), true)))
                        .then(Commands.literal("bubble")
                                .executes(ctx -> setServerMode(ctx.getSource(), false))))
                .then(Commands.literal("globalmode")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(ctx -> setServerMode(ctx.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> showStatus(ctx.getSource())))
                // Chrono-Allies & Friends (Accessible to ALL players without OP)
                .then(buildFriendSubtree("friend"))
                .then(buildFriendSubtree("party"))
                .then(buildFriendSubtree("ally"))
                .then(buildFriendSubtree("allies"))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildFriendSubtree(String name) {
        return Commands.literal(name)
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> FriendCommand.sendRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> FriendCommand.acceptRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> FriendCommand.declineRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    if (p != null) {
                                        for (java.util.UUID u : com.timestop.friend.FriendManager.getFriends(p.getUUID())) {
                                            builder.suggest(com.timestop.friend.FriendManager.getPlayerName(u));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> FriendCommand.removeFriendByName(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> FriendCommand.listFriends(ctx.getSource())))
                .then(Commands.literal("clear")
                        .executes(ctx -> FriendCommand.clearFriends(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> FriendCommand.showHelp(ctx.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> FriendCommand.sendRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                .executes(ctx -> FriendCommand.showHelp(ctx.getSource()));
    }

    private static int startTimeStop(CommandSourceStack source, int durationTicks, com.timestop.core.TimeMode mode) {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();

        if (TimeStopManager.isTimeStopped(level) && !com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            source.sendFailure(Component.literal("Time distortion is already active!"));
            return 0;
        }

        TimeStopManager.startGlobalTimeStop(level, player, durationTicks, mode);
        String durationStr = durationTicks > 0 ? (durationTicks / 20) + " seconds" : "indefinitely";
        source.sendSuccess(() -> Component.literal("§6[TimeStop] Global " + mode.name() + " activated for " + durationStr + " across the server."), true);
        return 1;
    }

    private static int stopTimeStop(CommandSourceStack source) {
        ServerLevel level = source.getLevel();

        if (!TimeStopManager.isTimeStopped(level) && !com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            source.sendFailure(Component.literal("Time is not currently stopped!"));
            return 0;
        }

        com.timestop.core.TemporalBubbleManager.stopAllBubbles(level);
        TimeStopManager.resumeTime(level);
        source.sendSuccess(() -> Component.literal("§bAll temporal bubbles collapsed and time resumed."), true);
        return 1;
    }

    private static int toggleTimeStop(CommandSourceStack source, int durationTicks, com.timestop.core.TimeMode mode) {
        ServerLevel level = source.getLevel();
        if (TimeStopManager.isTimeStopped(level) || com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            return stopTimeStop(source);
        } else {
            return startTimeStop(source, durationTicks, mode);
        }
    }

    private static int addExempt(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            TimeStopManager.addExemptPlayer(player.getUUID());
            source.sendSuccess(() -> Component.literal("Added §e" + player.getName().getString() + "§r to time stop exemption list."), true);
        }
        if (TimeStopManager.isGlobalTimeStopped()) {
            com.timestop.network.ModMessages.sendToClients(new com.timestop.network.TimeStopSyncPacket(
                    true, TimeStopManager.getRemainingTicks(), TimeStopManager.getInitiatorUuid(),
                    TimeStopManager.getCurrentMode(), TimeStopManager.getExemptPlayers()));
        }
        return players.size();
    }

    private static int removeExempt(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            TimeStopManager.removeExemptPlayer(player.getUUID());
            source.sendSuccess(() -> Component.literal("Removed §e" + player.getName().getString() + "§r from time stop exemption list."), true);
        }
        if (TimeStopManager.isGlobalTimeStopped()) {
            com.timestop.network.ModMessages.sendToClients(new com.timestop.network.TimeStopSyncPacket(
                    true, TimeStopManager.getRemainingTicks(), TimeStopManager.getInitiatorUuid(),
                    TimeStopManager.getCurrentMode(), TimeStopManager.getExemptPlayers()));
        }
        return players.size();
    }

    private static int setServerMode(CommandSourceStack source, boolean global) {
        TimeStopManager.setServerForceGlobalMode(global);
        if (global) {
            source.sendSuccess(() -> Component.literal("§6[TimeStop] Server-wide mode updated: §aGLOBAL (Full Server)§6. All Chronos Watches will now affect the entire server!"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§6[TimeStop] Server-wide mode updated: §bBUBBLE (Localized)§6. Chronos Watches will produce localized temporal spheres."), true);
        }
        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6Server Watch Mode: " + (TimeStopManager.isServerForceGlobalMode() ? "§aGLOBAL (Full Server)" : "§bBUBBLE (Localized Spheres)")), false);

        if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            int count = com.timestop.core.TemporalBubbleManager.getActiveBubbles().size();
            source.sendSuccess(() -> Component.literal("§6Active Temporal Bubbles: §a" + count), false);
            for (com.timestop.core.TemporalBubble b : com.timestop.core.TemporalBubbleManager.getActiveBubbles().values()) {
                String dur = b.getRemainingTicks() > 0 ? (b.getRemainingTicks() / 20) + "s" : "Indefinite";
                source.sendSuccess(() -> Component.literal(" - §e" + b.getTier().getDisplayName() + " §f(" + (int) b.getRadius() + "m): §a" + b.getMode().name() + " §7[" + dur + "]"), false);
            }
            return 1;
        }

        boolean active = TimeStopManager.isGlobalTimeStopped();
        if (active) {
            int remaining = TimeStopManager.getRemainingTicks();
            String duration = remaining > 0 ? (remaining / 20) + "s remaining" : "Indefinite";
            source.sendSuccess(() -> Component.literal("§6Time Stop Status: §aACTIVE §f(" + duration + ")"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§6Time Stop Status: §cINACTIVE"), false);
        }
        return 1;
    }
}
