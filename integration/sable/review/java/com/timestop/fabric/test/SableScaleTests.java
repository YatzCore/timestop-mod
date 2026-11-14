package com.timestop.fabric.test;

import com.timestop.compat.TemporalWorlds;
import com.timestop.compat.sable.SableBodyTimeManager;
import com.timestop.config.TimeStopConfig;
import com.timestop.core.*;
import com.timestop.core.rewind.*;
import com.timestop.core.rewind.data.TickFrame;
import com.timestop.item.WatchTier;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class SableScaleTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_scale_1", timeoutTicks = 600)
    public void oneBody(GameTestHelper h) throws Exception { exercise(h, 1); }
    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_scale_16", timeoutTicks = 600)
    public void sixteenBodies(GameTestHelper h) throws Exception { exercise(h, 16); }
    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_scale_64", timeoutTicks = 600)
    public void sixtyFourBodies(GameTestHelper h) throws Exception { exercise(h, 64); }

    private static void exercise(GameTestHelper h, int count) throws Exception {
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var bodies = new ArrayList<ServerSubLevel>();
        var holds = new ArrayList<PhysicsConstraintHandle>();
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
        var stateField = SableBodyTimeManager.class.getDeclaredField("states"); stateField.setAccessible(true);
        var states = (Map<?, ?>) stateField.get(null);
        int initialStates = states.size();
        int substeps = physics.getConfig().substepsPerTick;
        double slow = TimeStopConfig.COMMON.slowMotionRate.get(), fast = TimeStopConfig.COMMON.fastForwardRate.get();
        try {
            physics.getConfig().substepsPerTick = 4;
            for (int i = 0; i < count; i++) {
                var pos = new BlockPos(1000 + 4 * (i % 8), 180, 1000 + 4 * (i / 8));
                h.getLevel().getChunkAt(pos);
                h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                bodies.add(SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), pos, Set.of(pos),
                        new dev.ryanhcode.sable.companion.math.BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ())));
            }
            physics.tick(container);
            var baseline = new TickFrame(0);
            TemporalWorlds.bridge().captureSubLevelsBegin(h.getLevel().getServer(), baseline);
            var plan = RewindExecutor.buildPlan(List.of(baseline));
            for (double rate : new double[]{1, .25, 5, 50}) {
                RewindExecutor.applyPlan(h.getLevel().getServer(), plan, false);
                if (rate != 1) {
                    TimeStopConfig.COMMON.slowMotionRate.set(.25);
                    TimeStopConfig.COMMON.fastForwardRate.set(rate > 1 ? rate : 5.0);
                    field(h, rate < 1 ? TimeMode.SLOW_MOTION : TimeMode.FAST_FORWARD);
                }
                long start = System.nanoTime();
                for (int tick = 0; tick < 2; tick++) physics.tick(container);
                double millis = (System.nanoTime() - start) / 2_000_000.0;
                for (var body : bodies) h.assertTrue(body.logicalPose().position().isFinite(), "Scale workload must retain valid native poses");
                com.timestop.TimeStopMod.LOGGER.info("SABLE_SCALE bodies={} rate={} substeps=4 msPerTick={}", count, rate, millis);
                TemporalBubbleManager.stopAllBubbles(h.getLevel());
            }
            var history = new TimelineBuffer(40, 8L * 1024 * 1024);
            for (int cycle = 0; cycle < 10; cycle++) {
                field(h, TimeMode.TIME_STOP);
                physics.tick(container);
                for (var body : bodies) {
                    var state = SableBodyTimeManager.getState(body.getUniqueId());
                    h.assertTrue(state.isHeld && state.constraintHandle != null && state.constraintHandle.isValid(), "Every stopped body must own one valid hold");
                    holds.add(state.constraintHandle);
                }
                RewindExecutor.applyPlan(h.getLevel().getServer(), plan, false);
                for (var body : bodies) holds.add(SableBodyTimeManager.getState(body.getUniqueId()).constraintHandle);
                TemporalBubbleManager.stopAllBubbles(h.getLevel());
                physics.tick(container);
                for (var hold : holds) h.assertTrue(!hold.isValid(), "Rewind/thaw must release every superseded native hold");
                var frame = new TickFrame(cycle);
                TemporalWorlds.bridge().captureSubLevelsBegin(h.getLevel().getServer(), frame);
                history.pushFrame(frame);
                h.assertTrue(history.getTotalEstimatedBytes() <= history.getMaxMemoryBytes(), "Retained history must respect the configured estimator budget");
            }
            com.timestop.TimeStopMod.LOGGER.info("SABLE_SCALE cycles=10 bodies={} retainedFrames={} retainedBytes={} removedHolds={}",
                    count, history.getFrameCount(), history.getTotalEstimatedBytes(), holds.size());
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            for (var body : bodies) if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            physics.getConfig().substepsPerTick = substeps;
            TimeStopConfig.COMMON.slowMotionRate.set(slow); TimeStopConfig.COMMON.fastForwardRate.set(fast);
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
        }
        h.assertTrue(states.size() == initialStates, "Removing the workload must return body-state ownership to baseline");
        h.succeed();
    }

    private static void field(GameTestHelper h, TimeMode mode) {
        TemporalBubbleManager.putStationaryBubble(h.getLevel(), new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(),
                h.getLevel().dimension(), new Vec3(1014, 180, 1014), 2048, mode, 1000,
                WatchTier.DIAMOND, null, 0, Set.of()).stationary(true), null);
    }
}
