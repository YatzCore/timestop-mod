package com.timestop.fabric.test;

import com.timestop.config.TimeStopConfig;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.core.TimeStopSavedData;
import com.timestop.core.rewind.TickRecorder;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class BurstRewindTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, batch = "burst_rewind_initiator")
    public void initiatorRestoresInBurstModeEvenWhenOlderPositionIsOutsideSphere(GameTestHelper h) {
        var level = h.getLevel();
        var player = RewindRuneTests.survivalPlayer(h);
        var startPos = h.absolutePos(new BlockPos(2, 2, 2));
        var farPos = startPos.east(80);

        var settings = TimeStopSavedData.get();
        var oldScope = settings.getWatchScope();
        String oldMode = TimeStopConfig.COMMON.rewindMode.get();
        var recorder = TickRecorder.getInstance();

        try {
            settings.setWatchScope(TimeStopSavedData.WatchScope.WATCH);
            TimeStopConfig.COMMON.rewindMode.set("BURST");

            // Give Diamond Watch (localized 42m sphere with REWIND unlocked)
            var watch = new ItemStack(ModItems.DIAMOND_WATCH.get());
            AbstractWatchItem.setGlobalScope(watch, false);
            player.getInventory().setItem(0, watch);

            player.setPos(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
            player.setHealth(20.0F);

            level.setBlock(startPos, Blocks.STONE.defaultBlockState(), 18);
            level.setBlock(farPos, Blocks.STONE.defaultBlockState(), 18);

            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            recorder.serverTick(level.getServer());

            // Mutate: player moves 80 blocks away (far outside the 24m bubble), takes damage, and world changes
            player.setPos(farPos.getX() + 0.5, farPos.getY(), farPos.getZ() + 0.5);
            player.setHealth(6.0F);
            level.setBlock(startPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 18);
            level.setBlock(farPos, Blocks.GOLD_BLOCK.defaultBlockState(), 18);

            // Execute burst rewind
            TimeStopManager.startTimeStop(level, player, 20, TimeMode.REWIND);

            // Verify:
            // 1. Block inside the activation bubble (at farPos) rewound to STONE
            h.assertTrue(level.getBlockState(farPos).is(Blocks.STONE), "Block inside activation bubble must be rewound to STONE");
            // 2. Block outside the activation bubble (80m away at startPos) remains changed as DIAMOND_BLOCK
            h.assertTrue(level.getBlockState(startPos).is(Blocks.DIAMOND_BLOCK), "Block outside bubble must remain DIAMOND_BLOCK");
            // 3. Initiator player is fully restored despite startPos being 80m outside the activation bubble
            h.assertTrue(player.getHealth() == 20.0F, "Player health must be restored to 20.0F");
            h.assertTrue(player.blockPosition().closerThan(startPos, 2), "Player position must be rewound back to startPos");
            // 4. Watch cooldown is engaged
            h.assertTrue(player.getCooldowns().isOnCooldown(watch.getItem()), "Watch must be on cooldown after burst rewind");

        } finally {
            settings.setWatchScope(oldScope);
            TimeStopConfig.COMMON.rewindMode.set(oldMode);
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            player.discard();
        }

        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "burst_rewind_command")
    public void adminCommandRewindsGloballyRegardlessOfWatchScope(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var player = RewindRuneTests.survivalPlayer(h);
        var inside = h.absolutePos(new BlockPos(2, 2, 2));
        var outside = inside.east(100);

        var settings = TimeStopSavedData.get();
        var oldScope = settings.getWatchScope();
        String oldMode = TimeStopConfig.COMMON.rewindMode.get();
        var recorder = TickRecorder.getInstance();

        try {
            settings.setWatchScope(TimeStopSavedData.WatchScope.SPHERE);
            TimeStopConfig.COMMON.rewindMode.set("BURST");

            var watch = new ItemStack(ModItems.COPPER_WATCH.get());
            player.getInventory().setItem(0, watch);
            player.setPos(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);

            level.setBlock(inside, Blocks.STONE.defaultBlockState(), 18);
            level.setBlock(outside, Blocks.STONE.defaultBlockState(), 18);

            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            recorder.serverTick(level.getServer());

            level.setBlock(inside, Blocks.EMERALD_BLOCK.defaultBlockState(), 18);
            level.setBlock(outside, Blocks.EMERALD_BLOCK.defaultBlockState(), 18);

            var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
            com.timestop.command.TimeStopCommand.register(dispatcher);
            var source = player.createCommandSourceStack().withPermission(4);

            dispatcher.execute("timestop rewind 1", source);

            h.assertTrue(level.getBlockState(inside).is(Blocks.STONE), "Inside block must rewind via command");
            h.assertTrue(level.getBlockState(outside).is(Blocks.STONE), "Outside block must also rewind globally via command");

        } finally {
            settings.setWatchScope(oldScope);
            TimeStopConfig.COMMON.rewindMode.set(oldMode);
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            player.discard();
        }

        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "burst_rewind_buffer_exhaustion")
    public void continuousRewindTerminatesWhenBufferExhausted(GameTestHelper h) {
        var level = h.getLevel();
        var player = RewindRuneTests.survivalPlayer(h);
        var pos = h.absolutePos(new BlockPos(2, 2, 2));

        var settings = TimeStopSavedData.get();
        var oldScope = settings.getWatchScope();
        String oldMode = TimeStopConfig.COMMON.rewindMode.get();
        int oldSec = TimeStopConfig.COMMON.rewindHistorySeconds.get();
        var recorder = TickRecorder.getInstance();

        try {
            settings.setWatchScope(TimeStopSavedData.WatchScope.GLOBAL);
            TimeStopConfig.COMMON.rewindMode.set("CONTINUOUS");
            TimeStopConfig.COMMON.rewindHistorySeconds.set(60);

            // Mutate block first, then clear so currentFrame doesn't hold an extra frame
            level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 18);
            recorder.clearTrackingData();
            var buffer = recorder.getTimelineBuffer();
            buffer.clear();
            buffer.resizeSeconds(60);

            // Seed only 5 frames into a 60s buffer
            for (int i = 0; i < 5; i++) {
                var frame = new com.timestop.core.rewind.data.TickFrame(i);
                frame.captureEnvironment(level);
                if (i == 0) {
                    frame.addBlockDelta(com.timestop.core.rewind.data.BlockDelta.create(
                            level.dimension(), pos, Blocks.STONE.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), null, null));
                }
                buffer.pushFrame(frame);
            }
            h.assertTrue(buffer.getFrameCount() == 5, "Buffer must contain exactly 5 seeded frames");

            // Start continuous rewind with requested duration 60s (1200 ticks)
            TimeStopManager.startContinuousRewind(level, player, 1200, null);
            h.assertTrue(TimeStopManager.isGlobalTimeStopActive(), "Continuous rewind must activate");

            // Tick 5 times: each tick consumes 1 frame
            for (int t = 0; t < 5; t++) {
                TimeStopManager.serverTick();
            }

            // After 5 ticks, buffer is exhausted (0 frames)
            h.assertTrue(buffer.getFrameCount() == 0, "All 5 frames must be consumed");
            h.assertFalse(TimeStopManager.isGlobalTimeStopActive(), "Rewind must terminate immediately when buffer is exhausted, not wait 60s");
            h.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "Block state must have rewound to STONE");

        } finally {
            TimeStopManager.resumeTime(level);
            settings.setWatchScope(oldScope);
            TimeStopConfig.COMMON.rewindMode.set(oldMode);
            TimeStopConfig.COMMON.rewindHistorySeconds.set(oldSec);
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            recorder.getTimelineBuffer().resizeSeconds(oldSec);
            player.discard();
        }

        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "burst_rewind_buffer_reset")
    public void resetBufferCommandResetsCapacityAndClearsHistory(GameTestHelper h) throws Exception {
        var player = RewindRuneTests.survivalPlayer(h);
        var recorder = TickRecorder.getInstance();
        var buffer = recorder.getTimelineBuffer();

        int oldSec = TimeStopConfig.COMMON.rewindHistorySeconds.get();
        try {
            // Clear and set capacity to 60s and push frames
            recorder.clearTrackingData();
            buffer.clear();
            buffer.resizeSeconds(60);
            TimeStopConfig.COMMON.rewindHistorySeconds.set(60);
            for (int i = 0; i < 50; i++) {
                buffer.pushFrame(new com.timestop.core.rewind.data.TickFrame(i));
            }
            h.assertTrue(buffer.getFrameCount() == 50, "Buffer should contain 50 frames before reset");
            h.assertTrue(buffer.getCapacity() == 1200, "Buffer capacity should be 1200 (60s)");

            var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
            com.timestop.command.TimeStopCommand.register(dispatcher);
            var source = player.createCommandSourceStack().withPermission(4);

            // Execute /timestop rewind buffer reset
            dispatcher.execute("timestop rewind buffer reset", source);

            h.assertTrue(buffer.getFrameCount() == 0, "Buffer frame count must be 0 after reset");
            h.assertTrue(buffer.getCapacity() == 600, "Buffer capacity must reset to 600 frames (30s default)");
            h.assertTrue(TimeStopConfig.COMMON.rewindHistorySeconds.get() == 30, "Config rewindHistorySeconds must be reset to 30");

            // Seed frames again and test root /timestop buffer reset
            buffer.resizeSeconds(60);
            TimeStopConfig.COMMON.rewindHistorySeconds.set(60);
            for (int i = 0; i < 20; i++) {
                buffer.pushFrame(new com.timestop.core.rewind.data.TickFrame(i));
            }
            dispatcher.execute("timestop buffer reset", source);
            h.assertTrue(buffer.getFrameCount() == 0, "Buffer frame count must be 0 after /timestop buffer reset");
            h.assertTrue(buffer.getCapacity() == 600, "Buffer capacity must reset to 600 frames after /timestop buffer reset");

            // Seed frames again and test /timestop buffer clear (keeps capacity, clears frames)
            buffer.resizeSeconds(45);
            for (int i = 0; i < 10; i++) {
                buffer.pushFrame(new com.timestop.core.rewind.data.TickFrame(i));
            }
            dispatcher.execute("timestop buffer clear", source);
            h.assertTrue(buffer.getFrameCount() == 0, "Buffer frame count must be 0 after clear");
            h.assertTrue(buffer.getCapacity() == 900, "Buffer capacity (45s = 900) must be retained after clear");

        } finally {
            TimeStopConfig.COMMON.rewindHistorySeconds.set(oldSec);
            recorder.clearTrackingData();
            buffer.clear();
            buffer.resizeSeconds(oldSec);
            player.discard();
        }

        h.succeed();
    }
}
