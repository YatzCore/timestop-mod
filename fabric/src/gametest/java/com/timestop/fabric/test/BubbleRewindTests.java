package com.timestop.fabric.test;
import com.timestop.core.rewind.*;
import com.timestop.core.rewind.data.*;
import com.timestop.core.*;
import com.timestop.config.TimeStopConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;
public class BubbleRewindTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="bubble_rewind_scope")
    public void sphereRestoresOnlyInsideAndPreservesOutsideHistory(GameTestHelper h) {
        var level=h.getLevel(); var inside=h.absolutePos(new BlockPos(2,2,2)); var outside=inside.east(5);
        var recorder=TickRecorder.getInstance(); recorder.clearTrackingData();recorder.getTimelineBuffer().clear();
        level.setBlock(inside,Blocks.STONE.defaultBlockState(),18);level.setBlock(outside,Blocks.STONE.defaultBlockState(),18);
        recorder.clearTrackingData();recorder.getTimelineBuffer().clear();recorder.serverTick(level.getServer());
        level.setBlock(inside,Blocks.GOLD_BLOCK.defaultBlockState(),18);level.setBlock(outside,Blocks.GOLD_BLOCK.defaultBlockState(),18);
        long day=level.getDayTime();level.setDayTime(day+1000);
        var scope=new RewindScope(level.dimension(),Vec3.atCenterOf(inside),2);
        var result=RewindExecutor.execute(level.getServer(),1,null,true,scope);
        h.assertTrue(result.success() && level.getBlockState(inside).is(Blocks.STONE),"Inside block must rewind");
        h.assertTrue(level.getBlockState(outside).is(Blocks.GOLD_BLOCK),"Outside block must stay changed");
        h.assertTrue(level.getDayTime()==day+1000,"Local rewind must not change dimension daylight");
        var remaining=RewindExecutor.buildPlan(recorder.getTimelineBuffer().getFramesForRewind(20));
        h.assertTrue(remaining.blockTargetStates().containsKey(new RewindPlan.BlockKey(level.dimension(),outside.asLong())),"Outside history must remain available");
        h.assertTrue(!remaining.blockTargetStates().containsKey(new RewindPlan.BlockKey(level.dimension(),inside.asLong())),"Consumed inside history must not replay");
        RewindExecutor.execute(level.getServer(),1,null,true,null);
        h.assertTrue(level.getBlockState(outside).is(Blocks.STONE),"A later global rewind must still restore outside changes");
        level.setDayTime(day);recorder.clearTrackingData();recorder.getTimelineBuffer().clear();h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="bubble_entity_scope")
    public void entitiesAcrossBoundaryAndOtherDimensionsAreUntouched(GameTestHelper h) {
        var level=h.getLevel();var inside=h.absolutePos(new BlockPos(2,2,2));
        var pig=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG,2,2,2);
        var tag=new net.minecraft.nbt.CompoundTag();pig.save(tag);
        var frame=new TickFrame(0);frame.captureEnvironment(level);
        frame.addEntityDelta(EntityDelta.update(level.dimension(),pig.getUUID(),tag,null));
        pig.setPos(inside.getX()+10,inside.getY(),inside.getZ());
        var scope=new RewindScope(level.dimension(),Vec3.atCenterOf(inside),3);
        frame.addBlockDelta(BlockDelta.create(net.minecraft.world.level.Level.NETHER,inside,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState(),null,null));
        var plan=scope.filter(level.getServer(),RewindExecutor.buildPlan(List.of(frame)));
        h.assertTrue(plan.entityTargetStates().isEmpty(),"Mob which left sphere must not be dragged back from outside");
        h.assertTrue(plan.blockTargetStates().isEmpty() && plan.oldestFrame()==null,"Other dimensions and global weather must be excluded");
        pig.discard();h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="bubble_continuous")
    public void localPlaybackKeepsOutsideSimulationAndRecording(GameTestHelper h) {
        var player=RewindRuneTests.survivalPlayer(h);var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));
        player.setPos(pos.getX(),pos.getY(),pos.getZ());
        var recorder=TickRecorder.getInstance();recorder.clearTrackingData();recorder.getTimelineBuffer().clear();
        level.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
        recorder.clearTrackingData();recorder.getTimelineBuffer().clear();recorder.serverTick(level.getServer());
        level.setBlock(pos,Blocks.DIRT.defaultBlockState(),18);recorder.serverTick(level.getServer());
        level.setBlock(pos,Blocks.GOLD_BLOCK.defaultBlockState(),18);
        var outside=pos.east(8);level.setBlock(outside,Blocks.DIRT.defaultBlockState(),18);
        var pig=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG,10,2,2);
        h.assertTrue(LocalRewind.start(player,new RewindScope(level.dimension(),Vec3.atCenterOf(pos),3),2,()->{}),"Local playback must start");
        h.assertTrue(!recorder.getTimelineBuffer().isRewinding() && recorder.isRecording(),"Local rewind must not pause the whole recorder");
        int age=pig.tickCount;level.tickNonPassenger(pig);
        h.assertTrue(pig.tickCount>age,"Outside entities must continue ticking");
        LocalRewind.tick(level.getServer());
        h.assertTrue(level.getBlockState(pos).is(Blocks.DIRT),"First local playback step must restore dirt");
        recorder.serverTick(level.getServer());level.setBlock(outside,Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
        LocalRewind.tick(level.getServer());recorder.finishFrame(level.getServer());
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE) && !LocalRewind.isActive(player.getUUID()),"Final local playback step must finish cleanly");
        h.assertTrue(level.getBlockState(outside).is(Blocks.DIAMOND_BLOCK),"Outside mutations must not rewind");
        h.assertTrue(recorder.getTimelineBuffer().getFramesForRewind(20).stream().flatMap(f->f.getBlockDeltas().stream()).anyMatch(d->d.packedPos()==outside.asLong() && d.newState().is(Blocks.DIAMOND_BLOCK)),"Outside changes during playback must still be recorded");
        player.discard();pig.discard();recorder.clearTrackingData();recorder.getTimelineBuffer().clear();h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="bubble_controls")
    public void scopeSettingAndWatchKeyCancelLocalPlayback(GameTestHelper h) throws Exception {
        var player=RewindRuneTests.survivalPlayer(h);var level=h.getLevel();
        var watch=new net.minecraft.world.item.ItemStack(com.timestop.item.ModItems.CREATIVE_WATCH.get());
        com.timestop.item.AbstractWatchItem.setGlobalScope(watch,true);
        player.getInventory().setItem(0,watch);
        var previous=TimeStopSavedData.get().getWatchScope();
        var recorder=TickRecorder.getInstance();
        try {
            TimeStopSavedData.get().setWatchScope(TimeStopSavedData.WatchScope.SPHERE);
            var scope=RewindScope.forPlayer(player);
            h.assertTrue(scope!=null,"Server bubble setting must override a globally configured watch");
            TimeStopSavedData.get().setWatchScope(TimeStopSavedData.WatchScope.GLOBAL);
            h.assertTrue(RewindScope.forPlayer(player)==null,"Explicit global mode must remain available");
            TimeStopSavedData.get().setWatchScope(TimeStopSavedData.WatchScope.SPHERE);
            recorder.clearTrackingData();recorder.getTimelineBuffer().clear();recorder.serverTick(level.getServer());
            h.assertTrue(LocalRewind.start(player,scope,5,()->{}),"Local rewind must start");
            new com.timestop.network.ToggleTimeStopPacket().handle(player);
            h.assertTrue(!LocalRewind.isActive(player.getUUID()),"Watch key must cancel local playback");
            h.assertTrue(LocalRewind.start(player,scope,5,()->{}),"Playback must be restartable after cancellation");
            var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
            com.timestop.command.TimeStopCommand.register(dispatcher);
            dispatcher.execute("timestop rewind clear",level.getServer().createCommandSourceStack().withPermission(4));
            h.assertTrue(!LocalRewind.hasActive() && recorder.getTimelineBuffer().getFrameCount()==0,"Clearing history must cancel captured local playback too");
        } finally {
            LocalRewind.cancel(player.getUUID());TimeStopSavedData.get().setWatchScope(previous);
            recorder.clearTrackingData();recorder.getTimelineBuffer().clear();player.discard();
        }
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="rewind_buffer_command")
    public void bufferCommandAcceptsEverySecondAndRetainsNewestHistory(GameTestHelper h) throws Exception {
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        com.timestop.command.TimeStopCommand.register(dispatcher);
        var source=h.getLevel().getServer().createCommandSourceStack().withPermission(4);
        var recorder=TickRecorder.getInstance();var buffer=recorder.getTimelineBuffer();
        int old=TimeStopConfig.COMMON.rewindHistorySeconds.get();
        try {
            buffer.clear();buffer.resizeSeconds(60);
            for(int i=0;i<1200;i++)buffer.pushFrame(new TickFrame(i));
            for(int seconds=60;seconds>=1;seconds--) {
                dispatcher.execute("timestop rewind buffer "+seconds,source);
                h.assertTrue(buffer.getCapacity()==seconds*20 && buffer.getFrameCount()==seconds*20,"Every whole-second duration must resize immediately");
                h.assertTrue(buffer.getFramesForRewind(1).get(0).getGameTime()==1199,"Resize must keep newest history");
            }
            dispatcher.execute("timestop rewind buffer 60",source);
            h.assertTrue(buffer.getCapacity()==1200 && buffer.getFrameCount()==20,"Growing must preserve available history without inventing older frames");
            for(int invalid:new int[]{0,61}) {
                boolean rejected=false;try{dispatcher.execute("timestop rewind buffer "+invalid,source);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){rejected=true;}
                h.assertTrue(rejected,"Out of range durations must be rejected");
            }
        } finally {buffer.clear();buffer.resizeSeconds(Math.max(1,Math.min(60,old)));TimeStopConfig.COMMON.rewindHistorySeconds.set(old);TimeStopConfig.save();recorder.clearTrackingData();}
        h.succeed();
    }
}
