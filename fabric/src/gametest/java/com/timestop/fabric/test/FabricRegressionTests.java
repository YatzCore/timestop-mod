package com.timestop.fabric.test;

import com.timestop.combat.*;
import com.timestop.core.SuperhotMotion;
import com.timestop.core.TemporalBubble;
import com.timestop.core.TimeMode;
import com.timestop.item.*;
import com.timestop.item.rune.RuneType;
import com.timestop.platform.EntityDataHelper;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.UUID;

public class FabricRegressionTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void superhotInvoluntaryMotionAndVelocityNeverAdvance(GameTestHelper h) {
        h.assertFalse(SuperhotMotion.isMoving(new Vec3(0, -0.0784, 0), true), "Idle gravity must stay slow");
        h.assertFalse(SuperhotMotion.isMoving(Vec3.ZERO, true), "Stationary must stay slow");
        h.assertFalse(SuperhotMotion.isMoving(new Vec3(0.1, -0.0784, 0), true), "Velocity alone must NOT advance time");
        h.assertFalse(SuperhotMotion.isMoving(new Vec3(0, 0.42, 0), false), "Jumping velocity must NOT advance time");
        h.assertFalse(SuperhotMotion.isMoving(new Vec3(0, -0.3, 0), false), "Falling must NOT advance time");
        h.assertFalse(SuperhotMotion.isMoving(new Vec3(1.5, 0.2, 0), false), "Knockback must NOT advance time");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void superhotMovementKeysAdvanceImmediately(GameTestHelper h) {
        h.assertTrue(SuperhotMotion.isMovementInputActive(true, false, false, false, false, false, true), "Forward alone must advance time");
        h.assertTrue(SuperhotMotion.isMovementInputActive(false, true, false, false, false, false, true), "Backward alone must advance time");
        h.assertTrue(SuperhotMotion.isMovementInputActive(false, false, true, false, false, false, true), "Left alone must advance time");
        h.assertTrue(SuperhotMotion.isMovementInputActive(false, false, false, true, false, false, true), "Right alone must advance time");
        h.assertTrue(SuperhotMotion.isMovementInputActive(false, false, false, false, true, false, true), "Jump alone must advance time");
        h.assertFalse(SuperhotMotion.isMovementInputActive(false, false, false, false, false, false, true), "Releasing all keys must return to slow time");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void superhotMenuOrFocusLossHaltsAdvancement(GameTestHelper h) {
        h.assertFalse(SuperhotMotion.isMovementInputActive(true, false, false, false, false, true, true), "Open screen/menu must halt time advancement");
        h.assertFalse(SuperhotMotion.isMovementInputActive(true, false, false, false, false, false, false), "Lost window focus must halt time advancement");
        h.assertFalse(SuperhotMotion.isMovementInputActive(true, false, false, false, false, true, false), "Screen open and unfocused must halt time advancement");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void superhotSneakAndSprintAloneStaySlow(GameTestHelper h) {
        // Sneak alone (no movement key) -> false
        h.assertFalse(SuperhotMotion.isMovementInputActive(false, false, false, false, false, false, true), "Sneak alone must not advance time");
        // Sprint alone (no movement key) -> false
        h.assertFalse(SuperhotMotion.isMovementInputActive(false, false, false, false, false, false, true), "Sprint alone must not advance time");
        // Sneak + Forward -> true
        h.assertTrue(SuperhotMotion.isMovementInputActive(true, false, false, false, false, false, true), "Sneak + Forward must advance time");
        // Sprint + Forward -> true
        h.assertTrue(SuperhotMotion.isMovementInputActive(true, false, false, false, false, false, true), "Sprint + Forward must advance time");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void superhotMultiplayerBubbleScoping(GameTestHelper h) {
        var playerInside = h.makeMockServerPlayerInLevel();
        playerInside.setPos(h.absoluteVec(new Vec3(3, 2, 3)));
        var playerOutside = h.makeMockServerPlayerInLevel();
        playerOutside.setPos(h.absoluteVec(new Vec3(50, 2, 50)));

        var bubble = new TemporalBubble(
                UUID.randomUUID(),
                playerInside.getUUID(),
                h.getLevel().dimension(),
                h.absoluteVec(new Vec3(3, 2, 3)),
                10.0,
                TimeMode.SUPERHOT,
                600,
                WatchTier.DIAMOND,
                ModItems.DIAMOND_WATCH.get(),
                300,
                Collections.emptySet()
        );

        h.assertTrue(bubble.contains(playerInside), "Player inside must be contained in bubble");
        h.assertFalse(bubble.contains(playerOutside), "Player outside must NOT be contained in bubble");

        bubble.setPlayerActivity(playerInside.getUUID(), 1.0F);
        h.assertTrue(bubble.getSuperhotActivity() == 1.0F, "Active inside player must advance bubble");
        h.assertTrue(bubble.getOtherPlayersSuperhotActivity(playerInside.getUUID()) == 0.0F, "Own activity must not echo to self");
        h.assertTrue(bubble.getOtherPlayersSuperhotActivity(playerOutside.getUUID()) == 1.0F, "Other players must see bubble activity");

        bubble.setPlayerActivity(playerInside.getUUID(), 0.0F);
        h.assertTrue(bubble.getSuperhotActivity() == 0.0F, "Releasing keys must immediately return bubble to slow");

        playerInside.discard();
        playerOutside.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void watchPassiveRequiresOffhand(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        var watch = new ItemStack(ModItems.DIAMOND_WATCH.get());
        player.getInventory().setItem(9, watch);
        h.assertTrue(DecelerationFieldManager.getDecelerationRadius(player) == 0, "Inventory must not slow arrows");
        player.getInventory().setItem(9, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, watch);
        h.assertTrue(DecelerationFieldManager.getDecelerationRadius(player) == 0, "Main hand must not enable offhand passive");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, watch);
        h.assertTrue(DecelerationFieldManager.getDecelerationRadius(player) > 0, "Offhand must still slow arrows");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void marksmanSocketConservesItems(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        var watch = new ItemStack(ModItems.DIAMOND_WATCH.get());
        player.setItemInHand(InteractionHand.OFF_HAND, watch);
        player.getInventory().setItem(9, new ItemStack(ModItems.RUNE_COIN.get()));
        h.assertTrue(RuneSocketTransactions.apply(player, watch, 9, RuneType.RICOSHOT), "Marksman must insert without TACZ");
        h.assertTrue(AbstractWatchItem.getSocketedRuneType(watch) == RuneType.RICOSHOT, "Wrong socket contents");
        h.assertTrue(player.getInventory().getItem(9).isEmpty(), "Insertion must consume the rune");
        h.assertFalse(RuneSocketTransactions.apply(player, watch, 9, RuneType.RICOSHOT), "Stale insertion must fail");
        h.assertTrue(RuneSocketTransactions.apply(player, watch, -1, RuneType.BLANK), "Rune must eject");
        h.assertTrue(player.getInventory().countItem(ModItems.RUNE_COIN.get()) == 1, "Ejection must return exactly one rune");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void barrierCapturesAndDrops(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setPos(h.absoluteVec(new Vec3(3, 2, 2)));
        player.setYRot(0);
        player.setXRot(0);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.RUNE_BARRIER.get()));
        var arrow = new Arrow(h.getLevel(), player.getX(), player.getEyeY(), player.getZ() + 3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
        arrow.setDeltaMovement(0, 0, -1);
        h.getLevel().addFreshEntity(arrow);
        KineticPalmManager.setGuarding(player, true);
        KineticPalmManager.interceptIncoming(arrow);
        h.assertTrue(EntityDataHelper.getPersistentData(arrow).getBoolean("KineticPalmCaptured"), "Barrier must capture incoming arrow");
        KineticPalmManager.setGuarding(player, false);
        KineticPalmManager.dischargeDrop(player);
        h.assertFalse(EntityDataHelper.getPersistentData(arrow).getBoolean("KineticPalmCaptured"), "Release must clear capture");
        h.assertFalse(arrow.isNoGravity(), "Dropped arrow must regain gravity");
        player.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void ricochetDamagesBothTargets(GameTestHelper h) {
        ricochet(h, false);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void lethalHitStillRicochets(GameTestHelper h) {
        ricochet(h, true);
    }

    private void ricochet(GameTestHelper h, boolean lethal) {
        var player = RewindRuneTests.survivalPlayer(h);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.RUNE_RICOCHET.get()));
        var first = h.spawnWithNoFreeWill(EntityType.HUSK, 2, 2, 2);
        var second = h.spawnWithNoFreeWill(EntityType.HUSK, 5, 2, 2);
        if (lethal) first.setHealth(1);
        var arrow = new Arrow(h.getLevel(), first.getX() - 1.2, first.getY() + 1, first.getZ(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
        arrow.setOwner(player);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(1.5, 0, 0);
        h.getLevel().addFreshEntity(arrow);
        h.succeedWhen(() -> {
            h.assertTrue(first.getHealth() < first.getMaxHealth(), "First victim must take vanilla impact damage");
            h.assertTrue(second.getHealth() < second.getMaxHealth(), "Ricochet must reach the next victim");
        });
    }
}
