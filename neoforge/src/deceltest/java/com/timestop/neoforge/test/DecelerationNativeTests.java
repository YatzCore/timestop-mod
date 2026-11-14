package com.timestop.neoforge.test;

import com.timestop.combat.DecelerationFieldManager;
import com.timestop.config.TimeStopConfig;
import com.timestop.core.*;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import com.timestop.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("timestop")
@PrefixGameTestTemplate(false)
public class DecelerationNativeTests {
    private static Projectile bullet(GameTestHelper h, Vec3 pos, double speed) throws Exception {
        var clazz = Class.forName("com.tacz.guns.entity.EntityKineticBullet");
        var type = (EntityType<?>) clazz.getField("TYPE").get(null);
        var bullet = (Projectile) clazz.getConstructor(EntityType.class, Level.class).newInstance(type, h.getLevel());
        var friction = clazz.getDeclaredField("friction"); friction.setAccessible(true); friction.setFloat(bullet, 0);
        var gravity = clazz.getDeclaredField("gravity"); gravity.setAccessible(true); gravity.setFloat(bullet, 0);
        for (String name : List.of("gunId", "gunDisplayId")) {
            var id = clazz.getDeclaredField(name); id.setAccessible(true); id.set(bullet, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tacz", "ak47"));
        }
        bullet.setPos(pos); bullet.setDeltaMovement(speed, 0, 0);
        h.getLevel().getChunkAt(BlockPos.containing(pos));
        return bullet;
    }
    private static Vec3 center(GameTestHelper h) { var pos = h.absolutePos(new BlockPos(8, 8, 8)); return new Vec3(pos.getX() + .5, 200, pos.getZ() + .5); }
    private static void field(GameTestHelper h, Vec3 center, double radius) {
        var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(), center,
                radius, TimeMode.DECELERATION_FIELD, 10000, WatchTier.CREATIVE, null, 0, Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);
    }
    @GameTest(template = "deceleration_empty", batch = "native_deceleration_scopes")
    public static void realTaczBulletsSlowInStationaryWatchAndGlobalScopes(GameTestHelper h) throws Exception {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get();
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE); player.setPos(center(h));
        try {
            for (int scope = 0; scope < 3; scope++) {
                TimeStopConfig.COMMON.decelerationDrag.set(.25);
                Vec3 pos = center(h);
                if (scope == 0) field(h, pos, 12);
                if (scope == 1) {
                    var watch = new ItemStack(ModItems.CREATIVE_WATCH.get()); AbstractWatchItem.setBubbleRadius(watch, 12);
                    player.setItemInHand(InteractionHand.MAIN_HAND, watch);
                    var bubble = TemporalBubbleManager.startBubble(h.getLevel(), player, 10000, TimeMode.DECELERATION_FIELD);
                    pos = bubble.getCenter().add(8, 0, 0);
                }
                if (scope == 2) { TimeStopManager.startGlobalTimeStop(h.getLevel(), player, 10000, TimeMode.DECELERATION_FIELD); pos = pos.add(50, 0, 0); }
                var bullet = bullet(h, pos, .4); bullet.setOwner(player);
                try {
                    h.assertTrue(DecelerationFieldManager.hasMovementHook(bullet), "Actual TaCZ bullet must have the optional movement hook");
                    for (int i = 0; i < 5; i++) h.getLevel().tickNonPassenger(bullet);
                    h.assertTrue(bullet.isAlive() && Math.abs(bullet.getX() - pos.x - .5) < .002,
                            "Real TaCZ native tick must use configured quarter speed in scope " + scope + ": " + bullet.position());
                    h.assertTrue(Math.abs(bullet.getDeltaMovement().x - .4) < .002, "Native bullet must retain full momentum");
                    TimeStopConfig.COMMON.decelerationDrag.set(.4);
                    double before = bullet.getX(); h.getLevel().tickNonPassenger(bullet);
                    h.assertTrue(Math.abs(bullet.getX() - before - .16) < .002, "Native slowdown must follow speed configuration");
                    TimeStopConfig.COMMON.decelerationDrag.set(.25);
                    var friction = bullet.getClass().getDeclaredField("friction"); friction.setAccessible(true); friction.setFloat(bullet, .05f);
                    var gravity = bullet.getClass().getDeclaredField("gravity"); gravity.setAccessible(true); gravity.setFloat(bullet, .02f);
                    bullet.setDeltaMovement(.4, 0, 0); before = bullet.getX(); h.getLevel().tickNonPassenger(bullet);
                    h.assertTrue(Math.abs(bullet.getX() - before - .1) < .002
                                    && bullet.getDeltaMovement().distanceTo(new Vec3(.395, -.005, 0)) < .00001,
                            "TaCZ native gravity and friction must use elapsed field time");
                } finally { bullet.discard(); TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopManager.resumeTime(h.getLevel()); }
            }
            com.timestop.TimeStopMod.LOGGER.info("DECELERATION_TACZ_SCOPES_PASS");
        } finally { player.discard(); TimeStopConfig.COMMON.decelerationDrag.set(previous); }
        h.succeed();
    }
    @GameTest(template = "deceleration_empty", batch = "native_deceleration_boundary")
    public static void realTaczFastBulletCannotSkipTheBubbleAndRestoresSpeedOnExit(GameTestHelper h) throws Exception {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get(); var center = center(h);
        var bullet = bullet(h, center.add(-4, 0, 0), 20);
        try {
            TimeStopConfig.COMMON.decelerationDrag.set(.1); field(h, center, 2);
            h.getLevel().tickNonPassenger(bullet);
            h.assertTrue(Math.abs(bullet.getX() - center.x + .2) < .002, "Native swept step must slow immediately on bubble entry");
            h.assertTrue(Math.abs(bullet.getDeltaMovement().x - 20) < .002, "No permanent slowdown on entry");
            bullet.setPos(center.add(1.9, 0, 0)); bullet.setDeltaMovement(20, 0, 0);
            h.getLevel().tickNonPassenger(bullet);
            h.assertTrue(Math.abs(bullet.getX() - center.x - 21) < .003, "Native exit must restore speed during the same tick: " + bullet.position() + " expected " + center.add(21,0,0) + " alive " + bullet.isAlive());
            com.timestop.TimeStopMod.LOGGER.info("DECELERATION_TACZ_BOUNDARY_PASS");
        } finally { bullet.discard(); TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopConfig.COMMON.decelerationDrag.set(previous); }
        h.succeed();
    }
    @GameTest(template = "deceleration_empty", batch = "native_deceleration_wall")
    public static void realTaczBulletDoesNotHitBeyondItsSlowedStep(GameTestHelper h) throws Exception {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get(); var center = center(h);
        var wall = BlockPos.containing(center.add(2, 0, 0)); var bullet = bullet(h, center, 1);
        try {
            TimeStopConfig.COMMON.decelerationDrag.set(.1); field(h, center, 10);
            h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            for (int i = 0; i < 5; i++) h.getLevel().tickNonPassenger(bullet);
            h.assertTrue(bullet.isAlive() && bullet.getX() < wall.getX(), "Native raycast must not hit ahead of slowed movement");
            for (int i = 0; i < 40 && bullet.isAlive(); i++) h.getLevel().tickNonPassenger(bullet);
            h.assertFalse(bullet.isAlive(), "Native bullet must still collide when its slowed step reaches the wall");
            com.timestop.TimeStopMod.LOGGER.info("DECELERATION_TACZ_WALL_PASS");
        } finally {
            bullet.discard(); h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopConfig.COMMON.decelerationDrag.set(previous);
        }
        h.succeed();
    }
}
