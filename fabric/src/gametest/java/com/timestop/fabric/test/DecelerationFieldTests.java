package com.timestop.fabric.test;

import com.timestop.combat.DecelerationFieldManager;
import com.timestop.config.TimeStopConfig;
import com.timestop.core.*;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import com.timestop.mixin.AbstractArrowAccessor;
import com.timestop.item.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class DecelerationFieldTests implements FabricGameTest {
    private static TemporalBubble field(GameTestHelper h, Vec3 center, double radius) {
        var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(), center,
                radius, TimeMode.DECELERATION_FIELD, 10000, WatchTier.CREATIVE, null, 0, Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);
        return bubble;
    }
    private static Vec3 center(GameTestHelper h) { var pos = h.absolutePos(new BlockPos(8, 8, 8)); return new Vec3(pos.getX() + .5, 200, pos.getZ() + .5); }
    private static Projectile projectile(GameTestHelper h, EntityType<?> type, Vec3 position, double speed) {
        var projectile = (Projectile) type.create(h.getLevel());
        projectile.setPos(position); projectile.setNoGravity(true); projectile.setDeltaMovement(speed, 0, 0);
        if (projectile instanceof AbstractHurtingProjectile hurting) hurting.accelerationPower = 0;
        return projectile;
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "deceleration_native")
    public void nativeProjectileFamiliesUseConfiguredRateAndRecoverOnExit(GameTestHelper h) {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get();
        try {
            TimeStopConfig.COMMON.decelerationDrag.set(.25);
            var center = center(h); field(h, center, 4);
            for (var type : List.of(EntityType.ARROW, EntityType.TRIDENT, EntityType.SNOWBALL, EntityType.EGG,
                    EntityType.ENDER_PEARL, EntityType.SMALL_FIREBALL, EntityType.WITHER_SKULL, EntityType.LLAMA_SPIT)) {
                var projectile = projectile(h, type, center, .4);
                try {
                    h.getLevel().tickNonPassenger(projectile);
                    h.assertTrue(Math.abs(projectile.getX() - center.x - .1) < .002,
                            "Configured quarter-speed native movement for " + type + ": " + projectile.position());
                    h.assertTrue(projectile.getDeltaMovement().x > .35, "True momentum must survive slowing: " + type);
                    projectile.setPos(center.add(10, 0, 0));
                    var before = projectile.position();
                    h.getLevel().tickNonPassenger(projectile);
                    h.assertTrue(projectile.getX() - before.x > .35, "Leaving the bubble must restore native speed: " + type);
                } finally { projectile.discard(); }
            }
        } finally { TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopConfig.COMMON.decelerationDrag.set(previous); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "deceleration_watch")
    public void watchBubbleSlowsOwnerShotsAtItsSelectedRadius(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        double previous = TimeStopConfig.COMMON.decelerationDrag.get();
        try {
            TimeStopConfig.COMMON.decelerationDrag.set(.25);
            player.setPos(center(h));
            var watch = new ItemStack(ModItems.CREATIVE_WATCH.get()); AbstractWatchItem.setBubbleRadius(watch, 12);
            player.setItemInHand(InteractionHand.MAIN_HAND, watch);
            var bubble = TemporalBubbleManager.startBubble(h.getLevel(), player, 10000, TimeMode.DECELERATION_FIELD);
            h.assertTrue(bubble != null && bubble.getRadius() == 12, "Watch must use its selected scope");
            var arrow = projectile(h, EntityType.ARROW, bubble.getCenter().add(8, 0, 0), .4);
            arrow.setOwner(player);
            try {
                double before = arrow.getX(); h.getLevel().tickNonPassenger(arrow);
                h.assertTrue(Math.abs(arrow.getX() - before - .1) < .002, "Owner shots beyond the old defense aura must slow");
                arrow.setPos(bubble.getCenter().add(14, 0, 0));
                h.assertFalse(DecelerationFieldManager.isDecelerated(arrow), "Shots outside the selected bubble are normal");
            } finally { arrow.discard(); }
        } finally { TemporalBubbleManager.stopAllBubbles(h.getLevel()); player.discard(); TimeStopConfig.COMMON.decelerationDrag.set(previous); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "deceleration_global")
    public void globalModeSlowsProjectilesButLeavesOtherEntitiesAndClockNormal(GameTestHelper h) {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get();
        var player = RewindRuneTests.survivalPlayer(h);
        var center = center(h);
        var cow = EntityType.COW.create(h.getLevel()); cow.setPos(center.add(0, 0, 3)); cow.setNoAi(true);
        var tnt = EntityType.TNT.create(h.getLevel()); tnt.setPos(center.add(0, 0, -3)); tnt.setFuse(100);
        var custom = new CustomProjectile(h.getLevel()); custom.setPos(center); custom.setDeltaMovement(.4, 0, 0);
        var arrow = projectile(h, EntityType.ARROW, center.add(0, 0, 6), .4); arrow.setOwner(player);
        try {
            TimeStopConfig.COMMON.decelerationDrag.set(.25);
            TimeStopManager.startGlobalTimeStop(h.getLevel(), player, 10000, TimeMode.DECELERATION_FIELD);
            h.assertTrue(TimeStopManager.getServerTickMs() == 50, "Global Deceleration must leave the server at 20 TPS");
            for (int i = 0; i < 20; i++) {
                h.getLevel().tickNonPassenger(custom); h.getLevel().tickNonPassenger(cow);
                h.getLevel().tickNonPassenger(tnt); h.getLevel().tickNonPassenger(arrow);
            }
            h.assertTrue(custom.steps == 5, "Unrecognized mod projectile must run its complete physics at quarter rate: " + custom.steps);
            h.assertTrue(cow.tickCount == 20 && tnt.getFuse() == 80, "Mobs and TNT must tick at normal speed");
            h.assertTrue(arrow.getX() - center.x > 1.8 && arrow.getX() - center.x < 2.1, "Global owner shots must slow everywhere");
        } finally {
            TimeStopManager.resumeTime(h.getLevel()); player.discard(); cow.discard(); tnt.discard(); custom.discard(); arrow.discard();
            TimeStopConfig.COMMON.decelerationDrag.set(previous);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "deceleration_sweep")
    public void fastProjectileCrossingBubbleSpendsOnlyItsRemainingTimeInside(GameTestHelper h) {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get();
        var center = center(h); var arrow = projectile(h, EntityType.ARROW, center.add(-4, 0, 0), 20);
        try {
            TimeStopConfig.COMMON.decelerationDrag.set(.1); field(h, center, 2);
            var custom = new CustomProjectile(h.getLevel()); custom.setPos(center.add(-4, 0, 0)); custom.setDeltaMovement(20, 0, 0);
            try {
                h.getLevel().tickNonPassenger(custom);
                h.assertTrue(custom.steps == 0, "Fast custom projectile physics must also be budgeted on predicted field entry");
            } finally { custom.discard(); }
            h.getLevel().tickNonPassenger(arrow);
            h.assertTrue(Math.abs(arrow.getX() - center.x + .2) < .002, "Fast arrow must advance to entry then slow, not cross the whole field: " + arrow.position() + " expected " + center.add(-.2,0,0));
            h.assertTrue(arrow.getDeltaMovement().x > 19, "Boundary slowing must preserve native momentum");
            arrow.setPos(center.add(1.9, 0, 0)); arrow.setDeltaMovement(20, 0, 0);
            h.getLevel().tickNonPassenger(arrow);
            h.assertTrue(Math.abs(arrow.getX() - center.x - 21) < .003, "Exit must use the remaining tick at normal speed");
        } finally { arrow.discard(); TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopConfig.COMMON.decelerationDrag.set(previous); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "deceleration_collision")
    public void slowedArrowHitsOnlyWhenItsSlowedStepReachesTheWall(GameTestHelper h) {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get();
        var center = center(h); var arrow = projectile(h, EntityType.ARROW, center, 1);
        var wall = BlockPos.containing(center.add(2, 0, 0));
        try {
            TimeStopConfig.COMMON.decelerationDrag.set(.1); field(h, center, 10);
            h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            for (int i = 0; i < 5; i++) h.getLevel().tickNonPassenger(arrow);
            h.assertFalse(((AbstractArrowAccessor) arrow).timestop$isInGround(), "Raycast must not hit ahead of the slowed movement");
            for (int i = 0; i < 40 && !((AbstractArrowAccessor) arrow).timestop$isInGround(); i++) h.getLevel().tickNonPassenger(arrow);
            h.assertTrue(((AbstractArrowAccessor) arrow).timestop$isInGround(), "Native wall collision must still occur");
            h.assertTrue(arrow.getDeltaMovement().length() < .1, "Impact must not restore flying momentum");
        } finally {
            arrow.discard(); h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopConfig.COMMON.decelerationDrag.set(previous);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "deceleration_physics")
    public void nativeGravityDragAndAccelerationAdvanceAtTheFieldRate(GameTestHelper h) {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get();
        var center = center(h); Vec3 velocity = new Vec3(.4, .2, 0);
        try {
            TimeStopConfig.COMMON.decelerationDrag.set(.25); field(h, center, 10);
            for (var type : List.of(EntityType.ARROW, EntityType.SNOWBALL, EntityType.SMALL_FIREBALL, EntityType.LLAMA_SPIT)) {
                var slow = projectile(h, type, center, .4);
                var normal = projectile(h, type, center.add(40, 0, 0), .4);
                try {
                    for (var p : List.of(slow, normal)) {
                        p.setNoGravity(false); p.setDeltaMovement(velocity);
                        if (p instanceof AbstractHurtingProjectile hurting) { hurting.accelerationPower = .1; }
                    }
                    h.getLevel().tickNonPassenger(normal); h.getLevel().tickNonPassenger(slow);
                    Vec3 expected = velocity.add(normal.getDeltaMovement().subtract(velocity).scale(.25));
                    h.assertTrue(slow.getDeltaMovement().distanceTo(expected) < .00001,
                            "Gravity, drag and acceleration must advance at quarter rate: " + type + " actual " + slow.getDeltaMovement() + " expected " + expected);
                    h.assertTrue(slow.position().distanceTo(center.add(velocity.scale(.25))) < .00001,
                            "Native position step must use the same elapsed time: " + type);
                } finally { slow.discard(); normal.discard(); }
            }
        } finally { TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopConfig.COMMON.decelerationDrag.set(previous); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "deceleration_entities")
    public void localFieldsLeaveSurvivalPlayersMobsAndTntAtNormalSpeed(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.COPPER_WATCH.get()));
        var center = center(h); player.setPos(center);
        var cow = EntityType.COW.create(h.getLevel()); cow.setPos(center.add(0,0,3)); cow.setNoAi(true);
        var tnt = EntityType.TNT.create(h.getLevel()); tnt.setPos(center.add(0,0,-3)); tnt.setFuse(100);
        try {
            var bubble = field(h, center, 10);
            for (boolean stationary : List.of(true, false)) {
                bubble.stationary(stationary);
                h.assertTrue(bubble.getTimeDilationFactor(player) == 1.0F,
                        "Deceleration must not apply watch tier movement penalties to survival players");
                for (int i = 0; i < 10; i++) { h.getLevel().tickNonPassenger(cow); h.getLevel().tickNonPassenger(tnt); }
            }
            h.assertTrue(cow.tickCount == 20 && tnt.getFuse() == 80, "Local Deceleration must leave mobs and TNT normal");
        } finally { TemporalBubbleManager.stopAllBubbles(h.getLevel()); player.discard(); cow.discard(); tnt.discard(); }
        h.succeed();
    }

    private static class CustomProjectile extends Projectile {
        int steps;
        CustomProjectile(Level level) { super(EntityType.SNOWBALL, level); }
        @Override public void tick() { steps++; setPos(position().add(getDeltaMovement())); }
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    }
}
