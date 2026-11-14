package com.timestop.fabric.test;

import com.timestop.combat.KineticPalmManager;
import com.timestop.combat.ProjectileRedirection;
import com.timestop.core.*;
import com.timestop.core.rewind.RewindScope;
import com.timestop.item.*;
import com.timestop.item.rune.RuneType;
import com.timestop.network.SetWatchRadiusPacket;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class WatchRadiusAndAimTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "watch_radius_aim")
    public void radiusPersistsAndCannotChangeSurvivalWatch(GameTestHelper h) {
        var watch = new ItemStack(ModItems.CREATIVE_WATCH.get());
        h.assertTrue(AbstractWatchItem.getBubbleRadius(watch) == WatchTier.CREATIVE.getBubbleRadius(), "Existing watches must retain the server-configured default");
        AbstractWatchItem.setBubbleRadius(watch, 37);
        var restored = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) watch.save(h.getLevel().registryAccess()));
        h.assertTrue(AbstractWatchItem.getBubbleRadius(restored) == 37, "Radius must survive item NBT serialization");
        AbstractWatchItem.setBubbleRadius(watch, -1);
        h.assertTrue(AbstractWatchItem.getBubbleRadius(watch) == 1, "Negative radius must clamp to minimum");
        AbstractWatchItem.setBubbleRadius(watch, Integer.MAX_VALUE);
        h.assertTrue(AbstractWatchItem.getBubbleRadius(watch) == 512, "Oversized radius must clamp to maximum");
        var diamond = new ItemStack(ModItems.DIAMOND_WATCH.get());
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, diamond, tag -> tag.putInt("BubbleRadius", 512));
        AbstractWatchItem.setBubbleRadius(diamond, 100);
        h.assertTrue(AbstractWatchItem.getBubbleRadius(diamond) == WatchTier.DIAMOND.getBubbleRadius(), "Survival tiers must retain their radius");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "watch_radius_aim")
    public void radiusPacketRoundTripAndOffhandValidation(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIAMOND_WATCH.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.CREATIVE_WATCH.get()));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SetWatchRadiusPacket(InteractionHand.OFF_HAND, 31).toBytes(buffer);
            new SetWatchRadiusPacket(buffer).handle(player);
            h.assertTrue(AbstractWatchItem.getBubbleRadius(player.getOffhandItem()) == 31, "Packet must update requested hand");
            new SetWatchRadiusPacket(InteractionHand.MAIN_HAND, 512).handle(player);
            h.assertTrue(AbstractWatchItem.getBubbleRadius(player.getMainHandItem()) == WatchTier.DIAMOND.getBubbleRadius(), "Packet must reject non-creative watches");
        } finally { buffer.release(); player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "watch_radius_aim")
    public void radiusControlsBubbleAndLocalRewind(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        player.setPos(h.absoluteVec(new Vec3(2.5, 2, 1)));
        var watch = new ItemStack(ModItems.CREATIVE_WATCH.get());
        AbstractWatchItem.setGlobalScope(watch, false);
        AbstractWatchItem.setBubbleRadius(watch, 7);
        player.setItemInHand(InteractionHand.MAIN_HAND, watch);
        var saved = TimeStopSavedData.get();
        var previous = saved.getWatchScope();
        saved.setWatchScope(TimeStopSavedData.WatchScope.WATCH);
        TemporalBubble bubble = null;
        try {
            var scope = RewindScope.forWatch(player, watch);
            h.assertTrue(scope != null && scope.radius() == 7, "Local rewind must use custom radius");
            AbstractWatchItem.setGlobalScope(watch, true);
            h.assertTrue(RewindScope.forWatch(player, watch) == null, "Global scope must still be global");
            AbstractWatchItem.setGlobalScope(watch, false);
            bubble = TemporalBubbleManager.startBubble(h.getLevel(), player, 20, TimeMode.SLOW_MOTION);
            h.assertTrue(bubble.getRadius() == 7, "Activation must use custom radius");
            h.assertTrue(bubble.contains(h.getLevel().dimension(), bubble.getCenter().add(6.9, 0, 0)), "Inside point must be affected");
            h.assertFalse(bubble.contains(h.getLevel().dimension(), bubble.getCenter().add(7.1, 0, 0)), "Outside point must remain unaffected");
        } finally {
            if (bubble != null) TemporalBubbleManager.stopBubble(h.getLevel(), bubble);
            saved.setWatchScope(previous);
            player.discard();
        }
        h.succeed();
    }

    private ServerPlayer aimingPlayer(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        player.setPos(h.absoluteVec(new Vec3(2.5, 2, 1)));
        player.setYRot(0);
        player.setXRot(0);
        return player;
    }

    private Arrow offsetArrow(GameTestHelper h, ServerPlayer player) {
        var arrow = new Arrow(h.getLevel(), player.getX() + 1, player.getEyeY() - 0.6, player.getZ() + 1.2, new ItemStack(net.minecraft.world.item.Items.ARROW), null);
        arrow.setDeltaMovement(0, 0, -2);
        h.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    private Vec3 blockTarget(GameTestHelper h, ServerPlayer player) {
        h.setBlock(new BlockPos(2, 3, 6), Blocks.STONE);
        return new Vec3(player.getX(), player.getEyeY(), h.absolutePos(new BlockPos(2, 3, 6)).getZ());
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "watch_radius_aim")
    public void commandLookConvergesOnBlockAndRestoresGravityOnImpact(GameTestHelper h) {
        var player = aimingPlayer(h);
        var arrow = offsetArrow(h, player);
        Vec3 target = blockTarget(h, player);
        var saved = TimeStopSavedData.get();
        boolean previous = saved.isRedirectToLook();
        saved.setRedirectToLook(true);
        try {
            TimeStopManager.deflectDynamicProjectile(arrow, player);
            h.assertTrue(arrow.getDeltaMovement().normalize().distanceTo(target.subtract(arrow.position()).normalize()) < 1e-5, "Offset arrow must converge at crosshair instead of flying parallel");
            h.assertTrue(arrow.isNoGravity(), "Gravity must not bend precision shots below aim");
            for (int i = 0; i < 5 && arrow.isNoGravity(); i++) arrow.tick();
            h.assertTrue(arrow.position().distanceTo(target) < 0.3, "Actual arrow flight must hit the aimed block");
            h.assertFalse(arrow.isNoGravity(), "Block impact must restore ordinary gravity");
        } finally { saved.setRedirectToLook(previous); arrow.discard(); player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "watch_radius_aim")
    public void vectorRuneTargetsEntityBeforeBlock(GameTestHelper h) {
        var player = aimingPlayer(h);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.RUNE_VECTOR.get()));
        var victim = h.spawnWithNoFreeWill(EntityType.HUSK, 2, 2, 4);
        victim.setPos(player.getX(), player.getY(), player.getZ() + 3);
        blockTarget(h, player);
        var arrow = offsetArrow(h, player);
        try {
            Vec3 direction = ProjectileRedirection.direction(arrow, player, null, arrow.getDeltaMovement());
            var intersection = victim.getBoundingBox().clip(arrow.position(), arrow.position().add(direction.scale(10)));
            h.assertTrue(intersection.isPresent(), "Crosshair entity must win over block behind it");
            TimeStopManager.deflectDynamicProjectile(arrow, player);
            for (int i = 0; i < 4 && victim.getHealth() == victim.getMaxHealth(); i++) arrow.tick();
            h.assertTrue(victim.getHealth() < victim.getMaxHealth(), "Redirected arrow must actually damage the aimed entity");
        } finally { arrow.discard(); victim.discard(); player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "watch_radius_aim")
    public void suspendedVectorArrowKeepsAimOnResume(GameTestHelper h) {
        var player = aimingPlayer(h);
        var watch = new ItemStack(ModItems.CREATIVE_WATCH.get());
        AbstractWatchItem.setMatrixRune(watch, RuneType.VECTOR, true);
        player.setItemInHand(InteractionHand.OFF_HAND, watch);
        var arrow = offsetArrow(h, player);
        Vec3 target = blockTarget(h, player);
        try {
            TimeStopManager.punchSuspendedProjectile(arrow, player);
            h.assertTrue(arrow.getDeltaMovement().lengthSqr() == 0, "Arrow must stay frozen until release");
            TimeStopManager.resumeProjectilesInArea(h.getLevel(), arrow.position(), 2);
            h.assertTrue(arrow.getDeltaMovement().normalize().distanceTo(target.subtract(arrow.position()).normalize()) < 1e-5, "Resumed arrow must retain convergence");
            h.assertTrue(arrow.isNoGravity(), "Resumed aimed arrow must fly straight");
        } finally { TimeStopManager.removeSuspendedProjectile(arrow); arrow.discard(); player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "watch_radius_aim")
    public void returnModeStillTargetsShooter(GameTestHelper h) {
        var player = aimingPlayer(h);
        var shooter = h.spawnWithNoFreeWill(EntityType.SKELETON, 4, 2, 4);
        var arrow = offsetArrow(h, player);
        arrow.setOwner(shooter);
        var saved = TimeStopSavedData.get();
        boolean previous = saved.isRedirectToLook();
        saved.setRedirectToLook(false);
        try {
            TimeStopManager.deflectDynamicProjectile(arrow, player);
            h.assertTrue(arrow.getDeltaMovement().normalize().distanceTo(shooter.getEyePosition().subtract(arrow.position()).normalize()) < 1e-5, "Return mode must still aim at original shooter");
            h.assertFalse(arrow.isNoGravity(), "Normal reflected arrows must retain normal gravity");
        } finally { saved.setRedirectToLook(previous); arrow.discard(); shooter.discard(); player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "watch_radius_aim")
    public void barrierVolleyConvergesFromMultipleOffsets(GameTestHelper h) {
        var player = aimingPlayer(h);
        var watch = new ItemStack(ModItems.CREATIVE_WATCH.get());
        AbstractWatchItem.setMatrixRune(watch, RuneType.KINETIC_BARRIER, true);
        AbstractWatchItem.setMatrixRune(watch, RuneType.VECTOR, true);
        player.setItemInHand(InteractionHand.OFF_HAND, watch);
        Vec3 target = blockTarget(h, player);
        var first = offsetArrow(h, player);
        var second = offsetArrow(h, player);
        second.setPos(player.getX() - 1, player.getEyeY() + 0.3, player.getZ() + 1.2);
        try {
            KineticPalmManager.setGuarding(player, true);
            KineticPalmManager.interceptIncoming(first);
            KineticPalmManager.interceptIncoming(second);
            h.assertTrue(KineticPalmManager.getCapturedCount(player) == 2, "Both test arrows must enter the barrier");
            KineticPalmManager.dischargeRepulse(player, player.getLookAngle());
            for (var arrow : new Arrow[]{first, second}) {
                h.assertTrue(arrow.getDeltaMovement().normalize().distanceTo(target.subtract(arrow.position()).normalize()) < 1e-5,
                        "Every barrier shot must converge on crosshair: position=" + arrow.position() + ", actual=" + arrow.getDeltaMovement().normalize() + ", expected=" + target.subtract(arrow.position()).normalize());
                h.assertTrue(arrow.isNoGravity(), "Barrier precision arrows must retain straight flight");
            }
        } finally { KineticPalmManager.setGuarding(player, false); KineticPalmManager.dischargeDrop(player); first.discard(); second.discard(); player.discard(); }
        h.succeed();
    }
}
