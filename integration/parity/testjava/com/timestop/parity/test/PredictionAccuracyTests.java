package com.timestop.parity.test;

import com.timestop.prediction.*;
import com.timestop.core.*;
import com.timestop.item.WatchTier;
import com.timestop.network.SnatchProjectilePacket;
import com.timestop.combat.TemporalInteractionEvents;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class PredictionAccuracyTests {
    private static final String EMPTY_STRUCTURE="parity_empty";
    private static Vec3 center(GameTestHelper h) {
        var b=h.absolutePos(new BlockPos(8,8,8));var p=new Vec3(b.getX()+.5,220,b.getZ()+.5);
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) h.getLevel().getChunkAt(BlockPos.containing(p).offset(x*16,0,z*16));
        return p;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,Vec3 p) {
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"catch-test"),net.minecraft.server.level.ClientInformation.createDefault());
        
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {}
        };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(p);return player;
    }
    private static void field(GameTestHelper h,Vec3 p,UUID owner) {
        TemporalBubbleManager.putStationaryBubble(h.getLevel(),new TemporalBubble(UUID.randomUUID(),owner,h.getLevel().dimension(),p,16,TimeMode.TIME_PREDICTION,10000,WatchTier.NETHERITE,null,0,Set.of()).stationary(true),null);
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_catching")
    public void predictionCatchWorksOnceAndPreservesTippedArrow(GameTestHelper h) {
        var pos=center(h);var player=player(h,pos);var arrow=new Arrow(h.getLevel(),pos.x,pos.y+1.2,pos.z+2,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);
        arrow=PredictionTestPotions.arrow(arrow,PredictionTestPotions.setPotion(new net.minecraft.world.item.ItemStack(Items.TIPPED_ARROW),net.minecraft.world.item.alchemy.Potions.POISON));
        arrow.setDeltaMovement(0,0,-.2);h.getLevel().addFreshEntity(arrow);
        try {
            field(h,pos,player.getUUID());new SnatchProjectilePacket(arrow.getId()).handle(player);
            h.assertTrue(arrow.isRemoved(),"Prediction's catch packet must remove the in-flight arrow");
            new SnatchProjectilePacket(arrow.getId()).handle(player);
            TemporalInteractionEvents.onEntityInteract(player,arrow,InteractionHand.MAIN_HAND);
            int count=0;for(var item:player.getInventory().items) if(item.is(Items.TIPPED_ARROW)) {
                count+=item.getCount();h.assertTrue(PredictionTestPotions.getPotion(item)==net.minecraft.world.item.alchemy.Potions.POISON,"Catch retains potion data");
            }
            h.assertTrue(count==1,"Repeated catch routes cannot duplicate an arrow");
            var next=new Arrow(h.getLevel(),pos.x,pos.y+1.2,pos.z+2,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);next.setDeltaMovement(0,0,-.1);h.getLevel().addFreshEntity(next);
            h.assertTrue(TemporalInteractionEvents.onEntityInteract(player,next,InteractionHand.MAIN_HAND).consumesAction() && next.isRemoved(),"Native use-entity route shares prediction eligibility");
            com.timestop.TimeStopMod.LOGGER.info("PREDICTION_CATCH_PACKET_PASS");
        } finally {arrow.discard();TemporalBubbleManager.stopAllBubbles(h.getLevel());h.getLevel().getServer().getPlayerList().remove(player);player.discard();}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_catching")
    public void catchRejectsRemoteFieldsCoverDistanceAndCancelledPrediction(GameTestHelper h) {
        var pos=center(h);var player=player(h,pos);var arrow=new Arrow(h.getLevel(),pos.x,pos.y+1.2,pos.z+2,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);
        arrow.setDeltaMovement(0,0,-.1);h.getLevel().addFreshEntity(arrow);var wall=BlockPos.containing(pos.add(0,1,1));
        try {
            field(h,pos.add(100,0,0),player.getUUID());new SnatchProjectilePacket(arrow.getId()).handle(player);
            h.assertTrue(!arrow.isRemoved(),"An unrelated remote field cannot authorize catching");
            TemporalBubbleManager.stopAllBubbles(h.getLevel());field(h,pos,player.getUUID());
            arrow.setPos(pos.add(0,1.2,6));new SnatchProjectilePacket(arrow.getId()).handle(player);h.assertTrue(!arrow.isRemoved(),"Catch obeys reach");
            arrow.setPos(pos.add(0,1.2,2));h.getLevel().setBlock(wall,Blocks.STONE.defaultBlockState(),3);
            new SnatchProjectilePacket(arrow.getId()).handle(player);h.assertTrue(!arrow.isRemoved(),"Catch cannot reach through solid cover");
            h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);TemporalBubbleManager.stopAllBubbles(h.getLevel());
            new SnatchProjectilePacket(arrow.getId()).handle(player);h.assertTrue(!arrow.isRemoved(),"Late catch after cancellation is rejected");
        } finally {h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);arrow.discard();TemporalBubbleManager.stopAllBubbles(h.getLevel());h.getLevel().getServer().getPlayerList().remove(player);player.discard();}
        h.succeed();
    }
    private static PredictionMotion walking(Vec3 p,Vec3 velocity,List<Vec3> route) {
        return new PredictionMotion(999,-1,PredictionMotion.Kind.LIVING,p,velocity,Vec3.ZERO,.08,.98,.8,.6F,1.8F,true,false,false,0,1).withRoute(route,velocity.horizontalDistance());
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_accuracy")
    public void bodyCollisionStepsSlabsAndSlidesAlongWallInsteadOfPassingThrough(GameTestHelper h) {
        var p=center(h);var b=BlockPos.containing(p);var changed=new ArrayList<BlockPos>();
        try {
            for(int x=-2;x<=7;x++) for(int z=-2;z<=7;z++) {var v=b.offset(x,-1,z);changed.add(v);h.getLevel().setBlock(v,Blocks.STONE.defaultBlockState(),3);}
            var slab=b.offset(1,0,0);changed.add(slab);h.getLevel().setBlock(slab,Blocks.STONE_SLAB.defaultBlockState(),3);
            var seed=walking(p,new Vec3(.1,0,0),List.of(p.add(4,0,0)));var path=PredictionEngine.forecast(h.getLevel(),seed,v -> 1,List.of());
            h.assertTrue(path.points().stream().anyMatch(v -> Math.abs(v.position().y-p.y-.5)<.001),"Step forecast lands on actual slab top");
            h.assertTrue(Math.abs(path.end().position().y-p.y)<.001 && path.end().position().x>p.x+3.9,"Grounded route settles after stepping off a slab");
            for(int z=0;z<=5;z++) for(int y=0;y<=2;y++) {var v=b.offset(1,y,z);changed.add(v);h.getLevel().setBlock(v,Blocks.STONE.defaultBlockState(),3);}
            var slide=PredictionEngine.forecast(h.getLevel(),walking(p,new Vec3(.1,0,.1),List.of(p.add(4,0,4))),v -> 1,List.of());
            h.assertTrue(slide.end().position().x<=b.getX()+1-.3+.001 && slide.end().position().z>p.z+2,"Body stops at its edge and slides along cover");
        } finally {for(var v:changed) h.getLevel().setBlock(v,Blocks.AIR.defaultBlockState(),3);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_accuracy")
    public void risingBodyFallsNativelyAndArrowHitsItsFutureBodyWithoutMutation(GameTestHelper h) {
        var p=center(h);var cow=new net.minecraft.world.entity.animal.Cow(EntityType.COW,h.getLevel()) { {goalSelector.removeAllGoals(g -> true);targetSelector.removeAllGoals(g -> true);} };cow.setPos(p);cow.setOnGround(true);cow.setDeltaMovement(0,.42,0);h.getLevel().addFreshEntity(cow);
        var floor=BlockPos.containing(p).below();h.getLevel().setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        try {
            var before=cow.saveWithoutId(new CompoundTag());var seed=PredictionServer.capture(cow);
            var path=PredictionEngine.forecast(h.getLevel(),seed,v -> 1,List.of());
            h.assertTrue(path.positionAt(.2).y>p.y+.8 && Math.abs(path.positionAt(1.5).y-p.y)<.001,"Positive jump velocity survives grounded seed and lands on terrain");
            var shot=new PredictionMotion(888,-1,PredictionMotion.Kind.ARROW,p.add(-3,1.8,0),new Vec3(.3,0,0),Vec3.ZERO,0,1,1,.5F,.5F,false,true,false,0,1);
            var hit=PredictionEngine.forecast(h.getLevel(),shot,v -> 1,Map.of(cow.getId(),path));
            h.assertTrue(hit.hitEntity()==cow.getId() && hit.end().seconds()<.6,"Arrow intersects the airborne predicted body");
            h.assertTrue(before.equals(cow.saveWithoutId(new CompoundTag())),"Body/arrow forecasts leave live state untouched");
            for(int tick=1;tick<=24;tick++) {
                h.getLevel().tickNonPassenger(cow);
                if(tick==4 || tick==10 || tick==24) h.assertTrue(cow.position().distanceTo(path.positionAt(tick*.05))<.015,
                        "Airborne forecast matches native living travel and landing, tick="+tick+" error="+cow.position().distanceTo(path.positionAt(tick*.05)));
            }
            double oldRate=com.timestop.config.TimeStopConfig.COMMON.predictionRate.get();
            try {
                com.timestop.config.TimeStopConfig.COMMON.predictionRate.set(.25);field(h,p,UUID.randomUUID());
                cow.setPos(p);cow.setOnGround(true);cow.setDeltaMovement(0,.42,0);
                var slowed=PredictionEngine.forecast(h.getLevel(),PredictionServer.capture(cow),v -> .25,List.of());
                for(int tick=1;tick<=80;tick++) {
                    h.getLevel().tickNonPassenger(cow);
                    if(tick==8 || tick==32 || tick==80) h.assertTrue(cow.position().distanceTo(slowed.positionAt(tick*.05))<.03,
                            "Slowed jump/landing follows the native continuous travel clock, tick="+tick+" error="+cow.position().distanceTo(slowed.positionAt(tick*.05)));
                }
            } finally {com.timestop.config.TimeStopConfig.COMMON.predictionRate.set(oldRate);TemporalBubbleManager.stopAllBubbles(h.getLevel());}
            com.timestop.TimeStopMod.LOGGER.info("PREDICTION_BODY_COLLISION_PASS");
        } finally {cow.discard();h.getLevel().setBlock(floor,Blocks.AIR.defaultBlockState(),3);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_accuracy")
    public void distantPathCompactionPreservesSharpTurnsAndContactTime(GameTestHelper h) {
        var p=center(h);var seed=walking(p,new Vec3(.1,0,0),List.of());
        var points=new ArrayList<PredictionEngine.Point>();
        for(int i=0;i<=1000;i++) points.add(new PredictionEngine.Point(p.add(Math.min(i,137)*.05,0,Math.max(0,i-137)*.02),i*.05));
        var original=new PredictionEngine.Path(seed,points,true,77,new Vec3(0,1,0));var compact=original.compact();
        h.assertTrue(compact.points().size()<=193 && compact.points().contains(points.get(137)),"Bounded network flight retains sharp turn anchor");
        for(int i=0;i<=1000;i++) h.assertTrue(compact.positionAt(i*.05).distanceTo(points.get(i).position())<.011,"Compaction preserves time-dependent positions");
        var trimmed=compact.trimFrom(p.add(5,0,0));
        h.assertTrue(Math.abs(trimmed.points().get(0).seconds()-5)<.0001 && trimmed.positionAt(5.5).distanceTo(p.add(5.5,0,0))<.0001,
                "Advancing an arrow origin interpolates the actual segment time for future silhouettes");
        h.assertTrue(trimmed.end().equals(original.end()) && trimmed.hitEntity()==77 && trimmed.normal().equals(original.normal()),"Trimmed contact remains anchored to its original time");
        h.assertTrue(compact.end().equals(original.end()) && compact.hitEntity()==77 && compact.normal().equals(original.normal()),"Contact time, target, normal and world anchor stay exact");
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_accuracy")
    public void copiedStepHeightSurvivesBuildersWireAndRejectsInvalidValues(GameTestHelper h) {
        var p=center(h);var seed=walking(p,new Vec3(.1,0,0),List.of()).withStepHeight(1.25F).withJumpBoost(.2F);
        var copied=seed.at(p.add(1,0,0),seed.velocity()).withRoute(List.of(p.add(2,0,0)),.1).withEffects(PredictionEffects.NONE);
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            copied.write(buf);var decoded=PredictionMotion.read(buf);
            h.assertTrue(decoded.stepHeight()==1.25F && decoded.jumpBoost()==.2F && decoded.finite(),"Actual copied step height survives builders and network");
            h.assertTrue(!seed.withStepHeight(-1).finite() && !seed.withStepHeight(Float.NaN).finite()
                    && !seed.withStepHeight(32).finite(),"Malformed step heights cannot reach terrain forecasts");
        } finally {buf.release();}
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_accuracy")
    public void upperNavigationNodeForecastsJumpOntoAFullBlock(GameTestHelper h) {
        var p=center(h);var b=BlockPos.containing(p);var changed=new ArrayList<BlockPos>();
        try {
            for(int x=-2;x<=5;x++) for(int z=-1;z<=1;z++) {
                var floor=b.offset(x,-1,z);changed.add(floor);h.getLevel().setBlock(floor,Blocks.STONE.defaultBlockState(),3);
                if(x>=1) {var top=b.offset(x,0,z);changed.add(top);h.getLevel().setBlock(top,Blocks.STONE.defaultBlockState(),3);}
            }
            var seed=walking(p,new Vec3(.1,-.0784,0),List.of(p.add(1,1,0),p.add(4,1,0)));
            var path=PredictionEngine.forecast(h.getLevel(),seed,v -> 1,List.of());
            h.assertTrue(path.points().stream().anyMatch(v -> v.position().y>p.y+1.05),"Higher native waypoint forecasts an actual jump arc");
            h.assertTrue(path.end().position().distanceTo(p.add(4,1,0))<.01,"Jump lands on solid block top and continues the route");
        } finally {for(var v:changed) h.getLevel().setBlock(v,Blocks.AIR.defaultBlockState(),3);}
        h.succeed();
    }

}
