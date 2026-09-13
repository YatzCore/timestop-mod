package com.timestop;

import com.mojang.logging.LogUtils;
import com.timestop.combat.KineticPalmManager;
import com.timestop.combat.RuneManager;
import com.timestop.combat.TemporalDamageBuffer;
import com.timestop.combat.TemporalKineticBlockManager;
import com.timestop.combat.TranspositionManager;
import com.timestop.command.SyncCommand;
import com.timestop.command.TimeStopCommand;
import com.timestop.core.TemporalBubbleManager;
import com.timestop.core.TimeStopManager;
import com.timestop.network.ModMessages;
import com.timestop.network.TimeStopSyncPacket;
import com.timestop.sync.SyncManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public class TimeStopMod {
    public static final String MOD_ID = "timestop";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        LOGGER.info("[TimeStop] Ultimate Time Stop Mod (Common) initialized!");
    }

    public static void onServerTick() {
        TimeStopManager.serverTick();
        TemporalBubbleManager.serverTick();
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        TimeStopCommand.register(dispatcher);
        SyncCommand.register(dispatcher);
    }

    public static void onPlayerLoggedIn(ServerPlayer serverPlayer) {
        SyncManager.cachePlayerName(serverPlayer);
        TemporalBubbleManager.syncAllToPlayer(serverPlayer);
        ModMessages.sendToPlayer(new TimeStopSyncPacket(
                TimeStopManager.isGlobalTimeStopActive(), TimeStopManager.getRemainingTicks(),
                TimeStopManager.getInitiatorUuid(), TimeStopManager.getCurrentMode(),
                TimeStopManager.getExemptPlayers()), serverPlayer);
    }

    public static void onPlayerLoggedOut(ServerPlayer serverPlayer) {
        TemporalBubbleManager.stopPlayerBubble(serverPlayer.serverLevel(), serverPlayer.getUUID());
        if (serverPlayer.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
            TimeStopManager.resumeTime(serverPlayer.serverLevel());
        }
        KineticPalmManager.setGuarding(serverPlayer, false);
        KineticPalmManager.dischargeDrop(serverPlayer);
        TimeStopManager.removeMatrixAttributes(serverPlayer);
        RuneManager.clearPlayerCooldowns(serverPlayer.getUUID());
        TranspositionManager.clearPlayerCooldown(serverPlayer.getUUID());
    }

    public static void onLivingDeath(ServerPlayer serverPlayer) {
        TemporalBubbleManager.stopPlayerBubble(serverPlayer.serverLevel(), serverPlayer.getUUID());
        if (serverPlayer.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
            TimeStopManager.resumeTime(serverPlayer.serverLevel());
        }
        KineticPalmManager.setGuarding(serverPlayer, false);
        KineticPalmManager.dischargeDrop(serverPlayer);
        TimeStopManager.removeMatrixAttributes(serverPlayer);
    }

    public static void onServerStopping(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TemporalBubbleManager.stopAllBubbles(level);
        TimeStopManager.resumeTime(level);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            KineticPalmManager.setGuarding(player, false);
            KineticPalmManager.dischargeDrop(player);
        }
        TimeStopManager.reset();
        KineticPalmManager.clearAll();
        TemporalBubbleManager.reset();
        TemporalKineticBlockManager.clearAll();
        SyncManager.resetCache();
        TemporalDamageBuffer.clearAll();
        RuneManager.clearAllCooldowns();
        TranspositionManager.clearAllCooldowns();
    }
}