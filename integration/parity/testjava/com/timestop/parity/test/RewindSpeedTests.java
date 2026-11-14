package com.timestop.parity.test;
import com.timestop.core.*;
import com.timestop.core.rewind.*;
import com.timestop.core.rewind.data.*;
import com.timestop.config.TimeStopConfig;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
public class RewindSpeedTests {
    private static void seed(GameTestHelper h,BlockPos pos) {
        var recorder=TickRecorder.getInstance();recorder.clearTrackingData();var buffer=recorder.getTimelineBuffer();
        buffer.setRewinding(false);buffer.setRecording(true);buffer.setFrozen(false);buffer.clear();
        var states=new Block[]{Blocks.STONE,Blocks.DIRT,Blocks.GOLD_BLOCK,Blocks.DIAMOND_BLOCK,Blocks.EMERALD_BLOCK,Blocks.IRON_BLOCK};
        h.getLevel().setBlock(pos,states[5].defaultBlockState(),18);recorder.clearTrackingData();
        for(int i=0;i<5;i++) {
            var frame=new TickFrame(i);frame.captureEnvironment(h.getLevel());
            frame.addBlockDelta(BlockDelta.create(h.getLevel().dimension(),pos,states[i].defaultBlockState(),states[i+1].defaultBlockState(),null,null));buffer.pushFrame(frame);
        }
    }
    public void globalPlaybackHonorsFractionalSpeedAndRequestedHistory(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));double old=TimeStopConfig.COMMON.rewindSpeed.get();
        var recorder=TickRecorder.getInstance();
        try {
            seed(h,pos);TimeStopConfig.COMMON.rewindSpeed.set(.5);TimeStopManager.startContinuousRewind(level,null,3,null);
            TimeStopManager.serverTick();h.assertTrue(level.getBlockState(pos).is(Blocks.IRON_BLOCK),"Half speed must accumulate a fractional frame");
            TimeStopManager.serverTick();h.assertTrue(level.getBlockState(pos).is(Blocks.EMERALD_BLOCK),"Second half step consumes exactly one frame");
            TimeStopConfig.COMMON.rewindSpeed.set(20.0);TimeStopManager.serverTick();
            h.assertTrue(level.getBlockState(pos).is(Blocks.GOLD_BLOCK) && !TimeStopManager.isGlobalTimeStopActive(),"Fast rewind must stop at requested history depth");
            h.assertTrue(recorder.getTimelineBuffer().getFrameCount()==2,"Older unrequested history must remain available");
        } finally {TimeStopManager.resumeTime(level);TimeStopConfig.COMMON.rewindSpeed.set(old);recorder.clearTrackingData();recorder.getTimelineBuffer().clear();}
        h.succeed();
    }
    public void localPlaybackConsumesMultipleFramesAndClearsCreditOnCancel(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));var owner=RewindRuneTests.survivalPlayer(h);
        owner.setPos(Vec3.atCenterOf(pos));var scope=new RewindScope(level.dimension(),Vec3.atCenterOf(pos),3);
        double old=TimeStopConfig.COMMON.rewindSpeed.get();var recorder=TickRecorder.getInstance();
        try {
            seed(h,pos);TimeStopConfig.COMMON.rewindSpeed.set(.5);
            h.assertTrue(LocalRewind.start(owner,scope,3,()->{}),"Local playback must start");LocalRewind.tick(level.getServer());LocalRewind.cancel(owner.getUUID());
            h.assertTrue(LocalRewind.start(owner,scope,3,()->{}),"Cancelled local playback must restart");LocalRewind.tick(level.getServer());
            h.assertTrue(level.getBlockState(pos).is(Blocks.IRON_BLOCK),"Cancellation must clear fractional playback credit");
            LocalRewind.tick(level.getServer());h.assertTrue(level.getBlockState(pos).is(Blocks.EMERALD_BLOCK),"Local fractional playback consumes one frame");
            TimeStopConfig.COMMON.rewindSpeed.set(20.0);LocalRewind.tick(level.getServer());
            h.assertTrue(level.getBlockState(pos).is(Blocks.GOLD_BLOCK) && !LocalRewind.isActive(owner.getUUID()),"Fast local rewind preserves its requested scope and depth");
        } finally {LocalRewind.cancel(owner.getUUID());owner.discard();TimeStopConfig.COMMON.rewindSpeed.set(old);recorder.clearTrackingData();recorder.getTimelineBuffer().clear();}
        h.succeed();
    }
}
