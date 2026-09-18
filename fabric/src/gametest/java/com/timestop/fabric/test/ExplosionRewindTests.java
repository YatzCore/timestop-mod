package com.timestop.fabric.test;

import com.timestop.core.rewind.*;
import com.timestop.core.rewind.data.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public class ExplosionRewindTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "explosion_rewind")
    public void liveFuseIsSynchronizedAndRespawnUsesOldestFuse(GameTestHelper h) {
        var level = h.getLevel();
        var player = h.makeMockServerPlayerInLevel();
        var packets = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { packets.add(packet); }
        };
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 2, pos.getY(), pos.getZ());
        level.getChunkSource().move(player);
        var tnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, null);
        tnt.setNoGravity(true);
        tnt.setDeltaMovement(Vec3.ZERO);
        tnt.setFuse(60);
        level.addFreshEntity(tnt);
        var state = new net.minecraft.nbt.CompoundTag();
        tnt.save(state);
        var id = tnt.getUUID();
        var earlier = new TickFrame(0);
        earlier.captureEnvironment(level);
        earlier.addEntityDelta(new EntityDelta(level.dimension(), id, EntityDelta.Type.UPDATE,
                new net.minecraft.resources.ResourceLocation("minecraft:tnt"), state, null));
        tnt.setFuse(1);
        tnt.getEntityData().packDirty();
        packets.clear();
        RewindExecutor.applyPlan(level.getServer(), RewindExecutor.buildPlan(List.of(earlier)), true);
        h.assertTrue(tnt.getFuse() == 60, "Existing TNT must restore the older fuse");
        h.assertTrue(packets.stream().anyMatch(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket data
                && data.id() == tnt.getId() && data.packedItems().stream().anyMatch(value -> value.value().equals(60))),
                "Client must receive the restored fuse without waiting for normal entity ticking");
        tnt.setFuse(1);
        var lastState = new net.minecraft.nbt.CompoundTag();
        tnt.save(lastState);
        tnt.discard();
        var last = new TickFrame(1);
        last.captureEnvironment(level);
        last.addEntityDelta(EntityDelta.despawn(level.dimension(), id, new net.minecraft.resources.ResourceLocation("minecraft:tnt"), lastState));
        RewindExecutor.applyPlan(level.getServer(), RewindExecutor.buildPlan(List.of(last, earlier)), true);
        var restored = (PrimedTnt) level.getEntity(id);
        h.assertTrue(restored != null && restored.getFuse() == 60, "An exploded TNT entity must use the oldest fuse, not its final tick");
        restored.tick();
        h.assertTrue(!restored.isRemoved() && restored.getFuse() == 59, "Partway through the fuse must resume visibly with the remaining fuse");
        restored.discard();
        player.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "explosion_rewind")
    public void explosionRewindsThroughIgnitionWithoutGhostTnt(GameTestHelper h) {
        var level = h.getLevel();
        var recorder = TickRecorder.getInstance();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var wall = pos.east();
        level.setBlock(pos, Blocks.TNT.defaultBlockState(), 3);
        level.setBlock(wall, Blocks.WHITE_WOOL.defaultBlockState(), 3);
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        recorder.serverTick(level.getServer());
        TntBlock.explode(level, pos);
        level.removeBlock(pos, false);
        var tnt = level.getEntitiesOfClass(PrimedTnt.class, new net.minecraft.world.phys.AABB(pos).inflate(1)).get(0);
        var id = tnt.getUUID();
        tnt.setNoGravity(true);
        tnt.setDeltaMovement(Vec3.ZERO);
        tnt.setFuse(3);
        recorder.serverTick(level.getServer());
        for (int tick = 0; tick < 3; tick++) {
            tnt.tick();
            recorder.serverTick(level.getServer());
        }
        h.assertTrue(tnt.isRemoved() && level.getBlockState(wall).isAir(), "Real TNT must explode and destroy the test wall");
        h.assertTrue(recorder.getTimelineBuffer().getFramesForRewind(20).stream().anyMatch(f -> !f.getExplosions().isEmpty()), "Explosion must be recorded for the rewind effect");
        var result = RewindExecutor.execute(level.getServer(), 1, null, true);
        h.assertTrue(result.success(), "Explosion rewind failed");
        h.assertTrue(level.getBlockState(pos).is(Blocks.TNT), "Crossing ignition must restore the unlit TNT block");
        h.assertTrue(level.getBlockState(wall).is(Blocks.WHITE_WOOL), "Explosion-destroyed blocks must return");
        h.assertTrue(level.getEntity(id) == null, "Restoring the block must remove the primed entity, preventing a second blast");
        h.assertTrue(level.getEntitiesOfClass(PrimedTnt.class, new net.minecraft.world.phys.AABB(pos).inflate(6)).isEmpty(), "No invisible or duplicate primed TNT may remain");
        recorder.clearTrackingData();
        recorder.getTimelineBuffer().clear();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "explosion_rewind")
    public void restoringPoweredTntDoesNotIgniteDuringRollback(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var frame = new TickFrame(0);
        frame.captureEnvironment(level);
        frame.addBlockDelta(BlockDelta.create(level.dimension(), pos, Blocks.TNT.defaultBlockState(), Blocks.AIR.defaultBlockState(), null, null));
        var result = RewindExecutor.applyPlan(level.getServer(), RewindExecutor.buildPlan(List.of(frame)), true);
        h.assertTrue(result.success() && level.getBlockState(pos).is(Blocks.TNT), "Placement callbacks must not re-prime restored TNT");
        h.assertTrue(level.getEntitiesOfClass(PrimedTnt.class, new net.minecraft.world.phys.AABB(pos).inflate(2)).isEmpty(), "Rollback must not spawn a fresh fuse");
        h.assertFalse(RewindExecutor.isApplyingPlan(), "Restoration guard must be released");
        // Normal redstone behavior must still work after rollback.
        level.setBlock(pos.west(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        h.assertTrue(level.getBlockState(pos).isAir(), "Subsequent real power changes must still prime TNT normally");
        for (var tnt : level.getEntitiesOfClass(PrimedTnt.class, new net.minecraft.world.phys.AABB(pos).inflate(2))) tnt.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "explosion_rewind")
    public void chainExplosionEffectsStayBoundedAndExpire(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        RewindExplosionEffects.clear();
        for (int i = 0; i < 100; i++) RewindExplosionEffects.enqueue(h.getLevel(),
                new TickFrame.ExplosionMoment(h.getLevel().dimension(), i * 10, 50, 0, 100));
        h.assertTrue(RewindExplosionEffects.activeCount() == RewindExplosionEffects.MAX_ACTIVE, "Large chains must respect the global effect cap");
        for (int tick = 0; tick < RewindExplosionEffects.DURATION_TICKS; tick++) RewindExplosionEffects.tick();
        h.assertTrue(RewindExplosionEffects.activeCount() == 0, "Effects must expire without leaving a background queue");
        player.discard();
        h.succeed();
    }
}
