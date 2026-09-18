package com.timestop.fabric.test;

import com.timestop.combat.RewindRuneManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.core.rewind.data.PlayerDelta;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.ModItems;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;

public class RewindRuneTests implements FabricGameTest {
    public static net.minecraft.server.level.ServerPlayer survivalPlayer(GameTestHelper h) {
        var player = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "rune-test"));
        h.getLevel().getServer().getPlayerList().placeNewPlayer(
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player);
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
    }


    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind_rune_reg")
    public void testRewindRuneRegistration(GameTestHelper h) {
        ItemStack runeStack = new ItemStack(ModItems.RUNE_REWIND.get());
        h.assertTrue(!runeStack.isEmpty(), "Rewind rune item must be registered");
        h.assertTrue(runeStack.getItem() instanceof TemporalRuneItem rune && rune.getType() == RuneType.REWIND,
                "Rewind rune must have RuneType.REWIND");
        h.assertTrue(ModItems.getRuneItem(RuneType.REWIND) == ModItems.RUNE_REWIND.get(),
                "ModItems.getRuneItem must return RUNE_REWIND");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind_rune_inv")
    public void testDeathRewindIgnoresLooseRuneInInventory(GameTestHelper h) {
        var player = survivalPlayer(h);
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };

        ItemStack rune = new ItemStack(ModItems.RUNE_REWIND.get());
        player.getInventory().setItem(0, rune);
        player.setHealth(0.5F);

        h.assertFalse(RewindRuneManager.hasRewindRune(player), "Loose rune must not count as active");
        boolean triggered = RewindRuneManager.tryTriggerDeathRewind(player, player.damageSources().generic());
        h.assertFalse(triggered, "Death rewind must not trigger for loose rune in inventory");
        h.assertFalse(player.getInventory().getItem(0).isEmpty(), "Loose rune must not be consumed");

        RewindRuneManager.clearPlayer(player.getUUID());
        player.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind_rune_socket")
    public void testDeathRewindBreaksSocketedRuneAndSavesPlayer(GameTestHelper h) {
        var player = survivalPlayer(h);
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };

        ItemStack watch = new ItemStack(ModItems.CHRONOS_WATCH.get());
        AbstractWatchItem.setSocketedRune(watch, new ItemStack(ModItems.RUNE_REWIND.get()));
        player.getInventory().setItem(0, watch);
        player.setHealth(0.5F);

        h.assertTrue(RewindRuneManager.hasRewindRune(player), "Player must be detected as having Rewind Rune");
        boolean triggered = RewindRuneManager.tryTriggerDeathRewind(player, player.damageSources().generic());
        h.assertTrue(triggered, "Rewind rune must trigger from socketed watch");

        ItemStack watchAfter = player.getInventory().getItem(0);
        h.assertTrue(AbstractWatchItem.getSocketedRune(watchAfter).isEmpty(), "Socketed rune must be cleared/broken upon use");
        h.assertTrue(player.getHealth() > 1.0F, "Player must be revived");

        TimeStopManager.resumeTime(h.getLevel());
        RewindRuneManager.clearPlayer(player.getUUID());
        player.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind_rune_rollback")
    public void testInventoryRollbackPreservesSocketedRuneConsumption(GameTestHelper h) {
        var player = survivalPlayer(h);
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };

        ItemStack watch = new ItemStack(ModItems.CHRONOS_WATCH.get());
        AbstractWatchItem.setSocketedRune(watch, new ItemStack(ModItems.RUNE_REWIND.get()));
        player.getInventory().setItem(0, watch);

        // Snapshot historical state where watch had the rune
        var before = PlayerDelta.fromPlayer(player);

        // Player dies and rune breaks
        boolean triggered = RewindRuneManager.tryTriggerDeathRewind(player, player.damageSources().generic());
        h.assertTrue(triggered, "Rewind rune must trigger");
        h.assertTrue(AbstractWatchItem.getSocketedRune(player.getInventory().getItem(0)).isEmpty(), "Socketed rune was consumed");

        // Rollback inventory to historical snapshot (which originally had socketed rune)
        before.restoreTo(player, true);

        // Assert that the consumed rune was NOT resurrected by the historical inventory rollback!
        h.assertTrue(AbstractWatchItem.getSocketedRune(player.getInventory().getItem(0)).isEmpty(),
                "Restored inventory must not resurrect the broken socketed rune");

        TimeStopManager.resumeTime(h.getLevel());
        RewindRuneManager.clearPlayer(player.getUUID());
        player.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind_rune_modes")
    public void testContinuousModeTriggersContinuousRewind(GameTestHelper h) {
        var player = survivalPlayer(h);
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };

        com.timestop.config.TimeStopConfig.COMMON.rewindMode.set("CONTINUOUS");
        ItemStack watch = new ItemStack(ModItems.CHRONOS_WATCH.get());
        AbstractWatchItem.setSocketedRune(watch, new ItemStack(ModItems.RUNE_REWIND.get()));
        player.getInventory().setItem(0, watch);

        // Record a mock tick so timeline buffer has a frame
        var recorder = com.timestop.core.rewind.TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        recorder.serverTick(h.getLevel().getServer());

        boolean triggered = RewindRuneManager.tryTriggerDeathRewind(player, player.damageSources().generic());
        h.assertTrue(triggered, "Death rewind should trigger");
        h.assertFalse(TimeStopManager.isTimeStopped(h.getLevel()), "Must not start playback inside the death callback");
        h.assertTrue(recorder.isRecording(), "The rest of the lethal tick must still be recorded");
        h.runAfterDelay(4, () -> {
            h.assertTrue(player.isAlive(), "Player must survive deferred continuous playback");
            h.assertTrue(AbstractWatchItem.getSocketedRune(player.getInventory().getItem(0)).isEmpty(), "Playback must keep the rune broken");
            TimeStopManager.resumeTime(h.getLevel());
            RewindRuneManager.clearPlayer(player.getUUID());
            com.timestop.config.TimeStopConfig.COMMON.rewindMode.set("BURST");
            player.discard();
            h.succeed();
        });
    }
}
