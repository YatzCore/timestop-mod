package com.timestop.fabric.test;

import com.timestop.compat.sable.SableBodyTimeManager;
import com.timestop.config.TimeStopConfig;
import com.timestop.core.*;
import com.timestop.core.rewind.RewindExecutor;
import com.timestop.core.rewind.RewindScope;
import com.timestop.core.rewind.TickRecorder;
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
import java.util.Set;
import java.util.UUID;

/** Checks previously uncovered requirements from SABLE_PLAN.md against real stock Sable. */
public class SableReviewTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "fix_rotated_contacts", timeoutTicks = 150)
    public void offCenterContactsKeepLocalTorqueForBothRotatedBodies(GameTestHelper h) throws Exception {
        var a = assemble(h, 2);
        var b = assemble(h, 40);
        var container = SubLevelContainer.getContainer(h.getLevel());
        try {
            a.logicalPose().orientation().rotateY(Math.PI / 2);
            b.logicalPose().orientation().rotateX(Math.PI / 2);
            var pipeline = container.physicsSystem().getPipeline();
            pipeline.teleport(a, a.logicalPose().position(), a.logicalPose().orientation());
            pipeline.teleport(b, b.logicalPose().position(), b.logicalPose().orientation());
            field(h, a, TimeMode.TIME_STOP, 100);
            container.physicsSystem().tick(container);
            h.assertTrue(SableBodyTimeManager.isBodyHeld(a) && SableBodyTimeManager.isBodyHeld(b), "Both bodies must be held");
            for (var body : java.util.List.of(a, b)) {
                var state = SableBodyTimeManager.getState(body.getUniqueId());
                state.bufferedLinearImpulse.zero();
                state.bufferedAngularImpulse.zero();
                state.resetCollisionPairs();
            }
            var idsField = pipeline.getClass().getDeclaredField("activeSubLevels");
            idsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var ids = (it.unimi.dsi.fastutil.ints.Int2ObjectMap<ServerSubLevel>) idsField.get(pipeline);
            Integer idA = null, idB = null;
            for (var entry : ids.int2ObjectEntrySet()) {
                if (entry.getValue() == a) idA = entry.getIntKey();
                if (entry.getValue() == b) idB = entry.getIntKey();
            }
            h.assertTrue(idA != null && idB != null, "Bodies need native IDs");
            var decoder = java.util.Arrays.stream(pipeline.getClass().getDeclaredMethods())
                    .filter(m -> m.getName().contains("captureCollisions") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == double[].class).findFirst().orElseThrow();
            decoder.setAccessible(true);
            var pA = new Vector3d(a.getMassTracker().getCenterOfMass()).sub(a.logicalPose().rotationPoint()).add(0, 1, 0);
            var pB = new Vector3d(b.getMassTracker().getCenterOfMass()).sub(b.logicalPose().rotationPoint()).add(0, 0, 2);
            // Decoder contract only: all normals, lever arms and buffered impulses remain in body-local axes.
            double[] contacts = {idA, idB, 10, 1, 0, 0, -1, 0, 0, pA.x, pA.y, pA.z, pB.x, pB.y, pB.z};
            decoder.invoke(pipeline, (Object) contacts);
            var stateA = SableBodyTimeManager.getState(a.getUniqueId());
            var stateB = SableBodyTimeManager.getState(b.getUniqueId());
            var expectedA = new Vector3d(0, 1, 0).cross(stateA.bufferedLinearImpulse);
            var expectedB = new Vector3d(0, 0, 2).cross(stateB.bufferedLinearImpulse);
            h.assertTrue(expectedA.lengthSquared() > 0 && expectedB.lengthSquared() > 0, "Both contacts must be buffered");
            h.assertTrue(stateA.bufferedAngularImpulse.distanceSquared(expectedA) < 1e-12, "A local torque must equal r x J");
            h.assertTrue(stateB.bufferedAngularImpulse.distanceSquared(expectedB) < 1e-12, "B local torque must equal r x J");
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!a.isRemoved()) container.removeSubLevel(a, SubLevelRemovalReason.REMOVED);
            if (!b.isRemoved()) container.removeSubLevel(b, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "fix_joint_recreation", timeoutTicks = 150)
    public void recreatedJointPairKeepsIdentityOrderAndRemappedAnchors(GameTestHelper h) throws Exception {
        var a = assemble(h, 2);
        var b = assemble(h, 40);
        var idA = a.getUniqueId();
        var idB = b.getUniqueId();
        var oldCenterA = a.getPlot().getCenterBlock();
        var oldCenterB = b.getPlot().getCenterBlock();
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        ServerSubLevel occupant = null;
        try {
            var pipeline = container.physicsSystem().getPipeline();
            // Reverse endpoint order deliberately; the body UUID ordering must not change it.
            for (int offset = 0; offset < 2; offset++) {
                var config = new dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration(
                        new Vector3d(b.getMassTracker().getCenterOfMass()).add(0, offset, 0),
                        new Vector3d(a.getMassTracker().getCenterOfMass()).add(0, offset, 0), new org.joml.Quaterniond());
                h.assertTrue(pipeline.addConstraint(b, a, config).isValid(), "Initial joint must be valid");
            }
            var worldCom = SableBodyTimeManager.getBodyCenterOfMassWorld(a);
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(worldCom.x, worldCom.y, worldCom.z), 10);
            recorder.serverTick(h.getLevel().getServer());
            container.removeSubLevel(a, SubLevelRemovalReason.REMOVED);
            container.removeSubLevel(b, SubLevelRemovalReason.REMOVED);
            occupant = assemble(h, 80);
            h.assertTrue(occupant.getPlot().getCenterBlock().equals(oldCenterA), "Outside occupant must reuse A's plot");
            recorder.serverTick(h.getLevel().getServer());
            var plan = scope.filter(h.getLevel().getServer(), RewindExecutor.buildPlan(recorder.getTimelineBuffer().getFramesForRewind(40)));
            h.assertTrue(plan.subLevelTargetStates().size() == 2, "Historical joints must select both original bodies only");
            var saved = plan.subLevelTargetStates().get(idA).constraintConfigs();
            h.assertTrue(saved.size() == 2, "Target must retain both joint identities");
            h.assertTrue(RewindExecutor.execute(h.getLevel().getServer(), 2, null, false, scope).success(), "Rewind must succeed");
            var restoredA = container.getSubLevel(idA);
            var restoredB = container.getSubLevel(idB);
            h.assertTrue(restoredA != null && restoredB != null && !occupant.isRemoved(), "Restore both bodies, preserving outside occupant");
            h.assertTrue(!restoredA.getPlot().getCenterBlock().equals(oldCenterA), "Restored A needs a different plot");
            var recordsField = com.timestop.compat.sable.SableWorldBridge.class.getDeclaredField("ACTIVE_CONSTRAINTS");
            recordsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var records = (java.util.List<com.timestop.compat.sable.SableWorldBridge.ConstraintRecord>) recordsField.get(null);
            for (int repeat = 0; repeat < 2; repeat++) {
                var live = records.stream().filter(c -> c.handle().isValid() && saved.containsKey(c.id())).toList();
                h.assertTrue(live.size() == 2, "Both original joint identities must survive reconstruction");
                for (var joint : live) {
                    h.assertTrue(joint.a().equals(idB) && joint.b().equals(idA), "Ordered endpoints must be preserved");
                    var old = (com.timestop.compat.sable.SableWorldBridge.SavedConstraint) saved.get(joint.id());
                    var original = (dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration) old.config();
                    var current = (dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration) joint.config();
                    var offsetB = restoredB.getPlot().getCenterBlock().subtract(oldCenterB);
                    var offsetA = restoredA.getPlot().getCenterBlock().subtract(oldCenterA);
                    h.assertTrue(new Vector3d(original.pos1()).add(offsetB.getX(), offsetB.getY(), offsetB.getZ()).distanceSquared(current.pos1()) < 1e-12,
                            "First anchor must remap to B's plot");
                    h.assertTrue(new Vector3d(original.pos2()).add(offsetA.getX(), offsetA.getY(), offsetA.getZ()).distanceSquared(current.pos2()) < 1e-12,
                            "Second anchor must remap to A's plot");
                }
                if (repeat == 0) com.timestop.compat.TemporalWorlds.bridge().applySubLevelRewind(h.getLevel().getServer(), plan);
            }
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            for (var id : java.util.List.of(idA, idB)) {
                var body = container.getSubLevel(id);
                if (body != null && !body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            }
            if (occupant != null && !occupant.isRemoved()) container.removeSubLevel(occupant, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    private static void assertRetainedPlotAccounting(GameTestHelper h, com.timestop.core.rewind.TimelineBuffer buffer) {
        var tags = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.minecraft.nbt.CompoundTag, Boolean>());
        long expected = 0;
        for (var frame : buffer.getFramesForRewind(Integer.MAX_VALUE)) {
            expected += frame.getEstimatedMemoryBytes();
            for (var snapshot : frame.getSubLevelSnapshots().values()) {
                if (snapshot.plotData() != null && !tags.add(snapshot.plotData()))
                    expected -= com.timestop.core.rewind.data.SubLevelSnapshot.estimateTagBytes(snapshot.plotData());
            }
        }
        h.assertTrue(buffer.getBufferRetainedBytes() == expected,
                "Retained accounting after lifecycle operation: expected=" + expected + ", actual=" + buffer.getBufferRetainedBytes());
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "fix_plot_accounting_lifecycle", timeoutTicks = 150)
    public void sharedPlotAccountingSurvivesEvictionResizeAndPartialConsumption(GameTestHelper h) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putByteArray("payload", new byte[1024]);
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var buffer = new com.timestop.core.rewind.TimelineBuffer(3, 4096);
        for (int tick = 0; tick < 30; tick++) {
            var frame = new com.timestop.core.rewind.data.TickFrame(tick);
            for (var id : java.util.List.of(a, b)) {
                var connections = tick % 2 == 0 ? Set.<UUID>of() : Set.of(a, b);
                frame.putSubLevelSnapshot(new com.timestop.core.rewind.data.SubLevelSnapshot(id, h.getLevel().dimension(),
                        new Vector3d(tick, 0, 0), new org.joml.Quaterniond(), new Vector3d(), new Vector3d(),
                        new Vector3d(), new Vector3d(), 0, false, new Vector3d(), BlockPos.ZERO, tag, connections, java.util.Map.of()));
            }
            buffer.pushFrame(frame);
            assertRetainedPlotAccounting(h, buffer);
            if (tick == 5) buffer.resizeSeconds(1);
        }
        h.assertTrue(buffer.getMemoryEvictedFrames() > 0, "Budget eviction must be exercised");
        buffer.removeRecentFrames(1);
        assertRetainedPlotAccounting(h, buffer);
        var frames = buffer.getFramesForRewind(100);
        var all = RewindExecutor.buildPlan(frames);
        var partial = new com.timestop.core.rewind.RewindPlan(all.frameCount(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                Set.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                java.util.Map.of(a, all.subLevelTargetStates().get(a)), all.oldestFrame(), java.util.List.of());
        buffer.consumeFrames(frames, partial);
        h.assertTrue(buffer.getFramesForRewind(100).stream().allMatch(f -> f.getSubLevelSnapshots().size() == 1),
                "Partial consumption must retain the other body");
        assertRetainedPlotAccounting(h, buffer);
        buffer.resizeSeconds(2);
        assertRetainedPlotAccounting(h, buffer);
        buffer.clear();
        h.assertTrue(buffer.getBufferRetainedBytes() == 0, "Clear must release all charges");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "fix_plot_cache_cleanup", timeoutTicks = 150)
    public void removedBodiesDoNotRemainInPlotDedupCache(GameTestHelper h) throws Exception {
        var body = assemble(h, 2);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            recorder.serverTick(h.getLevel().getServer());
            recorder.serverTick(h.getLevel().getServer());
            recorder.serverTick(h.getLevel().getServer());
            var cacheField = com.timestop.compat.sable.SableWorldBridge.class.getDeclaredField("LAST_CAPTURED_PLOTS");
            cacheField.setAccessible(true);
            var cache = (java.util.Map<?, ?>) cacheField.get(null);
            h.assertTrue(cache.containsKey(body.getUniqueId()), "Fixture must capture a plot for deduplication");
            var retained = recorder.getTimelineBuffer().getFramesForRewind(40);
            h.assertTrue(retained.size() >= 2, "Fixture must finish two recorded frames");
            var first = retained.get(0).getSubLevelSnapshots().get(body.getUniqueId()).plotData();
            var second = retained.get(1).getSubLevelSnapshots().get(body.getUniqueId()).plotData();
            h.assertTrue(first == second, "Unchanged plot tags must still share identity");
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            h.assertTrue(!cache.containsKey(body.getUniqueId()), "Removed body must leave the dedup cache immediately");
            h.assertTrue(first != null && !first.isEmpty(), "Existing history must still own its checkpoint after cache removal");
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_collision_com", timeoutTicks = 150)
    public void contactAtCenterOfMassBuffersNoAngularImpulse(GameTestHelper h) throws Exception {
        var body = assemble(h, 2);
        var incoming = assemble(h, 40);
        var container = SubLevelContainer.getContainer(h.getLevel());
        try {
            field(h, body, TimeMode.TIME_STOP);
            container.physicsSystem().tick(container);
            h.assertTrue(SableBodyTimeManager.isBodyHeld(body), "Fixture body must be held");
            var state = SableBodyTimeManager.getState(body.getUniqueId());
            state.bufferedLinearImpulse.zero();
            state.bufferedAngularImpulse.zero();
            state.resetCollisionPairs();
            var pipeline = container.physicsSystem().getPipeline();
            var idsField = pipeline.getClass().getDeclaredField("activeSubLevels");
            idsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var ids = (it.unimi.dsi.fastutil.ints.Int2ObjectMap<ServerSubLevel>) idsField.get(pipeline);
            Integer bodyId = null, incomingId = null;
            for (var entry : ids.int2ObjectEntrySet()) {
                if (entry.getValue() == body) bodyId = entry.getIntKey();
                if (entry.getValue() == incoming) incomingId = entry.getIntKey();
            }
            h.assertTrue(bodyId != null && incomingId != null, "Both fixture bodies must have native IDs");
            var decoder = java.util.Arrays.stream(pipeline.getClass().getDeclaredMethods())
                    .filter(m -> m.getName().contains("captureCollisions") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == double[].class)
                    .findFirst().orElseThrow(() -> new IllegalStateException("Injected collision decoder not found"));
            decoder.setAccessible(true);
            // Native contact positions are offsets from logicalPose.rotationPoint(), not absolute plot coordinates.
            // Synthetic decoder-contract input; this does not simulate native contact generation.
            var contact = new Vector3d(body.getSelfMassTracker().getCenterOfMass()).sub(body.logicalPose().rotationPoint());
            double[] contacts = {bodyId, incomingId, 10, 1, 0, 0, -1, 0, 0,
                    contact.x, contact.y, contact.z, 0, 0, 0};
            decoder.invoke(pipeline, (Object) contacts);
            h.assertTrue(Math.abs(state.bufferedLinearImpulse.x) > 1e-8, "Fixture must buffer the contact");
            h.assertTrue(state.bufferedAngularImpulse.lengthSquared() < 1e-12,
                    "Contact at COM must not add angular impulse; actual=" + state.bufferedAngularImpulse);
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            if (!incoming.isRemoved()) container.removeSubLevel(incoming, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_parallel_joints", timeoutTicks = 150)
    public void rewindPreservesTwoDistinctJointsBetweenSameBodies(GameTestHelper h) throws Exception {
        var a = assemble(h, 2);
        var b = assemble(h, 40);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            var config1 = new dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration(
                    new Vector3d(a.getSelfMassTracker().getCenterOfMass()),
                    new Vector3d(b.getSelfMassTracker().getCenterOfMass()), new org.joml.Quaterniond());
            var config2 = new dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration(
                    new Vector3d(a.getSelfMassTracker().getCenterOfMass()).add(0, 1, 0),
                    new Vector3d(b.getSelfMassTracker().getCenterOfMass()).add(0, 1, 0), new org.joml.Quaterniond());
            var pipeline = container.physicsSystem().getPipeline();
            var joint1 = pipeline.addConstraint(a, b, config1);
            var joint2 = pipeline.addConstraint(a, b, config2);
            h.assertTrue(joint1.isValid() && joint2.isValid() && !config1.equals(config2),
                    "Fixture must have two valid joints with distinct configurations");
            recorder.serverTick(h.getLevel().getServer());
            recorder.serverTick(h.getLevel().getServer());
            var result = RewindExecutor.execute(h.getLevel().getServer(), 2, null, false);
            h.assertTrue(result.success(), "Rewind must accept recorded history");
            // Inspect live handles, allowing the implementation to replace originals during reconstruction.
            var recordsField = com.timestop.compat.sable.SableWorldBridge.class.getDeclaredField("ACTIVE_CONSTRAINTS");
            recordsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var records = (java.util.List<com.timestop.compat.sable.SableWorldBridge.ConstraintRecord>) recordsField.get(null);
            var live = records.stream().filter(c -> c.handle() != null && c.handle().isValid()
                    && ((c.a().equals(a.getUniqueId()) && c.b().equals(b.getUniqueId()))
                    || (c.a().equals(b.getUniqueId()) && c.b().equals(a.getUniqueId())))).toList();
            h.assertTrue(live.size() == 2 && live.stream().anyMatch(c -> config1.equals(c.config()))
                            && live.stream().anyMatch(c -> config2.equals(c.config())),
                    "Rewind must retain both distinct target joints; live count=" + live.size());
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (!a.isRemoved()) container.removeSubLevel(a, SubLevelRemovalReason.REMOVED);
            if (!b.isRemoved()) container.removeSubLevel(b, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_shared_plot_budget", timeoutTicks = 150)
    public void sharedPlotAccountingRetainsDistinctSnapshotCosts(GameTestHelper h) {
        var sharedPlot = new net.minecraft.nbt.CompoundTag();
        sharedPlot.putByteArray("payload", new byte[1024]);
        var buffer = new com.timestop.core.rewind.TimelineBuffer(600, 50L * 1024 * 1024);
        var bodyId = UUID.randomUUID();
        long unsharedBytes = 0;
        for (int tick = 0; tick < 2; tick++) {
            var frame = new com.timestop.core.rewind.data.TickFrame(tick);
            var snapshot = new com.timestop.core.rewind.data.SubLevelSnapshot(bodyId, h.getLevel().dimension(),
                    new Vector3d(tick, 0, 0), new org.joml.Quaterniond(), new Vector3d(), new Vector3d(),
                    new Vector3d(), new Vector3d(), 0, false, new Vector3d(), BlockPos.ZERO,
                    sharedPlot, Set.of(), java.util.Map.of());
            frame.putSubLevelSnapshot(snapshot);
            unsharedBytes += frame.getEstimatedMemoryBytes();
            buffer.pushFrame(frame);
        }
        // Only the tag is shared; both frame/snapshot records and their motion vectors remain retained.
        long expected = unsharedBytes - com.timestop.core.rewind.data.SubLevelSnapshot.estimateTagBytes(sharedPlot);
        h.assertTrue(buffer.getBufferRetainedBytes() == expected,
                "Dedup must subtract only the shared tag: expected=" + expected
                        + ", actual=" + buffer.getBufferRetainedBytes());
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_new_joint_rollback", timeoutTicks = 150)
    public void rewindRemovesJointCreatedAfterTarget(GameTestHelper h) {
        var a = assemble(h, 2);
        var b = assemble(h, 40);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle joint = null;
        try {
            recorder.serverTick(h.getLevel().getServer());
            var config = new dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration(
                    new Vector3d(a.getSelfMassTracker().getCenterOfMass()),
                    new Vector3d(b.getSelfMassTracker().getCenterOfMass()), new org.joml.Quaterniond());
            joint = container.physicsSystem().getPipeline().addConstraint(a, b, config);
            h.assertTrue(joint.isValid(), "Joint must be created after target frame");
            recorder.serverTick(h.getLevel().getServer());
            var plan = RewindExecutor.buildPlan(recorder.getTimelineBuffer().getFramesForRewind(40));
            h.assertTrue(plan.subLevelTargetStates().get(a.getUniqueId()).constraintConfigs().isEmpty(),
                    "Target snapshot must predate the joint");
            var result = RewindExecutor.execute(h.getLevel().getServer(), 2, null, false);
            h.assertTrue(result.success(), "Rewind must accept recorded history");
            h.assertTrue(!joint.isValid(), "Joint created after target must be removed by rewind");
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (joint != null && joint.isValid()) joint.remove();
            if (!a.isRemoved()) container.removeSubLevel(a, SubLevelRemovalReason.REMOVED);
            if (!b.isRemoved()) container.removeSubLevel(b, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_collision_decode", timeoutTicks = 150)
    public void nativeCollisionDecoderDoesNotReplayTerrainSupport(GameTestHelper h) throws Exception {
        var body = assemble(h, 2);
        var container = SubLevelContainer.getContainer(h.getLevel());
        try {
            field(h, body, TimeMode.TIME_STOP);
            container.physicsSystem().tick(container);
            h.assertTrue(SableBodyTimeManager.isBodyHeld(body), "Fixture body must be held");
            var state = SableBodyTimeManager.getState(body.getUniqueId());
            state.bufferedLinearImpulse.zero();
            state.bufferedAngularImpulse.zero();
            state.resetCollisionPairs();
            var pipeline = container.physicsSystem().getPipeline();
            var idsField = pipeline.getClass().getDeclaredField("activeSubLevels");
            idsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var ids = (it.unimi.dsi.fastutil.ints.Int2ObjectMap<ServerSubLevel>) idsField.get(pipeline);
            Integer bodyId = null;
            for (var entry : ids.int2ObjectEntrySet()) if (entry.getValue() == body) bodyId = entry.getIntKey();
            h.assertTrue(bodyId != null, "Fixture body must have a native ID");
            var decoder = java.util.Arrays.stream(pipeline.getClass().getDeclaredMethods())
                    .filter(m -> m.getName().contains("captureCollisions") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == double[].class)
                    .findFirst().orElseThrow(() -> new IllegalStateException("Injected collision decoder not found"));
            decoder.setAccessible(true);
            // Stock Sable's native record: IDs, force, normals A/B, contact points A/B.
            // This is a decoder contract test, not a claim of end-to-end contact simulation.
            double[] contacts = {bodyId, -1, 10, 1, 0, 0, -1, 0, 0, 0, 0, 2, 0, 0, -2};
            decoder.invoke(pipeline, (Object) contacts);
            var buffered = state.bufferedLinearImpulse;
            h.assertTrue(buffered.lengthSquared() == 0 && state.bufferedAngularImpulse.lengthSquared() == 0,
                    "Static terrain support must not be replayed as incoming momentum; actual=" + buffered);
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    private static long byteArrayPayloadBytes(net.minecraft.nbt.Tag tag) {
        if (tag instanceof net.minecraft.nbt.ByteArrayTag array) return array.getAsByteArray().length;
        long bytes = 0;
        if (tag instanceof net.minecraft.nbt.CompoundTag compound) {
            for (String key : compound.getAllKeys()) bytes += byteArrayPayloadBytes(compound.get(key));
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (var item : list) bytes += byteArrayPayloadBytes(item);
        }
        return bytes;
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_snapshot_budget", timeoutTicks = 150)
    public void plotSnapshotAccountsForRetainedInventoryPayload(GameTestHelper h) {
        var body = assemble(h, 2);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var chestPos = body.getPlot().getCenterBlock().above();
        h.getLevel().setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(chestPos);
        h.assertTrue(chest != null, "Fixture chest must exist");
        var payload = new net.minecraft.nbt.CompoundTag();
        payload.putByteArray("review_memory_payload", new byte[128 * 1024]);
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(payload));
        chest.setItem(0, stack);
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            recorder.serverTick(h.getLevel().getServer());
            recorder.serverTick(h.getLevel().getServer());
            var plan = RewindExecutor.buildPlan(recorder.getTimelineBuffer().getFramesForRewind(40));
            var snapshot = plan.subLevelTargetStates().get(body.getUniqueId());
            h.assertTrue(snapshot != null && snapshot.plotData() != null, "Full plot snapshot must exist");
            long retainedBytes = byteArrayPayloadBytes(snapshot.plotData());
            h.assertTrue(retainedBytes >= 128 * 1024, "Plot checkpoint must retain the inventory payload");
            h.assertTrue(snapshot.estimateMemoryBytes() >= retainedBytes,
                    "Snapshot budget must at least count its byte arrays: charged=" + snapshot.estimateMemoryBytes()
                            + ", payload=" + retainedBytes);
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_unchanged_structure", timeoutTicks = 150)
    public void plotReuseRestoresBlocksWithoutRecordedEdits(GameTestHelper h) {
        var body = assemble(h, 2);
        var id = body.getUniqueId();
        var container = SubLevelContainer.getContainer(h.getLevel());
        var originalCenter = body.getPlot().getCenterBlock();
        var markerOffset = new BlockPos(3, 0, 0);
        h.getLevel().setBlockAndUpdate(originalCenter.offset(markerOffset), Blocks.DIAMOND_BLOCK.defaultBlockState());
        var recorder = TickRecorder.getInstance();
        // The diamond is part of the existing structure, not an edit made during recorded history.
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        ServerSubLevel unrelated = null;
        try {
            h.assertTrue(h.getLevel().getBlockState(originalCenter.offset(markerOffset)).is(Blocks.DIAMOND_BLOCK),
                    "Fixture must start with the unchanged diamond marker");
            var com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(com.x, com.y, com.z), 10);
            recorder.serverTick(h.getLevel().getServer());
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            unrelated = assemble(h, 40);
            h.assertTrue(unrelated.getPlot().getCenterBlock().equals(originalCenter), "Fixture must reuse original plot");
            h.getLevel().setBlockAndUpdate(unrelated.getPlot().getCenterBlock(), Blocks.GOLD_BLOCK.defaultBlockState());
            recorder.serverTick(h.getLevel().getServer());
            var result = RewindExecutor.execute(h.getLevel().getServer(), 2, null, false, scope);
            h.assertTrue(result.success(), "Rewind must accept recorded history");
            var restored = container.getSubLevel(id);
            h.assertTrue(restored != null, "Original body must be recreated");
            h.assertTrue(h.getLevel().getBlockState(restored.getPlot().getCenterBlock()).is(Blocks.STONE),
                    "Previously tested center stone must still restore");
            h.assertTrue(!unrelated.isRemoved() && h.getLevel().getBlockState(unrelated.getPlot().getCenterBlock()).is(Blocks.GOLD_BLOCK),
                    "Unrelated outside occupant must retain gold block");
            var actual = h.getLevel().getBlockState(restored.getPlot().getCenterBlock().offset(markerOffset));
            h.assertTrue(actual.is(Blocks.DIAMOND_BLOCK),
                    "Full structural restore must include unchanged diamond block; actual=" + actual);
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            var remaining = container.getSubLevel(id);
            if (remaining != null && !remaining.isRemoved()) container.removeSubLevel(remaining, SubLevelRemovalReason.REMOVED);
            if (unrelated != null && !unrelated.isRemoved()) container.removeSubLevel(unrelated, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_historical_joint", timeoutTicks = 150)
    public void localRewindIncludesJointRemovedDuringRecordedHistory(GameTestHelper h) {
        var inside = assemble(h, 2);
        var outside = assemble(h, 40);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var config = new dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration(
                new Vector3d(inside.getSelfMassTracker().getCenterOfMass()),
                new Vector3d(outside.getSelfMassTracker().getCenterOfMass()), new org.joml.Quaterniond());
        var joint = container.physicsSystem().getPipeline().addConstraint(inside, outside, config);
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            h.assertTrue(joint.isValid(), "Fixture must begin with a valid joint");
            var com = SableBodyTimeManager.getBodyCenterOfMassWorld(inside);
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(com.x, com.y, com.z), 10);
            recorder.serverTick(h.getLevel().getServer());
            joint.remove();
            h.assertTrue(!joint.isValid(), "Joint must be removed during the recorded interval");
            recorder.serverTick(h.getLevel().getServer());
            var plan = RewindExecutor.buildPlan(recorder.getTimelineBuffer().getFramesForRewind(40));
            h.assertTrue(plan.subLevelTargetStates().containsKey(inside.getUniqueId())
                    && plan.subLevelTargetStates().containsKey(outside.getUniqueId()), "Both body snapshots must exist");
            var selected = scope.filter(h.getLevel().getServer(), plan);
            h.assertTrue(selected.subLevelTargetStates().containsKey(inside.getUniqueId()), "Inside seed must be selected");
            h.assertTrue(selected.subLevelTargetStates().containsKey(outside.getUniqueId()),
                    "Historical dependency closure must include body joined at the target time even after joint removal");
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (joint.isValid()) joint.remove();
            if (!inside.isRemoved()) container.removeSubLevel(inside, SubLevelRemovalReason.REMOVED);
            if (!outside.isRemoved()) container.removeSubLevel(outside, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_dimension_scope", timeoutTicks = 150)
    public void localShipRewindExcludesOtherDimensionPlotBlocks(GameTestHelper h) {
        var body = assemble(h, 2);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            var otherLevel = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            h.assertTrue(otherLevel != null, "Fixture requires the server Nether level");
            var center = body.getPlot().getCenterBlock();
            h.assertTrue(SubLevelContainer.getContainer(otherLevel).inBounds(center),
                    "Fixture must use the same plot coordinate range in the other dimension");
            recorder.serverTick(h.getLevel().getServer());
            recorder.serverTick(h.getLevel().getServer());
            var recorded = RewindExecutor.buildPlan(recorder.getTimelineBuffer().getFramesForRewind(40));
            h.assertTrue(recorded.subLevelTargetStates().containsKey(body.getUniqueId()), "Body snapshot must be recorded");
            var foreignKey = new com.timestop.core.rewind.RewindPlan.BlockKey(otherLevel.dimension(), center.asLong());
            // A global history plan may contain block edits in many dimensions. Local filtering must exclude these.
            var plan = new com.timestop.core.rewind.RewindPlan(1,
                    java.util.Map.of(foreignKey, Blocks.GOLD_BLOCK.defaultBlockState()),
                    java.util.Map.of(), java.util.Map.of(), Set.of(), java.util.Map.of(), java.util.Map.of(),
                    java.util.Map.of(), recorded.subLevelTargetStates(), null, java.util.List.of());
            var com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(com.x, com.y, com.z), 10);
            var selected = scope.filter(h.getLevel().getServer(), plan);
            h.assertTrue(selected.subLevelTargetStates().containsKey(body.getUniqueId()), "Overworld ship must be selected");
            h.assertTrue(!selected.blockTargetStates().containsKey(foreignKey),
                    "Overworld local rewind must not select Nether block at matching plot coordinates");
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_spherical_exit", timeoutTicks = 150)
    public void bodyExitingSphereVerticallyReturnsToNormalRate(GameTestHelper h) {
        var body = assemble(h, 2);
        var container = SubLevelContainer.getContainer(h.getLevel());
        try {
            var bubble = field(h, body, TimeMode.FAST_FORWARD);
            h.assertTrue(SableBodyTimeManager.getBodyTimeRate(h.getLevel(), body) > 1,
                    "Fixture must begin inside fast-forward field");
            var outside = new Vector3d(body.logicalPose().position()).add(0, -2 * bubble.getRadius(), 0);
            container.physicsSystem().getPipeline().teleport(body, outside, body.logicalPose().orientation());
            body.logicalPose().position().set(outside);
            var com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            h.assertTrue(TemporalBubbleManager.getDominantBubble(h.getLevel().dimension(), com.x, com.y, com.z) == null,
                    "Fixture COM must be outside the spherical field");
            double rate = SableBodyTimeManager.getBodyTimeRate(h.getLevel(), body);
            h.assertTrue(rate == 1.0, "Outside COM must return to 1x; retained rate=" + rate);
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_restored_contents", timeoutTicks = 150)
    public void recreatedBodyRestoresContentsAfterPlotReuse(GameTestHelper h) {
        var body = assemble(h, 2);
        var id = body.getUniqueId();
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        ServerSubLevel unrelated = null;
        try {
            h.assertTrue(h.getLevel().getBlockState(body.getPlot().getCenterBlock()).is(Blocks.STONE),
                    "Fixture must start with an assembled stone block");
            var oldPlot = body.getPlot().getCenterBlock();
            var com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(com.x, com.y, com.z), 10);
            recorder.serverTick(h.getLevel().getServer());
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            unrelated = assemble(h, 40);
            h.getLevel().setBlockAndUpdate(unrelated.getPlot().getCenterBlock(), Blocks.GOLD_BLOCK.defaultBlockState());
            h.assertTrue(unrelated.getPlot().getCenterBlock().equals(oldPlot), "Fixture must reuse original plot");
            var unrelatedCom = SableBodyTimeManager.getBodyCenterOfMassWorld(unrelated);
            h.assertTrue(!scope.contains(h.getLevel().dimension(), new Vec3(unrelatedCom.x, unrelatedCom.y, unrelatedCom.z)),
                    "New plot occupant must be outside local rewind sphere");
            recorder.serverTick(h.getLevel().getServer());
            var result = RewindExecutor.execute(h.getLevel().getServer(), 2, null, false, scope);
            h.assertTrue(result.success(), "Recorded rewind must succeed");
            var restored = container.getSubLevel(id);
            h.assertTrue(restored != null, "Original UUID must be recreated");
            var actual = h.getLevel().getBlockState(restored.getPlot().getCenterBlock());
            h.assertTrue(actual.is(Blocks.STONE), "Recreated UUID must contain original stone; actual=" + actual);
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            var remaining = container.getSubLevel(id);
            if (remaining != null && !remaining.isRemoved()) container.removeSubLevel(remaining, SubLevelRemovalReason.REMOVED);
            if (unrelated != null && !unrelated.isRemoved()) container.removeSubLevel(unrelated, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_removed_last_live", timeoutTicks = 150)
    public void localRewindUsesRemovedBodysLastLiveCom(GameTestHelper h) {
        var body = assemble(h, 2);
        var id = body.getUniqueId();
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            var startCom = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(startCom.x, startCom.y, startCom.z), 10);
            recorder.serverTick(h.getLevel().getServer());
            var outside = new Vector3d(body.logicalPose().position()).add(40, 0, 0);
            container.physicsSystem().getPipeline().teleport(body, outside, body.logicalPose().orientation());
            body.logicalPose().position().set(outside);
            var lastCom = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            h.assertTrue(!scope.contains(h.getLevel().dimension(), new Vec3(lastCom.x, lastCom.y, lastCom.z)),
                    "Last live COM must be outside activation sphere");
            recorder.serverTick(h.getLevel().getServer());
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            recorder.serverTick(h.getLevel().getServer());
            RewindExecutor.execute(h.getLevel().getServer(), 2, null, false, scope);
            h.assertTrue(container.getSubLevel(id) == null,
                    "Removed body with last-live COM outside scope must not be recreated inside");
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            var remaining = container.getSubLevel(id);
            if (remaining != null && !remaining.isRemoved()) container.removeSubLevel(remaining, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_joint_closure", timeoutTicks = 150)
    public void localRewindIncludesConnectedBodyOutsideSphere(GameTestHelper h) {
        var inside = assemble(h, 2);
        var outside = assemble(h, 40);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var config = new dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration(
                new Vector3d(inside.getSelfMassTracker().getCenterOfMass()),
                new Vector3d(outside.getSelfMassTracker().getCenterOfMass()), new org.joml.Quaterniond());
        var joint = container.physicsSystem().getPipeline().addConstraint(inside, outside, config);
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            h.assertTrue(joint.isValid(), "Fixture joint must be valid");
            var com = SableBodyTimeManager.getBodyCenterOfMassWorld(inside);
            var otherCom = SableBodyTimeManager.getBodyCenterOfMassWorld(outside);
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(com.x, com.y, com.z), 10);
            h.assertTrue(!scope.contains(h.getLevel().dimension(), new Vec3(otherCom.x, otherCom.y, otherCom.z)),
                    "Connected body must lie outside sphere");
            recorder.serverTick(h.getLevel().getServer());
            recorder.serverTick(h.getLevel().getServer());
            var plan = RewindExecutor.buildPlan(recorder.getTimelineBuffer().getFramesForRewind(40));
            h.assertTrue(plan.subLevelTargetStates().containsKey(inside.getUniqueId())
                    && plan.subLevelTargetStates().containsKey(outside.getUniqueId()), "Both bodies must be recorded");
            var selected = scope.filter(h.getLevel().getServer(), plan);
            h.assertTrue(selected.subLevelTargetStates().containsKey(inside.getUniqueId()), "Inside seed must be selected");
            h.assertTrue(selected.subLevelTargetStates().containsKey(outside.getUniqueId()),
                    "Connected assembly closure must include the jointed body outside the sphere");
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (joint.isValid()) joint.remove();
            if (!inside.isRemoved()) container.removeSubLevel(inside, SubLevelRemovalReason.REMOVED);
            if (!outside.isRemoved()) container.removeSubLevel(outside, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    private static ServerSubLevel assemble(GameTestHelper h, int x) {
        // GameTest may choose locations millions of blocks from the origin. Keep these
        // native-physics measurements near zero to avoid float precision dominating dx.
        BlockPos p = new BlockPos(200 + x, 150, 200);
        h.getLevel().getChunkAt(p);
        h.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        var bounds = new dev.ryanhcode.sable.companion.math.BoundingBox3i(
                p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ());
        return SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), p, Set.of(p), bounds);
    }

    private static TemporalBubble field(GameTestHelper h, ServerSubLevel body, TimeMode mode) {
        return field(h, body, mode, 12);
    }

    private static TemporalBubble field(GameTestHelper h, ServerSubLevel body, TimeMode mode, double radius) {
        var com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
        var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(),
                new Vec3(com.x, com.y, com.z), radius, mode, 200, WatchTier.DIAMOND, null, 0, Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);
        return bubble;
    }

    private static void checkHorizontalRate(GameTestHelper h, TimeMode mode, double expected, int substeps) {
        var body = assemble(h, 2);
        var control = assemble(h, 200);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        int previousSubsteps = physics.getConfig().substepsPerTick;
        double previousSlow = TimeStopConfig.COMMON.slowMotionRate.get();
        double previousFast = TimeStopConfig.COMMON.fastForwardRate.get();
        try {
            TimeStopConfig.COMMON.slowMotionRate.set(0.25);
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);
            physics.getConfig().substepsPerTick = substeps;
            // At 5x, gravity can carry the body out of a radius-12 sphere during
            // this measurement. Keep the real sphere large enough for the entire
            // trajectory and move the control farther away; do not alter field semantics.
            var bubble = field(h, body, mode, 80);
            h.assertTrue(Math.abs(SableBodyTimeManager.getBodyTimeRate(h.getLevel(), body) - expected) < 1e-8,
                    "Fixture must select requested field rate");
            h.assertTrue(SableBodyTimeManager.getBodyTimeRate(h.getLevel(), control) == 1.0,
                    "Control must remain outside field");
            RigidBodyHandle.of(body).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
            RigidBodyHandle.of(control).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
            double initialX = body.logicalPose().position().x();
            double controlX = control.logicalPose().position().x();
            for (int i = 0; i < 10; i++) physics.tick(container);
            var finalCom = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            h.assertTrue(new Vec3(finalCom.x, finalCom.y, finalCom.z).distanceTo(bubble.getCenter()) < bubble.getRadius(),
                    "Rate measurement must remain inside the real spherical field");
            double dx = body.logicalPose().position().x() - initialX;
            double normalDx = control.logicalPose().position().x() - controlX;
            h.assertTrue(normalDx > 0.1, "Control must move horizontally; dx=" + normalDx);
            // Generous tolerance allows discrete scheduling, but excludes unscaled or double-speed motion.
            double ratio = dx / normalDx;
            h.assertTrue(Math.abs(ratio - expected) < expected * 0.25,
                    "Expected horizontal rate " + expected + ", actual=" + ratio + ", dx=" + dx + ", control=" + normalDx);
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            physics.getConfig().substepsPerTick = previousSubsteps;
            TimeStopConfig.COMMON.slowMotionRate.set(previousSlow);
            TimeStopConfig.COMMON.fastForwardRate.set(previousFast);
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            if (!control.isRemoved()) container.removeSubLevel(control, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_fast", timeoutTicks = 150)
    public void localFastForwardAcceleratesBodyFivefold(GameTestHelper h) {
        checkHorizontalRate(h, TimeMode.FAST_FORWARD, 5.0, 2);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_slow", timeoutTicks = 150)
    public void quarterSpeedWithFourSubsteps(GameTestHelper h) {
        checkHorizontalRate(h, TimeMode.SLOW_MOTION, 0.25, 4);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_superhot", timeoutTicks = 150)
    public void superhotRespondsToOwnerActivity(GameTestHelper h) {
        var body = assemble(h, 2);
        var container = SubLevelContainer.getContainer(h.getLevel());
        try {
            var bubble = field(h, body, TimeMode.SUPERHOT);
            bubble.setSuperhotActivity(1.0f);
            h.assertTrue(bubble.getSuperhotActivity() == 1.0f, "Fixture owner must be active");
            double actual = SableBodyTimeManager.getBodyTimeRate(h.getLevel(), body);
            h.assertTrue(Math.abs(actual - 1.0) < 1e-6, "Active SUPERHOT must yield 1x, actual=" + actual);
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_destroyed", timeoutTicks = 150)
    public void globalRewindRecreatesRemovedBody(GameTestHelper h) {
        var body = assemble(h, 2);
        var id = body.getUniqueId();
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            recorder.serverTick(h.getLevel().getServer());
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            h.assertTrue(container.getSubLevel(id) == null, "Fixture must remove original body");
            recorder.serverTick(h.getLevel().getServer());
            var result = RewindExecutor.execute(h.getLevel().getServer(), 2, null, false);
            h.assertTrue(result.success(), "Rewind must accept recorded history");
            h.assertTrue(container.getSubLevel(id) != null, "Rewind reported success but removed ship UUID was not restored");
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (container.getSubLevel(id) instanceof ServerSubLevel restored && !restored.isRemoved()) {
                container.removeSubLevel(restored, SubLevelRemovalReason.REMOVED);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "review_scope", timeoutTicks = 150)
    public void localRewindExcludesShipCurrentlyOutsideSphere(GameTestHelper h) {
        var body = assemble(h, 2);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        try {
            var start = new Vector3d(body.logicalPose().position());
            var startCom = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            recorder.serverTick(h.getLevel().getServer());
            var outside = new Vector3d(start).add(40, 0, 0);
            container.physicsSystem().getPipeline().teleport(body, outside, body.logicalPose().orientation());
            body.logicalPose().position().set(outside);
            recorder.serverTick(h.getLevel().getServer());
            var scope = new RewindScope(h.getLevel().dimension(), new Vec3(startCom.x, startCom.y, startCom.z), 10);
            var currentCom = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            h.assertTrue(!scope.contains(h.getLevel().dimension(), new Vec3(currentCom.x, currentCom.y, currentCom.z)),
                    "Fixture current COM must be outside scope");
            RewindExecutor.execute(h.getLevel().getServer(), 2, null, false, scope);
            h.assertTrue(body.logicalPose().position().distance(outside) < 1e-5,
                    "Current AND target COM rule violated: outside body moved " + body.logicalPose().position().distance(outside));
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }
    @GameTest(template = EMPTY_STRUCTURE, batch = "parity_sable_prediction", timeoutTicks = 150)
    public void predictionUsesFractionalNativeBodySteps(GameTestHelper h) {
        double previous=TimeStopConfig.COMMON.predictionRate.get();
        try {TimeStopConfig.COMMON.predictionRate.set(.25);checkHorizontalRate(h,TimeMode.TIME_PREDICTION,.25,4);}
        finally {TimeStopConfig.COMMON.predictionRate.set(previous);}
    }
    @GameTest(template = EMPTY_STRUCTURE, batch = "parity_sable_combat", timeoutTicks = 150)
    public void combatSlowKeepsNativeBodiesAtNormalSpeed(GameTestHelper h) {checkHorizontalRate(h,TimeMode.COMBAT_SLOW,1,4);}
    @GameTest(template = EMPTY_STRUCTURE, batch = "parity_sable_self", timeoutTicks = 150)
    public void selfAccelerationKeepsNativeBodiesAtNormalSpeed(GameTestHelper h) {checkHorizontalRate(h,TimeMode.SELF_ACCELERATION,1,4);}
}
