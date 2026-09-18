package com.timestop.fabric.test;

import com.timestop.config.TimeStopConfig;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.core.rewind.*;
import com.timestop.core.rewind.data.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.List;

public class RewindRegressionTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind")
    public void rewoundEntityResetsRelativeMovementBase(GameTestHelper h) {
        var mob = h.spawnWithNoFreeWill(EntityType.PIG, 1, 2, 1);
        var packets = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        var tracker = new net.minecraft.server.level.ServerEntity(h.getLevel(), mob, 1, true, packets::add);
        tracker.sendChanges();
        packets.clear();
        mob.setPos(mob.getX() + 0.5, mob.getY(), mob.getZ());
        RewindEntitySync.mark(mob);
        tracker.sendChanges();
        h.assertTrue(packets.stream().anyMatch(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket),
                "Tracking must send an absolute position after a rewind teleport");
        packets.clear();
        mob.setPos(mob.getX() + 0.25, mob.getY(), mob.getZ());
        tracker.sendChanges();
        var movement = packets.stream().filter(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundMoveEntityPacket)
                .map(packet -> (net.minecraft.network.protocol.game.ClientboundMoveEntityPacket) packet).findFirst().orElseThrow();
        h.assertTrue(movement.getXa() == 1024, "Next relative movement must start at the restored position, without a second rewind offset");
        mob.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind")
    public void rewindClearsUnfinishedMining(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        var pos = h.absolutePos(new BlockPos(3, 2, 1));
        h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        var before = PlayerDelta.fromPlayer(player);
        player.gameMode.handleBlockBreakAction(pos, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                net.minecraft.core.Direction.UP, h.getLevel().getMaxBuildHeight(), 1);
        player.gameMode.handleBlockBreakAction(pos, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                net.minecraft.core.Direction.UP, h.getLevel().getMaxBuildHeight(), 2);
        before.restoreTo(player, true);
        for (int tick = 0; tick < 100; tick++) player.gameMode.tick();
        h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE), "Queued mining must not finish against restored history");
        player.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind")
    public void canPlaceAndBreakAfterBurstAndContinuous(GameTestHelper h) {
        checkInteractionAfterRewind(h, false);
        checkInteractionAfterRewind(h, true);
        h.succeed();
    }

    private void checkInteractionAfterRewind(GameTestHelper h, boolean continuous) {
        var level = h.getLevel();
        var recorder = TickRecorder.getInstance();
        var player = h.makeMockServerPlayerInLevel();
        var packets = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { packets.add(packet); }
        };
        String previousMode = TimeStopConfig.COMMON.rewindMode.get();
        try {
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
            var ground = h.absolutePos(new BlockPos(3, 1, 1));
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 3);
            player.getInventory().selected = 2;
            player.getInventory().setItem(2, new ItemStack(Items.DIRT, 32));
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            recorder.serverTick(level.getServer());
            player.getInventory().selected = 7;
            player.getInventory().setItem(7, new ItemStack(Items.DIAMOND_SWORD));
            player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            player.setPos(player.getX() + 10, player.getY(), player.getZ());
            packets.clear();
            if (continuous) {
                TimeStopConfig.COMMON.rewindMode.set("CONTINUOUS");
                TimeStopManager.startGlobalTimeStop(level, player, 20, TimeMode.REWIND);
                TimeStopManager.serverTick();
                h.assertFalse(TimeStopManager.isGlobalTimeStopActive(), "Last rewind frame must release mode immediately");
            } else {
                h.assertTrue(RewindExecutor.execute(level.getServer(), 1, player, true).success(), "Burst failed");
            }
            h.assertTrue(packets.stream().anyMatch(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket slot && slot.getSlot() == 2),
                    "Client must receive the restored hotbar selection before interaction resumes");
            h.assertFalse(player.isUsingItem(), "Rewind must end stale item use");
            var teleport = packets.stream().filter(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket)
                    .map(packet -> (net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket) packet).reduce((a, b) -> b).orElseThrow();
            player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(teleport.getId()));
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0, 0.5, 0),
                    net.minecraft.core.Direction.UP, ground, false);
            player.connection.handleUseItemOn(new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(net.minecraft.world.InteractionHand.MAIN_HAND, hit, 1));
            h.assertTrue(level.getBlockState(ground.above()).is(Blocks.DIRT), "Placement through the normal packet handler must work after rewind");
            player.connection.handlePlayerAction(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, ground.above(), net.minecraft.core.Direction.UP, 2));
            h.assertTrue(level.getBlockState(ground.above()).isAir(), "Breaking through the normal packet handler must work after rewind");
            h.assertTrue(recorder.getTimelineBuffer().isRecording() && !recorder.getTimelineBuffer().isRewinding(), "Playback flags must be released");
        } finally {
            TimeStopManager.resumeTime(level);
            TimeStopConfig.COMMON.rewindMode.set(previousMode);
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            player.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind")
    public void missingUpdateAndTransientDespawnNeverRespawn(GameTestHelper h) {
        var level = h.getLevel();
        var pig = h.spawnWithNoFreeWill(EntityType.PIG, 1, 2, 1);
        var state = new CompoundTag();
        pig.save(state);
        var id = pig.getUUID();
        pig.discard();
        var frame = new TickFrame(0);
        frame.captureEnvironment(level);
        frame.addEntityDelta(new EntityDelta(level.dimension(), id, EntityDelta.Type.UPDATE,
                new net.minecraft.resources.ResourceLocation("minecraft:pig"), state, null));
        var plan = RewindExecutor.buildPlan(List.of(frame));
        h.assertTrue(plan.entitiesToRespawn().isEmpty(), "An UPDATE is not evidence of death or despawn");
        RewindExecutor.applyPlan(level.getServer(), plan, true);
        h.assertTrue(level.getEntity(id) == null, "Unloaded entities must not get duplicate replacements");
        state.putBoolean("TimeStopTransient", true);
        var transientFrame = new TickFrame(0);
        transientFrame.captureEnvironment(level);
        transientFrame.addEntityDelta(EntityDelta.despawn(level.dimension(), id, new net.minecraft.resources.ResourceLocation("minecraft:pig"), state));
        RewindExecutor.applyPlan(level.getServer(), RewindExecutor.buildPlan(List.of(transientFrame)), true);
        h.assertTrue(level.getEntity(id) == null, "Transient entities with incomplete save data must never be recreated");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind")
    public void corruptEntityDoesNotAbortBlockRestoration(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        var frame = new TickFrame(0);
        frame.captureEnvironment(level);
        frame.addBlockDelta(BlockDelta.create(level.dimension(), pos, Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(), null, null));
        var state = new CompoundTag();
        var invalidPos = new net.minecraft.nbt.ListTag();
        invalidPos.add(net.minecraft.nbt.DoubleTag.valueOf(Double.NaN));
        invalidPos.add(net.minecraft.nbt.DoubleTag.valueOf(0));
        invalidPos.add(net.minecraft.nbt.DoubleTag.valueOf(0));
        state.put("Pos", invalidPos);
        var id = java.util.UUID.randomUUID();
        state.putUUID("UUID", id);
        frame.addEntityDelta(EntityDelta.despawn(level.dimension(), id, new net.minecraft.resources.ResourceLocation("minecraft:pig"), state));
        var result = RewindExecutor.applyPlan(level.getServer(), RewindExecutor.buildPlan(List.of(frame)), true);
        h.assertTrue(result.success() && !result.warnings().isEmpty(), "Bad entity must be reported without aborting the plan");
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "Block restoration must survive a mod entity exception");
        h.assertTrue(level.getEntity(id) == null, "Failed respawn must not leave a registered entity");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind")
    public void placedChestReversalDoesNotDuplicateContents(GameTestHelper h) {
        var level = h.getLevel();
        var recorder = TickRecorder.getInstance();
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        recorder.serverTick(level.getServer());
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (ChestBlockEntity) level.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 64));
        chest.setChanged();
        var result = RewindExecutor.execute(level.getServer(), 1, null, true);
        h.assertTrue(result.success() && level.getBlockState(pos).isAir(), "New chest must disappear");
        h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1)).isEmpty(), "Reversing a container placement must not generate fresh item drops");
        recorder.clearTrackingData();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind")
    public void playerInventoryExperienceAndPositionRestore(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        player.getInventory().selected = 2;
        player.totalExperience = 123;
        player.experienceLevel = 7;
        player.experienceProgress = 0.25F;
        var before = PlayerDelta.fromPlayer(player);
        player.getInventory().clearContent();
        player.totalExperience = 0;
        player.experienceLevel = 0;
        player.experienceProgress = 0;
        player.setPos(player.getX() + 10, player.getY(), player.getZ());
        player.setHealth(3);
        before.restoreTo(player, true);
        h.assertTrue(player.getInventory().getItem(0).getCount() == 5 && player.getInventory().selected == 2, "Inventory and selected hotbar slot must restore");
        h.assertTrue(player.totalExperience == 123 && player.experienceLevel == 7 && player.experienceProgress == 0.25F, "All XP fields must restore");
        h.assertTrue(player.getHealth() == before.health() && player.getX() == before.posX(), "Health and position must restore");
        player.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
        before.restoreTo(player, false);
        h.assertTrue(player.getInventory().getItem(0).is(Items.GOLD_INGOT), "Inventory rollback opt-out must be honored");
        player.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind")
    public void reverseOrderAndMemoryEviction(GameTestHelper h) {
        var frame = new TickFrame(0);
        frame.addBlockDelta(BlockDelta.create(Level.OVERWORLD, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(), null, null));
        frame.addBlockDelta(BlockDelta.create(Level.OVERWORLD, BlockPos.ZERO, Blocks.DIRT.defaultBlockState(), Blocks.AIR.defaultBlockState(), null, null));
        var plan = RewindExecutor.buildPlan(List.of(frame));
        h.assertTrue(plan.blockTargetStates().values().iterator().next().is(Blocks.STONE), "First state within a tick must win");
        var buffer = new TimelineBuffer(3, 128);
        for (int i = 0; i < 5; i++) buffer.pushFrame(new TickFrame(i));
        h.assertTrue(buffer.getFrameCount() == 2, "Memory cap must evict oldest frames");
        h.assertTrue(buffer.getFramesForRewind(10).get(0).getGameTime() == 4, "Newest frame lost at wraparound");
        buffer.removeRecentFrames(1);
        buffer.pushFrame(new TickFrame(8));
        h.assertTrue(buffer.getFramesForRewind(2).get(1).getGameTime() == 3, "Branching after rewind must keep older history");
        buffer.setRewinding(true);
        buffer.pushFrame(new TickFrame(9));
        h.assertTrue(buffer.getFrameCount() == 2, "Playback must not record new frames");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind")
    public void blocksContainersAndDeadMobsRestoreTogether(GameTestHelper h) {
        var level = h.getLevel();
        var recorder = TickRecorder.getInstance();
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var chestPos = h.absolutePos(new BlockPos(2, 2, 1));
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        chest.setChanged();
        var mob = h.spawnWithNoFreeWill(EntityType.PIG, 1, 2, 2);
        var id = mob.getUUID();
        double x = mob.getX(), y = mob.getY(), z = mob.getZ();
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        recorder.serverTick(level.getServer());
        level.getBlockEntity(chestPos); // First access seeds inventory BEFORE the first mutation.
        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        chest.removeItem(0, 7);
        chest.setChanged();
        level.setBlock(chestPos, Blocks.AIR.defaultBlockState(), 3);
        mob.setPos(x + 5, y, z);
        mob.setHealth(0);
        mob.discard();
        var spawned = h.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2);
        var spawnedId = spawned.getUUID();
        var result = RewindExecutor.execute(level.getServer(), 1, null, true);
        h.assertTrue(result.success(), "Rewind failed: " + result.warnings());
        h.assertTrue(result.ticksRewound() == 1, "All dimensions must share one server frame");
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "Multiple edits in one tick restored wrong block");
        var restoredChest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        h.assertTrue(restoredChest != null && restoredChest.getItem(0).getCount() == 7, "First chest mutation plus destruction lost inventory");
        var restored = level.getEntity(id);
        h.assertTrue(restored != null && restored.isAlive(), "Killed mob must return alive with original UUID");
        h.assertTrue(restored.position().distanceToSqr(x, y, z) < 0.001, "Respawn must use recorded position, never origin/default height");
        h.assertTrue(level.getEntity(spawnedId) == null, "New entities must disappear");
        recorder.clearTrackingData();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind")
    public void continuousConsumesHistoryAndResumesRecording(GameTestHelper h) {
        var level = h.getLevel();
        var recorder = TickRecorder.getInstance();
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        String oldMode = TimeStopConfig.COMMON.rewindMode.get();
        try {
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
            recorder.serverTick(level.getServer());
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            recorder.serverTick(level.getServer());
            level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            TimeStopConfig.COMMON.rewindMode.set("CONTINUOUS");
            TimeStopManager.startGlobalTimeStop(level, null, 0, TimeMode.REWIND);
            var buffer = recorder.getTimelineBuffer();
            h.assertTrue(buffer.getFrameCount() == 2 && buffer.isRewinding(), "Continuous activation must commit pending changes once");
            TimeStopManager.serverTick();
            h.assertTrue(level.getBlockState(pos).is(Blocks.DIRT), "First step must reach previous tick");
            recorder.serverTick(level.getServer());
            h.assertTrue(buffer.getFrameCount() == 1 && !buffer.isRecording(), "A backward step must not be recorded again");
            TimeStopManager.serverTick();
            h.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "Second step must reach older history");
            TimeStopManager.serverTick();
            h.assertTrue(!buffer.isRewinding() && buffer.isRecording(), "Exhausted history must resume normal recording");
            h.assertFalse(TimeStopManager.isGlobalTimeStopActive(), "Exhaustion must end the active mode");
        } finally {
            TimeStopManager.resumeTime(level);
            TimeStopConfig.COMMON.rewindMode.set(oldMode);
            recorder.clearTrackingData();
            recorder.getTimelineBuffer().clear();
        }
        h.succeed();
    }
}
