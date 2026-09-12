package com.timestop;

import com.timestop.combat.RuneManager;
import com.timestop.core.*;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.ModItems;
import com.timestop.item.rune.RuneType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TimeStopMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TimeStopRegressionTests {
    @GameTest(template = "empty")
    public static void ownVanillaArrowsCollectAndStayReleased(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "own-arrow-test"));
        player.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(1, 0, 1)).getCenter().add(0, 240, 0));
        player.setYRot(0);
        player.setXRot(0);
        level.addNewPlayer(player);
        try {
            for (boolean barrier : new boolean[] {false, true}) {
                for (boolean frozen : new boolean[] {false, true}) {
                    Arrow arrow = new Arrow(level, player);
                    arrow.setPos(player.getEyePosition().add(0, -0.1, 0.2));
                    arrow.setDeltaMovement(0, 0, 3);
                    verifyOwnShotCapture(helper, player, arrow, barrier, frozen);
                }
            }
        } finally {
            com.timestop.combat.KineticPalmManager.setGuarding(player, false);
            level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            player.discard();
        }
        helper.succeed();
    }

    public static void verifyOwnShotCapture(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player,
                                            net.minecraft.world.entity.projectile.Projectile shot, boolean barrier, boolean frozen) {
        ItemStack watch = new ItemStack(ModItems.DIAMOND_WATCH.get());
        AbstractWatchItem.setSocketedRune(watch, new ItemStack(barrier ? ModItems.RUNE_BARRIER.get() : ModItems.RUNE_ORBITAL.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, watch);
        shot.setOwner(player);
        Vec3 incoming = shot.getDeltaMovement();
        if (frozen) {
            TimeStopManager.startGlobalTimeStop(helper.getLevel(), player, 200, TimeMode.TIME_STOP);
            TimeStopManager.registerSuspendedProjectile(shot, incoming);
            helper.assertTrue(TimeStopManager.isProjectileSuspended(shot) && shot.getDeltaMovement().equals(Vec3.ZERO),
                    "Frozen fixture must have zero live velocity and a saved stasis velocity");
        }
        com.timestop.combat.KineticPalmManager.setGuarding(player, barrier);
        boolean oldLook = TimeStopSavedData.get().isRedirectToLook();
        TimeStopSavedData.get().setRedirectToLook(false);
        try {
            if (!barrier && !frozen) {
                Vec3 muzzle = shot.position();
                int count = com.timestop.combat.OrbitalProjectileManager.getOrbitCount(player);
                for (int tick = 0; tick < 4; tick++) {
                    helper.getLevel().tickNonPassenger(shot);
                    // Exercise the proximity sweep as well as the pre-movement capture hook.
                    com.timestop.combat.OrbitalProjectileManager.interceptIncoming(shot);
                    helper.assertTrue(shot.isAlive() && !shot.getPersistentData().getBoolean("InStasisOrbit"),
                            "Equipping Orbital must not capture the player's ordinary outgoing shots");
                }
                helper.assertTrue(shot.position().distanceToSqr(muzzle) > 4
                                && com.timestop.combat.OrbitalProjectileManager.getOrbitCount(player) == count,
                        "Fresh own shots must fly clear without entering the orbit inventory");
                return;
            }
            helper.getLevel().tickNonPassenger(shot);
            String flag = barrier ? "KineticPalmCaptured" : "InStasisOrbit";
            helper.assertTrue(shot.isAlive() && shot.getPersistentData().getBoolean(flag),
                    "Own " + shot.getType() + " must be captured; barrier=" + barrier + ", frozen=" + frozen);
            helper.assertTrue(!TimeStopManager.isProjectileSuspended(shot), "Capture must transfer the saved stasis velocity");
            int age = shot.tickCount;
            for (int tick = 0; tick < 120; tick++) helper.getLevel().tickNonPassenger(shot);
            helper.assertTrue(shot.isAlive() && shot.tickCount == age, "Captured rounds must not age or run an explosive fuse");
            if (frozen) TimeStopManager.resumeTime(helper.getLevel());
            if (barrier) com.timestop.combat.KineticPalmManager.dischargeRepulse(player, player.getLookAngle());
            else com.timestop.combat.OrbitalProjectileManager.launchSingleProjectile(player);
            helper.assertTrue(!shot.getPersistentData().getBoolean(flag) && shot.getDeltaMovement().length() >= incoming.length() - 0.001,
                    "Release must retain the original native speed even when captured from stasis");
            helper.assertTrue(shot.getDeltaMovement().dot(player.getLookAngle()) > 0,
                    "An own-shot barrier volley must launch away from its owner in return-to-shooter mode");
            Vec3 releasedAt = shot.position();
            for (int tick = 0; tick < 3; tick++) helper.getLevel().tickNonPassenger(shot);
            helper.assertTrue(shot.isAlive() && !shot.getPersistentData().getBoolean(flag) && shot.position().distanceToSqr(releasedAt) > 1,
                    "The player's own released volley must escape orbit and barrier without recapture");
        } finally {
            if (frozen) TimeStopManager.resumeTime(helper.getLevel());
            TimeStopSavedData.get().setRedirectToLook(oldLook);
            com.timestop.combat.KineticPalmManager.setGuarding(player, false);
            TimeStopManager.removeSuspendedProjectile(shot);
            shot.discard();
        }
    }

    @GameTest(template = "empty")
    public static void superhotCollectsPlayersAndForgetsDepartures(GameTestHelper helper) {
        Player owner = helper.makeMockSurvivalPlayer();
        Player guest = helper.makeMockSurvivalPlayer();
        var bubble = new TemporalBubble(java.util.UUID.randomUUID(), owner.getUUID(), helper.getLevel().dimension(),
                Vec3.ZERO, 20, TimeMode.SUPERHOT, 200, com.timestop.item.WatchTier.DIAMOND, null, 0, java.util.Set.of());
        var mob = new net.minecraft.world.entity.animal.Cow(net.minecraft.world.entity.EntityType.COW, helper.getLevel());
        try {
            helper.assertTrue(bubble.canEntityAct(guest), "Every Superhot participant must be able to initiate movement");
            bubble.setPlayerActivity(owner.getUUID(), 0);
            bubble.setPlayerActivity(guest.getUUID(), 1);
            helper.assertTrue(bubble.getTimeDilationFactor(mob) == 1, "A moving guest must advance shared time normally");
            bubble.setPlayerActivity(owner.getUUID(), 0);
            helper.assertTrue(bubble.getSuperhotActivity() == 1, "An idle owner's report must not overwrite a moving guest");
            bubble.setPlayerActivity(guest.getUUID(), 0);
            helper.assertTrue(bubble.getTimeDilationFactor(mob) == 0.05f, "All idle occupants must slow time to five percent");
            bubble.setPlayerActivity(guest.getUUID(), 1);
            bubble.retainActivePlayers(java.util.Set.of(owner.getUUID()));
            helper.assertTrue(bubble.getSuperhotActivity() == 0, "Leaving or disconnected players must not keep a bubble fast");
            bubble.setPlayerActivity(owner.getUUID(), Float.NaN);
            helper.assertTrue(bubble.getSuperhotActivity() == 0, "Invalid activity must not poison time dilation");
        } finally { owner.discard(); guest.discard(); mob.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void untouchedProjectileKeepsVelocity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Arrow arrow = new Arrow(level, 0, 100, 0);
        Vec3 velocity = new Vec3(0.3, -0.1, 0.2);
        arrow.setDeltaMovement(velocity);
        arrow.setNoGravity(true);
        try {
            TimeStopManager.startGlobalTimeStop(level, null, 200, TimeMode.TIME_STOP);
            TimeStopManager.registerSuspendedProjectile(arrow, velocity);
            TimeStopManager.resumeTime(level);
            helper.assertTrue(arrow.getDeltaMovement().distanceToSqr(velocity) < 1e-12,
                    "Untouched arrows must retain their original direction and speed");
            helper.assertTrue(arrow.isNoGravity(), "Resuming must preserve the original gravity setting");
        } finally {
            TimeStopManager.resumeTime(level);
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void globalCleanupKeepsProjectilesFrozen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Arrow arrow = new Arrow(level, 0, 100, 0);
        try {
            TimeStopManager.startGlobalTimeStop(level, null, 200, TimeMode.TIME_STOP);
            TimeStopManager.registerSuspendedProjectile(arrow, new Vec3(1, 0, 0));
            TemporalBubbleManager.serverTick();
            helper.assertTrue(TimeStopManager.isProjectileSuspended(arrow),
                    "Bubble cleanup must not resume a globally frozen projectile");
            helper.assertTrue(arrow.getDeltaMovement().equals(Vec3.ZERO), "Frozen arrows must remain still");
        } finally {
            TimeStopManager.resumeTime(level);
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blankRuneDoesNotMaskActiveRune(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.BLANK_RUNE.get()));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RUNE_TACHYON.get()));
        helper.assertTrue(RuneManager.getSocketedRuneType(player) == null,
                "Loose runes in hands or inventory must not be active without being socketed in a watch");

        ItemStack watch = new ItemStack(ModItems.DIAMOND_WATCH.get());
        AbstractWatchItem.setSocketedRune(watch, new ItemStack(ModItems.RUNE_TACHYON.get()));
        player.setItemInHand(InteractionHand.MAIN_HAND, watch);

        helper.assertTrue(RuneManager.getSocketedRuneType(player) == RuneType.TACHYON,
                "Socketed rune in watch must be active");
        helper.assertTrue(RuneManager.getSocketedRuneStack(player).is(ModItems.RUNE_TACHYON.get()),
                "Rune type and rune stack must resolve to the socketed item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void localBubbleDoesNotStopWorldClock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long originalGameTime = level.getGameTime();
        long originalDayTime = level.getDayTime();
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIAMOND_WATCH.get()));
        try {
            TemporalBubbleManager.startBubble(level, player, 200, TimeMode.TIME_STOP);
            long before = level.getGameTime();
            tickWorldClock(level);
            helper.assertTrue(level.getGameTime() == before + 1, "A local bubble must not freeze the world clock");
        } finally {
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(originalGameTime);
            level.setDayTime(originalDayTime);
            TemporalBubbleManager.stopAllBubbles(level);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fastForwardClockDoesNotDeadlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long originalGameTime = level.getGameTime();
        long originalDayTime = level.getDayTime();
        try {
            TimeStopManager.startGlobalTimeStop(level, null, 200, TimeMode.FAST_FORWARD);
            long before = level.getGameTime();
            for (int i = 0; i < 10; i++) tickWorldClock(level);
            helper.assertTrue(level.getGameTime() == before + 10, "FAST_FORWARD must advance the clock on every server tick");
        } finally {
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(originalGameTime);
            level.setDayTime(originalDayTime);
            TimeStopManager.resumeTime(level);
        }
        helper.succeed();
    }

    private static void tickWorldClock(ServerLevel level) {
        try {
            var method = ServerLevel.class.getDeclaredMethod("tickTime");
            method.setAccessible(true);
            method.invoke(level);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not tick the development server clock", e);
        }
    }

    @GameTest(template = "empty")
    public static void localSlowMotionAndFastForwardTickEntities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        var cow = new net.minecraft.world.entity.animal.Cow(net.minecraft.world.entity.EntityType.COW, level);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPos(0, 100, 0);
        try {
            TemporalBubble bubble = TemporalBubbleManager.startBubble(level, player, 200, TimeMode.SLOW_MOTION);
            bubble.setCenter(cow.position());
            int before = cow.tickCount;
            for (int i = 0; i < 4; i++) level.tickNonPassenger(cow);
            helper.assertTrue(cow.tickCount == before + 1, "Local slow motion must tick mobs at one-quarter speed");
            bubble.setMode(TimeMode.FAST_FORWARD);
            before = cow.tickCount;
            level.tickNonPassenger(cow);
            helper.assertTrue(cow.tickCount == before + 5, "Local fast forward must tick mobs five times");
        } finally {
            TemporalBubbleManager.stopAllBubbles(level);
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryWatchKeepsItsTierAndCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        player.getInventory().setItem(9, new ItemStack(ModItems.NETHERITE_WATCH.get()));
        try {
            TemporalBubble bubble = TemporalBubbleManager.startBubble(level, player, 200, TimeMode.TIME_STOP);
            helper.assertTrue(bubble.getTier() == com.timestop.item.WatchTier.NETHERITE,
                    "Inventory activation must use the Netherite watch tier");
            helper.assertTrue(bubble.getWatchItem() == ModItems.NETHERITE_WATCH.get(),
                    "Inventory activation must retain the item used for cooldowns");
            helper.assertTrue(bubble.getCooldownTicks() == com.timestop.item.WatchTier.NETHERITE.getCooldownTicks(),
                    "Inventory activation must use the actual watch cooldown");
        } finally {
            TemporalBubbleManager.stopAllBubbles(level);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void closingOverlappingBubbleDoesNotLaunchArrow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player first = helper.makeMockSurvivalPlayer();
        Player second = helper.makeMockSurvivalPlayer();
        second.setUUID(java.util.UUID.randomUUID());
        first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIAMOND_WATCH.get()));
        second.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIAMOND_WATCH.get()));
        Arrow arrow = new Arrow(level, 0, 100, 0);
        try {
            TemporalBubble a = TemporalBubbleManager.startBubble(level, first, 200, TimeMode.TIME_STOP);
            TemporalBubble b = TemporalBubbleManager.startBubble(level, second, 200, TimeMode.TIME_STOP);
            a.setCenter(arrow.position());
            b.setCenter(arrow.position());
            TimeStopManager.registerSuspendedProjectile(arrow, new Vec3(1, 0, 0));
            TemporalBubbleManager.stopBubble(level, a);
            helper.assertTrue(TimeStopManager.isProjectileSuspended(arrow), "The second bubble must keep the arrow suspended");
            helper.assertTrue(arrow.getDeltaMovement().equals(Vec3.ZERO), "Overlap collapse must not launch an arrow");
        } finally {
            TemporalBubbleManager.stopAllBubbles(level);
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capturedArrowDoesNotRunSubclassPhysics(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Arrow arrow = new Arrow(level, 0, 100, 0);
        arrow.getPersistentData().putBoolean("KineticPalmCaptured", true);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(new Vec3(1, 0, 0));
        Vec3 before = arrow.position();
        level.tickNonPassenger(arrow);
        helper.assertTrue(arrow.position().equals(before), "A barrier-captured arrow must not move or hit anything");
        arrow.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void localBubbleOnlyFreezesFurnaceInsideIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var inside = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        var outside = inside.east();
        level.setBlockAndUpdate(inside, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
        level.setBlockAndUpdate(outside, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
        var frozen = (net.minecraft.world.level.block.entity.FurnaceBlockEntity) level.getBlockEntity(inside);
        var running = (net.minecraft.world.level.block.entity.FurnaceBlockEntity) level.getBlockEntity(outside);
        for (var furnace : java.util.List.of(frozen, running)) {
            furnace.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_ORE));
            furnace.setItem(1, new ItemStack(net.minecraft.world.item.Items.COAL));
        }
        Player player = helper.makeMockSurvivalPlayer();
        try {
            var bubble = TemporalBubbleManager.startBubble(level, player, 200, TimeMode.TIME_STOP);
            bubble.setCenter(Vec3.atCenterOf(inside).add(-bubble.getRadius() + 0.25, 0, 0));
            try {
                var method = net.minecraft.world.level.Level.class.getDeclaredMethod("tickBlockEntities");
                method.setAccessible(true);
                method.invoke(level);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Could not tick block entities on the development server", e);
            }
            helper.assertTrue(frozen.getItem(1).getCount() == 1, "The furnace inside stasis must not consume fuel");
            helper.assertTrue(running.getItem(1).isEmpty(), "A furnace outside stasis must keep smelting");
        } finally {
            TemporalBubbleManager.stopAllBubbles(level);
            level.removeBlock(inside, false);
            level.removeBlock(outside, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replacingGlobalModeReleasesStasis(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Arrow arrow = new Arrow(level, 0, 100, 0);
        Vec3 velocity = new Vec3(0.4, 0.2, -0.3);
        try {
            TimeStopManager.startGlobalTimeStop(level, null, 200, TimeMode.TIME_STOP);
            TimeStopManager.registerSuspendedProjectile(arrow, velocity);
            TimeStopManager.startGlobalTimeStop(level, null, 200, TimeMode.SLOW_MOTION);
            helper.assertTrue(!TimeStopManager.isProjectileSuspended(arrow), "Changing modes must finish the previous stasis session");
            helper.assertTrue(arrow.getDeltaMovement().equals(velocity), "Changing modes must restore projectile motion");
        } finally {
            TimeStopManager.resumeTime(level);
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void timeSyncPacketSnapshotsExemptionsAndRoundTrips(GameTestHelper helper) {
        var exemptions = new java.util.HashSet<java.util.UUID>();
        exemptions.add(java.util.UUID.randomUUID());
        var packet = new com.timestop.network.TimeStopSyncPacket(true, 123, null, TimeMode.TIME_STOP, exemptions);
        exemptions.clear();
        var encoded = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        var roundTrip = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            packet.toBytes(encoded);
            var inspection = new net.minecraft.network.FriendlyByteBuf(encoded.duplicate());
            inspection.readBoolean();
            inspection.readVarInt();
            inspection.readBoolean();
            inspection.readEnum(TimeMode.class);
            helper.assertTrue(inspection.readVarInt() == 1, "Queued packets must keep their original exemption snapshot");
            var decoded = new com.timestop.network.TimeStopSyncPacket(new net.minecraft.network.FriendlyByteBuf(encoded.duplicate()));
            decoded.toBytes(roundTrip);
            helper.assertTrue(io.netty.buffer.ByteBufUtil.equals(encoded, roundTrip), "Time and projectile-flow settings must survive a packet round trip");
        } finally {
            encoded.release();
            roundTrip.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void liveModeSwitchesReleaseStasisWithoutResettingDuration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Arrow arrow = new Arrow(level, 0, 100, 0);
        Vec3 velocity = new Vec3(0.4, 0.2, -0.3);
        Player player = helper.makeMockSurvivalPlayer();
        try {
            TimeStopManager.startGlobalTimeStop(level, null, 123, TimeMode.TIME_STOP);
            TimeStopManager.registerSuspendedProjectile(arrow, velocity);
            TimeStopManager.setMode(TimeMode.MATRIX);
            helper.assertTrue(!TimeStopManager.isProjectileSuspended(arrow), "The global mode menu must release old stasis");
            helper.assertTrue(arrow.getDeltaMovement().equals(velocity), "Global mode switching must restore motion");
            helper.assertTrue(TimeStopManager.getRemainingTicks() == 123, "Global mode switching must retain the duration");
            TimeStopManager.resumeTime(level);

            var bubble = TemporalBubbleManager.startBubble(level, player, 123, TimeMode.TIME_STOP);
            bubble.setCenter(arrow.position());
            TimeStopManager.registerSuspendedProjectile(arrow, velocity);
            TemporalBubbleManager.changeBubbleMode(level, bubble, TimeMode.MATRIX);
            helper.assertTrue(!TimeStopManager.isProjectileSuspended(arrow), "The bubble mode menu must release old stasis");
            helper.assertTrue(arrow.getDeltaMovement().equals(velocity), "Bubble mode switching must restore motion");
            helper.assertTrue(bubble.getRemainingTicks() == 123, "Bubble mode switching must retain the duration");
        } finally {
            TimeStopManager.resumeTime(level);
            TemporalBubbleManager.stopAllBubbles(level);
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void neoCaptureIsCenteredAndSweepsFastProjectiles(GameTestHelper helper) {
        Vec3 eye = Vec3.ZERO;
        Vec3 look = new Vec3(0, 0, 1);
        Vec3 incoming = new Vec3(0, 0, -20);
        Vec3 left = com.timestop.combat.NeoBulletMotion.entryPoint(new Vec3(-1, 0, 8), incoming, eye, look);
        Vec3 right = com.timestop.combat.NeoBulletMotion.entryPoint(new Vec3(1, 0, 8), incoming, eye, look);
        helper.assertTrue(left != null && right != null && Math.abs(left.z - right.z) < 1e-9,
                "Protection must be symmetric about the crosshair and intercept fast bullets before they cross the player");
        helper.assertTrue(com.timestop.combat.NeoBulletMotion.entryPoint(new Vec3(0, 0, -3),
                new Vec3(0, 0, 1), eye, look) == null, "The palm must not capture shots coming from behind");
        helper.assertTrue(com.timestop.combat.NeoBulletMotion.entryPoint(new Vec3(0, 0, 3),
                new Vec3(0, 0, 1), eye, look) == null, "Outgoing projectiles must pass through");
        Vec3 sideEntry = com.timestop.combat.NeoBulletMotion.entryPoint(new Vec3(8, 0, 0),
                new Vec3(-20, 0, 0), eye, new Vec3(1, 0, 0));
        helper.assertTrue(sideEntry != null && Math.abs(sideEntry.x - 4.5) < 1e-9,
                "Turning the player must rotate the capture zone without a horizontal offset");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void neoBulletsBrakeAlongTheirPathAndStayPut(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 10, 1)));
        Arrow arrow = new Arrow(level, start.x, start.y, start.z);
        Vec3 drift = com.timestop.combat.NeoBulletMotion.initialDrift(new Vec3(0, 0, -20), 4.5);
        var data = arrow.getPersistentData();
        data.putBoolean("KineticPalmCaptured", true);
        data.putDouble("NeoDriftZ", drift.z);
        double previousStep = Double.MAX_VALUE;
        for (int tick = 0; tick < 40; tick++) {
            Vec3 before = arrow.position();
            level.tickNonPassenger(arrow);
            double step = arrow.position().distanceTo(before);
            helper.assertTrue(step <= previousStep + 1e-9, "Incoming motion must decelerate monotonically");
            helper.assertTrue(arrow.getX() == start.x && arrow.getY() == start.y,
                    "Captured bullets must not snap sideways into a grid or follow the camera");
            previousStep = step;
        }
        double travel = arrow.position().distanceTo(start);
        helper.assertTrue(travel > 1.5 && travel < 1.66, "Bullets must visibly brake within a bounded distance");
        Vec3 stopped = arrow.position();
        level.tickNonPassenger(arrow);
        helper.assertTrue(stopped.equals(arrow.position()), "Settled bullets must stay at their own world position");
        helper.assertTrue(com.timestop.combat.NeoBulletMotion.initialDrift(new Vec3(0, 0, -20), 1.2).length()
                / (1 - com.timestop.combat.NeoBulletMotion.DRAG) < 0.051, "Close shots must stop before reaching the player");
        arrow.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void neoCapturePacketPreservesWorldPosition(GameTestHelper helper) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var packet = new com.timestop.network.KineticCaptureSyncPacket(42, java.util.UUID.randomUUID(),
                    true, new Vec3(-123.5, 80.25, 456.75));
            packet.toBytes(buffer);
            helper.assertTrue(packet.equals(new com.timestop.network.KineticCaptureSyncPacket(buffer)),
                    "Capture sync must preserve owner, capture state and exact world position");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void barrierDropsDistantProjectiles(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        Arrow arrow = new Arrow(helper.getLevel(), 0, 100, 0);
        player.setPos(0, 100 - player.getEyeHeight(), -3);
        arrow.setNoGravity(true);
        arrow.getPersistentData().putBoolean("KineticPalmCaptured", true);
        helper.assertTrue(!com.timestop.combat.KineticPalmManager.releaseIfTooFar(player, arrow),
                "Nearby captured projectiles must stay suspended");
        player.setPos(0, 100 - player.getEyeHeight(), -5.1);
        helper.assertTrue(com.timestop.combat.KineticPalmManager.releaseIfTooFar(player, arrow),
                "Walking beyond five blocks must release the projectile even while guarding");
        helper.assertTrue(!arrow.isNoGravity() && arrow.getDeltaMovement().y < 0
                && !arrow.getPersistentData().getBoolean("KineticPalmCaptured")
                && arrow.getPersistentData().getBoolean("KineticPalmDropped"),
                "Out-of-range release must restore falling and prevent recapture");
        arrow.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runeTransfersConserveItemsAcrossRepeatedRequests(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack watch = new ItemStack(ModItems.CHRONOS_WATCH.get());
        for (int slot = 2; slot <= 4; slot++) player.getInventory().setItem(slot, new ItemStack(ModItems.RUNE_BARRIER.get()));
        for (int i = 0; i < 20; i++) {
            int source = 0;
            while (!player.getInventory().getItem(source).is(ModItems.RUNE_BARRIER.get())) source++;
            helper.assertTrue(com.timestop.item.RuneSocketTransactions.apply(player, watch, source, RuneType.KINETIC_BARRIER),
                    "Valid rune insertion must succeed");
            helper.assertTrue(com.timestop.item.RuneSocketTransactions.apply(player, watch, -1, RuneType.BLANK),
                    "Ejection must succeed once");
            helper.assertTrue(!com.timestop.item.RuneSocketTransactions.apply(player, watch, -1, RuneType.BLANK),
                    "Repeated ejection must not create another rune");
            int total = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(ModItems.RUNE_BARRIER.get())) total += stack.getCount();
            }
            helper.assertTrue(total == 3, "Insert/eject cycles must preserve the exact rune count");
        }
        helper.assertTrue(!com.timestop.item.RuneSocketTransactions.apply(player, watch, 2, RuneType.DEAD_EYE),
                "Stale or mismatched rune requests must be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deadEyeFeedbackSnapshotsAndPreservesBurstTiming(GameTestHelper helper) {
        ItemStack original = new ItemStack(net.minecraft.world.item.Items.BOW);
        original.getOrCreateTag().putString("GunId", "example:test");
        var packet = new com.timestop.network.DeadEyeGunFeedbackPacket(original, false);
        original.getOrCreateTag().putString("GunId", "example:changed");
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            var decoded = new com.timestop.network.DeadEyeGunFeedbackPacket(buffer);
            helper.assertTrue(!decoded.firstRound() && decoded.gun().getTag().getString("GunId").equals("example:test"),
                    "Feedback must snapshot the fired gun and preserve the later-burst-round flag");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scopeCommandsOverrideEveryWatchAndPersist(GameTestHelper helper) {
        var settings = TimeStopSavedData.get();
        var oldScope = settings.getWatchScope();
        boolean oldLook = settings.isRedirectToLook();
        Player player = helper.makeMockSurvivalPlayer();
        var server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput();
        try {
            for (var item : new net.minecraft.world.item.Item[]{ModItems.COPPER_WATCH.get(), ModItems.CHRONOS_WATCH.get(), ModItems.DIAMOND_WATCH.get(), ModItems.NETHERITE_WATCH.get(), ModItems.CREATIVE_WATCH.get()}) {
                ItemStack watch = new ItemStack(item);
                player.setItemInHand(InteractionHand.MAIN_HAND, watch);
                com.timestop.item.AbstractWatchItem.setGlobalScope(watch, true);
                server.getCommands().performPrefixedCommand(source, "timestop scope sphere");
                helper.assertTrue(!TimeStopManager.usesGlobalWatchScope(player), "Sphere must override global watch NBT");
                com.timestop.item.AbstractWatchItem.setGlobalScope(watch, false);
                server.getCommands().performPrefixedCommand(source, "timestop scope global");
                helper.assertTrue(TimeStopManager.usesGlobalWatchScope(player), "Global must override local watch NBT");
                server.getCommands().performPrefixedCommand(source, "timestop scope watch");
                helper.assertTrue(!TimeStopManager.usesGlobalWatchScope(player), "Watch mode must restore item scope");
            }
            server.getCommands().performPrefixedCommand(source, "timestop scope sphere");
            server.getCommands().performPrefixedCommand(source, "timestop redirect look");
            var restored = TimeStopSavedData.load(settings.save(new net.minecraft.nbt.CompoundTag()));
            helper.assertTrue(restored.getWatchScope() == TimeStopSavedData.WatchScope.SPHERE && restored.isRedirectToLook(),
                    "Both commands must survive world reload");
            server.getCommands().performPrefixedCommand(source, "timestop redirect return");
            helper.assertTrue(!settings.isRedirectToLook(), "Return command must restore original redirection");
            var legacy = new net.minecraft.nbt.CompoundTag();
            legacy.putBoolean("ServerForceGlobalMode", true);
            helper.assertTrue(TimeStopSavedData.load(legacy).getWatchScope() == TimeStopSavedData.WatchScope.GLOBAL,
                    "Existing global saves must migrate");
        } finally { settings.setWatchScope(oldScope); settings.setRedirectToLook(oldLook); player.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vectorModifierCoexistsWithBarrierAndAimsEachPunch(GameTestHelper helper) {
        var settings = TimeStopSavedData.get();
        boolean oldLook = settings.isRedirectToLook();
        Player player = helper.makeMockSurvivalPlayer();
        Arrow arrow = new Arrow(helper.getLevel(), 0, 200, 0);
        try {
            settings.setRedirectToLook(false);
            ItemStack barrierWatch = new ItemStack(ModItems.DIAMOND_WATCH.get());
            AbstractWatchItem.setSocketedRune(barrierWatch, new ItemStack(ModItems.RUNE_BARRIER.get()));
            player.setItemInHand(InteractionHand.MAIN_HAND, barrierWatch);

            ItemStack vectorWatch = new ItemStack(ModItems.CHRONOS_WATCH.get());
            AbstractWatchItem.setSocketedRune(vectorWatch, new ItemStack(ModItems.RUNE_VECTOR.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, vectorWatch);

            helper.assertTrue(RuneManager.hasRune(player, RuneType.KINETIC_BARRIER)
                    && RuneManager.getSocketedRuneStack(player).is(ModItems.RUNE_BARRIER.get()),
                    "Passive vector rune must not disable the barrier");
            arrow.setDeltaMovement(new Vec3(0, 0, -4));
            player.setYRot(90);
            TimeStopManager.punchSuspendedProjectile(arrow, player);
            player.setYRot(-90);
            TimeStopManager.punchSuspendedProjectile(arrow, player);
            Vec3 chosen = player.getLookAngle().normalize();
            player.setYRot(0);
            TimeStopManager.resumeSingleProjectile(helper.getLevel(), arrow);
            helper.assertTrue(arrow.getDeltaMovement().normalize().distanceToSqr(chosen) < 1e-10,
                    "Release must use the latest click direction, not the camera direction at release");
            helper.assertTrue(!arrow.isNoGravity(), "Redirected arrows must retain normal gravity");
        } finally {
            settings.setRedirectToLook(oldLook); TimeStopManager.removeSuspendedProjectile(arrow); arrow.discard(); player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void returnAndLookModesChooseDifferentDirections(GameTestHelper helper) {
        var settings = TimeStopSavedData.get();
        boolean oldLook = settings.isRedirectToLook();
        Player player = helper.makeMockSurvivalPlayer();
        Player shooter = helper.makeMockSurvivalPlayer();
        Arrow arrow = new Arrow(helper.getLevel(), 0, 200, 0);
        try {
            shooter.setPos(0, 200, -20);
            arrow.setOwner(shooter);
            player.setYRot(90);
            settings.setRedirectToLook(false);
            Vec3 back = com.timestop.combat.ProjectileRedirection.direction(arrow, player, shooter, new Vec3(0, 0, 4));
            helper.assertTrue(back.distanceToSqr(shooter.getEyePosition().subtract(arrow.position()).normalize()) < 1e-10,
                    "Return mode must aim toward the original shooter");
            settings.setRedirectToLook(true);
            arrow.setDeltaMovement(new Vec3(0, 0, 4));
            arrow.getPersistentData().putBoolean("DeadEyeTaczPrecision", true);
            TimeStopManager.deflectDynamicProjectile(arrow, player);
            helper.assertTrue(arrow.getDeltaMovement().normalize().distanceToSqr(player.getLookAngle().normalize()) < 1e-10
                    && !arrow.getPersistentData().getBoolean("DeadEyeTaczPrecision"),
                    "Dynamic look redirection must discard previous Dead Eye guidance");
        } finally { settings.setRedirectToLook(oldLook); arrow.discard(); player.discard(); shooter.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void marksmanRuneRequiredForCoins(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RUNE_COIN.get()));
            helper.assertTrue(!com.timestop.combat.CoinManager.hasCharge(player),
                    "Loose marksman rune in hand must not grant coin charges");
            helper.assertTrue(com.timestop.combat.CoinManager.getCharges(player) == 0,
                    "Loose marksman rune must have 0 charges");

            ItemStack watch = new ItemStack(ModItems.DIAMOND_WATCH.get());
            AbstractWatchItem.setSocketedRune(watch, new ItemStack(ModItems.RUNE_COIN.get()));
            player.setItemInHand(InteractionHand.MAIN_HAND, watch);

            helper.assertTrue(RuneManager.hasRune(player, RuneType.RICOSHOT),
                    "Socketed marksman rune must be detected");
            helper.assertTrue(com.timestop.combat.CoinManager.hasCharge(player),
                    "Socketed marksman rune must grant coin charges");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void projectileFlowPacketAndPersistence(GameTestHelper helper) {
        var explicitBuf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        var toggleBuf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var explicitPacket = new com.timestop.network.ToggleProjectileFlowPacket(TimeStopManager.ProjectileStasisMode.SUSPENDED);
            explicitPacket.toBytes(explicitBuf);
            var decodedExplicit = new com.timestop.network.ToggleProjectileFlowPacket(explicitBuf);

            var togglePacket = new com.timestop.network.ToggleProjectileFlowPacket();
            togglePacket.toBytes(toggleBuf);
            var decodedToggle = new com.timestop.network.ToggleProjectileFlowPacket(toggleBuf);

            var savedData = TimeStopSavedData.get();
            savedData.setProjectileStasisMode(TimeStopManager.ProjectileStasisMode.SUSPENDED);
            var nbt = savedData.save(new net.minecraft.nbt.CompoundTag());
            var reloaded = TimeStopSavedData.load(nbt);
            helper.assertTrue(reloaded.getProjectileStasisMode() == TimeStopManager.ProjectileStasisMode.SUSPENDED,
                    "ProjectileStasisMode must persist across NBT load/save");
            savedData.setProjectileStasisMode(TimeStopManager.ProjectileStasisMode.FLOWING);
        } finally {
            explicitBuf.release();
            toggleBuf.release();
        }
        helper.succeed();
    }
}
