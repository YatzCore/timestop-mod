package com.timestop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.timestop.config.TimeStopConfig;
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
                .then(Commands.literal("pedestal").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("affectplayers")
                                .executes(ctx -> pedestalPlayers(ctx.getSource(), null))
                                .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(ctx -> pedestalPlayers(ctx.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "enabled"))))))
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
                                        .executes(ctx -> startTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20, com.timestop.core.TimeMode.DECELERATION_FIELD))))
                        .then(Commands.literal("rewind")
                                .executes(ctx -> startTimeStop(ctx.getSource(), 0, com.timestop.core.TimeMode.REWIND))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                        .executes(ctx -> startTimeStop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds") * 20, com.timestop.core.TimeMode.REWIND)))))
                .then(Commands.literal("rewind")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> executeDirectRewind(ctx.getSource(), TimeStopConfig.rewindDurationSeconds()))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                .executes(ctx -> executeDirectRewind(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"))))
                        .then(Commands.literal("mode")
                                .then(Commands.literal("burst")
                                        .executes(ctx -> setRewindMode(ctx.getSource(), "BURST")))
                                .then(Commands.literal("continuous")
                                        .executes(ctx -> setRewindMode(ctx.getSource(), "CONTINUOUS"))))
                        .then(Commands.literal("ondeath")
                                .executes(ctx -> showDeathRewind(ctx.getSource()))
                                .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            com.timestop.core.TimeStopSavedData.get().setAutoDeathRewind(
                                                    com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "enabled"));
                                            return showDeathRewind(ctx.getSource());
                                        })))
                        .then(Commands.literal("buffer")
                                .executes(ctx -> showRewindStatus(ctx.getSource()))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> resetRewindBuffer(ctx.getSource())))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> clearRewindBuffer(ctx.getSource())))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                        .executes(ctx -> setRewindBuffer(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> resetRewindBuffer(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearRewindBuffer(ctx.getSource())))
                        .then(Commands.literal("include")
                                .executes(ctx -> showRewindAllowed(ctx.getSource()))
                                .then(Commands.argument("allowed", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(ctx -> setRewindAllowed(ctx.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "allowed")))))
                        .then(Commands.literal("allow")
                                .executes(ctx -> showRewindAllowed(ctx.getSource()))
                                .then(Commands.argument("allowed", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(ctx -> setRewindAllowed(ctx.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "allowed")))))
                        .then(Commands.literal("allowed")
                                .executes(ctx -> showRewindAllowed(ctx.getSource()))
                                .then(Commands.argument("allowed", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(ctx -> setRewindAllowed(ctx.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "allowed")))))
                        .then(Commands.literal("enable")
                                .executes(ctx -> setRewindAllowed(ctx.getSource(), true)))
                        .then(Commands.literal("disable")
                                .executes(ctx -> setRewindAllowed(ctx.getSource(), false)))
                        .then(Commands.literal("on")
                                .executes(ctx -> setRewindAllowed(ctx.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> setRewindAllowed(ctx.getSource(), false)))
                        .then(Commands.literal("status")
                                .executes(ctx -> showRewindStatus(ctx.getSource()))))
                .then(Commands.literal("buffer")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> showRewindStatus(ctx.getSource()))
                        .then(Commands.literal("reset")
                                .executes(ctx -> resetRewindBuffer(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearRewindBuffer(ctx.getSource())))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                .executes(ctx -> setRewindBuffer(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
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
                        .executes(ctx -> showStatus(ctx.getSource()))
                        .then(Commands.literal("global").executes(ctx -> setServerMode(ctx.getSource(), true)))
                        .then(Commands.literal("sphere").executes(ctx -> setServerMode(ctx.getSource(), false)))
                        .then(Commands.literal("bubble").executes(ctx -> setServerMode(ctx.getSource(), false)))
                        .then(Commands.literal("watch").executes(ctx -> {
                            com.timestop.core.TimeStopSavedData.get().setWatchScope(com.timestop.core.TimeStopSavedData.WatchScope.WATCH);
                            ctx.getSource().sendSuccess(() -> Component.literal("Watch scope follows each watch's own setting on its next activation."), true);
                            return 1;
                        })))
                .then(Commands.literal("redirect")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> showStatus(ctx.getSource()))
                        .then(Commands.literal("look").executes(ctx -> setRedirection(ctx.getSource(), true)))
                        .then(Commands.literal("return").executes(ctx -> setRedirection(ctx.getSource(), false))))
                .then(Commands.literal("globalmode")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(ctx -> setServerMode(ctx.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> showStatus(ctx.getSource())))
                .then(Commands.literal("allowrewind")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> showRewindAllowed(ctx.getSource()))
                        .then(Commands.argument("allowed", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(ctx -> setRewindAllowed(ctx.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "allowed")))))
                .then(Commands.literal("rewindinclude")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> showRewindAllowed(ctx.getSource()))
                        .then(Commands.argument("allowed", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(ctx -> setRewindAllowed(ctx.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "allowed")))))
                .then(buildSpeedSubtree())
                // Time Sync & Resonators (Accessible to ALL players without OP)
                .then(buildSyncSubtree("sync"))
                .then(buildSyncSubtree("timesync"))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSyncSubtree(String name) {
        return Commands.literal(name)
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> SyncCommand.sendRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> SyncCommand.acceptRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> SyncCommand.declineRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    if (p != null) {
                                        for (java.util.UUID u : com.timestop.sync.SyncManager.getResonators(p.getUUID())) {
                                            builder.suggest(com.timestop.sync.SyncManager.getPlayerName(u));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> SyncCommand.removeSyncByName(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> SyncCommand.listSync(ctx.getSource())))
                .then(Commands.literal("clear")
                        .executes(ctx -> SyncCommand.clearSync(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> SyncCommand.showHelp(ctx.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> SyncCommand.sendRequest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                .executes(ctx -> SyncCommand.showHelp(ctx.getSource()));
    }

    private static int startTimeStop(CommandSourceStack source, int durationTicks, com.timestop.core.TimeMode mode) {
        if (mode == com.timestop.core.TimeMode.REWIND) {
            if (!com.timestop.core.TimeStopManager.isRewindAllowed()) {
                source.sendFailure(Component.literal("§c[TimeStop] Rewind mode has been disabled by the server administrator."));
                return 0;
            }
            int ticks = durationTicks > 0 ? durationTicks : com.timestop.config.TimeStopConfig.rewindDurationSeconds() * 20;
            if ("BURST".equalsIgnoreCase(TimeStopConfig.COMMON.rewindMode.get()))
                return executeDirectRewind(source, Math.max(1, ticks / 20));
            var recorder = com.timestop.core.rewind.TickRecorder.getInstance();
            recorder.finishFrame(source.getServer());
            var buffer = recorder.getTimelineBuffer();
            int available = buffer.getFrameCount();
            if (available <= 0) {
                source.sendFailure(Component.literal("§c[TimeStop] Rewind buffer is empty. History records as the world runs."));
                return 0;
            }
            int playableTicks = Math.min(ticks, available);
            TimeStopManager.startContinuousRewind(source.getLevel(), source.getPlayer(), playableTicks);
            source.sendSuccess(() -> Component.literal("Rewinding " + String.format(java.util.Locale.ROOT, "%.1f", playableTicks / 20.0) + "s of available history."), true);
            return 1;
        }
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();

        if (TimeStopManager.isTimeStopped(level) && !com.timestop.core.TemporalBubbleManager.hasActiveBubbles() && !com.timestop.core.rewind.LocalRewind.hasActive()) {
            source.sendFailure(Component.literal("Time distortion is already active!"));
            return 0;
        }

        TimeStopManager.startTimeStop(level, player, durationTicks, mode);
        String durationStr = durationTicks > 0 ? (durationTicks / 20) + " seconds" : "indefinitely";
        source.sendSuccess(() -> Component.literal("§6[TimeStop] Global " + mode.name() + " activated for " + durationStr + " across the server."), true);
        return 1;
    }

    private static int stopTimeStop(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        boolean hadPedestal = com.timestop.core.TemporalBubbleManager.getActiveBubbles().values().stream().anyMatch(com.timestop.core.TemporalBubble::isStationary);
        com.timestop.pedestal.PedestalManager.disarmAll();

        if (!hadPedestal && !TimeStopManager.isTimeStopped(level) && !com.timestop.core.TemporalBubbleManager.hasActiveBubbles() && !com.timestop.core.rewind.LocalRewind.hasActive()) {
            source.sendFailure(Component.literal("Time is not currently stopped!"));
            return 0;
        }

        com.timestop.core.rewind.LocalRewind.clear();
        com.timestop.core.TemporalBubbleManager.stopAllBubbles(level);
        TimeStopManager.resumeTime(level);
        source.sendSuccess(() -> Component.literal("§bAll temporal bubbles collapsed and time resumed."), true);
        return 1;
    }

    private static int toggleTimeStop(CommandSourceStack source, int durationTicks, com.timestop.core.TimeMode mode) {
        ServerLevel level = source.getLevel();
        if (TimeStopManager.isTimeStopped(level) || com.timestop.core.TemporalBubbleManager.hasActiveBubbles() || com.timestop.core.rewind.LocalRewind.hasActive()) {
            return stopTimeStop(source);
        } else {
            return startTimeStop(source, durationTicks, mode);
        }
    }

    private static int pedestalPlayers(CommandSourceStack source, Boolean enabled) {
        var data = com.timestop.core.TimeStopSavedData.get();
        if (enabled != null) {
            data.setPedestalsAffectPlayers(enabled);
            com.timestop.pedestal.PedestalManager.serverTick();
        }
        source.sendSuccess(() -> Component.literal("Pedestals affect non-exempt players: " + data.pedestalsAffectPlayers()), enabled != null);
        return 1;
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
            source.sendSuccess(() -> Component.literal("§6[TimeStop] Server-wide mode updated: §aGLOBAL (Full Server)§6. All watches affect the entire server on their next activation!"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§6[TimeStop] Server-wide mode updated: §bBUBBLE (Localized)§6. All watches produce localized spheres on their next activation, including watches set to global."), true);
        }
        return 1;
    }

    private static int setRedirection(CommandSourceStack source, boolean look) {
        com.timestop.core.TimeStopSavedData.get().setRedirectToLook(look);
        source.sendSuccess(() -> Component.literal(look
                ? "Projectile redirection: LOOK. New deflections and shield volleys follow your aim."
                : "Projectile redirection: RETURN. Shots return to the sender; Vector Control runes override this."), true);
        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Watch scope: " + com.timestop.core.TimeStopSavedData.get().getWatchScope()
                + "; projectile redirection: " + (com.timestop.core.TimeStopSavedData.get().isRedirectToLook() ? "LOOK" : "RETURN")
                + "; rewind mode: " + (TimeStopManager.isRewindAllowed() ? "ENABLED" : "DISABLED")), false);

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

    private static LiteralArgumentBuilder<CommandSourceStack> buildSpeedSubtree() {
        return Commands.literal("speed")
                .executes(ctx -> showSpeeds(ctx.getSource()))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> resetSpeeds(ctx.getSource())))
                .then(Commands.literal("fastforward")
                        .executes(ctx -> showSpeed(ctx.getSource(), "fastforward", TimeStopConfig.COMMON.fastForwardRate.get()))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.1, 50.0))
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> setSpeed(ctx.getSource(), "fastforward", DoubleArgumentType.getDouble(ctx, "value")))))
                .then(Commands.literal("slowmotion")
                        .executes(ctx -> showSpeed(ctx.getSource(), "slowmotion", TimeStopConfig.COMMON.slowMotionRate.get()))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01, 0.99))
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> setSpeed(ctx.getSource(), "slowmotion", DoubleArgumentType.getDouble(ctx, "value")))))
                .then(Commands.literal("matrix")
                        .executes(ctx -> showSpeed(ctx.getSource(), "matrix", TimeStopConfig.COMMON.matrixRate.get()))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01, 0.99))
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> setSpeed(ctx.getSource(), "matrix", DoubleArgumentType.getDouble(ctx, "value")))))
                .then(Commands.literal("superhot")
                        .executes(ctx -> showSpeed(ctx.getSource(), "superhot", TimeStopConfig.COMMON.superhotIdleRate.get()))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.005, 0.80))
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> setSpeed(ctx.getSource(), "superhot", DoubleArgumentType.getDouble(ctx, "value")))))
                .then(Commands.literal("drag")
                        .executes(ctx -> showSpeed(ctx.getSource(), "drag", TimeStopConfig.COMMON.decelerationDrag.get()))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.001, 0.95))
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> setSpeed(ctx.getSource(), "drag", DoubleArgumentType.getDouble(ctx, "value")))));
    }

    private static int setSpeed(CommandSourceStack source, String key, double value) {
        switch (key) {
            case "fastforward" -> TimeStopConfig.COMMON.fastForwardRate.set(TimeStopConfig.clampFastForward(value));
            case "slowmotion" -> TimeStopConfig.COMMON.slowMotionRate.set(TimeStopConfig.clampSlowMotion(value));
            case "matrix" -> TimeStopConfig.COMMON.matrixRate.set(TimeStopConfig.clampMatrix(value));
            case "superhot" -> TimeStopConfig.COMMON.superhotIdleRate.set(TimeStopConfig.clampSuperhotIdle(value));
            case "drag" -> TimeStopConfig.COMMON.decelerationDrag.set(TimeStopConfig.clampDecelerationDrag(value));
        }
        TimeStopConfig.save();
        com.timestop.network.ModMessages.sendToClients(com.timestop.network.SyncSpeedConfigPacket.current());
        source.sendSuccess(() -> Component.literal(String.format("§6[TimeStop] %s speed multiplier set to §a%.3fx§6.", key, value)), true);
        return 1;
    }

    private static int resetSpeeds(CommandSourceStack source) {
        TimeStopConfig.resetSpeedsToDefaults();
        TimeStopConfig.save();
        com.timestop.network.ModMessages.sendToClients(com.timestop.network.SyncSpeedConfigPacket.current());
        source.sendSuccess(() -> Component.literal("§6[TimeStop] All speed multipliers reset to default calibration."), true);
        return 1;
    }

    private static int showSpeed(CommandSourceStack source, String key, double value) {
        source.sendSuccess(() -> Component.literal(String.format("§6[TimeStop] %s multiplier: §a%.3fx", key, value)), false);
        return 1;
    }

    private static int showSpeeds(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6=== TimeStop Speed Multipliers ==="), false);
        source.sendSuccess(() -> Component.literal(String.format(" §eFast Forward: §a%.2fx §7[1.1x - 50.0x]", TimeStopConfig.COMMON.fastForwardRate.get())), false);
        source.sendSuccess(() -> Component.literal(String.format(" §eSlow Motion:  §a%.2fx §7[0.01x - 0.99x]", TimeStopConfig.COMMON.slowMotionRate.get())), false);
        source.sendSuccess(() -> Component.literal(String.format(" §eMatrix:       §a%.2fx §7[0.01x - 0.99x]", TimeStopConfig.COMMON.matrixRate.get())), false);
        source.sendSuccess(() -> Component.literal(String.format(" §eSuperhot Idle:§a%.3fx §7[0.005x - 0.80x]", TimeStopConfig.COMMON.superhotIdleRate.get())), false);
        source.sendSuccess(() -> Component.literal(String.format(" §eDecel Drag:   §a%.3fx §7[0.001x - 0.95x]", TimeStopConfig.COMMON.decelerationDrag.get())), false);
        return 1;
    }

    private static int executeDirectRewind(CommandSourceStack source, int seconds) {
        if (!com.timestop.core.TimeStopManager.isRewindAllowed()) {
            source.sendFailure(Component.literal("§c[TimeStop] Rewind mode has been disabled by the server administrator."));
            return 0;
        }
        if ("CONTINUOUS".equalsIgnoreCase(TimeStopConfig.COMMON.rewindMode.get())) {
            var recorder = com.timestop.core.rewind.TickRecorder.getInstance();
            recorder.finishFrame(source.getServer());
            var buffer = recorder.getTimelineBuffer();
            int available = buffer.getFrameCount();
            if (available <= 0) {
                source.sendFailure(Component.literal("§c[TimeStop] Rewind buffer is empty. History records as the world runs."));
                return 0;
            }
            int requestedTicks = seconds * 20;
            int playableTicks = Math.min(requestedTicks, available);
            TimeStopManager.startContinuousRewind(source.getLevel(), source.getPlayer(), playableTicks);
            source.sendSuccess(() -> Component.literal("Rewinding " + String.format(java.util.Locale.ROOT, "%.1f", playableTicks / 20.0) + "s of available history."), true);
            return 1;
        }
        ServerPlayer player = source.getPlayer();
        boolean rollbackInv = TimeStopConfig.COMMON.rollbackPlayerInventory.get();
        var result = com.timestop.core.rewind.RewindExecutor.execute(source.getServer(), seconds, player, rollbackInv, null);
        source.sendSuccess(result::toComponent, true);
        return result.success() ? 1 : 0;
    }

    private static int resetRewindBuffer(CommandSourceStack source) {
        com.timestop.core.rewind.LocalRewind.clear();
        if (TimeStopManager.isTimeStopped(source.getLevel())) {
            TimeStopManager.resumeTime(source.getLevel());
        }
        var recorder = com.timestop.core.rewind.TickRecorder.getInstance();
        recorder.reset();
        var buffer = recorder.getTimelineBuffer();
        buffer.resizeSeconds(30);
        TimeStopConfig.COMMON.rewindHistorySeconds.set(30);
        TimeStopConfig.save();
        source.sendSuccess(() -> Component.literal("§6[TimeStop] Rewind buffer reset to default (30s, 0 recorded frames retained)."), true);
        return 1;
    }

    private static int clearRewindBuffer(CommandSourceStack source) {
        com.timestop.core.rewind.LocalRewind.clear();
        if (TimeStopManager.isTimeStopped(source.getLevel())) {
            TimeStopManager.resumeTime(source.getLevel());
        }
        var recorder = com.timestop.core.rewind.TickRecorder.getInstance();
        recorder.clearTrackingData();
        var buffer = recorder.getTimelineBuffer();
        buffer.clear();
        source.sendSuccess(() -> Component.literal("§6[TimeStop] Rewind buffer cleared (0 recorded frames retained, capacity " + (buffer.getCapacity() / 20) + "s retained)."), true);
        return 1;
    }

    private static int showDeathRewind(CommandSourceStack source) {
        boolean enabled = com.timestop.core.TimeStopSavedData.get().isAutoDeathRewind();
        source.sendSuccess(() -> Component.literal("Automatic death rewind: " + (enabled ? "ON" : "OFF")
                + (enabled ? ". Applies to all players; no rune is required or consumed. Uses the configured rewind mode and scope."
                           : ". Socketed rewind runes still work normally.")), true);
        return 1;
    }

    private static int setRewindBuffer(CommandSourceStack source, int seconds) {
        var buffer = com.timestop.core.rewind.TickRecorder.getInstance().getTimelineBuffer();
        buffer.resizeSeconds(seconds);
        TimeStopConfig.COMMON.rewindHistorySeconds.set(seconds);
        TimeStopConfig.save();
        source.sendSuccess(() -> Component.literal("Rewind buffer set to " + seconds + "s (" + buffer.getFrameCount()
                + " recorded ticks retained); default rewind " + seconds + "s, memory budget " + buffer.getMaxMemoryBytes() / (1024 * 1024) + " MB. Longer history fills as you play."), true);
        return 1;
    }

    private static int setRewindMode(CommandSourceStack source, String mode) {
        TimeStopConfig.COMMON.rewindMode.set(mode.toUpperCase());
        TimeStopConfig.save();
        source.sendSuccess(() -> Component.literal("§a[TimeStop] Rewind mode set to: §e" + mode.toUpperCase()), true);
        return 1;
    }

    private static int setRewindAllowed(CommandSourceStack source, boolean allowed) {
        TimeStopManager.setRewindAllowed(allowed);
        if (allowed) {
            source.sendSuccess(() -> Component.literal("§6[TimeStop] Rewind mode has been §aENABLED§6. It is now available on compatible watches and commands."), true);
        } else {
            source.sendSuccess(() -> Component.literal("§6[TimeStop] Rewind mode has been §cDISABLED§6. It is now excluded from all watches."), true);
        }
        return 1;
    }

    private static int showRewindAllowed(CommandSourceStack source) {
        boolean allowed = TimeStopManager.isRewindAllowed();
        source.sendSuccess(() -> Component.literal("§6[TimeStop] Rewind mode inclusion on watches: " + (allowed ? "§aENABLED (Included)" : "§cDISABLED (Excluded)")), false);
        return 1;
    }

    private static int showRewindStatus(CommandSourceStack source) {
        var buffer = com.timestop.core.rewind.TickRecorder.getInstance().getTimelineBuffer();
        int frames = buffer.getFrameCount();
        long mb = buffer.getTotalEstimatedBytes() / (1024 * 1024);
        String mode = TimeStopConfig.COMMON.rewindMode.get();
        boolean allowed = TimeStopManager.isRewindAllowed();
        source.sendSuccess(() -> Component.literal(String.format(
                "§d[TimeStop Rewind Status]\n" +
                "§7• Watch Mode Allowed: %s\n" +
                "§7• Frames Recorded: §e%d / %d §7(%.1fs)\n" +
                "§7• Memory Used: §e%d / %d MB\n" +
                "§7• Default rewind: §e%ds §7| Memory-evicted frames: §e%d\n" +
                "§7• Mode: §e%s\n" +
                "§7• Recording: §a%b",
                allowed ? "§aYES (Included on watches)" : "§cNO (Excluded from watches)",
                frames, buffer.getCapacity(), frames / 20.0, mb, buffer.getMaxMemoryBytes() / (1024 * 1024),
                TimeStopConfig.rewindDurationSeconds(), buffer.getMemoryEvictedFrames(), mode, buffer.isRecording()
        )), false);
        return 1;
    }
}
