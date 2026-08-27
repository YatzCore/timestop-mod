package com.timestop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> startTimeStop(ctx.getSource(), 0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(ctx -> startTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20))))
                .then(Commands.literal("stop")
                        .executes(ctx -> stopTimeStop(ctx.getSource())))
                .then(Commands.literal("toggle")
                        .executes(ctx -> toggleTimeStop(ctx.getSource(), 0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(ctx -> toggleTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20))))
                .then(Commands.literal("exempt")
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> addExempt(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> removeExempt(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("status")
                        .executes(ctx -> showStatus(ctx.getSource())))
        );
    }

    private static int startTimeStop(CommandSourceStack source, int durationTicks) {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();

        if (TimeStopManager.isTimeStopped(level)) {
            source.sendFailure(Component.literal("Time is already stopped!"));
            return 0;
        }

        TimeStopManager.startTimeStop(level, player, durationTicks);
        String durationStr = durationTicks > 0 ? (durationTicks / 20) + " seconds" : "indefinitely";
        source.sendSuccess(() -> Component.literal("§6Time stopped for " + durationStr + "."), true);
        return 1;
    }

    private static int stopTimeStop(CommandSourceStack source) {
        ServerLevel level = source.getLevel();

        if (!TimeStopManager.isTimeStopped(level)) {
            source.sendFailure(Component.literal("Time is not currently stopped!"));
            return 0;
        }

        TimeStopManager.resumeTime(level);
        source.sendSuccess(() -> Component.literal("§bTime resumed."), true);
        return 1;
    }

    private static int toggleTimeStop(CommandSourceStack source, int durationTicks) {
        ServerLevel level = source.getLevel();
        if (TimeStopManager.isTimeStopped(level)) {
            return stopTimeStop(source);
        } else {
            return startTimeStop(source, durationTicks);
        }
    }

    private static int addExempt(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            TimeStopManager.addExemptPlayer(player.getUUID());
            source.sendSuccess(() -> Component.literal("Added §e" + player.getName().getString() + "§r to time stop exemption list."), true);
        }
        return players.size();
    }

    private static int removeExempt(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            TimeStopManager.removeExemptPlayer(player.getUUID());
            source.sendSuccess(() -> Component.literal("Removed §e" + player.getName().getString() + "§r from time stop exemption list."), true);
        }
        return players.size();
    }

    private static int showStatus(CommandSourceStack source) {
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
