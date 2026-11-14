package com.timestop.fabric.test;

import com.timestop.combat.*;
import com.timestop.config.TimeStopConfig;
import com.timestop.core.*;
import com.timestop.core.rewind.*;
import com.timestop.item.*;
import com.timestop.item.rune.RuneType;
import com.timestop.network.*;
import com.timestop.platform.EntityDataHelper;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class CreativeWatchFeatureTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "creative_watch_features")
    public void matrixTogglesAreIndependentAndSurvivalWatchesRejectPackets(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        var watch = new ItemStack(ModItems.CREATIVE_WATCH.get());
        player.setItemInHand(InteractionHand.OFF_HAND, watch);
        try {
            new ToggleMatrixRunePacket(InteractionHand.OFF_HAND, RuneType.KINETIC, true, false).handle(player);
            new ToggleMatrixRunePacket(InteractionHand.OFF_HAND, RuneType.VOLATILE, true, false).handle(player);
            new ToggleMatrixRunePacket(InteractionHand.OFF_HAND, RuneType.VECTOR, true, false).handle(player);
            h.assertTrue(RuneManager.hasRune(player, RuneType.KINETIC) && RuneManager.hasRune(player, RuneType.VOLATILE) && ProjectileRedirection.usesLook(player), "Several matrix runes must operate together");
            new ToggleMatrixRunePacket(InteractionHand.OFF_HAND, RuneType.KINETIC, false, false).handle(player);
            h.assertFalse(RuneManager.hasRune(player, RuneType.KINETIC), "Toggled rune must turn off");
            h.assertTrue(RuneManager.hasRune(player, RuneType.VOLATILE), "Other runes must remain on");
            new ToggleMatrixRunePacket(InteractionHand.OFF_HAND, null, true, true).handle(player);
            h.assertTrue(AbstractWatchItem.getActiveMatrixRuneCount(watch) == 15, "Enable all must activate every rune");
            new ToggleMatrixRunePacket(InteractionHand.OFF_HAND, null, false, true).handle(player);
            h.assertTrue(AbstractWatchItem.getActiveMatrixRuneCount(watch) == 0, "Disable all must deactivate every rune");
            var diamond = new ItemStack(ModItems.DIAMOND_WATCH.get());
            player.setItemInHand(InteractionHand.OFF_HAND, diamond);
            new ToggleMatrixRunePacket(InteractionHand.OFF_HAND, null, true, true).handle(player);
            new SetRewindExemptPacket(InteractionHand.OFF_HAND, true).handle(player);
            h.assertTrue(AbstractWatchItem.getActiveMatrixRuneCount(diamond) == 0 && !AbstractWatchItem.isRewindInitiatorExempt(diamond), "Survival watches must reject creative-only settings");
        } finally { player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creative_watch_features")
    public void legacyCreativeSocketReturnsRuneAndDisableAllStopsItsAbility(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        var watch = new ItemStack(ModItems.CREATIVE_WATCH.get());
        AbstractWatchItem.setSocketedRune(watch, new ItemStack(ModItems.RUNE_DEFLECTION.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, watch);
        try {
            h.assertTrue(RuneManager.hasRune(player, RuneType.DEFLECTION), "Old socketed ability must work before migration");
            new ToggleMatrixRunePacket(InteractionHand.OFF_HAND, RuneType.KINETIC, true, false).handle(player);
            h.assertTrue(RuneManager.hasRune(player, RuneType.DEFLECTION) && RuneManager.hasRune(player, RuneType.KINETIC), "Migration must preserve old ability with new one");
            h.assertTrue(AbstractWatchItem.getSocketedRune(watch).isEmpty() && player.getInventory().countItem(ModItems.RUNE_DEFLECTION.get()) == 1, "Physical legacy rune must be returned exactly once");
            new ToggleMatrixRunePacket(InteractionHand.OFF_HAND, null, false, true).handle(player);
            h.assertFalse(RuneManager.hasRune(player, RuneType.DEFLECTION), "Disabled legacy matrix rune must stay disabled");
            h.assertTrue(player.getInventory().countItem(ModItems.RUNE_DEFLECTION.get()) == 1, "Repeated toggles must not duplicate runes");
        } finally { player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creative_watch_features")
    public void kineticAndVolatileBothModifySameSuspendedArrow(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        player.setPos(h.absoluteVec(new Vec3(2, 2, 2)));
        var watch = new ItemStack(ModItems.CREATIVE_WATCH.get());
        AbstractWatchItem.setMatrixRune(watch, RuneType.KINETIC, true);
        AbstractWatchItem.setMatrixRune(watch, RuneType.VOLATILE, true);
        player.setItemInHand(InteractionHand.OFF_HAND, watch);
        var arrow = new Arrow(h.getLevel(), player.getX(), player.getEyeY(), player.getZ() + 1, new ItemStack(Items.ARROW), null);
        arrow.setDeltaMovement(0, 0, -2);
        h.getLevel().addFreshEntity(arrow);
        try {
            TimeStopManager.punchSuspendedProjectile(arrow, player);
            h.assertTrue(EntityDataHelper.getPersistentData(arrow).getBoolean("VolatileStasis"), "Volatile must apply alongside kinetic enhancement");
            h.assertTrue(TimeStopManager.getSuspendedVelocity(arrow).length() > 3.5, "Kinetic must also amplify the same arrow");
        } finally { TimeStopManager.removeSuspendedProjectile(arrow); arrow.discard(); player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creative_watch_features")
    public void rewindPreferencesSurviveInventoryRestoration(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        var watch = new ItemStack(ModItems.CREATIVE_WATCH.get());
        player.setItemInHand(InteractionHand.OFF_HAND, watch);
        try {
            var history = WatchPreferences.snapshot(watch);
            AbstractWatchItem.setMatrixRune(watch, RuneType.KINETIC, true);
            AbstractWatchItem.setMatrixRune(watch, RuneType.REWIND, true);
            AbstractWatchItem.setBubbleRadius(watch, 17);
            AbstractWatchItem.setRewindInitiatorExempt(watch, true);
            var restored = WatchPreferences.restore(history, WatchPreferences.capture(player));
            h.assertTrue(AbstractWatchItem.isMatrixRuneActive(restored, RuneType.KINETIC) && AbstractWatchItem.isMatrixRuneActive(restored, RuneType.REWIND), "Rewind must retain current matrix settings");
            h.assertTrue(AbstractWatchItem.getBubbleRadius(restored) == 17 && AbstractWatchItem.isRewindInitiatorExempt(restored), "Rewind must retain radius and Self preference");
        } finally { player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creative_watch_features")
    public void rewindExemptionServerPolicyAndCommandsWork(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_WATCH.get()));
        boolean allowed = TimeStopManager.isRewindExemptAllowed();
        try {
            TimeStopManager.setRewindExemptAllowed(true);
            var commands = player.server.getCommands();
            commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "timestop rewind exempt true");
            h.assertTrue(AbstractWatchItem.isPlayerRewindExempt(player), "Command must enable Self exemption");
            commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "timestop rewind allowexempt false");
            h.assertFalse(AbstractWatchItem.isPlayerRewindExempt(player), "Server policy must override per-watch exemption");
            new SetRewindExemptPacket(InteractionHand.MAIN_HAND, false).handle(player);
            h.assertTrue(AbstractWatchItem.isRewindInitiatorExempt(player.getMainHandItem()), "Locked policy must reject packet edits");
        } finally { TimeStopManager.setRewindExemptAllowed(allowed); player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creative_watch_features")
    public void burstGlobalSelfOffPreservesPlayer(GameTestHelper h) { rewind(h, false, true, true); }
    @GameTest(template = EMPTY_STRUCTURE, batch = "creative_watch_features")
    public void burstLocalSelfOffPreservesPlayer(GameTestHelper h) { rewind(h, false, false, true); }
    @GameTest(template = EMPTY_STRUCTURE, batch = "creative_watch_features")
    public void continuousGlobalSelfOffPreservesPlayer(GameTestHelper h) { rewind(h, true, true, true); }
    @GameTest(template = EMPTY_STRUCTURE, batch = "creative_watch_features")
    public void continuousLocalSelfOffPreservesPlayer(GameTestHelper h) { rewind(h, true, false, true); }
    @GameTest(template = EMPTY_STRUCTURE, batch = "creative_watch_features")
    public void selfOnRestoresPlayerNormally(GameTestHelper h) { rewind(h, false, false, false); }

    private void rewind(GameTestHelper h, boolean continuous, boolean global, boolean exempt) {
        var player = RewindRuneTests.survivalPlayer(h);
        var level = h.getLevel();
        var watch = new ItemStack(ModItems.CREATIVE_WATCH.get());
        AbstractWatchItem.setGlobalScope(watch, global);
        AbstractWatchItem.setMode(watch, TimeMode.REWIND);
        AbstractWatchItem.setBubbleRadius(watch, 8);
        AbstractWatchItem.setRewindInitiatorExempt(watch, exempt);
        player.setItemInHand(InteractionHand.OFF_HAND, watch);
        var settings = TimeStopSavedData.get();
        var oldScope = settings.getWatchScope();
        boolean oldAllowed = settings.isRewindExemptInitiatorAllowed();
        String oldMode = TimeStopConfig.COMMON.rewindMode.get();
        var recorder = TickRecorder.getInstance();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        try {
            settings.setWatchScope(TimeStopSavedData.WatchScope.WATCH);
            settings.setRewindExemptInitiatorAllowed(true);
            TimeStopConfig.COMMON.rewindMode.set(continuous ? "CONTINUOUS" : "BURST");
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            player.setHealth(20);
            player.getInventory().setItem(9, new ItemStack(Items.STONE, 4));
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 18);
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear(); recorder.serverTick(level.getServer());
            player.setPos(player.getX() + 2, player.getY(), player.getZ());
            double currentX = player.getX();
            player.setHealth(6);
            player.getInventory().setItem(9, new ItemStack(Items.GOLD_INGOT, 7));
            level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 18);
            TimeStopManager.startTimeStop(level, player, 20, TimeMode.REWIND);
            for (int i = 0; i < 10 && (TimeStopManager.isGlobalTimeStopActive() || LocalRewind.isActive(player.getUUID())); i++) {
                if (global) TimeStopManager.serverTick(); else LocalRewind.tick(level.getServer());
            }
            h.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "World must rewind with either Self setting");
            h.assertTrue(player.getHealth() == (exempt ? 6 : 20), "Self setting must control health restoration");
            h.assertTrue(Math.abs(player.getX() - (exempt ? currentX : pos.getX() + 0.5)) < 0.01, "Self setting must control position restoration");
            h.assertTrue(player.getInventory().getItem(9).is(exempt ? Items.GOLD_INGOT : Items.STONE), "Self setting must control inventory restoration");
        } finally {
            LocalRewind.cancel(player.getUUID());
            if (TimeStopManager.isGlobalTimeStopActive()) TimeStopManager.resumeTime(level);
            settings.setWatchScope(oldScope); settings.setRewindExemptInitiatorAllowed(oldAllowed);
            TimeStopConfig.COMMON.rewindMode.set(oldMode);
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear(); player.discard();
        }
        h.succeed();
    }
}
