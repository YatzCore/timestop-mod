package com.timestop.fabric.test;

import com.timestop.compat.sable.SableBodyTimeManager;
import com.timestop.core.TemporalBubble;
import com.timestop.core.TemporalBubbleManager;
import com.timestop.core.TimeMode;
import com.timestop.core.rewind.*;
import com.timestop.core.rewind.data.*;
import com.timestop.item.WatchTier;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.util.*;

public class SableReadinessTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_global_reject_start", timeoutTicks = 150)
    public void globalPlaybackRejectsUnloadedDependencyBeforeChangingTime(GameTestHelper h) {
        checkGlobalUnloadFailure(h, true);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_global_reject_frame", timeoutTicks = 150)
    public void globalPlaybackPreservesHistoryWhenDependencyUnloadsBeforeNextFrame(GameTestHelper h) {
        checkGlobalUnloadFailure(h, false);
    }

    private void checkGlobalUnloadFailure(GameTestHelper h, boolean unloadBeforeStart) {
        var body = assemble(h, 0);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        var buffer = recorder.getTimelineBuffer();
        int reservedIndex = -1;
        for (int x = 0; x < 1 << container.getLogSideLength(); x++) for (int z = 0; z < 1 << container.getLogSideLength(); z++)
            if (container.getSubLevel(x, z) == body) reservedIndex = container.getIndex(x, z);
        try {
            recorder.clearTrackingData(); buffer.clear();
            recorder.serverTick(h.getLevel().getServer());
            recorder.serverTick(h.getLevel().getServer());
            recorder.finishFrame(h.getLevel().getServer());
            int retained = buffer.getFrameCount();
            h.assertTrue(retained >= 2, "Fixture requires retained playback frames");
            if (unloadBeforeStart) {
                freeze(h, body, 1);
                container.removeSubLevel(body, SubLevelRemovalReason.UNLOADED);
            }
            com.timestop.core.TimeStopManager.startContinuousRewind(h.getLevel(), null, 2, null);
            if (unloadBeforeStart) {
                h.assertTrue(TemporalBubbleManager.hasActiveBubbles(), "Rejected global activation must preserve existing bubbles");
            } else {
                h.assertTrue(com.timestop.core.TimeStopManager.isGlobalTimeStopActive(), "Valid global playback must start");
                container.removeSubLevel(body, SubLevelRemovalReason.UNLOADED);
                com.timestop.core.TimeStopManager.serverTick();
            }
            h.assertTrue(!com.timestop.core.TimeStopManager.isGlobalTimeStopActive() && buffer.isRecording() && !buffer.isRewinding(),
                    "Validation rejection must leave normal time and recording");
            h.assertTrue(buffer.getFrameCount() == retained, "Rejected frame must retain all history for retry after loading the ship");
            h.assertTrue(container.getSubLevel(body.getUniqueId()) == null && container.getOccupancy().get(reservedIndex),
                    "Validation rejection must preserve the unloaded ship reservation");
        } finally {
            com.timestop.core.TimeStopManager.resumeTime(h.getLevel());
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            if (reservedIndex >= 0) container.getOccupancy().clear(reservedIndex);
            recorder.clearTrackingData(); buffer.clear();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_assembly_ship_scope", timeoutTicks = 150)
    public void localAssemblyRewindReturnsOutsideSourceInventoryExactlyOnce(GameTestHelper h) {
        checkAssemblyRewind(h, false);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_assembly_world_scope", timeoutTicks = 150)
    public void localWorldCounterpartRewindAlsoRemovesShipOutsideSphere(GameTestHelper h) {
        checkAssemblyRewind(h, true);
    }

    private void checkAssemblyRewind(GameTestHelper h, boolean selectWorldCounterpart) {
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        var source = new BlockPos(260, 150, 200);
        h.getLevel().getChunkAt(source);
        h.getLevel().setBlockAndUpdate(source, Blocks.CHEST.defaultBlockState());
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(source);
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
        ServerSubLevel body = null;
        try {
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
            recorder.serverTick(h.getLevel().getServer());
            body = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), source, Set.of(source),
                    new dev.ryanhcode.sable.companion.math.BoundingBox3i(260, 150, 200, 260, 150, 200));
            container.physicsSystem().getPipeline().teleport(body, new Vector3d(200.5, 150.5, 200.5), new Quaterniond());
            container.physicsSystem().updatePose(body);
            recorder.serverTick(h.getLevel().getServer());
            var center = selectWorldCounterpart ? Vec3.atCenterOf(source) : new Vec3(200.5, 150.5, 200.5);
            var scope = new RewindScope(h.getLevel().dimension(), center, 5);
            h.assertTrue(RewindExecutor.execute(h.getLevel().getServer(), 2, null, false, scope).success(), "Assembly rewind must succeed");
            h.assertTrue(container.getSubLevel(body.getUniqueId()) == null, "Rewind must remove the assembled ship even when only its source is selected");
            var restored = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(source);
            h.assertTrue(restored != null && restored.getItem(0).is(net.minecraft.world.item.Items.DIAMOND) && restored.getItem(0).getCount() == 7,
                    "Rewind must return all seven diamonds to the original world chest exactly once");
            var remaining = RewindExecutor.buildPlan(recorder.getTimelineBuffer().getFramesForRewind(40));
            h.assertTrue(!remaining.subLevelBlockDependencies().containsKey(body.getUniqueId()), "Consumed assembly links must not remain in history");
        } finally {
            if (body != null && !body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            net.minecraft.world.Clearable.tryClear(h.getLevel().getBlockEntity(source));
            h.getLevel().setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_disassembly_transfer", timeoutTicks = 150)
    public void localDisassemblyRewindRemovesOutsideCounterpartAndRestoresChestOnce(GameTestHelper h) {
        checkDisassemblyRewind(h, false);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_disassembly_playback", timeoutTicks = 150)
    public void continuousDisassemblyRewindKeepsOutsideCounterpartSelectedAndRejectsConflicts(GameTestHelper h) {
        checkDisassemblyRewind(h, true);
    }

    private void checkDisassemblyRewind(GameTestHelper h, boolean continuous) {
        var body = assemble(h, 0);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        var bodyId = body.getUniqueId();
        var plotPos = body.getPlot().getCenterBlock();
        var output = new BlockPos(260, 150, 200);
        var owner = continuous ? player(h) : null;
        h.getLevel().getChunkAt(output);
        h.getLevel().setBlockAndUpdate(output, Blocks.AIR.defaultBlockState());
        try {
            h.getLevel().setBlockAndUpdate(plotPos, Blocks.CHEST.defaultBlockState());
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(plotPos);
            chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
            container.physicsSystem().tick(container);
            var center = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(center.x, center.y, center.z), 5);
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
            recorder.serverTick(h.getLevel().getServer());
            SubLevelAssemblyHelper.moveBlocks(h.getLevel(), new SubLevelAssemblyHelper.AssemblyTransform(plotPos, output, 0,
                    net.minecraft.world.level.block.Rotation.NONE, h.getLevel()), Set.of(plotPos));
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            h.assertTrue(h.getLevel().getBlockEntity(output) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity,
                    "Native disassembly must transfer the chest to the outside world");
            recorder.serverTick(h.getLevel().getServer());
            if (continuous) {
                var movedChest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(output);
                recorder.observeBlockEntity(movedChest);
                movedChest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
                movedChest.setChanged();
                recorder.serverTick(h.getLevel().getServer());
                h.assertTrue(LocalRewind.start(owner, scope, 10, () -> {}), "Continuous disassembly rewind must start");
                h.assertTrue(LocalRewind.contains(h.getLevel().dimension(), output), "Outside counterpart must stay frozen for the entire job");
                var conflicting = new RewindScope(h.getLevel().dimension(), Vec3.atCenterOf(output), 5);
                h.assertTrue(!RewindExecutor.execute(h.getLevel().getServer(), 2, null, false, conflicting).success(),
                        "Burst rewind must reject an assembly counterpart owned by active playback");
                boolean restoredIntermediate = false;
                for (int tick = 0; tick < 10 && LocalRewind.isActive(owner.getUUID()); tick++) {
                    LocalRewind.tick(h.getLevel().getServer());
                    var intermediate = h.getLevel().getBlockEntity(output);
                    if (intermediate instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c && c.getItem(0).getCount() == 7)
                        restoredIntermediate = true;
                }
                h.assertTrue(restoredIntermediate, "Intermediate inventory frame outside the sphere must be restored before undoing disassembly");
                h.assertTrue(!LocalRewind.isActive(owner.getUUID()), "Playback must complete");
            } else {
                h.assertTrue(RewindExecutor.execute(h.getLevel().getServer(), 2, null, false, scope).success(), "Local disassembly rewind must succeed");
            }
            h.assertTrue(h.getLevel().getBlockState(output).isAir(), "Rewind must remove the disassembled counterpart outside the sphere");
            var restored = (ServerSubLevel) container.getSubLevel(bodyId);
            h.assertTrue(restored != null, "Rewind must restore the original ship identity");
            var restoredChest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(restored.getPlot().getCenterBlock());
            h.assertTrue(restoredChest != null && restoredChest.getItem(0).getCount() == 7
                    && restoredChest.getItem(0).is(net.minecraft.world.item.Items.DIAMOND), "Seven diamonds must exist in the restored ship exactly once");
        } finally {
            if (owner != null) { LocalRewind.cancel(owner.getUUID()); h.getLevel().getServer().getPlayerList().remove(owner); }
            var live = container.getSubLevel(bodyId);
            if (live != null && !live.isRemoved()) container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
            net.minecraft.world.Clearable.tryClear(h.getLevel().getBlockEntity(output));
            h.getLevel().setBlockAndUpdate(output, Blocks.AIR.defaultBlockState());
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_resize_playback", timeoutTicks = 100)
    public void shrinkingHistoryCancelsPlaybackThatRetainsExcessMemory(GameTestHelper h) {
        var recorder = TickRecorder.getInstance();
        var original = recorder.getTimelineBuffer();
        var buffer = new TimelineBuffer(1200, 1024 * 1024);
        var owner = player(h);
        recorder.setTimelineBuffer(buffer);
        try {
            // A controlled allocation estimate avoids allocating megabytes solely for this budget test.
            var frame = new TickFrame(1) {
                @Override public long getEstimatedMemoryBytes() { return 1536 * 1024; }
            };
            buffer.pushFrame(frame);
            var scope = new RewindScope(h.getLevel().dimension(), owner.position(), 5);
            h.assertTrue(LocalRewind.start(owner, scope, 1, () -> {}), "Playback must fit the original two-MiB budget");
            buffer.resizeSeconds(30);
            h.assertTrue(!LocalRewind.isActive(owner.getUUID()), "Shrinking to one MiB must release playback-owned history above budget");
            h.assertTrue(buffer.getTotalEstimatedBytes() <= buffer.getMaxMemoryBytes(), "Resize must enforce the total retained budget immediately");
        } finally {
            LocalRewind.clear(); recorder.setTimelineBuffer(original);
            h.getLevel().getServer().getPlayerList().remove(owner);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_global_playback", timeoutTicks = 200)
    public void globalContinuousRewindRestoresMotionAndDiscardsPlaybackImpulses(GameTestHelper h) {
        var body = assemble(h, 0);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var recorder = TickRecorder.getInstance();
        var buffer = recorder.getTimelineBuffer();
        recorder.clearTrackingData(); buffer.clear();
        try {
            physics.tick(container);
            RigidBodyHandle.of(body).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d(0, .2, 0));
            for (int tick = 0; tick < 8; tick++) { recorder.serverTick(h.getLevel().getServer()); physics.tick(container); }
            com.timestop.core.TimeStopManager.startContinuousRewind(h.getLevel(), null, 3, null);
            h.assertTrue(com.timestop.core.TimeStopManager.isTimeStopped(h.getLevel())
                    && com.timestop.core.TimeStopManager.getCurrentMode() == TimeMode.REWIND, "Global playback must start");
            for (int tick = 0; tick < 3; tick++) {
                var expected = RewindExecutor.buildPlan(buffer.getFramesForRewind(1)).subLevelTargetStates().get(body.getUniqueId());
                h.assertTrue(expected != null, "Each playback frame must contain the ship");
                com.timestop.core.TimeStopManager.serverTick();
                h.assertTrue(body.logicalPose().position().distance(expected.position()) < 1e-3, "Global playback must apply the recorded pose");
                if (tick < 2) {
                    var applied = new Vector3d(body.logicalPose().position());
                    physics.getPipeline().applyLinearAndAngularImpulse(body, new Vector3d(1e7, 0, 0), new Vector3d(0, 1e7, 0), true);
                    physics.tick(container);
                    h.assertTrue(body.logicalPose().position().distance(applied) < 1e-3, "Global playback must suppress forward physics");
                    h.assertTrue(SableBodyTimeManager.getState(body.getUniqueId()).bufferedLinearImpulse.lengthSquared() == 0,
                            "Global playback must discard incoming impulses");
                }
            }
            h.assertTrue(!com.timestop.core.TimeStopManager.isTimeStopped(h.getLevel()) && buffer.isRecording() && !buffer.isRewinding(),
                    "Playback duration must restore normal recording and time");
            var resumed = new Vector3d(body.logicalPose().position());
            physics.tick(container);
            h.assertTrue(body.logicalPose().position().distance(resumed) > .01 && !SableBodyTimeManager.isBodyHeld(body),
                    "Ship must resume moving after global playback");
            h.assertTrue(Math.abs(RigidBodyHandle.of(body).getLinearVelocity(new Vector3d()).x) < 3,
                    "Discarded playback impulse must not appear on resume");
        } finally {
            com.timestop.core.TimeStopManager.resumeTime(h.getLevel());
            recorder.clearTrackingData(); buffer.clear();
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_mounted_transport", timeoutTicks = 150)
    public void mountedRiderFollowsRotatingDeckAndDismountDropsOldAnchor(GameTestHelper h) {
        var body = assemble(h, 0);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var bridge = com.timestop.compat.TemporalWorlds.bridge();
        var firstDeck = body.getPlot().getCenterBlock().east(4);
        var secondDeck = body.getPlot().getCenterBlock().east(8);
        h.getLevel().setBlockAndUpdate(firstDeck, Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(secondDeck, Blocks.STONE.defaultBlockState());
        var pig = new net.minecraft.world.entity.animal.Pig(net.minecraft.world.entity.EntityType.PIG, h.getLevel());
        var boat = new net.minecraft.world.entity.vehicle.Boat(net.minecraft.world.entity.EntityType.BOAT, h.getLevel());
        var first = com.timestop.compat.TemporalWorlds.toWorld(h.getLevel(), Vec3.atCenterOf(firstDeck)).add(0, .6, 0);
        var second = com.timestop.compat.TemporalWorlds.toWorld(h.getLevel(), Vec3.atCenterOf(secondDeck)).add(0, .6, 0);
        pig.setPos(first); boat.setPos(second);
        h.getLevel().addFreshEntity(pig); h.getLevel().addFreshEntity(boat);
        try {
            bridge.updateStasisEntityTransport(pig); // Seed the old unmounted anchor.
            bridge.updateStasisEntityTransport(boat);
            h.assertTrue(pig.startRiding(boat, true), "Fixture passenger must mount the boat");
            bridge.updateStasisEntityTransport(pig);
            var pose = new Vector3d(body.logicalPose().position()).add(3, 0, 0);
            container.physicsSystem().getPipeline().teleport(body, pose, new Quaterniond().rotateY(.5));
            container.physicsSystem().updatePose(body);
            bridge.updateStasisEntityTransport(boat);
            boat.positionRider(pig);
            Vec3 expected = pig.position();
            pig.setPos(expected.add(10, 0, 0));
            bridge.updateStasisEntityTransport(pig);
            h.assertTrue(pig.position().distanceTo(expected) < 1e-6, "Mounted passenger must use vehicle positioning after deck rotation");
            pig.stopRiding();
            var dismount = com.timestop.compat.TemporalWorlds.toWorld(h.getLevel(), Vec3.atCenterOf(secondDeck)).add(0, .6, 0);
            pig.setPos(dismount);
            bridge.updateStasisEntityTransport(pig);
            h.assertTrue(pig.position().distanceTo(dismount) < .01, "Dismount must not snap back to the old pre-mount deck anchor");
        } finally {
            bridge.clearStasisEntityTransport(pig); bridge.clearStasisEntityTransport(boat);
            pig.discard(); boat.discard();
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_live_plot_ticks", timeoutTicks = 150)
    public void liveShipRewindRestoresScheduledTickDelayAndRemovesForwardTicks(GameTestHelper h) {
        var body = assemble(h, 0);
        var container = SubLevelContainer.getContainer(h.getLevel());
        try {
            var pos = body.getPlot().getCenterBlock();
            var chunk = body.getPlot().getChunk(body.getPlot().toLocal(new net.minecraft.world.level.ChunkPos(pos)));
            chunk.getBlockTicks().schedule(new net.minecraft.world.ticks.ScheduledTick<>(Blocks.STONE, pos,
                    h.getLevel().getGameTime() + 40, net.minecraft.world.ticks.TickPriority.HIGH, 1));
            var frame = new TickFrame(1);
            com.timestop.compat.TemporalWorlds.bridge().captureSubLevelsBegin(h.getLevel().getServer(), frame);
            ((net.minecraft.world.ticks.LevelChunkTicks<net.minecraft.world.level.block.Block>) chunk.getBlockTicks()).removeIf(tick -> true);
            chunk.getBlockTicks().schedule(new net.minecraft.world.ticks.ScheduledTick<>(Blocks.DIRT, pos.east(),
                    h.getLevel().getGameTime() + 1, net.minecraft.world.ticks.TickPriority.NORMAL, 2));
            RewindExecutor.applyPlan(h.getLevel().getServer(), RewindExecutor.buildPlan(List.of(frame)), false);
            h.assertTrue(chunk.getBlockTicks().hasScheduledTick(pos, Blocks.STONE), "Existing ship must regain its historical scheduled tick");
            h.assertTrue(!chunk.getBlockTicks().hasScheduledTick(pos.east(), Blocks.DIRT), "Forward-only scheduled tick must be removed");
            var restored = ((net.minecraft.world.ticks.LevelChunkTicks<net.minecraft.world.level.block.Block>) chunk.getBlockTicks())
                    .getAll().filter(tick -> tick.type() == Blocks.STONE).findFirst().orElseThrow();
            h.assertTrue(restored.triggerTick() == h.getLevel().getGameTime() + 40 && restored.priority() == net.minecraft.world.ticks.TickPriority.HIGH,
                    "Restore relative delay and priority, not a stale absolute game time");
        } finally { if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_split_rewind", timeoutTicks = 150)
    public void localRewindIncludesSplitChildAfterNotificationClearsAndChildLeavesSphere(GameTestHelper h) {
        var parent = assemble(h, 0);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
        ServerSubLevel child = null;
        try {
            var splitBlock = parent.getPlot().getCenterBlock().east();
            h.getLevel().setBlockAndUpdate(splitBlock, Blocks.DIAMOND_BLOCK.defaultBlockState());
            container.physicsSystem().tick(container);
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
            recorder.serverTick(h.getLevel().getServer());
            child = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), splitBlock, Set.of(splitBlock),
                    new dev.ryanhcode.sable.companion.math.BoundingBox3i(splitBlock.getX(), splitBlock.getY(), splitBlock.getZ(),
                            splitBlock.getX(), splitBlock.getY(), splitBlock.getZ()));
            h.assertTrue(parent.getUniqueId().equals(child.getSplitFromSubLevel()), "Fixture must use a real native split");
            child.clearSplitFrom();
            container.physicsSystem().getPipeline().teleport(child, new Vector3d(child.logicalPose().position()).add(50, 0, 0), new Quaterniond());
            container.physicsSystem().updatePose(child);
            recorder.serverTick(h.getLevel().getServer());
            var com = SableBodyTimeManager.getBodyCenterOfMassWorld(parent);
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(com.x, com.y, com.z), 5);
            var plan = scope.filter(h.getLevel().getServer(), RewindExecutor.buildPlan(recorder.getTimelineBuffer().getFramesForRewind(40)));
            h.assertTrue(plan.subLevelTargetStates().containsKey(child.getUniqueId()), "Historical split must select child outside sphere");
            h.assertTrue(plan.subLevelTargetStates().get(child.getUniqueId()).removed(), "Child did not exist before split");
            h.assertTrue(RewindExecutor.execute(h.getLevel().getServer(), 2, null, false, scope).success(), "Split rewind must succeed");
            h.assertTrue(container.getSubLevel(child.getUniqueId()) == null, "Reassembly must remove split child identity");
            h.assertTrue(h.getLevel().getBlockState(splitBlock).is(Blocks.DIAMOND_BLOCK), "Transferred diamond block must return to parent exactly once");
            h.assertTrue(h.getLevel().getBlockState(parent.getPlot().getCenterBlock()).is(Blocks.STONE), "Parent's original block must remain intact");
        } finally {
            if (child != null && !child.isRemoved()) container.removeSubLevel(child, SubLevelRemovalReason.REMOVED);
            if (!parent.isRemoved()) container.removeSubLevel(parent, SubLevelRemovalReason.REMOVED);
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_plot_inventory_ticks", timeoutTicks = 150)
    public void relocatedPlotRestoresInventoryAndScheduledTicksWithoutSpentRunes(GameTestHelper h) throws Exception {
        var body = assemble(h, 0);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var bridge = com.timestop.compat.TemporalWorlds.bridge();
        var id = body.getUniqueId();
        var oldCenter = body.getPlot().getCenterBlock();
        var runeId = UUID.randomUUID();
        var consumedField = com.timestop.combat.RewindRuneManager.class.getDeclaredField("CONSUMED_RUNES");
        consumedField.setAccessible(true);
        @SuppressWarnings("unchecked") var consumed = (Set<UUID>) consumedField.get(null);
        ServerSubLevel occupant = null;
        try {
            h.getLevel().setBlockAndUpdate(oldCenter, Blocks.CHEST.defaultBlockState());
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(oldCenter);
            chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
            var rune = new net.minecraft.world.item.ItemStack(com.timestop.item.ModItems.RUNE_REWIND.get());
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, rune,
                    tag -> tag.putUUID("TimeStopRewindRuneId", runeId));
            chest.setItem(1, rune);
            var chunk = body.getPlot().getChunk(body.getPlot().toLocal(new net.minecraft.world.level.ChunkPos(oldCenter)));
            chunk.getBlockTicks().schedule(new net.minecraft.world.ticks.ScheduledTick<>(Blocks.CHEST, oldCenter,
                    h.getLevel().getGameTime() + 40, net.minecraft.world.ticks.TickPriority.NORMAL, 1));
            chunk.getFluidTicks().schedule(new net.minecraft.world.ticks.ScheduledTick<>(net.minecraft.world.level.material.Fluids.WATER, oldCenter,
                    h.getLevel().getGameTime() + 60, net.minecraft.world.ticks.TickPriority.HIGH, 2));
            var frame = new TickFrame(1);
            bridge.captureSubLevelsBegin(h.getLevel().getServer(), frame);
            consumed.add(runeId);
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            occupant = assemble(h, 8);
            h.assertTrue(occupant.getPlot().getCenterBlock().equals(oldCenter), "Fixture occupant must reuse historical plot");
            RewindExecutor.applyPlan(h.getLevel().getServer(), RewindExecutor.buildPlan(List.of(frame)), false);
            var restored = (ServerSubLevel) container.getSubLevel(id);
            var newCenter = restored.getPlot().getCenterBlock();
            h.assertTrue(!newCenter.equals(oldCenter), "Restored ship must be allocated elsewhere");
            var restoredChest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(newCenter);
            h.assertTrue(restoredChest != null && restoredChest.getBlockPos().equals(newCenter), "Restored block entity must use new plot coordinates");
            h.assertTrue(restoredChest.getItem(0).is(net.minecraft.world.item.Items.DIAMOND) && restoredChest.getItem(0).getCount() == 7,
                    "Full plot checkpoint must retain seven diamonds without recorded inventory deltas");
            h.assertTrue(restoredChest.getItem(1).isEmpty(), "Full plot restoration must not resurrect a consumed rewind rune");
            var restoredChunk = restored.getPlot().getChunk(restored.getPlot().toLocal(new net.minecraft.world.level.ChunkPos(newCenter)));
            h.assertTrue(restoredChunk.getBlockTicks().hasScheduledTick(newCenter, Blocks.CHEST), "Block tick must follow plot reallocation");
            h.assertTrue(restoredChunk.getFluidTicks().hasScheduledTick(newCenter, net.minecraft.world.level.material.Fluids.WATER), "Fluid tick must follow plot reallocation");
            h.assertTrue(h.getLevel().getBlockState(oldCenter).is(Blocks.STONE), "Outside occupant must remain intact");
        } finally {
            consumed.remove(runeId);
            var restored = container.getSubLevel(id);
            if (restored != null && !restored.isRemoved()) container.removeSubLevel(restored, SubLevelRemovalReason.REMOVED);
            if (occupant != null && !occupant.isRemoved()) container.removeSubLevel(occupant, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_preflight", timeoutTicks = 150)
    public void missingJointDependencyRejectsBeforeMutationAndPreservesHistory(GameTestHelper h) {
        var a = assemble(h, 0);
        var b = assemble(h, 4);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            var physics = container.physicsSystem();
            physics.tick(container);
            physics.getPipeline().addConstraint(a, b, new dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration(
                    new Vector3d(a.getMassTracker().getCenterOfMass()), new Vector3d(b.getMassTracker().getCenterOfMass()), new Quaterniond()));
            var complete = new TickFrame(1);
            com.timestop.compat.TemporalWorlds.bridge().captureSubLevelsBegin(h.getLevel().getServer(), complete);
            var incomplete = new TickFrame(2);
            incomplete.putSubLevelSnapshot(complete.getSubLevelSnapshots().get(a.getUniqueId()));
            var marker = new BlockPos(195, 150, 200);
            h.getLevel().setBlockAndUpdate(marker, Blocks.GOLD_BLOCK.defaultBlockState());
            incomplete.addBlockDelta(BlockDelta.create(h.getLevel().dimension(), marker,
                    Blocks.DIAMOND_BLOCK.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), null, null));
            recorder.getTimelineBuffer().pushFrame(incomplete);
            physics.getPipeline().resetVelocity(a);
            RigidBodyHandle.of(a).addLinearAndAngularVelocity(new Vector3d(3, 0, 0), new Vector3d());
            var position = new Vector3d(a.logicalPose().position());
            var result = RewindExecutor.execute(h.getLevel().getServer(), 1, null, false);
            h.assertTrue(!result.success() && result.warnings().getFirst().contains("endpoint"), "Missing dependency must reject the whole rewind");
            h.assertTrue(h.getLevel().getBlockState(marker).is(Blocks.GOLD_BLOCK), "Preflight failure must leave blocks untouched");
            h.assertTrue(a.logicalPose().position().equals(position) && RigidBodyHandle.of(a).getLinearVelocity(new Vector3d()).x == 3,
                    "Preflight failure must leave body pose and native momentum untouched");
            h.assertTrue(recorder.getTimelineBuffer().getFramesForRewind(40).contains(incomplete), "Rejected rewind must retain history");
            h.assertTrue(!RewindExecutor.isApplyingPlan() && !recorder.getTimelineBuffer().isRewinding(), "Rejected rewind must not leave suppression active");
        } finally {
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
            if (!a.isRemoved()) container.removeSubLevel(a, SubLevelRemovalReason.REMOVED);
            if (!b.isRemoved()) container.removeSubLevel(b, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_unloaded_plot", timeoutTicks = 150)
    public void unloadedShipsRejectRewindAndTheirPlotsRemainReserved(GameTestHelper h) {
        var reserved = assemble(h, 0);
        var destroyed = assemble(h, 4);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var bridge = com.timestop.compat.TemporalWorlds.bridge();
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
        var reservedCenter = reserved.getPlot().getCenterBlock();
        int reservedIndex = -1;
        for (int x = 0; x < 1 << container.getLogSideLength(); x++) for (int z = 0; z < 1 << container.getLogSideLength(); z++)
            if (container.getSubLevel(x, z) == reserved) reservedIndex = container.getIndex(x, z);
        var id = destroyed.getUniqueId();
        try {
            var frame = new TickFrame(1);
            bridge.captureSubLevelsBegin(h.getLevel().getServer(), frame);
            container.removeSubLevel(reserved, SubLevelRemovalReason.UNLOADED);
            boolean rejected = false;
            try { RewindExecutor.applyPlan(h.getLevel().getServer(), RewindExecutor.buildPlan(List.of(frame)), false); }
            catch (IllegalStateException expected) { rejected = expected.getMessage().contains("unloaded"); }
            h.assertTrue(rejected, "Unloaded identity must not be recreated as a duplicate ship");
            h.assertTrue(container.getSubLevel(reserved.getUniqueId()) == null, "Rejected plan must leave unloaded ship absent");
            container.removeSubLevel(destroyed, SubLevelRemovalReason.REMOVED);
            var selected = new TickFrame(2);
            selected.putSubLevelSnapshot(frame.getSubLevelSnapshots().get(id));
            RewindExecutor.applyPlan(h.getLevel().getServer(), RewindExecutor.buildPlan(List.of(selected)), false);
            var restored = container.getSubLevel(id);
            h.assertTrue(restored != null && !restored.getPlot().getCenterBlock().equals(reservedCenter),
                    "Recreation must skip an occupied plot even when its body is unloaded");
            h.assertTrue(container.getOccupancy().get(reservedIndex), "Unloaded plot reservation must remain intact");
        } finally {
            var restored = container.getSubLevel(id);
            if (restored != null && !restored.isRemoved()) container.removeSubLevel(restored, SubLevelRemovalReason.REMOVED);
            if (!reserved.isRemoved()) container.removeSubLevel(reserved, SubLevelRemovalReason.REMOVED);
            if (reservedIndex >= 0) container.getOccupancy().clear(reservedIndex); // Isolated fixture's unloaded reservation.
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_full_rates", timeoutTicks = 400)
    public void allSupportedRatesAdvanceNativePhysicsAtOneFourAndEightSubsteps(GameTestHelper h) {
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var config = com.timestop.config.TimeStopConfig.COMMON;
        int previousSubsteps = physics.getConfig().substepsPerTick;
        double previousSlow = config.slowMotionRate.get(), previousFast = config.fastForwardRate.get();
        try {
            for (int substeps : new int[]{1, 4, 8}) {
                physics.getConfig().substepsPerTick = substeps;
                for (double rate : new double[]{0, .01, .25, 1, 5, 50}) {
                    int ticks = rate == .01 ? 100 : rate == .25 ? 4 : 2;
                    double expected = 0, expectedVelocity = 2;
                    var expectedOrientation = new Quaterniond();
                    // Compare equal amounts of simulated time. At 50x, stock aerodynamic
                    // drag makes constant-velocity distance an invalid reference.
                    if (rate > 0) {
                        var reference = assemble(h, 0);
                        try {
                            physics.tick(container);
                            physics.getPipeline().resetVelocity(reference);
                            RigidBodyHandle.of(reference).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d(0, .4, 0));
                            double start = reference.logicalPose().position().x();
                            for (int tick = 0; tick < (int) Math.round(rate * ticks); tick++) physics.tick(container);
                            expected = reference.logicalPose().position().x() - start;
                            expectedVelocity = RigidBodyHandle.of(reference).getLinearVelocity(new Vector3d()).x;
                            expectedOrientation.set(reference.logicalPose().orientation());
                        } finally {
                            if (!reference.isRemoved()) container.removeSubLevel(reference, SubLevelRemovalReason.REMOVED);
                        }
                    }
                    var body = assemble(h, 0);
                    try {
                        physics.tick(container);
                        physics.getPipeline().resetVelocity(body);
                        RigidBodyHandle.of(body).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d(0, .4, 0));
                        config.slowMotionRate.set(rate > 0 && rate < 1 ? rate : .25);
                        config.fastForwardRate.set(rate > 1 ? rate : 5.0);
                        if (rate != 1) {
                            var com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
                            var mode = rate == 0 ? TimeMode.TIME_STOP : rate < 1 ? TimeMode.SLOW_MOTION : TimeMode.FAST_FORWARD;
                            TemporalBubbleManager.putStationaryBubble(h.getLevel(), new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(),
                                    h.getLevel().dimension(), new Vec3(com.x, com.y, com.z), 512, mode, 1000,
                                    WatchTier.DIAMOND, null, 0, Set.of()).stationary(true), null);
                        }
                        double start = body.logicalPose().position().x();
                        for (int tick = 0; tick < ticks; tick++) physics.tick(container);
                        double actual = body.logicalPose().position().x() - start;
                        h.assertTrue(Math.abs(actual - expected) <= Math.max(.003, expected * .05),
                                "Native rate=" + rate + ", substeps=" + substeps + ": expected dx=" + expected + ", actual=" + actual);
                        h.assertTrue(SableBodyTimeManager.getState(body.getUniqueId()).credits.pendingSeconds() < .05 / substeps + 1e-8,
                                "Supported rate must not leave overdue substeps queued");
                        double nativeX = RigidBodyHandle.of(body).getLinearVelocity(new Vector3d()).x;
                        if (rate > 0) h.assertTrue(Math.abs(nativeX - expectedVelocity) < .05,
                                "Native momentum must match equal simulated time: rate=" + rate + ", substeps=" + substeps + ", vx=" + nativeX + ", reference=" + expectedVelocity);
                        h.assertTrue(Math.abs(body.logicalPose().orientation().dot(expectedOrientation)) > .999,
                                "Angular advance must match equal simulated time: rate=" + rate + ", substeps=" + substeps);
                        double published = (body.logicalPose().position().x() - body.lastPose().position().x()) * 20;
                        h.assertTrue(Math.abs(body.latestLinearVelocity.x - published) < .001,
                                "Published velocity must describe the actual server-tick displacement");
                    } finally {
                        TemporalBubbleManager.stopAllBubbles(h.getLevel());
                        if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                    }
                }
            }
        } finally {
            physics.getConfig().substepsPerTick = previousSubsteps;
            config.slowMotionRate.set(previousSlow);
            config.fastForwardRate.set(previousFast);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_world_joint_settings", timeoutTicks = 150)
    public void worldJointRestoresMotorLimitsFramesAndContactsWithoutRecordingStasisHold(GameTestHelper h) throws Exception {
        var body = assemble(h, 0);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            physics.tick(container);
            var local = new Vector3d(body.getMassTracker().getCenterOfMass());
            var world = body.logicalPose().transformPosition(local, new Vector3d());
            var config = new dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration(local, world, new Quaterniond(), new Quaterniond());
            var joint = physics.getPipeline().addConstraint(body, null, config);
            var generic = (dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle) joint;
            var axis = dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis.values()[0];
            var angular = dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis.values()[4];
            joint.setMotor(axis, 2, 3, 4, true, 5);
            joint.setContactsEnabled(false);
            generic.setLimit(angular, -.5, .5);
            generic.setFrame1(new Vector3d(.1, .2, .3), new Quaterniond());
            generic.lockAxes(angular);
            var expected = com.timestop.compat.sable.SableConstraintState.capture(joint);
            freeze(h, body, 10);
            physics.tick(container);
            recorder.serverTick(h.getLevel().getServer());
            recorder.serverTick(h.getLevel().getServer());
            joint.setMotor(axis, 20, 30, 40, false, 50);
            generic.setFrame1(new Vector3d(1, 2, 3), new Quaterniond().rotateY(.2));
            joint.remove();
            recorder.serverTick(h.getLevel().getServer());
            var plan = RewindExecutor.buildPlan(recorder.getTimelineBuffer().getFramesForRewind(40));
            var joints = plan.subLevelTargetStates().get(body.getUniqueId()).constraintConfigs();
            h.assertTrue(joints.size() == 1, "History must include world joint, excluding integration-owned stasis hold");
            var saved = (com.timestop.compat.sable.SableWorldBridge.SavedConstraint) joints.values().iterator().next();
            h.assertTrue(saved.bodyB() == null && saved.settings().equals(expected), "World endpoint and original runtime state must be immutable");
            h.assertTrue(RewindExecutor.execute(h.getLevel().getServer(), 2, null, false).success(), "World-joint rewind must succeed");
            var field = com.timestop.compat.sable.SableWorldBridge.class.getDeclaredField("ACTIVE_CONSTRAINTS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var records = (List<com.timestop.compat.sable.SableWorldBridge.ConstraintRecord>) field.get(null);
            var restored = records.stream().filter(c -> joints.containsKey(c.id()) && c.handle().isValid()).findFirst().orElseThrow();
            h.assertTrue(restored.b() == null && com.timestop.compat.sable.SableConstraintState.capture(restored.handle()).equals(expected),
                    "Native restored handle must receive saved motors, limits, frames, locks and contact mode");
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_global_impulse", timeoutTicks = 150)
    public void explicitImpulseDuringPureGlobalStopReleasesOnce(GameTestHelper h) {
        var body = assemble(h, 0);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var handle = RigidBodyHandle.of(body);
        try {
            container.physicsSystem().tick(container);
            container.physicsSystem().getPipeline().resetVelocity(body);
            h.assertTrue(body.getMassTracker().getMass() > 0, "Fixture must initialize merged mass before computing impulse");
            handle.addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
            com.timestop.core.TimeStopManager.startGlobalTimeStop(h.getLevel(), null, 100, TimeMode.TIME_STOP);
            container.physicsSystem().tick(container);
            double impulse = body.getMassTracker().getMass() * 3;
            handle.applyLinearAndAngularImpulse(new Vector3d(impulse, 0, 0), new Vector3d(), true);
            h.assertTrue(Math.abs(handle.getLinearVelocity(new Vector3d()).x - 2) < 1e-6, "Native velocity must stay unchanged during global stop");
            h.assertTrue(Math.abs(SableBodyTimeManager.getState(body.getUniqueId()).bufferedLinearImpulse.x - impulse) < 1e-6, "Explicit impulse must be buffered");
            com.timestop.core.TimeStopManager.resumeTime(h.getLevel());
            container.physicsSystem().tick(container);
            double first = handle.getLinearVelocity(new Vector3d()).x;
            h.assertTrue(Math.abs(first - 5) < .2, "Global resume must add exactly one buffered impulse, actual=" + first);
            container.physicsSystem().tick(container);
            h.assertTrue(Math.abs(handle.getLinearVelocity(new Vector3d()).x - first) < .2, "Global impact must not replay");
        } finally {
            com.timestop.core.TimeStopManager.resumeTime(h.getLevel());
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h) {
        var result = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "sable-ready"), net.minecraft.server.level.ClientInformation.createDefault());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, result,
                net.minecraft.server.network.CommonListenerCookie.createInitial(result.getGameProfile(), false));
        result.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), result,
                net.minecraft.server.network.CommonListenerCookie.createInitial(result.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        result.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        return result;
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_continuous_playback", timeoutTicks = 200)
    public void continuousRewindSuppressesOnlySelectedBodiesAndDiscardsNewImpulses(GameTestHelper h) {
        var selected = assemble(h, 0);
        var outside = assemble(h, 40);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var recorder = TickRecorder.getInstance();
        var owner = player(h);
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            RigidBodyHandle.of(selected).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
            RigidBodyHandle.of(outside).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
            for (int tick = 0; tick < 12; tick++) { recorder.serverTick(h.getLevel().getServer()); physics.tick(container); }
            var center = SableBodyTimeManager.getBodyCenterOfMassWorld(selected);
            owner.setPos(center.x, center.y + 2, center.z);
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(center.x, center.y, center.z), 10);
            h.assertTrue(LocalRewind.start(owner, scope, 10, () -> {}), "Continuous rewind must start");
            var beforeExcursion = new Vector3d(selected.logicalPose().position());
            physics.getPipeline().teleport(selected, new Vector3d(beforeExcursion).add(100, 0, 0), new Quaterniond());
            physics.updatePose(selected);
            h.assertTrue(LocalRewind.contains(h.getLevel().dimension(), selected.getPlot().getCenterBlock()),
                    "Fixed selected plots must suppress block/fluid/entity updates even outside the activation sphere");
            physics.getPipeline().teleport(selected, beforeExcursion, new Quaterniond());
            physics.updatePose(selected);
            h.assertTrue(LocalRewind.containsSubLevel(selected.getUniqueId()) && !LocalRewind.containsSubLevel(outside.getUniqueId()),
                    "Fixed selection must contain only the inside body");
            var freeStart = new Vector3d(outside.logicalPose().position());
            for (int tick = 0; tick < 3; tick++) {
                LocalRewind.tick(h.getLevel().getServer());
                h.assertTrue(LocalRewind.isActive(owner.getUUID()), "Playback must still have pending frames");
                var applied = new Vector3d(selected.logicalPose().position());
                physics.getPipeline().applyLinearAndAngularImpulse(selected, new Vector3d(1e7, 0, 0), new Vector3d(0, 1e7, 0), true);
                physics.tick(container);
                h.assertTrue(selected.logicalPose().position().distance(applied) < 1e-3, "Selected body must not advance between playback frames");
                h.assertTrue(SableBodyTimeManager.getState(selected.getUniqueId()).bufferedLinearImpulse.lengthSquared() == 0,
                        "Playback must discard incoming impulses");
            }
            h.assertTrue(outside.logicalPose().position().distance(freeStart) > .05, "Outside body must keep simulating");
            LocalRewind.cancel(owner.getUUID());
            physics.tick(container);
            h.assertTrue(!SableBodyTimeManager.isBodyHeld(selected), "Cancel must release the playback hold");
            h.assertTrue(Math.abs(RigidBodyHandle.of(selected).getLinearVelocity(new Vector3d()).x) < 3, "Discarded playback impulse must never release");
        } finally {
            LocalRewind.clear();
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            h.getLevel().getServer().getPlayerList().remove(owner);
            if (!selected.isRemoved()) container.removeSubLevel(selected, SubLevelRemovalReason.REMOVED);
            if (!outside.isRemoved()) container.removeSubLevel(outside, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    private static ServerSubLevel assemble(GameTestHelper h, int x) {
        var pos = new BlockPos(200 + x, 150, 200);
        h.getLevel().getChunkAt(pos);
        h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        return SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), pos, Set.of(pos),
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ()));
    }

    private static void freeze(GameTestHelper h, ServerSubLevel body, double radius) {
        var com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
        var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(),
                new Vec3(com.x, com.y, com.z), radius, TimeMode.TIME_STOP, 1000, WatchTier.DIAMOND, null, 0, Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_native_contact", timeoutTicks = 200)
    public void realMovingBodyContactBuffersCorrectDirectionAndReleasesOnce(GameTestHelper h) {
        var held = assemble(h, 0);
        var moving = assemble(h, 4);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        int oldSubsteps = physics.getConfig().substepsPerTick;
        try {
            physics.getConfig().substepsPerTick = 4;
            freeze(h, held, .6);
            physics.tick(container);
            var origin = new Vector3d(held.logicalPose().position());
            physics.getPipeline().teleport(moving, new Vector3d(origin).add(2.1, 0, 0), new Quaterniond());
            physics.getPipeline().resetVelocity(moving);
            RigidBodyHandle.of(moving).addLinearAndAngularVelocity(new Vector3d(-20, 0, 0), new Vector3d());
            var state = SableBodyTimeManager.getState(held.getUniqueId());
            double bound = moving.getMassTracker().getMass() * 20 * 4;
            for (int tick = 0; tick < 8 && Math.abs(state.bufferedLinearImpulse.x) < 1e-5; tick++) physics.tick(container);
            h.assertTrue(state.bufferedLinearImpulse.x < -1e-5,
                    "Actual right-to-left native impact must push held body left: " + state.bufferedLinearImpulse);
            h.assertTrue(Math.abs(state.bufferedLinearImpulse.x) <= bound, "F * dt must bound momentum, not buffer force as impulse");
            h.assertTrue(held.logicalPose().position().distance(origin) < 1e-3, "Impacted ship must stay frozen");
            double expectedX = state.storedLinearVelocity.x + state.bufferedLinearImpulse.x / held.getMassTracker().getMass();
            container.removeSubLevel(moving, SubLevelRemovalReason.REMOVED);
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            physics.tick(container);
            double releasedX = RigidBodyHandle.of(held).getLinearVelocity(new Vector3d()).x;
            h.assertTrue(Math.abs(releasedX - expectedX) < Math.max(.01, Math.abs(expectedX) * .25),
                    "Release must apply saved impact once: expected=" + expectedX + ", actual=" + releasedX);
            h.assertTrue(state.bufferedLinearImpulse.lengthSquared() == 0 && state.bufferedAngularImpulse.lengthSquared() == 0,
                    "Released buffers must be empty");
            physics.tick(container);
            double nextX = RigidBodyHandle.of(held).getLinearVelocity(new Vector3d()).x;
            h.assertTrue(Math.abs(nextX) <= Math.abs(releasedX) * 1.1 + .01, "Impact must not replay on the next tick");
        } finally {
            physics.getConfig().substepsPerTick = oldSubsteps;
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!moving.isRemoved()) container.removeSubLevel(moving, SubLevelRemovalReason.REMOVED);
            if (!held.isRemoved()) container.removeSubLevel(held, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ready_playback_memory", timeoutTicks = 100)
    public void playbackRetainsEvictedFrameMemoryWithoutDuplicatingSharedTags(GameTestHelper h) {
        var buffer = new TimelineBuffer(1, 1024 * 1024);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putByteArray("payload", new byte[65536]);
        var id = UUID.randomUUID();
        var older = new TickFrame(1);
        older.putSubLevelSnapshot(new SubLevelSnapshot(id, h.getLevel().dimension(), new Vector3d(), new Quaterniond(),
                new Vector3d(), new Vector3d(), new Vector3d(), new Vector3d(), 0, false, new Vector3d(), BlockPos.ZERO, tag, Set.of(), Map.of()));
        buffer.pushFrame(older);
        h.assertTrue(buffer.additionalRetainedBytes(List.of(older, older)) == 0, "Timeline-owned frame must not be charged twice");
        var newer = new TickFrame(2);
        newer.putSubLevelSnapshot(new SubLevelSnapshot(id, h.getLevel().dimension(), new Vector3d(1, 0, 0), new Quaterniond(),
                new Vector3d(), new Vector3d(), new Vector3d(), new Vector3d(), 0, false, new Vector3d(), BlockPos.ZERO, tag, Set.of(), Map.of()));
        buffer.pushFrame(newer);
        h.assertTrue(buffer.additionalRetainedBytes(List.of(older, older)) == older.getEstimatedMemoryBytes() - SubLevelSnapshot.estimateTagBytes(tag),
                "Evicted playback frame must retain unique costs and share tag ownership with the live timeline");
        buffer.clear();
        h.assertTrue(buffer.additionalRetainedBytes(List.of(older, older)) == older.getEstimatedMemoryBytes(),
                "Playback becomes sole owner after timeline clear; full payload remains charged exactly once");
        h.succeed();
    }
}
