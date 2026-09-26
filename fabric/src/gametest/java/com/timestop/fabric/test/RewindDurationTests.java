package com.timestop.fabric.test;
import com.timestop.core.*;
import com.timestop.core.rewind.*;
import com.timestop.core.rewind.data.*;
import com.timestop.config.TimeStopConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public class RewindDurationTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="rewind_memory_scaling")
    public void memoryScalesOnStartupAndResizeWithoutCompounding(GameTestHelper h) {
        long mb=1024L*1024;
        var buffer=new TimelineBuffer(600,50*mb);
        buffer.resizeSeconds(60);
        h.assertTrue(buffer.getMaxMemoryBytes()==100*mb,"60 seconds must receive 100 MB from the default 50 MB baseline");
        for(int i=0;i<1200;i++) buffer.pushFrame(new TickFrame(i) {
            @Override public int getEstimatedMemoryBytes(){return 80*1024;}
        });
        h.assertTrue(buffer.getFrameCount()==1200 && buffer.getMemoryEvictedFrames()==0,"A 94 MB simulated history must retain the complete 60 seconds");
        buffer.resizeSeconds(30);buffer.resizeSeconds(60);buffer.resizeSeconds(60);
        h.assertTrue(buffer.getMaxMemoryBytes()==100*mb && buffer.getFrameCount()==600,"Repeated resizing must not compound the budget or invent history");
        h.assertTrue(new TimelineBuffer(1200,50*mb).getMaxMemoryBytes()==100*mb,"Restart must use the same scaled budget");
        h.assertTrue(new TimelineBuffer(1200,80*mb).getMaxMemoryBytes()==160*mb,"Custom baseline budgets must scale too");
        var small=new TimelineBuffer(600,100);
        small.pushFrame(new TickFrame(0));small.pushFrame(new TickFrame(1));
        h.assertTrue(small.getMemoryEvictedFrames()==1,"Status must expose history lost to memory pressure");
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="rewind_saved_duration")
    public void durationAndModeSurviveConfigReload(GameTestHelper h) throws Exception {
        var config = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("timestop.json");
        h.assertTrue(java.nio.file.Files.exists(config), "Loader must initialize the config path at startup");
        var temp = java.nio.file.Files.createTempFile("rewind-config-test", ".json");
        int old = TimeStopConfig.COMMON.rewindHistorySeconds.get();
        String mode = TimeStopConfig.COMMON.rewindMode.get();
        try {
            TimeStopConfig.setConfigFile(temp.toFile());
            TimeStopConfig.COMMON.rewindHistorySeconds.set(60); TimeStopConfig.COMMON.rewindMode.set("CONTINUOUS");
            TimeStopConfig.save();
            TimeStopConfig.COMMON.rewindHistorySeconds.set(10); TimeStopConfig.COMMON.rewindMode.set("BURST");
            TimeStopConfig.load();
            h.assertTrue(TimeStopConfig.rewindDurationSeconds()==60 && TimeStopConfig.COMMON.rewindMode.get().equals("CONTINUOUS"), "Saved duration and mode must reload");
        } finally {
            TimeStopConfig.setConfigFile(config.toFile());
            TimeStopConfig.COMMON.rewindHistorySeconds.set(old); TimeStopConfig.COMMON.rewindMode.set(mode);
            java.nio.file.Files.deleteIfExists(temp);
        }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="rewind_real_server_minute",timeoutTicks=2500)
    public void recordAndReplaySixtySecondsThroughServerTicks(GameTestHelper h) {
        var player=RewindRuneTests.survivalPlayer(h);
        var pos=h.absolutePos(new BlockPos(2,2,2));player.setPos(pos.getX(),pos.getY()+1,pos.getZ());
        int old=TimeStopConfig.COMMON.rewindHistorySeconds.get();String mode=TimeStopConfig.COMMON.rewindMode.get();
        var settings=TimeStopSavedData.get();var scope=settings.getWatchScope();
        var recorder=TickRecorder.getInstance();
        TimeStopConfig.COMMON.rewindHistorySeconds.set(60);TimeStopConfig.COMMON.rewindMode.set("CONTINUOUS");settings.setWatchScope(TimeStopSavedData.WatchScope.SPHERE);
        recorder.clearTrackingData();recorder.getTimelineBuffer().clear();recorder.getTimelineBuffer().resizeSeconds(60);
        h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),18);
        h.runAfterDelay(20,()->h.getLevel().setBlock(pos,Blocks.GOLD_BLOCK.defaultBlockState(),18));
        h.runAfterDelay(1205,()->{
            h.assertTrue(recorder.getTimelineBuffer().getFrameCount()==1200,"Natural server ticks must fill all 1200 frames");
            TimeStopManager.startTimeStop(h.getLevel(),player,200,TimeMode.REWIND);
        });
        h.runAfterDelay(1505,()->h.assertTrue(LocalRewind.isActive(player.getUUID()),"Real playback must remain active past ten seconds"));
        h.runAfterDelay(2390,()->h.assertTrue(LocalRewind.isActive(player.getUUID()),"Real playback must remain active near sixty seconds"));
        h.runAfterDelay(2410,()->{
            try {
                h.assertTrue(!LocalRewind.isActive(player.getUUID()),"Playback must finish after sixty seconds");
                h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"Playback must restore the naturally recorded event near the oldest end");
                h.succeed();
            } finally {
                LocalRewind.cancel(player.getUUID());player.discard();settings.setWatchScope(scope);
                TimeStopConfig.COMMON.rewindHistorySeconds.set(old);TimeStopConfig.COMMON.rewindMode.set(mode);
                recorder.clearTrackingData();recorder.getTimelineBuffer().clear();recorder.getTimelineBuffer().resizeSeconds(old);
            }
        });
    }
    private static void seed(GameTestHelper h,BlockPos pos) {
        var recorder=TickRecorder.getInstance();recorder.clearTrackingData();var buffer=recorder.getTimelineBuffer();buffer.clear();buffer.resizeSeconds(60);
        h.getLevel().setBlock(pos,Blocks.GOLD_BLOCK.defaultBlockState(),18);recorder.clearTrackingData();
        for(int i=0;i<1200;i++) {
            var frame=new TickFrame(i);frame.captureEnvironment(h.getLevel());
            if(i==0)frame.addBlockDelta(BlockDelta.create(h.getLevel().dimension(),pos,Blocks.STONE.defaultBlockState(),Blocks.GOLD_BLOCK.defaultBlockState(),null,null));
            buffer.pushFrame(frame);
        }
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="rewind_default_duration")
    public void defaultCommandUsesOldestOfSixtySeconds(GameTestHelper h) throws Exception {
        int old=TimeStopConfig.COMMON.rewindHistorySeconds.get();
        var recorder=TickRecorder.getInstance();var pos=h.absolutePos(new BlockPos(2,2,2));
        try {
            TimeStopConfig.COMMON.rewindHistorySeconds.set(60);seed(h,pos);
            var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
            com.timestop.command.TimeStopCommand.register(dispatcher);
            dispatcher.execute("timestop rewind",h.getLevel().getServer().createCommandSourceStack().withPermission(4));
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"Default burst must reach an event 60 seconds back, not stop at 10 seconds");
        } finally {TimeStopConfig.COMMON.rewindHistorySeconds.set(old);recorder.clearTrackingData();recorder.getTimelineBuffer().clear();recorder.getTimelineBuffer().resizeSeconds(old);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="rewind_watch_duration")
    public void watchContinuousIgnoresShortWatchTimer(GameTestHelper h) {
        var player=RewindRuneTests.survivalPlayer(h);var pos=h.absolutePos(new BlockPos(2,2,2));player.setPos(pos.getX(),pos.getY()+1,pos.getZ());
        int old=TimeStopConfig.COMMON.rewindHistorySeconds.get();String mode=TimeStopConfig.COMMON.rewindMode.get();var scope=TimeStopSavedData.get().getWatchScope();
        var recorder=TickRecorder.getInstance();
        try {
            TimeStopConfig.COMMON.rewindHistorySeconds.set(60);TimeStopConfig.COMMON.rewindMode.set("CONTINUOUS");TimeStopSavedData.get().setWatchScope(TimeStopSavedData.WatchScope.SPHERE);
            seed(h,pos);TimeStopManager.startTimeStop(h.getLevel(),player,20,TimeMode.REWIND);
            for(int i=0;i<1199;i++)LocalRewind.tick(h.getLevel().getServer());
            h.assertTrue(LocalRewind.isActive(player.getUUID()),"A short watch timer must not truncate a 60-second rewind");
            LocalRewind.tick(h.getLevel().getServer());
            h.assertTrue(!LocalRewind.isActive(player.getUUID()) && h.getLevel().getBlockState(pos).is(Blocks.STONE),"Watch must replay all 1200 available intervals");
        } finally {LocalRewind.cancel(player.getUUID());player.discard();TimeStopConfig.COMMON.rewindHistorySeconds.set(old);TimeStopConfig.COMMON.rewindMode.set(mode);TimeStopSavedData.get().setWatchScope(scope);recorder.clearTrackingData();recorder.getTimelineBuffer().clear();recorder.getTimelineBuffer().resizeSeconds(old);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="rewind_death_duration")
    public void deathContinuousIsNotCappedAtSixSeconds(GameTestHelper h) {
        var player=RewindRuneTests.survivalPlayer(h);var pos=h.absolutePos(new BlockPos(2,2,2));player.setPos(pos.getX(),pos.getY()+1,pos.getZ());
        int old=TimeStopConfig.COMMON.rewindHistorySeconds.get();String mode=TimeStopConfig.COMMON.rewindMode.get();var settings=TimeStopSavedData.get();var scope=settings.getWatchScope();boolean automatic=settings.isAutoDeathRewind();
        TimeStopConfig.COMMON.rewindHistorySeconds.set(60);TimeStopConfig.COMMON.rewindMode.set("CONTINUOUS");settings.setWatchScope(TimeStopSavedData.WatchScope.SPHERE);settings.setAutoDeathRewind(true);
        seed(h,pos);com.timestop.combat.RewindRuneManager.tryTriggerDeathRewind(player,player.damageSources().generic());
        h.runAfterDelay(3,()->{
            try {
                for(int i=0;i<200;i++)LocalRewind.tick(h.getLevel().getServer());
                h.assertTrue(LocalRewind.isActive(player.getUUID()),"Death rewind must still be playing after the former six-second limit");
                h.succeed();
            } finally {
                LocalRewind.cancel(player.getUUID());com.timestop.combat.RewindRuneManager.clearPlayer(player.getUUID());player.discard();settings.setAutoDeathRewind(automatic);settings.setWatchScope(scope);TimeStopConfig.COMMON.rewindHistorySeconds.set(old);TimeStopConfig.COMMON.rewindMode.set(mode);
                var recorder=TickRecorder.getInstance();recorder.clearTrackingData();recorder.getTimelineBuffer().clear();recorder.getTimelineBuffer().resizeSeconds(old);
            }
        });
    }

    @GameTest(template=EMPTY_STRUCTURE, batch="rewind_heavy_retention", timeoutTicks=400)
    public void heavyWorkloadRetentionAndRestorationAcrossMilestones(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var recorder = TickRecorder.getInstance();
        var settings = TimeStopSavedData.get();

        int oldSeconds = TimeStopConfig.COMMON.rewindHistorySeconds.get();
        int oldCap = TimeStopConfig.COMMON.rewindMemoryCapMB.get();
        String oldMode = TimeStopConfig.COMMON.rewindMode.get();
        var oldScope = settings.getWatchScope();

        TimeStopConfig.COMMON.rewindHistorySeconds.set(60);
        TimeStopConfig.COMMON.rewindMemoryCapMB.set(50);
        TimeStopConfig.COMMON.rewindMode.set("CONTINUOUS");
        settings.setWatchScope(TimeStopSavedData.WatchScope.SPHERE);

        recorder.reset();
        var buffer = recorder.getTimelineBuffer();
        buffer.resizeSeconds(60);

        var player = RewindRuneTests.survivalPlayer(h);
        for (int s = 0; s < 36; s++) {
            var stack = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> tag.putByteArray("heavy_item_nbt", new byte[6144]));
            player.getInventory().setItem(s, stack);
        }
        player.getInventory().setChanged();

        List<LivingEntity> entities = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            var mob = h.spawnWithNoFreeWill(EntityType.PIG, 1 + (i % 6), 2, 1 + (i / 6));
            mob.addTag("heavy_mod_entity_data_" + "x".repeat(5000));
            entities.add(mob);
        }

        var posMilestone = h.absolutePos(new BlockPos(2, 2, 2));
        var posBubbleInside = h.absolutePos(new BlockPos(3, 2, 2));
        var posBubbleOutside = h.absolutePos(new BlockPos(8, 2, 8));

        level.setBlock(posMilestone, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(posBubbleInside, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        level.setBlock(posBubbleOutside, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);

        player.setHealth(20.0F);
        player.getFoodData().setFoodLevel(20);
        var entity0 = entities.get(0);
        entity0.setHealth(10.0F);

        try {
            for (int tick = 0; tick < 1200; tick++) {
                recorder.beginFrame(server);

                if (tick == 100) {
                    level.setBlock(posMilestone, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                    player.setHealth(18.0F);
                    entity0.setHealth(8.0F);
                    entity0.setPos(posMilestone.getX(), posMilestone.getY() + 1, posMilestone.getZ());
                } else if (tick == 600) {
                    level.setBlock(posMilestone, Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
                    player.setHealth(14.0F);
                    entity0.setHealth(6.0F);
                    entity0.setPos(posMilestone.getX() + 1, posMilestone.getY() + 1, posMilestone.getZ());
                } else if (tick == 900) {
                    level.setBlock(posMilestone, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                    player.setHealth(10.0F);
                    entity0.setHealth(4.0F);
                    entity0.setPos(posMilestone.getX() + 2, posMilestone.getY() + 1, posMilestone.getZ());
                } else if (tick == 1150) {
                    level.setBlock(posMilestone, Blocks.NETHERITE_BLOCK.defaultBlockState(), 3);
                    level.setBlock(posBubbleInside, Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(posBubbleOutside, Blocks.AIR.defaultBlockState(), 3);
                    player.setHealth(6.0F);
                    entity0.setHealth(2.0F);
                    entity0.setPos(posMilestone.getX() + 3, posMilestone.getY() + 1, posMilestone.getZ());
                }

                LivingEntity mob = entities.get(tick % entities.size());
                mob.setPos(mob.getX() + 0.005, mob.getY(), mob.getZ());

                recorder.finishFrame(server);
            }

            // 1. History Retention & Memory Budget Assertions
            h.assertTrue(buffer.getFrameCount() == 1200, "History buffer must retain exactly 1200 frames for 60 seconds");
            h.assertTrue(buffer.getMemoryEvictedFrames() == 0, "Zero frames must be evicted by memory budget under heavy 32-entity + 36-slot workload");
            h.assertTrue(buffer.getTotalEstimatedBytes() <= buffer.getMaxMemoryBytes(), "Total estimated bytes must be within scaled 100 MB budget");
            h.assertTrue(buffer.getTotalEstimatedBytes() < 25 * 1024 * 1024L, "Structural optimization must keep 1200 heavy frames under 25 MB");
            h.assertTrue(buffer.getActiveEntitySnapshotsCount() <= 35, "Shared entity snapshots must be deduped without per-tick duplication");
            h.assertTrue(buffer.getActiveInventoriesCount() <= 5, "Shared player inventory must be deduped without per-tick cloning");

            // 2. Milestone Restorations
            // 15s ago (~280 ticks back)
            var plan15 = RewindExecutor.buildPlan(buffer.getFramesForRewind(280));
            RewindExecutor.applyPlan(server, plan15, true);
            h.assertTrue(level.getBlockState(posMilestone).is(Blocks.GOLD_BLOCK), "15s rewind must restore block to Gold Block");
            h.assertTrue(Math.abs(player.getHealth() - 10.0F) < 0.01F, "15s rewind must restore player health to 10.0");
            h.assertTrue(Math.abs(entity0.getHealth() - 4.0F) < 0.01F, "15s rewind must restore entity health to 4.0");

            // 30s ago (~580 ticks back)
            var plan30 = RewindExecutor.buildPlan(buffer.getFramesForRewind(580));
            RewindExecutor.applyPlan(server, plan30, true);
            h.assertTrue(level.getBlockState(posMilestone).is(Blocks.EMERALD_BLOCK), "30s rewind must restore block to Emerald Block");
            h.assertTrue(Math.abs(player.getHealth() - 14.0F) < 0.01F, "30s rewind must restore player health to 14.0");
            h.assertTrue(Math.abs(entity0.getHealth() - 6.0F) < 0.01F, "30s rewind must restore entity health to 6.0");

            // 55s ago (~1080 ticks back)
            var plan55 = RewindExecutor.buildPlan(buffer.getFramesForRewind(1080));
            RewindExecutor.applyPlan(server, plan55, true);
            h.assertTrue(level.getBlockState(posMilestone).is(Blocks.DIAMOND_BLOCK), "55s rewind must restore block to Diamond Block");
            h.assertTrue(Math.abs(player.getHealth() - 18.0F) < 0.01F, "55s rewind must restore player health to 18.0");
            h.assertTrue(Math.abs(entity0.getHealth() - 8.0F) < 0.01F, "55s rewind must restore entity health to 8.0");

            // Full 60s ago (1200 ticks back)
            var plan60 = RewindExecutor.buildPlan(buffer.getFramesForRewind(1200));
            RewindExecutor.applyPlan(server, plan60, true);
            h.assertTrue(level.getBlockState(posMilestone).is(Blocks.STONE), "60s rewind must restore block to Stone");
            h.assertTrue(Math.abs(player.getHealth() - 20.0F) < 0.01F, "60s rewind must restore player health to 20.0");
            h.assertTrue(Math.abs(entity0.getHealth() - 10.0F) < 0.01F, "60s rewind must restore entity health to 10.0");

            // 3. Bubble Rewind Boundary Confinement
            level.setBlock(posBubbleInside, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(posBubbleOutside, Blocks.AIR.defaultBlockState(), 3);
            var bubbleScope = new RewindScope(level.dimension(), Vec3.atCenterOf(posBubbleInside), 3.0);
            var rawPlan = RewindExecutor.buildPlan(buffer.getFramesForRewind(1200));
            var bubblePlan = bubbleScope.filter(server, rawPlan);
            RewindExecutor.applyPlan(server, bubblePlan, true);
            h.assertTrue(level.getBlockState(posBubbleInside).is(Blocks.DIAMOND_BLOCK), "Bubble rewind must restore block inside the sphere");
            h.assertTrue(level.getBlockState(posBubbleOutside).is(Blocks.AIR), "Bubble rewind must NOT restore block outside the sphere");

            // 4. Resize and Trimming Behavior
            buffer.resizeSeconds(30);
            h.assertTrue(buffer.getFrameCount() == 600, "Resizing down to 30s must trim to 600 frames");
            h.assertTrue(buffer.getMaxMemoryBytes() == 50 * 1024 * 1024L, "30s budget must scale to 50 MB");
            h.assertTrue(buffer.getMemoryEvictedFrames() == 0, "Trimming on resize must not count as out-of-memory eviction");

            buffer.resizeSeconds(60);
            h.assertTrue(buffer.getFrameCount() == 600, "Resizing back to 60s must retain surviving 600 frames without inventing history");
            h.assertTrue(buffer.getMaxMemoryBytes() == 100 * 1024 * 1024L, "60s budget must scale back to 100 MB without compounding");

            // Repeated frame queries must be idempotent
            long trackedBefore = buffer.getTotalEstimatedBytes();
            buffer.getFramesForRewind(600);
            buffer.getFramesForRewind(600);
            h.assertTrue(buffer.getTotalEstimatedBytes() == trackedBefore, "Multiple rewind queries must not alter memory tracking");

            // Clear releases memory and references
            buffer.clear();
            h.assertTrue(buffer.getFrameCount() == 0, "clear() must empty the buffer");
            h.assertTrue(buffer.getBufferRetainedBytes() == 0, "clear() must reset retained buffer memory to 0");
            h.assertTrue(buffer.getActiveEntitySnapshotsCount() == 0, "clear() must remove all tracked entity snapshot references");
            h.assertTrue(buffer.getActiveInventoriesCount() == 0, "clear() must remove all tracked inventory references");

            h.succeed();
        } finally {
            for (var mob : entities) mob.discard();
            player.discard();
            TimeStopConfig.COMMON.rewindHistorySeconds.set(oldSeconds);
            TimeStopConfig.COMMON.rewindMemoryCapMB.set(oldCap);
            TimeStopConfig.COMMON.rewindMode.set(oldMode);
            settings.setWatchScope(oldScope);
            recorder.reset();
        }
    }
}
