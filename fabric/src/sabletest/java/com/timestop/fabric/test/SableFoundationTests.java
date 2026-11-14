package com.timestop.fabric.test;

import com.timestop.compat.TemporalWorlds;
import com.timestop.compat.sable.SableBodyTimeManager;
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

public class SableFoundationTests implements FabricGameTest {
    private static ServerSubLevel assemble(GameTestHelper h) {
        return assemble(h, new BlockPos(2, 8, 2));
    }

    private static ServerSubLevel assemble(GameTestHelper h, BlockPos relativePos) {
        BlockPos pos = h.absolutePos(relativePos);
        h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        // Use the actual assembly path, including plot registration and native collider upload.
        var bounds = new dev.ryanhcode.sable.companion.math.BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX(), pos.getY(), pos.getZ());
        return SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), pos, Set.of(pos), bounds);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_spatial", timeoutTicks = 100)
    public void bodyAnchorFollowsMotionAndNeverAliasesRemovedPlots(GameTestHelper h) {
        var body = assemble(h);
        var container = SubLevelContainer.getContainer(h.getLevel());
        try {
            var massCenter = body.getSelfMassTracker().getCenterOfMass();
            Vec3 plotPoint = new Vec3(massCenter.x(), massCenter.y(), massCenter.z());
            var bridge = TemporalWorlds.bridge();
            var anchor = bridge.anchor(h.getLevel(), plotPoint);
            h.assertTrue(body.getUniqueId().equals(anchor.bodyId()), "Anchor must contain stable body UUID");
            Vec3 before = bridge.resolve(h.getLevel(), anchor).orElseThrow();
            body.logicalPose().position().add(10, 3, -4);
            Vec3 after = bridge.resolve(h.getLevel(), anchor).orElseThrow();
            h.assertTrue(after.distanceTo(before.add(10, 3, -4)) < 1e-6, "Anchor must follow body translation");
            var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(), after,
                    2, TimeMode.TIME_STOP, 100, WatchTier.DIAMOND, null, 0, Set.of());
            h.assertTrue(bubble.contains(h.getLevel().dimension(), plotPoint), "Plot coordinates must select world-space field");
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            h.assertTrue(bridge.resolve(h.getLevel(), anchor).isEmpty(), "Removed UUID must not resolve stale plot coordinates");
        } finally {
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_global", timeoutTicks = 100)
    public void globalStasisPreservesPoseAndMomentumWithoutOwningPause(GameTestHelper h) {
        var body = assemble(h);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var handle = RigidBodyHandle.of(body);
        try {
            handle.addLinearAndAngularVelocity(new Vector3d(2, 1, 0), new Vector3d(0, .15, 0));
            Vector3d linear = handle.getLinearVelocity(new Vector3d());
            Vector3d angular = handle.getAngularVelocity(new Vector3d());
            Vector3d position = new Vector3d(body.logicalPose().position());
            boolean paused = physics.getPaused();
            TimeStopManager.startGlobalTimeStop(h.getLevel(), null, 100, TimeMode.TIME_STOP);
            for (int i = 0; i < 20; i++) physics.tick(container);
            h.assertTrue(body.logicalPose().position().distance(position) < 1e-8, "Global stasis must stop body translation");
            h.assertTrue(handle.getLinearVelocity(new Vector3d()).distance(linear) < 1e-8, "Stasis must preserve linear velocity");
            h.assertTrue(handle.getAngularVelocity(new Vector3d()).distance(angular) < 1e-8, "Stasis must preserve angular velocity");
            h.assertTrue(physics.getPaused() == paused, "Time Stop must not overwrite Sable pause state");
            TimeStopManager.resumeTime(h.getLevel());
            physics.tick(container);
            h.assertTrue(body.logicalPose().position().distance(position) > 1e-4, "Physics must resume");
        } finally {
            TimeStopManager.resumeTime(h.getLevel());
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_local_physics", timeoutTicks = 150)
    public void localBubbleFreezesContainedBodyWhileFreeBodyMoves(GameTestHelper h) {
        var bodyFrozen = assemble(h, new BlockPos(2, 8, 2));
        var bodyFree = assemble(h, new BlockPos(25, 8, 2));
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var handleFrozen = RigidBodyHandle.of(bodyFrozen);
        var handleFree = RigidBodyHandle.of(bodyFree);

        try {
            handleFrozen.addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
            handleFree.addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());

            Vector3d startFrozen = new Vector3d(bodyFrozen.logicalPose().position());
            Vector3d startFree = new Vector3d(bodyFree.logicalPose().position());

            // Place local TIME_STOP bubble around bodyFrozen
            Vec3 bubbleCenter = new Vec3(startFrozen.x, startFrozen.y, startFrozen.z);
            var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(),
                    bubbleCenter, 6.0, TimeMode.TIME_STOP, 200, WatchTier.DIAMOND, null, 0, Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);

            for (int i = 0; i < 20; i++) physics.tick(container);

            // Frozen body must not have moved
            h.assertTrue(bodyFrozen.logicalPose().position().distance(startFrozen) < 1e-4, "Contained body must remain frozen");
            // Free body outside the bubble must have advanced
            h.assertTrue(bodyFree.logicalPose().position().distance(startFree) > 0.5, "Uncontained body must advance freely in physics");

            // Resume stasis by stopping bubbles
            TemporalBubbleManager.stopAllBubbles(h.getLevel());

            Vector3d unfreezePos = new Vector3d(bodyFrozen.logicalPose().position());
            for (int i = 0; i < 5; i++) physics.tick(container);

            // Now frozen body must resume motion
            h.assertTrue(bodyFrozen.logicalPose().position().distance(unfreezePos) > 1e-3, "Formerly frozen body must resume motion");
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!bodyFrozen.isRemoved()) container.removeSubLevel(bodyFrozen, SubLevelRemovalReason.REMOVED);
            if (!bodyFree.isRemoved()) container.removeSubLevel(bodyFree, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_impulse_buffer", timeoutTicks = 150)
    public void externalImpulsesBufferDuringStasisAndReleaseOnResume(GameTestHelper h) {
        var body = assemble(h, new BlockPos(2, 8, 2));
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();

        try {
            Vector3d startPos = new Vector3d(body.logicalPose().position());

            // Place local TIME_STOP bubble around body
            Vec3 bubbleCenter = new Vec3(startPos.x, startPos.y, startPos.z);
            var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(),
                    bubbleCenter, 6.0, TimeMode.TIME_STOP, 200, WatchTier.DIAMOND, null, 0, Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);

            // Tick once to engage stasis hold
            physics.tick(container);
            h.assertTrue(SableBodyTimeManager.isBodyHeld(body), "Body should be held by stasis constraint");

            // Buffer an external impulse
            SableBodyTimeManager.bufferImpulse(body, new Vector3d(100, 0, 0), new Vector3d());

            // Tick while still frozen
            for (int i = 0; i < 10; i++) physics.tick(container);
            h.assertTrue(body.logicalPose().position().distance(startPos) < 1e-4, "Body must remain static while frozen despite buffered impulse");

            // End stasis
            TemporalBubbleManager.stopAllBubbles(h.getLevel());

            // Tick physics; buffered impulse must now apply and accelerate the body
            for (int i = 0; i < 5; i++) physics.tick(container);
            h.assertTrue(body.logicalPose().position().distance(startPos) > 0.05, "Buffered impulse must accelerate body upon stasis release");
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_deck_transport", timeoutTicks = 150)
    public void frozenEntityFollowsMovingDeck(GameTestHelper h) {
        BlockPos relativePos = new BlockPos(2, 8, 2);
        var body = assemble(h, relativePos);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var handle = RigidBodyHandle.of(body);

        BlockPos plotCenter = body.getPlot().getCenterBlock();
        // Place a deck block 4 blocks offset from COM
        BlockPos deckPlotPos = plotCenter.offset(4, 0, 0);
        h.getLevel().setBlockAndUpdate(deckPlotPos, Blocks.STONE.defaultBlockState());

        Vec3 deckWorldPos = TemporalWorlds.toWorld(h.getLevel(), Vec3.atCenterOf(deckPlotPos));
        var mob = new net.minecraft.world.entity.animal.Pig(net.minecraft.world.entity.EntityType.PIG, h.getLevel());
        mob.setPos(deckWorldPos.x, deckWorldPos.y + 0.6, deckWorldPos.z);
        h.getLevel().addFreshEntity(mob);

        try {
            // Apply TIME_STOP bubble around the mob with radius 2.0, leaving ship COM (4 blocks away) active
            Vec3 mobPos = mob.position();
            var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(),
                    mobPos, 2.0, TimeMode.TIME_STOP, 200, WatchTier.DIAMOND, null, 0, Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);
            h.assertTrue(TemporalBubbleManager.isEntityInStasis(mob), "Mob must be detected in stasis");
            h.assertTrue(!SableBodyTimeManager.isBodyStasisActive(h.getLevel(), body), "Ship COM must remain outside stasis");

            // Tick transport anchor
            TemporalWorlds.bridge().updateStasisEntityTransport(mob);

            // Now move the ship forward
            handle.addLinearAndAngularVelocity(new Vector3d(5, 0, 0), new Vector3d());
            for (int i = 0; i < 10; i++) {
                physics.tick(container);
                TemporalWorlds.bridge().updateStasisEntityTransport(mob);
            }

            Vec3 newDeckWorldPos = TemporalWorlds.toWorld(h.getLevel(), Vec3.atCenterOf(deckPlotPos));
            h.assertTrue(newDeckWorldPos.distanceTo(deckWorldPos) > 0.4, "Ship deck must have moved forward");

            // The frozen mob must have maintained its relative position on top of the moving deck
            double deckDistance = mob.position().distanceTo(newDeckWorldPos.add(0, 0.6, 0));
            h.assertTrue(deckDistance < 0.25, "Frozen mob must follow moving deck: " + deckDistance);
            h.assertTrue(mob.position().distanceTo(mobPos) > 0.4, "Frozen mob must not be left behind at its starting position");
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            TemporalWorlds.bridge().clearStasisEntityTransport(mob);
            mob.discard();
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_moving_pedestal", timeoutTicks = 150)
    public void movingPedestalSynchronizesBubbleCenter(GameTestHelper h) {
        BlockPos relativePos = new BlockPos(2, 8, 2);
        var body = assemble(h, relativePos);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var handle = RigidBodyHandle.of(body);

        BlockPos plotCenter = body.getPlot().getCenterBlock();
        BlockPos pedestalPos = plotCenter.above();
        BlockPos redstonePos = pedestalPos.east();

        h.getLevel().setBlockAndUpdate(pedestalPos, com.timestop.pedestal.ModPedestals.DIAMOND.get().defaultBlockState()
                .setValue(com.timestop.pedestal.PedestalBlock.ACTIVE, true));
        h.getLevel().setBlockAndUpdate(redstonePos, Blocks.REDSTONE_BLOCK.defaultBlockState());

        var be = (com.timestop.pedestal.PedestalBlockEntity) h.getLevel().getBlockEntity(pedestalPos);
        h.assertTrue(be != null, "Pedestal block entity must exist");
        h.assertTrue(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.isInPlotGrid(h.getLevel(), be.getBlockPos()), "Pedestal must be in plot grid");

        be.inventory.setItem(0, new net.minecraft.world.item.ItemStack(com.timestop.item.ModItems.DIAMOND_WATCH.get()));
        be.setOwner(h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
        be.configure(TimeMode.FAST_FORWARD, 10);
        com.timestop.pedestal.PedestalManager.track(be);

        try {
            // Server tick should register and start the pedestal's stationary bubble
            com.timestop.pedestal.PedestalManager.serverTick();

            TemporalBubble bubble = TemporalBubbleManager.getBubble(be.getFieldId());
            h.assertTrue(bubble != null, "Bubble must be created by pedestal");
            Vec3 initialCenter = bubble.getCenter();
            Vec3 expectedInitial = TemporalWorlds.toWorld(h.getLevel(), Vec3.atCenterOf(pedestalPos));
            h.assertTrue(initialCenter.distanceTo(expectedInitial) < 1e-4, "Bubble center must match pedestal world position");

            // Verify getBlockEntityAtWorld works across sublevel boundaries
            var foundBe = TemporalWorlds.bridge().getBlockEntityAtWorld(h.getLevel(), initialCenter);
            h.assertTrue(foundBe == be, "getBlockEntityAtWorld must locate pedestal in plot grid from world coordinate");

            // Now move the sublevel
            handle.addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());
            for (int i = 0; i < 10; i++) {
                physics.tick(container);
            }

            // Server tick updates the bubble center in place
            com.timestop.pedestal.PedestalManager.serverTick();

            Vec3 newCenter = bubble.getCenter();
            Vec3 expectedNew = TemporalWorlds.toWorld(h.getLevel(), Vec3.atCenterOf(pedestalPos));
            h.assertTrue(newCenter.distanceTo(expectedNew) < 1e-4, "Bubble center must follow pedestal to new world position");
            h.assertTrue(newCenter.distanceTo(initialCenter) > 0.4, "Bubble center must have advanced with ship: " + newCenter.distanceTo(initialCenter));

            // Verify the bubble instance was retained (not recreated)
            h.assertTrue(TemporalBubbleManager.getBubble(be.getFieldId()) == bubble, "Bubble instance must be preserved in place");
        } finally {
            com.timestop.pedestal.PedestalManager.stop(be);
            com.timestop.pedestal.PedestalManager.untrack(be);
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_rewind_global", timeoutTicks = 150)
    public void globalRewindRestoresSublevelPoseAndMomentum(GameTestHelper h) {
        var body = assemble(h, new BlockPos(2, 8, 2));
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var handle = RigidBodyHandle.of(body);

        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();

        try {
            handle.addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d(0, 0.1, 0));
            Vector3d initialPos = new Vector3d(body.logicalPose().position());
            Vector3d initialLinVel = handle.getLinearVelocity(new Vector3d());
            Vector3d initialAngVel = handle.getAngularVelocity(new Vector3d());

            // Record frame at t0
            recorder.serverTick(h.getLevel().getServer());

            // Run physics forward for 10 ticks
            for (int i = 0; i < 10; i++) {
                physics.tick(container);
            }

            Vector3d advancedPos = new Vector3d(body.logicalPose().position());
            h.assertTrue(advancedPos.distance(initialPos) > 0.5, "Body must have translated forward: " + advancedPos.distance(initialPos));

            // Record advanced frame
            recorder.serverTick(h.getLevel().getServer());

            // Perform global rewind to restore the ship to t0
            var result = RewindExecutor.execute(h.getLevel().getServer(), 2, null, false);
            h.assertTrue(result.success(), "Rewind execution must succeed");

            Vector3d rewoundPos = body.logicalPose().position();
            Vector3d rewoundLinVel = handle.getLinearVelocity(new Vector3d());
            Vector3d rewoundAngVel = handle.getAngularVelocity(new Vector3d());

            h.assertTrue(rewoundPos.distance(initialPos) < 1e-4, "Rewind must restore exact initial position: distance = " + rewoundPos.distance(initialPos));
            h.assertTrue(rewoundLinVel.distance(initialLinVel) < 1e-4, "Rewind must restore exact linear velocity");
            h.assertTrue(rewoundAngVel.distance(initialAngVel) < 1e-4, "Rewind must restore exact angular velocity");
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_rewind_local", timeoutTicks = 150)
    public void localRewindRestoresContainedShipWhileFreeShipContinues(GameTestHelper h) {
        var bodyContained = assemble(h, new BlockPos(2, 8, 2));
        var bodyFree = assemble(h, new BlockPos(30, 8, 2));
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var handleContained = RigidBodyHandle.of(bodyContained);
        var handleFree = RigidBodyHandle.of(bodyFree);

        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();

        try {
            handleContained.addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());
            handleFree.addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());

            Vector3d initialContainedPos = new Vector3d(bodyContained.logicalPose().position());
            Vector3d initialFreePos = new Vector3d(bodyFree.logicalPose().position());

            // Record initial frame
            recorder.serverTick(h.getLevel().getServer());

            // Run physics forward for 10 ticks
            for (int i = 0; i < 10; i++) {
                physics.tick(container);
            }

            Vector3d advancedContainedPos = new Vector3d(bodyContained.logicalPose().position());
            Vector3d advancedFreePos = new Vector3d(bodyFree.logicalPose().position());

            h.assertTrue(advancedContainedPos.distance(initialContainedPos) > 0.5, "Contained body must move forward");
            h.assertTrue(advancedFreePos.distance(initialFreePos) > 0.5, "Free body must move forward");

            // Record advanced frame
            recorder.serverTick(h.getLevel().getServer());

            // Local rewind sphere centered around initialContainedPos
            Vec3 sphereCenter = new Vec3(initialContainedPos.x, initialContainedPos.y, initialContainedPos.z);
            RewindScope scope = new RewindScope(h.getLevel().dimension(), sphereCenter, 10.0);

            var result = RewindExecutor.execute(h.getLevel().getServer(), 2, null, false, scope);
            h.assertTrue(result.success(), "Local rewind execution must succeed");

            Vector3d rewoundContainedPos = bodyContained.logicalPose().position();
            Vector3d currentFreePos = bodyFree.logicalPose().position();

            h.assertTrue(rewoundContainedPos.distance(initialContainedPos) < 1e-4, "Local rewind must restore contained body to initial position");
            h.assertTrue(currentFreePos.distance(advancedFreePos) < 1e-4, "Free body outside rewind scope must NOT be rewound");
        } finally {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            if (!bodyContained.isRemoved()) container.removeSubLevel(bodyContained, SubLevelRemovalReason.REMOVED);
            if (!bodyFree.isRemoved()) container.removeSubLevel(bodyFree, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_rate_dilation", timeoutTicks = 150)
    public void slowMotionDilationScalesBodyAdvancement(GameTestHelper h) {
        var bodySlow = assemble(h, new BlockPos(2, 8, 2));
        var bodyNormal = assemble(h, new BlockPos(30, 8, 2));
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();
        var handleSlow = RigidBodyHandle.of(bodySlow);
        var handleNormal = RigidBodyHandle.of(bodyNormal);

        try {
            handleSlow.addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());
            handleNormal.addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());

            Vector3d startSlow = new Vector3d(bodySlow.logicalPose().position());
            Vector3d startNormal = new Vector3d(bodyNormal.logicalPose().position());

            Vector3d com = SableBodyTimeManager.getBodyCenterOfMassWorld(bodySlow);
            var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(),
                    new Vec3(com.x, com.y, com.z), 10, TimeMode.SLOW_MOTION, 200, WatchTier.DIAMOND, null, 0, Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);

            for (int i = 0; i < 20; i++) {
                physics.tick(container);
            }

            double distSlow = bodySlow.logicalPose().position().distance(startSlow);
            double distNormal = bodyNormal.logicalPose().position().distance(startNormal);

            h.assertTrue(distNormal > 1.0, "Normal body must advance forward: " + distNormal);
            h.assertTrue(distSlow > 0.1, "Slow body must advance some distance: " + distSlow);
            h.assertTrue(distSlow < distNormal * 0.5, "Slowed body must advance significantly less than normal body: slow=" + distSlow + ", normal=" + distNormal);
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!bodySlow.isRemoved()) container.removeSubLevel(bodySlow, SubLevelRemovalReason.REMOVED);
            if (!bodyNormal.isRemoved()) container.removeSubLevel(bodyNormal, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_multibody_cleanup", timeoutTicks = 150)
    public void multiBodyConstraintCleanupOnRemoval(GameTestHelper h) {
        var body1 = assemble(h, new BlockPos(2, 8, 2));
        var body2 = assemble(h, new BlockPos(15, 8, 2));
        var body3 = assemble(h, new BlockPos(30, 8, 2));
        var bodyFree = assemble(h, new BlockPos(60, 8, 2));
        var container = SubLevelContainer.getContainer(h.getLevel());
        var physics = container.physicsSystem();

        try {
            Vector3d com2 = SableBodyTimeManager.getBodyCenterOfMassWorld(body2);
            var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(),
                    new Vec3(com2.x, com2.y, com2.z), 25, TimeMode.TIME_STOP, 200, WatchTier.DIAMOND, null, 0, Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);

            physics.tick(container);

            h.assertTrue(SableBodyTimeManager.isBodyHeld(body1), "Body 1 must be held");
            h.assertTrue(SableBodyTimeManager.isBodyHeld(body2), "Body 2 must be held");
            h.assertTrue(SableBodyTimeManager.isBodyHeld(body3), "Body 3 must be held");
            h.assertTrue(!SableBodyTimeManager.isBodyHeld(bodyFree), "Free body must not be held");

            container.removeSubLevel(body2, SubLevelRemovalReason.REMOVED);
            h.assertTrue(!SableBodyTimeManager.isBodyHeld(body2), "Removed body must have constraint cleaned up");

            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            physics.tick(container);

            h.assertTrue(!SableBodyTimeManager.isBodyHeld(body1), "Body 1 must be released");
            h.assertTrue(!SableBodyTimeManager.isBodyHeld(body3), "Body 3 must be released");
        } finally {
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            if (!body1.isRemoved()) container.removeSubLevel(body1, SubLevelRemovalReason.REMOVED);
            if (!body2.isRemoved()) container.removeSubLevel(body2, SubLevelRemovalReason.REMOVED);
            if (!body3.isRemoved()) container.removeSubLevel(body3, SubLevelRemovalReason.REMOVED);
            if (!bodyFree.isRemoved()) container.removeSubLevel(bodyFree, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }
}
