package com.timestop.neoforge.test;

import com.timestop.TimeStopMod;
import com.timestop.core.*;
import com.timestop.item.WatchTier;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.util.*;

@GameTestHolder("timestop")
@PrefixGameTestTemplate(false)
public class SableLandingTests {
    @GameTest(template = "landing_empty", timeoutTicks = 4500, batch = "sable_landing")
    public static void teleportedStructureSurvivesBubbleEntryAndTerrainContact(GameTestHelper h) {
        runDrops(h);
    }

    private static void runDrops(GameTestHelper h) {
        var level = h.getLevel();
        // Use ordinary playable world coordinates, avoiding GameTest's random distant arena.
        var origin = new BlockPos(32, 64, 32);
        var container = SubLevelContainer.getContainer(level);
        var physics = container.physicsSystem();
        // Bedrock cannot break on impact. Test the same stone structure in each mode.
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++)
            level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState());
        // GameTest chunks do not acquire normal player physics tickets. Upload their terrain.
        for (int cx = (origin.getX() - 6) >> 4; cx <= (origin.getX() + 6) >> 4; cx++) {
            for (int cz = (origin.getZ() - 6) >> 4; cz <= (origin.getZ() + 6) >> 4; cz++) {
                var chunk = level.getChunk(cx, cz);
                int sectionY = (origin.getY() - 1) >> 4;
                physics.getPipeline().handleChunkSectionAddition(chunk.getSection(chunk.getSectionIndex(origin.getY() - 1)), cx, sectionY, cz, true);
            }
        }
        var modes = new TimeMode[]{null, TimeMode.DECELERATION_FIELD, TimeMode.FAST_FORWARD,
                TimeMode.SLOW_MOTION, TimeMode.MATRIX, TimeMode.SUPERHOT, TimeMode.TIME_STOP,
                TimeMode.TIME_PREDICTION,TimeMode.COMBAT_SLOW,TimeMode.SELF_ACCELERATION};
        var index = new int[]{0};
        var step = new int[]{0};
        var current = new ServerSubLevel[]{null};
        var warmup = new int[]{0};
        h.onEachTick(() -> {
            if (warmup[0]++ < 30) return;
            var mode = modes[index[0] % modes.length];
            boolean startsOnGround = index[0] >= modes.length * 2;
            boolean rotated = index[0] >= modes.length;
            ServerSubLevel body = null;
            if (current[0] == null) {
                var blocks = new HashSet<BlockPos>();
                var base = origin.above(2);
                for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
                    var pos = base.offset(x, 0, z);
                    level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    blocks.add(pos);
                }
                body = SubLevelAssemblyHelper.assembleBlocks(level, base, blocks,
                        new BoundingBox3i(base.getX(), base.getY(), base.getZ(), base.getX() + 2, base.getY(), base.getZ() + 2));
                if (mode != null) {
                    var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), level.dimension(),
                            Vec3.atCenterOf(origin.offset(1, 0, 1)), 6, mode, 10000, WatchTier.CREATIVE, null, 0, Set.of()).stationary(true);
                    TemporalBubbleManager.putStationaryBubble(level, bubble, null);
                }
                // Use the same pipeline operations as /sable teleport, starting above the field.
                physics.getPipeline().resetVelocity(body);
                physics.getPipeline().teleport(body,
                        new Vector3d(origin.getX() + 1.5, origin.getY() + (startsOnGround ? .5 : 12), origin.getZ() + 1.5),
                        rotated ? new Quaterniond().rotateY(.7) : body.logicalPose().orientation());
                current[0] = body;
                step[0] = 0;
            }
            body = current[0];
            try {
                for (int j = 0; j < 4; j++) {
                    int i = step[0]++;
                    if (i == 150 && (mode == TimeMode.TIME_STOP || mode == TimeMode.SUPERHOT))
                        TemporalBubbleManager.stopAllBubbles(level);
                    var beforeOrientation = new Quaterniond(body.logicalPose().orientation());
                    var beforePosition = new Vector3d(body.logicalPose().position());
                    physics.tick(container);
                    h.assertFalse(body.isRemoved(), "Structure removed after terrain contact in " + mode + " at step " + i);
                    h.assertTrue(body.logicalPose().position().isFinite(), "Non-finite structure pose in " + mode);
                    h.assertTrue(RigidBodyHandle.of(body).getLinearVelocity(new Vector3d()).isFinite(), "Non-finite momentum in " + mode);
                    h.assertTrue(body.logicalPose().position().distance(new Vector3d(33.5, 70, 33.5)) < 15,
                            "Structure must stay near the landing area in " + mode + " at step " + i);
                    h.assertTrue(RigidBodyHandle.of(body).getLinearVelocity(new Vector3d()).length() < 30,
                            "Terrain contact must not inject runaway momentum in " + mode + " at step " + i);
                    var angular = RigidBodyHandle.of(body).getAngularVelocity(new Vector3d());
                    double orientationStep = 2 * Math.acos(Math.min(1, Math.abs(beforeOrientation.dot(body.logicalPose().orientation()))));
                    if (i % 50 == 0) TimeStopMod.LOGGER.info("SABLE_ROTATION_TRACE: mode={}, rotated={}, ground={}, step={}, angular={}, orientationStep={}, position={}",
                            mode, rotated, startsOnGround, i, angular, orientationStep, body.logicalPose().position());
                    h.assertTrue(angular.isFinite() && angular.length() < 2,
                            "Structure must not spin rapidly in " + mode + " at step " + i + ": " + angular);
                    h.assertTrue(orientationStep < .1,
                            "Structure orientation must not jump during a hold in " + mode + " at step " + i + ": " + orientationStep);
                    var nativePose = physics.getPipeline().readPose(body, new Pose3d());
                    double nativeOrientationDifference = 2 * Math.acos(Math.min(1,
                            Math.abs(nativePose.orientation().dot(body.logicalPose().orientation()))));
                    h.assertTrue(nativeOrientationDifference < .002 && nativePose.position().distance(body.logicalPose().position()) < .02,
                            "Native and published pose must agree after a hold in " + mode + " at step " + i);
                    if (i > 250) {
                        h.assertTrue(angular.length() < .1 && orientationStep < .01,
                                "Landed structure must settle in " + mode + " at step " + i);
                        h.assertTrue(RigidBodyHandle.of(body).getLinearVelocity(new Vector3d()).length() < .1
                                        && beforePosition.distance(body.logicalPose().position()) < .005,
                                "Landed structure must not shake in " + mode + " at step " + i);
                    }
                }
                if (step[0] < 450) return;
                double finalY = body.logicalPose().position().y();
                h.assertTrue(finalY >= origin.getY() - .2 && finalY < origin.getY() + 3,
                        "Structure must land on terrain in " + mode + ": y=" + finalY + ", floor=" + origin.getY());
                h.assertTrue(body.getSelfMassTracker().getMass() == 18, "All nine structure blocks must survive in " + mode);
                TimeStopMod.LOGGER.info("SABLE_LANDING_PASS: mode={}, rotated={}, startsOnGround={}, position={}", mode, rotated, startsOnGround, body.logicalPose().position());
                TemporalBubbleManager.stopAllBubbles(level);
                container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                current[0] = null;
                if (++index[0] == modes.length * 3) h.succeed();
            } catch (Throwable failure) {
                TemporalBubbleManager.stopAllBubbles(level);
                if (body != null && !body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                throw failure;
            }
        });
    }

    @GameTest(template = "landing_empty", timeoutTicks = 200, batch = "sable_rotation")
    public static void tiltedStructureKeepsItsNativeRotationAcrossSlowHolds(GameTestHelper h) {
        var level = h.getLevel();
        var container = SubLevelContainer.getContainer(level);
        var physics = container.physicsSystem();
        var base = new BlockPos(80, 150, 80);
        var blocks = new HashSet<BlockPos>();
        for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) {
            var pos = base.offset(x, y, 0);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            blocks.add(pos);
        }
        var body = SubLevelAssemblyHelper.assembleBlocks(level, base, blocks,
                new BoundingBox3i(80, 150, 80, 82, 151, 80));
        var orientation = new Quaterniond().rotateXYZ(.35, .7, -.2);
        try {
            physics.getPipeline().resetVelocity(body);
            physics.getPipeline().teleport(body, new Vector3d(81.5, 150, 80.5), orientation);
            var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), level.dimension(),
                    new Vec3(81.5, 150, 80.5), 20, TimeMode.SLOW_MOTION, 10000, WatchTier.CREATIVE, null, 0, Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(level, bubble, null);
            for (int i = 0; i < 60; i++) {
                physics.tick(container);
                h.assertFalse(body.isRemoved(), "Tilted body must survive slow-time holds");
                var nativePose = physics.getPipeline().readPose(body, new Pose3d());
                double angle = 2 * Math.acos(Math.min(1, Math.abs(orientation.dot(nativePose.orientation()))));
                h.assertTrue(angle < .002, "Slow-time holds must preserve pitch/yaw/roll without torque: " + angle);
                h.assertTrue(RigidBodyHandle.of(body).getAngularVelocity(new Vector3d()).length() < .001,
                        "Slow-time holds must not generate angular momentum");
            }
            TimeStopMod.LOGGER.info("SABLE_TILTED_HOLD_PASS: native rotation preserved for 60 physics ticks");
            var beforeSpin = new Quaterniond(body.logicalPose().orientation());
            RigidBodyHandle.of(body).addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0, .5, 0));
            for (int i = 0; i < 20; i++) {
                physics.tick(container);
                double speed = RigidBodyHandle.of(body).getAngularVelocity(new Vector3d()).length();
                h.assertTrue(speed > .1 && speed <= .51,
                        "Slow-time holds must preserve intentional angular momentum without amplification: " + speed);
            }
            double spinAngle = 2 * Math.acos(Math.min(1, Math.abs(beforeSpin.dot(body.logicalPose().orientation()))));
            h.assertTrue(spinAngle > .01, "Intentional rotation must still advance in slow time");
            TimeStopMod.LOGGER.info("SABLE_INTENTIONAL_SPIN_PASS: preserved angular momentum across 20 slow physics ticks");
        } finally {
            TemporalBubbleManager.stopAllBubbles(level);
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }
}
