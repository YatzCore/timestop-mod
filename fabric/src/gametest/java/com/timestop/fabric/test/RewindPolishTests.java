package com.timestop.fabric.test;
import com.timestop.core.rewind.*;
import com.timestop.core.rewind.data.*;
import com.timestop.item.*;
import com.timestop.core.TimeMode;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public class RewindPolishTests implements FabricGameTest {
 @GameTest(template=EMPTY_STRUCTURE,batch="piston_polish")
 public void normalPistonRewindsWithoutDuplicateBlocks(GameTestHelper h){ pistonCycle(h,false); }
 @GameTest(template=EMPTY_STRUCTURE,batch="piston_polish_sticky")
 public void stickyPistonRewindsWithoutDuplicateBlocks(GameTestHelper h){ pistonCycle(h,true); }
 private static void pistonCycle(GameTestHelper h,boolean sticky){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));
  var block=sticky?Blocks.STICKY_PISTON:Blocks.PISTON;
  var base=block.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.EAST);
  level.setBlock(pos,base,18);level.setBlock(pos.east(),Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
  var recorder=TickRecorder.getInstance();recorder.clearTrackingData();recorder.getTimelineBuffer().clear();
  recorder.serverTick(level.getServer());
  level.setBlock(pos.west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),18);
  h.assertTrue(base.triggerEvent(level,pos,0,Direction.EAST.get3DDataValue()),"Piston must extend");
  recorder.serverTick(level.getServer());
  for(int tick=0;tick<3;tick++){
   for(int offset=1;offset<=2;offset++) {
    var p=pos.relative(Direction.EAST,offset);
    if(level.getBlockEntity(p) instanceof PistonMovingBlockEntity moving) PistonMovingBlockEntity.tick(level,p,level.getBlockState(p),moving);
   }
   recorder.serverTick(level.getServer());
  }
  h.assertTrue(level.getBlockState(pos.east(2)).is(Blocks.DIAMOND_BLOCK),"Piston must actually move the diamond block");
  // Queue a forward event which must not survive rewinding its affected base.
  level.blockEvent(pos,block,1,Direction.EAST.get3DDataValue());
  var result=RewindExecutor.execute(level.getServer(),2,null,true);
  h.assertTrue(result.success(),"Piston rewind must succeed");
  h.assertTrue(level.getBlockState(pos).equals(base),"Original piston base must return unextended");
  h.assertTrue(level.getBlockState(pos.east()).is(Blocks.DIAMOND_BLOCK),"Original diamond block must return");
  h.assertTrue(level.getBlockState(pos.east(2)).isAir(),"Destination must be empty, not a duplicate");
  h.assertTrue(level.getBlockEntity(pos.east())==null && level.getBlockEntity(pos.east(2))==null,"No stale moving block entities");
  h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3)).isEmpty(),"Removing a piston head during rollback must not drop its base");
  recorder.clearTrackingData();recorder.getTimelineBuffer().clear();
  h.runAfterDelay(5,()->{
   h.assertTrue(level.getBlockState(pos).equals(base) && level.getBlockState(pos.east()).is(Blocks.DIAMOND_BLOCK) && level.getBlockState(pos.east(2)).isAir(),"Stale queued piston events must not change restored blocks");
   h.succeed();
  });
 }
 @GameTest(template=EMPTY_STRUCTURE,batch="piston_midmotion")
 public void movingPistonBlockEntityIsRecreatedAndCompletesOnce(GameTestHelper h){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));
  var state=Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING,Direction.EAST);
  var moving=new PistonMovingBlockEntity(pos,state,Blocks.DIAMOND_BLOCK.defaultBlockState(),Direction.EAST,true,false);
  var tag=moving.saveWithFullMetadata(h.getLevel().registryAccess());
  level.setBlock(pos,Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
  var frame=new TickFrame(0);frame.captureEnvironment(level);
  frame.addBlockDelta(BlockDelta.create(level.dimension(),pos,state,Blocks.DIAMOND_BLOCK.defaultBlockState(),tag,null));
  RewindExecutor.applyPlan(level.getServer(),RewindExecutor.buildPlan(List.of(frame)),true);
  h.assertTrue(level.getBlockEntity(pos) instanceof PistonMovingBlockEntity,"Restoring MOVING_PISTON must recreate its block entity");
  for(int i=0;i<4;i++) if(level.getBlockEntity(pos) instanceof PistonMovingBlockEntity be) PistonMovingBlockEntity.tick(level,pos,level.getBlockState(pos),be);
  h.assertTrue(level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK) && level.getBlockEntity(pos)==null,"Restored movement must finish as exactly one diamond block");
  h.succeed();
 }
 @GameTest(template=EMPTY_STRUCTURE,batch="piston_unfinished")
 public void unfinishedMovementDoesNotFinishForwardDuringRollback(GameTestHelper h){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));
  var base=Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.EAST);
  level.setBlock(pos,base,18);level.setBlock(pos.east(),Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
  var recorder=TickRecorder.getInstance();recorder.clearTrackingData();recorder.getTimelineBuffer().clear();
  recorder.serverTick(level.getServer());
  level.setBlock(pos.west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),18);
  base.triggerEvent(level,pos,0,Direction.EAST.get3DDataValue());
  h.assertTrue(level.getBlockEntity(pos.east(2)) instanceof PistonMovingBlockEntity,"Test must rewind during active movement");
  RewindExecutor.execute(level.getServer(),1,null,true);
  h.assertTrue(level.getBlockState(pos).equals(base) && level.getBlockState(pos.east()).is(Blocks.DIAMOND_BLOCK)
   && level.getBlockState(pos.east(2)).isAir(),"Moving-piston onRemove must not write its moved block back into the destination");
  recorder.clearTrackingData();recorder.getTimelineBuffer().clear();h.succeed();
 }
 @GameTest(template=EMPTY_STRUCTURE,batch="piston_retraction")
 public void stickyRetractionRewindsToExtendedAssembly(GameTestHelper h){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));
  var base=Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.EAST).setValue(PistonBaseBlock.EXTENDED,true);
  var head=Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING,Direction.EAST)
    .setValue(PistonHeadBlock.TYPE,net.minecraft.world.level.block.state.properties.PistonType.STICKY);
  level.setBlock(pos.west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),18);
  level.setBlock(pos,base,18);level.setBlock(pos.east(),head,18);level.setBlock(pos.east(2),Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
  var recorder=TickRecorder.getInstance();recorder.clearTrackingData();recorder.getTimelineBuffer().clear();
  recorder.serverTick(level.getServer());
  level.setBlock(pos.west(),Blocks.AIR.defaultBlockState(),18);
  base.triggerEvent(level,pos,1,Direction.EAST.get3DDataValue());
  h.assertTrue(level.getBlockEntity(pos) instanceof PistonMovingBlockEntity,"Test must retract the base");
  RewindExecutor.execute(level.getServer(),1,null,true);
  h.assertTrue(level.getBlockState(pos).equals(base) && level.getBlockState(pos.east()).equals(head)
   && level.getBlockState(pos.east(2)).is(Blocks.DIAMOND_BLOCK),"Rewinding retraction must restore one extended assembly and one moved block");
  h.assertTrue(level.getBlockEntity(pos)==null && level.getBlockEntity(pos.east())==null,"No retracting block entity may survive");
  h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3)).isEmpty(),"Retraction rollback must not drop duplicate pistons");
  recorder.clearTrackingData();recorder.getTimelineBuffer().clear();h.succeed();
 }
 @GameTest(template=EMPTY_STRUCTURE,batch="watch_preferences")
 public void watchPreferencesFollowIdentityWithoutPreservingRunes(GameTestHelper h){
  var player=h.makeMockServerPlayerInLevel();
  player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player, net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)){
   @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
  };
  var first=new ItemStack(ModItems.CREATIVE_WATCH.get());var second=new ItemStack(ModItems.CREATIVE_WATCH.get());
  AbstractWatchItem.setMode(first,TimeMode.TIME_STOP);AbstractWatchItem.setMode(second,TimeMode.TIME_STOP);
  player.getInventory().setItem(0,first);player.getInventory().setItem(1,second);
  var before=PlayerDelta.fromPlayer(player);
  AbstractWatchItem.setMode(first,TimeMode.REWIND);AbstractWatchItem.setGlobalScope(first,false);
  AbstractWatchItem.setMode(second,TimeMode.SLOW_MOTION);AbstractWatchItem.setGlobalScope(second,true);
  AbstractWatchItem.setSocketedRune(first,new ItemStack(ModItems.RUNE_DEFLECTION.get()));
  player.getInventory().setItem(0,second);player.getInventory().setItem(1,ItemStack.EMPTY);
  player.containerMenu.setCarried(first);
  before.restoreTo(player,true);
  var restored=player.getInventory().getItem(0);var other=player.getInventory().getItem(1);
  h.assertTrue(AbstractWatchItem.getMode(restored)==TimeMode.REWIND && !AbstractWatchItem.isGlobalScope(restored),"Current mode and scope must follow the watch from cursor to historical slot");
  h.assertTrue(AbstractWatchItem.getMode(other)==TimeMode.SLOW_MOTION && AbstractWatchItem.isGlobalScope(other),"Two same-tier watches must retain separate preferences");
  h.assertTrue(AbstractWatchItem.getSocketedRune(restored).isEmpty(),"A newly socketed rune must still rewind, not duplicate");
  before.restoreTo(player,true);
  h.assertTrue(AbstractWatchItem.getMode(player.getInventory().getItem(0))==TimeMode.REWIND,"Repeated playback frames must keep selected mode");
  player.discard();h.succeed();
 }
}
