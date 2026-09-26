package com.timestop.fabric.test;

import com.timestop.combat.RewindRuneManager;
import com.timestop.config.TimeStopConfig;
import com.timestop.core.TimeStopManager;
import com.timestop.core.rewind.*;
import com.timestop.core.rewind.data.PlayerDelta;
import com.timestop.item.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.AABB;

public class RewindRuneIntegrationTests implements FabricGameTest {
    private static ItemStack watch() {
        var stack = new ItemStack(ModItems.CHRONOS_WATCH.get());
        AbstractWatchItem.setSocketedRune(stack, new ItemStack(ModItems.RUNE_REWIND.get()));
        return stack;
    }

    @GameTest(template=EMPTY_STRUCTURE, batch="rune_explosion_burst", timeoutTicks=150)
    public void lethalExplosionRestoresBlocksAndTntInBurst(GameTestHelper h) { explosion(h, false); }

    @GameTest(template=EMPTY_STRUCTURE, batch="rune_explosion_continuous", timeoutTicks=150)
    public void lethalExplosionRestoresBlocksAndTntInContinuous(GameTestHelper h) { explosion(h, true); }

    private static void explosion(GameTestHelper h, boolean continuous) {
        var player = RewindRuneTests.survivalPlayer(h);
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.2);
        player.setNoGravity(true);
        // Allow vanilla login damage immunity to expire before exercising real lethal damage.
        h.runAfterDelay(65, () -> {
            TimeStopConfig.COMMON.rewindMode.set(continuous ? "CONTINUOUS" : "BURST");
            var recorder = TickRecorder.getInstance();
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
            player.getInventory().setItem(0, watch());
            player.setHealth(1); player.invulnerableTime = 0;
            var outside = pos.east(120);
            level.setBlock(outside, Blocks.STONE.defaultBlockState(), 18);
            level.setBlock(pos, Blocks.TNT.defaultBlockState(), 3);
            level.setBlock(pos.east(), Blocks.WHITE_WOOL.defaultBlockState(), 3);
            recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
            recorder.serverTick(level.getServer());
            level.setBlock(outside, Blocks.DIAMOND_BLOCK.defaultBlockState(), 18);
            TntBlock.explode(level, pos);
            level.removeBlock(pos, false);
            var tnt = level.getEntitiesOfClass(PrimedTnt.class, new AABB(pos).inflate(1)).get(0);
            tnt.setFuse(1);
            tnt.tick();
            h.assertTrue(player.isAlive() && RewindRuneManager.isPlayerInvulnerable(player), "Real lethal TNT damage must trigger rescue");
            h.assertTrue(AbstractWatchItem.getSocketedRune(player.getInventory().getItem(0)).isEmpty(), "Lethal damage must consume the rune");
            h.assertTrue(level.getBlockState(pos.east()).isAir(), "Explosion must actually destroy the wall before playback");
            h.assertTrue(recorder.isRecording(), "Damage callback must not disable recording of explosion destruction");
            // A second change during the fade must be recorded as well.
            var extra = pos.above(3);
            level.setBlock(extra, Blocks.GLASS.defaultBlockState(), 18);
            h.runAfterDelay(18, () -> {
                h.assertTrue(level.getBlockState(outside).is(Blocks.DIAMOND_BLOCK), "Local death-rune rewind must leave outside changes untouched");
                h.assertTrue(level.getBlockState(pos).is(Blocks.TNT), "Rewind must cross ignition and restore unlit TNT");
                h.assertTrue(level.getBlockState(pos.east()).is(Blocks.WHITE_WOOL), "Lethal explosion damage must be restored");
                h.assertTrue(level.getBlockState(extra).isAir(), "Changes after the damage callback must be included");
                h.assertTrue(level.getEntitiesOfClass(PrimedTnt.class, new AABB(pos).inflate(5)).isEmpty(), "No primed ghost TNT may survive rollback");
                h.assertTrue(player.isAlive() && RewindRuneManager.isPlayerInvulnerable(player), "Grace must survive completion of continuous or burst playback");
                h.assertTrue(AbstractWatchItem.getSocketedRune(player.getInventory().getItem(0)).isEmpty(), "The consumed rune must stay broken");
                TimeStopManager.resumeTime(level);
                RewindRuneManager.clearPlayer(player.getUUID());
                recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
                TimeStopConfig.COMMON.rewindMode.set("BURST");
                player.discard(); h.succeed();
            });
        });
    }

    @GameTest(template=EMPTY_STRUCTURE, batch="rune_mitigated", timeoutTicks=120)
    public void mitigatedDamageDoesNotBreakRune(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        player.setNoGravity(true);
        h.runAfterDelay(65, () -> {
            player.getInventory().setItem(0, watch());
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(40);
            player.setHealth(20); player.setAbsorptionAmount(40); player.invulnerableTime = 0;
            player.hurt(player.damageSources().magic(), 25);
            h.assertTrue(RewindRuneManager.hasRewindRune(player), "Raw damage exceeding health must not consume a rune when absorption prevents death");
            h.assertTrue(!RewindRuneManager.isPlayerInvulnerable(player), "Nonlethal damage must not schedule a rewind");
            player.discard(); h.succeed();
        });
    }

    @GameTest(template=EMPTY_STRUCTURE, batch="rune_identity")
    public void consumptionFollowsExactRuneAndSurvivesCompletion(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        var spare = watch(); var used = watch();
        player.getInventory().setItem(0, spare);
        player.getInventory().setItem(40, used); // Offhand has activation priority.
        var historical = PlayerDelta.fromPlayer(player);
        var looseHistorical = AbstractWatchItem.getSocketedRune(used);
        h.assertTrue(RewindRuneManager.tryTriggerDeathRewind(player, player.damageSources().generic()), "Must consume offhand rune");
        historical.restoreTo(player, true);
        h.assertTrue(!AbstractWatchItem.getSocketedRune(player.getInventory().getItem(0)).isEmpty(), "Spare rune in earlier inventory slot must remain intact");
        h.assertTrue(AbstractWatchItem.getSocketedRune(player.getOffhandItem()).isEmpty(), "Only the activated rune must stay broken");
        RewindRuneManager.onRewindFinished(h.getLevel().getServer());
        RewindRuneManager.clearPlayer(player.getUUID());
        historical.restoreTo(player, true);
        h.assertTrue(AbstractWatchItem.getSocketedRune(player.getOffhandItem()).isEmpty(), "Later rewind or logout cleanup must not resurrect the same rune");
        RewindRuneManager.removeConsumedRune(looseHistorical);
        h.assertTrue(looseHistorical.isEmpty(), "An older loose-item snapshot must not resurrect a spent rune");
        var container = new net.minecraft.nbt.CompoundTag();
        container.put("Items", new net.minecraft.nbt.ListTag());
        container.getList("Items", 10).add(AbstractWatchItem.getSocketedRune(historical.inventory().get(40)).save(h.getLevel().registryAccess()));
        var sanitized = RewindRuneManager.restorationNbt(container);
        h.assertTrue(ItemStack.parseOptional(h.getLevel().registryAccess(), sanitized.getList("Items", 10).getCompound(0)).isEmpty(), "Container restoration must not resurrect spent rune identities");
        h.assertTrue(!ItemStack.parseOptional(h.getLevel().registryAccess(), container.getList("Items", 10).getCompound(0)).isEmpty(), "Sanitizing restoration must not mutate stored timeline NBT");
        player.discard(); h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE, batch="rune_chest_history")
    public void spentRuneCannotReturnToItsHistoricalChest(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 18);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(ModItems.RUNE_REWIND.get()));
        var recorder = TickRecorder.getInstance();
        recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
        recorder.serverTick(level.getServer());
        recorder.observeBlockEntity(chest);
        var historicalTag = chest.saveWithFullMetadata(h.getLevel().registryAccess());
        var rune = chest.removeItemNoUpdate(0);
        chest.setChanged();
        var watch = new ItemStack(ModItems.CHRONOS_WATCH.get());
        AbstractWatchItem.setSocketedRune(watch, rune);
        player.getInventory().setItem(0, watch);
        RewindRuneManager.tryTriggerDeathRewind(player, player.damageSources().generic());
        var frame = new com.timestop.core.rewind.data.TickFrame(0);
        frame.captureEnvironment(level);
        frame.addBlockEntityDelta(com.timestop.core.rewind.data.BlockEntityDelta.create(level.dimension(), pos,
                net.minecraft.resources.ResourceLocation.parse("minecraft:chest"), historicalTag, null));
        RewindExecutor.applyPlan(level.getServer(), RewindExecutor.buildPlan(java.util.List.of(frame)), true);
        h.assertTrue(chest.getItem(0).isEmpty(), "Rewinding to before socketing must not put the consumed rune back in its chest");
        RewindRuneManager.clearPlayer(player.getUUID());
        recorder.clearTrackingData(); recorder.getTimelineBuffer().clear();
        player.discard(); h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE, batch="rune_cancel")
    public void cancelledFadeDoesNotFreezeRecorderOrBlockNextRune(GameTestHelper h) {
        var player = RewindRuneTests.survivalPlayer(h);
        var recorder = TickRecorder.getInstance();
        TimeStopConfig.COMMON.rewindMode.set("BURST");
        player.getInventory().setItem(0, watch());
        RewindRuneManager.tryTriggerDeathRewind(player, player.damageSources().generic());
        RewindRuneManager.clearPlayer(player.getUUID());
        h.assertTrue(recorder.isRecording() && !recorder.getTimelineBuffer().isFrozen(), "Disconnect during fade must not strand recording");
        player.getInventory().setItem(0, watch());
        h.assertTrue(RewindRuneManager.tryTriggerDeathRewind(player, player.damageSources().generic()), "A fresh rune after cleanup must not be rejected by a stale trigger cooldown");
        RewindRuneManager.clearPlayer(player.getUUID()); player.discard(); h.succeed();
    }
}
