package com.timestop.fabric.test;

import com.timestop.prediction.*;
import com.timestop.network.PredictionSnapshotPacket;
import com.timestop.core.*;
import com.timestop.item.WatchTier;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class PredictionOutcomeTests implements FabricGameTest {
    private static Vec3 center(GameTestHelper h) {
        var p=h.absolutePos(new BlockPos(4,4,4));var v=new Vec3(p.getX()+.5,210,p.getZ()+.5);
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) h.getLevel().getChunkAt(BlockPos.containing(v).offset(x*16,0,z*16));
        return v;
    }
    private static PredictionEngine.Path impact(GameTestHelper h,Vec3 pos,ItemStack item,int hit) {
        var m=new PredictionMotion(999,-1,PredictionMotion.Kind.THROWABLE,pos.add(0,1,0),new Vec3(0,-.5,0),Vec3.ZERO,.05,.99,.8,.25F,.25F,false,false,false,0,1).withEffects(PredictionEffects.item(item));
        return new PredictionEngine.Path(m,List.of(new PredictionEngine.Point(m.position(),0),new PredictionEngine.Point(pos,1)),true,hit,new Vec3(0,1,0));
    }
    private static Map<Integer,PredictionEngine.Path> targets(GameTestHelper h,net.minecraft.world.entity.LivingEntity... entities) {
        var result=new HashMap<Integer,PredictionEngine.Path>();
        for(var e:entities) {var m=PredictionServer.capture(e);result.put(e.getId(),PredictionEngine.forecast(h.getLevel(),m,p -> 1,Map.of()));}
        return result;
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void splashHonorsDistanceDirectHitsAndDoesNotInventWallOcclusion(GameTestHelper h) {
        Vec3 pos=center(h);
        var direct=EntityType.COW.create(h.getLevel());direct.setNoAi(true);direct.setNoGravity(true);direct.setPos(pos.add(3,0,0));h.getLevel().addFreshEntity(direct);
        var other=EntityType.COW.create(h.getLevel());other.setNoAi(true);other.setNoGravity(true);other.setPos(pos.add(0,0,2));h.getLevel().addFreshEntity(other);
        var outside=EntityType.COW.create(h.getLevel());outside.setNoAi(true);outside.setNoGravity(true);outside.setPos(pos.add(0,0,4));h.getLevel().addFreshEntity(outside);
        var wall=BlockPos.containing(pos).offset(0,0,1);h.getLevel().setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        try {
            var item=PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION),Potions.POISON);var before=item.save(new CompoundTag());
            var event=PredictionOutcomes.event(h.getLevel(),impact(h,pos,item,direct.getId()),targets(h,direct,other,outside));
            h.assertTrue(event.radius()==4 && event.affected().size()==2,"Native splash distance is strictly less than four blocks and ignores walls");
            h.assertTrue(event.affected().stream().anyMatch(a -> a.entityId()==direct.getId() && a.strength()==1),"Direct contact uses full splash intensity");
            h.assertTrue(event.affected().stream().anyMatch(a -> a.entityId()==other.getId() && Math.abs(a.strength()-.5)<.001),"Other targets use native distance falloff");
            h.assertTrue(before.equals(item.save(new CompoundTag())),"Effect capture cannot alter the held potion");
        } finally {direct.discard();other.discard();outside.discard();h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void splashExcludesShortEffectsVerticalMissesAndPotionImmuneTargets(GameTestHelper h) {
        var pos=center(h);var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(pos.add(3,0,0));
        var tall=EntityType.COW.create(h.getLevel());tall.setNoAi(true);tall.setNoGravity(true);tall.setPos(pos.add(0,3,0));
        var spider=EntityType.SPIDER.create(h.getLevel());spider.setNoAi(true);spider.setNoGravity(true);spider.setPos(pos.add(0,0,1));
        h.getLevel().addFreshEntity(cow);h.getLevel().addFreshEntity(tall);h.getLevel().addFreshEntity(spider);
        try {
            var item=new ItemStack(Items.SPLASH_POTION);
            PotionUtils.setCustomEffects(item,List.of(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,40)));
            var event=PredictionOutcomes.event(h.getLevel(),impact(h,pos,item,-1),targets(h,cow,tall,spider));
            h.assertTrue(event.affected().stream().noneMatch(a -> a.entityId()==cow.getId() || a.entityId()==tall.getId()),"Short falloff durations and targets above the native search box are excluded");
            var poison=PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION),Potions.POISON);
            var poisoning=PredictionOutcomes.event(h.getLevel(),impact(h,pos,poison,-1),targets(h,cow,tall,spider));
            h.assertTrue(poisoning.affected().stream().noneMatch(a -> a.entityId()==spider.getId()),"Native spider poison immunity is retained without application callbacks");
            h.assertTrue(PredictionEffects.item(new ItemStack(Items.SPLASH_POTION)).kind()==PredictionEffects.Kind.NONE,"Empty potions do not invent effects");
        } finally {cow.discard();tall.discard();spider.discard();}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void lingeringShowsInitialCloudWithoutInventingFutureEntrants(GameTestHelper h) {
        var pos=center(h);var item=PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION),Potions.HARMING);
        var event=PredictionOutcomes.event(h.getLevel(),impact(h,pos,item,-1),Map.of());
        h.assertTrue(event.kind()==PredictionEffects.Kind.LINGERING && event.radius()==3 && event.affected().isEmpty(),"Lingering preview uses initial three-block cloud only");
        h.assertTrue(PredictionEffects.item(new ItemStack(Items.LINGERING_POTION)).kind()==PredictionEffects.Kind.NONE,"Empty lingering potions do not create a cloud");
        var water=PredictionEffects.item(PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION),Potions.WATER));
        h.assertTrue(water.kind()==PredictionEffects.Kind.WATER,"Lingering water uses native water splash, not a cloud");
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void explosionExposureSamplesFutureGeometryAndLeavesWorldUntouched(GameTestHelper h) {
        var pos=center(h);var box=new net.minecraft.world.phys.AABB(pos.add(2,0,-.3),pos.add(2.6,1.8,.3));
        float open=PredictionOutcomes.exposure(h.getLevel(),pos.add(0,1,0),box);
        var wall=BlockPos.containing(pos).offset(1,0,0);
        for(int y=-1;y<4;y++) for(int z=-2;z<3;z++) h.getLevel().setBlock(wall.offset(0,y,z),Blocks.STONE.defaultBlockState(),3);
        try {
            float covered=PredictionOutcomes.exposure(h.getLevel(),pos.add(0,1,0),box);
            h.assertTrue(open>.99 && covered==0,"Future-box exposure accounts for current terrain cover");
            h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"Forecast cannot destroy cover");
        } finally {for(int y=-1;y<4;y++) for(int z=-2;z<3;z++) h.getLevel().setBlock(wall.offset(0,y,z),Blocks.AIR.defaultBlockState(),3);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void tntForecastMatchesNativeCollisionAndFluidSteps(GameTestHelper h) {
        var pos=center(h);var floor=BlockPos.containing(pos).below();
        for(int x=-2;x<=4;x++) for(int z=-2;z<=2;z++) h.getLevel().setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        var tnt=new PrimedTnt(EntityType.TNT,h.getLevel());tnt.setPos(pos.add(0,.2,0));tnt.setDeltaMovement(.12,-.3,0);tnt.setFuse(12);
        try {
            var before=tnt.saveWithoutId(new CompoundTag());var path=PredictionEngine.forecast(h.getLevel(),PredictionServer.capture(tnt),p -> 1,Map.of());
            h.assertTrue(before.equals(tnt.saveWithoutId(new CompoundTag())),"TNT forecasting must not change its live fuse or motion");
            h.assertTrue(path.impact() && Math.abs(path.end().seconds()-.6)<1e-8,"TNT detonation follows native fuse ticks");
            tnt.setFuse(200);for(int i=0;i<12;i++) tnt.tick();
            h.assertTrue(tnt.position().distanceTo(path.end().position())<.00001,"Read-only TNT collision forecast matches native landing/friction");
            var water=BlockPos.containing(pos);h.getLevel().setBlock(water,Blocks.WATER.defaultBlockState(),3);
            tnt.setPos(pos);tnt.setDeltaMovement(0,0,0);tnt.setFuse(8);
            var wet=PredictionEngine.forecast(h.getLevel(),PredictionServer.capture(tnt),p -> 1,Map.of());
            tnt.setFuse(200);for(int i=0;i<8;i++) tnt.tick();
            h.assertTrue(tnt.position().distanceTo(wet.end().position())<.00001,"Water pushing matches the native copied TNT steps");
            h.getLevel().setBlock(water,Blocks.AIR.defaultBlockState(),3);
        } finally {tnt.discard();h.getLevel().setBlock(BlockPos.containing(pos),Blocks.AIR.defaultBlockState(),3);for(int x=-2;x<=4;x++) for(int z=-2;z<=2;z++) h.getLevel().setBlock(floor.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void tntForecastUsesPredictionTickCreditAndRetractsLongFuse(GameTestHelper h) {
        var pos=center(h);var tnt=new PrimedTnt(EntityType.TNT,h.getLevel());tnt.setPos(pos);tnt.setNoGravity(true);tnt.setDeltaMovement(.03,0,0);tnt.setFuse(12);h.getLevel().addFreshEntity(tnt);
        var b=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),pos,40,TimeMode.TIME_PREDICTION,10000,WatchTier.NETHERITE,null,0,Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(),b,null);
        try {
            h.getLevel().tickNonPassenger(tnt); // one skipped native tick leaves fractional credit
            var motion=PredictionServer.capture(tnt);var path=PredictionEngine.forecast(h.getLevel(),motion,p -> PredictionServer.timeRateAt(tnt,p),Map.of());
            h.assertTrue(motion.phase()>0 && path.impact(),"Native skipped-tick credit is captured");
            int ticks=(int)Math.round(path.end().seconds()*20);tnt.setFuse(200);
            for(int i=0;i<ticks;i++) h.getLevel().tickNonPassenger(tnt);
            h.assertTrue(tnt.position().distanceTo(path.end().position())<.00001,"Fractional TNT forecast matches ServerLevel ticking");
            tnt.setFuse(80);var longPath=PredictionEngine.forecast(h.getLevel(),PredictionServer.capture(tnt),p -> .25,Map.of());
            h.assertTrue(!longPath.impact() && PredictionOutcomes.event(h.getLevel(),longPath,Map.of())==null,"Long-fuse TNT does not fill the view with speculative areas");
        } finally {TemporalBubbleManager.stopAllBubbles(h.getLevel());tnt.discard();}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void creeperUsesNativeFusePowerAndClearsWhenSwellingStops(GameTestHelper h) {
        var pos=center(h);var c=EntityType.CREEPER.create(h.getLevel());c.setPos(pos);c.setNoAi(true);c.setNoGravity(true);
        var tag=c.saveWithoutId(new CompoundTag());tag.putShort("Fuse",(short)40);tag.putByte("ExplosionRadius",(byte)5);tag.putBoolean("powered",true);c.load(tag);c.setSwellDir(1);h.getLevel().addFreshEntity(c);
        try {
            var before=c.saveWithoutId(new CompoundTag());c.getRandom().setSeed(155);
            var motion=PredictionServer.capture(c);var path=PredictionEngine.forecast(h.getLevel(),motion,p -> .25,Map.of());
            var event=PredictionOutcomes.event(h.getLevel(),path,Map.of());
            h.assertTrue(event.radius()==20 && event.seconds()==2 && event.conditional(),"Charged configured creeper power and unscaled living fuse are retained");
            h.assertTrue(before.equals(c.saveWithoutId(new CompoundTag())),"Creeper preview changes no entity state");
            long random=c.getRandom().nextLong();c.getRandom().setSeed(155);h.assertTrue(random==c.getRandom().nextLong(),"Outcome preview consumes no native randomness");
            c.setSwellDir(-1);h.assertTrue(PredictionOutcomes.event(h.getLevel(),path,Map.of())==null,"Aborted swelling retracts even a still-valid snapshot");
            c.ignite();h.assertTrue(!PredictionServer.capture(c).effects().conditional(),"Ignition is distinguished from a conditional AI windup");
        } finally {c.discard();}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void largeFireballsUseConfiguredPowerButSmallFireballsDoNotInventExplosions(GameTestHelper h) {
        var large=new LargeFireball(EntityType.FIREBALL,h.getLevel());var small=new SmallFireball(EntityType.SMALL_FIREBALL,h.getLevel());
        var tag=large.saveWithoutId(new CompoundTag());tag.putByte("ExplosionPower",(byte)4);large.load(tag);
        h.assertTrue(PredictionEffects.capture(large).power()==4 && PredictionEffects.capture(large).kind()==PredictionEffects.Kind.EXPLOSION,"Actual large-fireball power is captured");
        h.assertTrue(PredictionEffects.capture(small).kind()==PredictionEffects.Kind.NONE,"Small fireballs retain ordinary trajectories");
        large.discard();small.discard();h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void tntForecastCrossesFieldBoundaryWithNativeCreditReset(GameTestHelper h) {
        var pos=center(h);var tnt=new PrimedTnt(EntityType.TNT,h.getLevel());tnt.setPos(pos);tnt.setNoGravity(true);tnt.setDeltaMovement(.2,0,0);tnt.setFuse(12);h.getLevel().addFreshEntity(tnt);
        var bubble=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),pos.add(-39.9,tnt.getBbHeight()*.5,0),40,TimeMode.TIME_PREDICTION,10000,WatchTier.NETHERITE,null,0,Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(),bubble,null);
        try {
            var path=PredictionEngine.forecast(h.getLevel(),PredictionServer.capture(tnt),p -> PredictionServer.timeRateAt(tnt,p),Map.of());
            h.assertTrue(path.impact() && path.end().seconds()<2.4 && path.end().seconds()>.6,"TNT changes fuse clock when it leaves the slow field");
            tnt.setFuse(200);for(int i=0;i<(int)Math.round(path.end().seconds()*20);i++) h.getLevel().tickNonPassenger(tnt);
            h.assertTrue(tnt.position().distanceTo(path.end().position())<.00001,"Boundary forecast matches native server credit reset and movement");
        } finally {TemporalBubbleManager.stopAllBubbles(h.getLevel());tnt.discard();}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void tntStopsAtUnloadedGeometryWithoutLoadingIt(GameTestHelper h) {
        var level=h.getLevel();var pos=new Vec3(500008.5,210,500008.5);var block=BlockPos.containing(pos);level.getChunkAt(block);
        int cx=block.getX()>>4,cz=block.getZ()>>4;
        h.assertTrue(!level.hasChunk(cx+1,cz),"Fixture's next chunk must be unloaded");
        var tnt=new PrimedTnt(EntityType.TNT,level);tnt.setPos(pos);tnt.setNoGravity(true);tnt.setDeltaMovement(8,0,0);tnt.setFuse(2);
        var path=PredictionEngine.forecast(level,PredictionServer.capture(tnt),p -> 1,Map.of());
        h.assertTrue(!path.impact() && PredictionOutcomes.event(level,path,Map.of())==null && !level.hasChunk(cx+1,cz),"Incomplete loaded flight cannot invent a blast or load neighboring chunks");
        tnt.discard();h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_outcomes")
    public void impactMetadataAndSurfaceNormalsSurviveBoundedNetworkRoundTrip(GameTestHelper h) {
        var pos=center(h);var path=impact(h,pos,PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION),Potions.HEALING),-1);
        var event=PredictionOutcomes.event(h.getLevel(),path,Map.of());
        var packet=new PredictionSnapshotPacket(UUID.randomUUID(),h.getLevel().dimension().location(),750,List.of(path.motion()),null,List.of(path),List.of(),List.of(event));
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(b);var copy=new PredictionSnapshotPacket(b);
            h.assertTrue(copy.outcomes().get(0).equals(event) && copy.forecasts().get(0).normal().equals(path.normal()),"Outcome and surface normal survive wire encoding");
            h.assertTrue(copy.motions().get(0).effects().equals(path.motion().effects()),"Potion effect metadata survives encoding");
            boolean bounded=false;
            try {new PredictionSnapshotPacket(packet.field(),packet.dimension(),750,packet.motions(),null,packet.forecasts(),List.of(),Collections.nCopies(5,event));}
            catch(IllegalArgumentException expected) {bounded=true;}
            h.assertTrue(bounded,"Outcome packet is capped at four events");
        } finally {b.release();}
        h.succeed();
    }
}
