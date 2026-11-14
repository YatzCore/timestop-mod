package com.timestop.forge.test;

import com.timestop.core.*;
import com.timestop.item.*;
import com.timestop.config.TimeStopConfig;
import java.util.*;
import net.minecraftforge.gametest.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.phys.Vec3;

/** Edge cases around the personal clock, overlapping fields, config extremes and the random-tick hook. */
@GameTestHolder("timestop")
@PrefixGameTestTemplate(false)
public class EdgeCaseTests {
    private static Vec3 center(GameTestHelper h) {
        var b=h.absolutePos(new BlockPos(4,4,4));var p=new Vec3(b.getX()+.5,240,b.getZ()+.5);
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) h.getLevel().getChunkAt(BlockPos.containing(p).offset(x*16,0,z*16));return p;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,Vec3 p) {
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"edge-test"));
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.setGameMode(GameType.SURVIVAL);player.setPos(p);return player;
    }
    private static void stop(GameTestHelper h) {
        if(TimeStopManager.isGlobalTimeStopActive()) TimeStopManager.resumeTime(h.getLevel());
        TemporalBubbleManager.stopAllBubbles(h.getLevel());
    }

    /** The Tachyon rune promises 3x mining in Slow Motion / Matrix; it must reach real block breaking. */
    @GameTest(template="deceleration_empty",batch="edge_cases")
    public static void tachyonRuneTriplesRealBlockBreaking(GameTestHelper h) {
        var p=center(h);var owner=player(h,p);var other=player(h,p.add(2,0,0));
        try {
            owner.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ModItems.RUNE_TACHYON.get()));
            var stone=Blocks.STONE.defaultBlockState();var at=h.absolutePos(new BlockPos(1,1,1));
            float normal=stone.getDestroyProgress(other,h.getLevel(),at);
            h.assertTrue(Math.abs(stone.getDestroyProgress(owner,h.getLevel(),at)-normal)<1e-6,"Tachyon is idle outside Slow Motion");
            TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.SLOW_MOTION);
            h.assertTrue(com.timestop.combat.TachyonRuneHandler.isTachyonActive(owner) && !com.timestop.combat.TachyonRuneHandler.isTachyonActive(other),"Tachyon is active for its holder in Slow Motion");
            float fast=stone.getDestroyProgress(owner,h.getLevel(),at);
            h.assertTrue(Math.abs(fast-normal*3)<1e-6,"Tachyon triples block breaking: "+fast+" vs "+normal);
            h.assertTrue(Math.abs(stone.getDestroyProgress(other,h.getLevel(),at)-normal)<1e-6,"Other players keep normal mining");
            stop(h);
            h.assertTrue(Math.abs(stone.getDestroyProgress(owner,h.getLevel(),at)-normal)<1e-6,"Mining returns to normal when Slow Motion ends");
        } finally {stop(h);owner.discard();other.discard();}h.succeed();
    }

    /** Inside your own Fast Forward field the client clock is capped at 100 TPS; the server half must match it. */
    @GameTest(template="deceleration_empty",batch="edge_cases")
    public static void fastForwardPersonalClockMatchesTheCappedClientClock(GameTestHelper h) {
        var p=center(h);var owner=player(h,p);double old=TimeStopConfig.COMMON.fastForwardRate.get();
        try {
            owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WATCH.get()));
            for(double rate:new double[]{2,5,8,50}) {
                TimeStopConfig.COMMON.fastForwardRate.set(rate);
                TemporalBubbleManager.startBubble(h.getLevel(),owner,100,TimeMode.FAST_FORWARD);
                double clientClock=50.0/Math.max(10.0,50.0/rate);
                h.assertTrue(Math.abs(PersonalTime.extraRate(owner)-clientClock)<1e-9,"At Fast Forward "+rate+"x the client clock runs "+clientClock+"x but the server personal clock runs "+PersonalTime.extraRate(owner)+"x");
                stop(h);
            }
        } finally {stop(h);TimeStopConfig.COMMON.fastForwardRate.set(old);owner.discard();}h.succeed();
    }

    /** Spectators, passengers and dead players are never accelerated, and nothing throws. */
    @GameTest(template="deceleration_empty",batch="edge_cases")
    public static void spectatorPassengerAndDeadPlayersAreNotAccelerated(GameTestHelper h) {
        var p=center(h);var owner=player(h,p);var pig=EntityType.PIG.create(h.getLevel());pig.setPos(p);pig.setNoGravity(true);double old=TimeStopConfig.COMMON.fastForwardRate.get();
        try {
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);owner.setNoGravity(true);
            TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.SELF_ACCELERATION);
            h.assertTrue(SelfAcceleration.rate(owner)==5 && PersonalTime.extraRate(owner)==5,"Survival owner is accelerated");
            owner.setGameMode(GameType.CREATIVE);
            h.assertTrue(SelfAcceleration.rate(owner)==5,"Creative owner is accelerated too");
            owner.setAirSupply(100);owner.aiStep();
            h.assertTrue(owner.getAirSupply()>=100,"Creative players never lose breath faster");
            owner.setGameMode(GameType.SPECTATOR);
            h.assertTrue(SelfAcceleration.rate(owner)==1 && PersonalTime.extraRate(owner)==1,"Spectators are not accelerated");
            owner.setGameMode(GameType.SURVIVAL);
            h.assertTrue(owner.startRiding(pig,true),"Test player mounts");
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100));
            h.assertTrue(SelfAcceleration.rate(owner)==1 && PersonalTime.extraRate(owner)==1,"Passengers are not accelerated");
            owner.aiStep();
            h.assertTrue(owner.getEffect(MobEffects.MOVEMENT_SPEED).getDuration()==100,"A riding player gets no extra personal ticks");
            owner.stopRiding();
            h.assertTrue(SelfAcceleration.rate(owner)==5,"Acceleration returns after dismounting");
            owner.setHealth(0);
            h.assertTrue(SelfAcceleration.rate(owner)==1 && PersonalTime.extraRate(owner)==1 && PersonalTime.localFastForwardRate(owner)==1,"Dead players are not accelerated");
            owner.aiStep();
        } finally {stop(h);TimeStopConfig.COMMON.fastForwardRate.set(old);owner.discard();pig.discard();}h.succeed();
    }

    /** Accelerated falling only applies in free fall, never raises a jump, and respects the cap. */
    @GameTest(template="deceleration_empty",batch="edge_cases")
    public static void acceleratedFallOnlyInFreeFallAndWithinTheCap(GameTestHelper h) {
        var p=center(h);var owner=player(h,p);var other=player(h,p.add(3,0,0));double old=TimeStopConfig.COMMON.fastForwardRate.get();
        try {
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);
            TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.SELF_ACCELERATION);
            double extra=.08*4*.98;
            for(var pl:List.of(owner,other)) {pl.setPos(pl.getX(),240,pl.getZ());pl.setOnGround(false);pl.setDeltaMovement(0,-.5,0);pl.travel(Vec3.ZERO);}
            h.assertTrue(Math.abs(owner.getDeltaMovement().y-(other.getDeltaMovement().y-extra))<1e-6,"Free fall gains four extra gravity steps: "+owner.getDeltaMovement().y+" vs "+other.getDeltaMovement().y);
            for(var pl:List.of(owner,other)) {pl.setPos(pl.getX(),240,pl.getZ());pl.setOnGround(false);pl.setDeltaMovement(0,.42,0);pl.travel(Vec3.ZERO);}
            h.assertTrue(owner.getDeltaMovement().y>0 && Math.abs(owner.getDeltaMovement().y-other.getDeltaMovement().y)<1e-6,"Rising (jumping) is unchanged");
            for(var pl:List.of(owner,other)) {pl.setPos(pl.getX(),240,pl.getZ());pl.setOnGround(false);pl.setNoGravity(true);pl.setDeltaMovement(0,-.5,0);pl.travel(Vec3.ZERO);}
            h.assertTrue(Math.abs(owner.getDeltaMovement().y-other.getDeltaMovement().y)<1e-6,"No-gravity players are unchanged");
            for(var pl:List.of(owner,other)) {pl.setNoGravity(false);pl.addEffect(new MobEffectInstance(MobEffects.LEVITATION,100));pl.setPos(pl.getX(),240,pl.getZ());pl.setOnGround(false);pl.setDeltaMovement(0,-.5,0);pl.travel(Vec3.ZERO);}
            h.assertTrue(Math.abs(owner.getDeltaMovement().y-other.getDeltaMovement().y)<1e-6,"Levitation is unchanged");
            for(var pl:List.of(owner,other)) {pl.removeAllEffects();pl.getAbilities().flying=true;pl.setPos(pl.getX(),240,pl.getZ());pl.setOnGround(false);pl.setDeltaMovement(0,-.5,0);pl.travel(Vec3.ZERO);pl.getAbilities().flying=false;}
            h.assertTrue(Math.abs(owner.getDeltaMovement().y-other.getDeltaMovement().y)<1e-6,"Creative flight is unchanged");
            TimeStopConfig.COMMON.fastForwardRate.set(50.0);
            owner.setPos(owner.getX(),240,owner.getZ());owner.setOnGround(false);owner.setDeltaMovement(0,-3.5,0);
            for(int i=0;i<4;i++) {owner.setPos(owner.getX(),240,owner.getZ());owner.setOnGround(false);owner.travel(Vec3.ZERO);
                h.assertTrue(owner.getDeltaMovement().y>=PersonalTime.MAX_FALL_SPEED-1e-9,"Fall speed never exceeds the cap at 50x: "+owner.getDeltaMovement().y);}
            // Vanilla rejects a move when (distance^2 - velocity^2) > 100; a full-speed capped fall with 5x sprint stays far below.
            double sprint=.2806*5;
            h.assertTrue(PersonalTime.MAX_FALL_SPEED*PersonalTime.MAX_FALL_SPEED+sprint*sprint<100,"Capped fall plus 5x sprint stays under the server movement check");
        } finally {stop(h);TimeStopConfig.COMMON.fastForwardRate.set(old);owner.discard();other.discard();}h.succeed();
    }

    /** Overlapping fields never multiply a player's clock, whichever one dominates. */
    @GameTest(template="deceleration_empty",batch="edge_cases")
    public static void overlappingFieldsNeverStackThePersonalClock(GameTestHelper h) {
        var p=center(h);var a=player(h,p);var b=player(h,p.add(1,0,0));double old=TimeStopConfig.COMMON.fastForwardRate.get();
        try {
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);
            a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WATCH.get()));
            b.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WATCH.get()));
            TemporalBubbleManager.startBubble(h.getLevel(),a,100,TimeMode.SELF_ACCELERATION);
            TemporalBubbleManager.startBubble(h.getLevel(),b,100,TimeMode.FAST_FORWARD);
            for(var pl:List.of(a,b)) {
                double rate=PersonalTime.extraRate(pl);
                h.assertTrue(rate==1 || rate==5,"Two overlapping watch fields give 1x or 5x, never a product: "+rate);
            }
            h.assertTrue(PersonalTime.extraRate(a)==5,"The Self Acceleration owner is 5x under either dominant field");
            a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100));a.setNoGravity(true);a.aiStep();
            h.assertTrue(a.getEffect(MobEffects.MOVEMENT_SPEED).getDuration()==96,"Exactly four extra personal ticks, not more: "+a.getEffect(MobEffects.MOVEMENT_SPEED).getDuration());
            // A pedestal (stationary) Fast Forward field of a higher tier on top of both.
            var pedestal=new TemporalBubble(UUID.randomUUID(),b.getUUID(),h.getLevel().dimension(),p,8,TimeMode.FAST_FORWARD,100,WatchTier.NETHERITE,null,0,Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(),pedestal,null);
            for(var pl:List.of(a,b)) {
                double rate=PersonalTime.extraRate(pl);
                h.assertTrue(rate==1 || rate==5,"Pedestal plus watch fields give 1x or 5x, never a product: "+rate);
            }
            h.assertTrue(PersonalTime.localFastForwardRate(b)==1,"A dominant pedestal Fast Forward field does not change players");
        } finally {stop(h);TimeStopConfig.COMMON.fastForwardRate.set(old);a.discard();b.discard();}h.succeed();
    }

    /** Extreme speed settings stay finite, clamp, and clean up. */
    @GameTest(template="deceleration_empty",batch="edge_cases")
    public static void configExtremesStayBoundedAndCleanUp(GameTestHelper h) {
        var p=center(h);var owner=player(h,p);
        double ff=TimeStopConfig.COMMON.fastForwardRate.get(),slow=TimeStopConfig.COMMON.slowMotionRate.get(),matrix=TimeStopConfig.COMMON.matrixRate.get();
        try {
            h.assertTrue(TimeStopConfig.clampFastForward(1000)==50 && TimeStopConfig.clampSlowMotion(0)==.01,"Out-of-range speeds are clamped");
            TimeStopConfig.COMMON.fastForwardRate.set(50.0);TimeStopConfig.COMMON.slowMotionRate.set(.01);TimeStopConfig.COMMON.matrixRate.set(.01);
            owner.setNoGravity(true);
            TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.FAST_FORWARD);
            h.assertTrue(TimeStopManager.getServerTickMs()==10,"Global Fast Forward 50x is capped at 100 TPS: "+TimeStopManager.getServerTickMs());
            h.assertTrue(PersonalTime.extraRate(owner)==1,"Global Fast Forward adds no personal ticks");
            stop(h);h.assertTrue(TimeStopManager.getServerTickMs()==50,"Tick length returns to 50 ms");
            TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.SLOW_MOTION);
            h.assertTrue(TimeStopManager.getServerTickMs()==5000,"Slow Motion 0.01 gives 5 s ticks: "+TimeStopManager.getServerTickMs());
            stop(h);
            TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.MATRIX);
            h.assertTrue(TimeStopManager.getServerTickMs()==5000,"Matrix 0.01 gives 5 s ticks: "+TimeStopManager.getServerTickMs());
            stop(h);h.assertTrue(TimeStopManager.getServerTickMs()==50,"Tick length returns to 50 ms");
            TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.SELF_ACCELERATION);
            h.assertTrue(SelfAcceleration.rate(owner)==50,"Self Acceleration follows the 50x setting");
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,1000));
            owner.getFoodData().setFoodLevel(20);owner.setHealth(owner.getMaxHealth());
            owner.aiStep();
            h.assertTrue(owner.getEffect(MobEffects.MOVEMENT_SPEED).getDuration()==951,"49 extra personal ticks at 50x: "+owner.getEffect(MobEffects.MOVEMENT_SPEED).getDuration());
            owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD));owner.getFoodData().setFoodLevel(10);
            owner.startUsingItem(InteractionHand.MAIN_HAND);owner.aiStep();
            h.assertTrue(!owner.isUsingItem() && owner.getFoodData().getFoodLevel()>10,"Eating completes exactly once at 50x");
            h.assertTrue(owner.isAlive() && Float.isFinite(owner.getHealth()),"Player state stays valid");
            stop(h);h.assertTrue(SelfAcceleration.rate(owner)==1,"Nothing stays accelerated");
        } finally {stop(h);TimeStopConfig.COMMON.fastForwardRate.set(ff);TimeStopConfig.COMMON.slowMotionRate.set(slow);TimeStopConfig.COMMON.matrixRate.set(matrix);owner.discard();}h.succeed();
    }

    /** The real BlockState.randomTick hook (the Lithium-safe path) scales, freezes and never leaks its re-entry guard. */
    @GameTest(template="deceleration_empty",batch="edge_cases")
    public static void randomTickHookScalesFreezesAndResetsItsGuard(GameTestHelper h) {
        var p=center(h);var owner=player(h,p);var pos=BlockPos.containing(p);var far=pos.offset(0,0,40);
        double ff=TimeStopConfig.COMMON.fastForwardRate.get(),slow=TimeStopConfig.COMMON.slowMotionRate.get();
        var random=net.minecraft.util.RandomSource.create(7);
        try {
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);TimeStopConfig.COMMON.slowMotionRate.set(.25);
            h.getLevel().getChunkAt(far);
            for(var at:List.of(pos,far)) {h.getLevel().setBlock(at.below(),Blocks.GLASS.defaultBlockState(),2);h.getLevel().setBlock(at,Blocks.SUGAR_CANE.defaultBlockState(),2);h.getLevel().setBlock(at.above(),Blocks.AIR.defaultBlockState(),2);}
            var field=new TemporalBubble(UUID.randomUUID(),owner.getUUID(),h.getLevel().dimension(),p,8,TimeMode.FAST_FORWARD,100,WatchTier.COPPER,null,0,Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(),field,null);
            h.getLevel().getBlockState(pos).randomTick(h.getLevel(),pos,random);
            h.getLevel().getBlockState(far).randomTick(h.getLevel(),far,random);
            h.assertTrue(h.getLevel().getBlockState(pos).getValue(SugarCaneBlock.AGE)==5,"One random tick inside a 5x pedestal field ages the cane by 5: "+h.getLevel().getBlockState(pos).getValue(SugarCaneBlock.AGE));
            h.assertTrue(h.getLevel().getBlockState(far).getValue(SugarCaneBlock.AGE)==1,"Outside the field one random tick is one tick (guard was reset)");
            stop(h);
            var frozen=new TemporalBubble(UUID.randomUUID(),owner.getUUID(),h.getLevel().dimension(),p,8,TimeMode.TIME_STOP,100,WatchTier.COPPER,null,0,Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(),frozen,null);
            for(int i=0;i<10;i++) h.getLevel().getBlockState(pos).randomTick(h.getLevel(),pos,random);
            h.assertTrue(h.getLevel().getBlockState(pos).getValue(SugarCaneBlock.AGE)==5,"Random ticks are frozen inside Time Stop");
            stop(h);
            var slowField=new TemporalBubble(UUID.randomUUID(),owner.getUUID(),h.getLevel().dimension(),p,8,TimeMode.SLOW_MOTION,100,WatchTier.COPPER,null,0,Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(),slowField,null);
            h.getLevel().setBlock(pos,Blocks.SUGAR_CANE.defaultBlockState(),2);
            int applied=0;
            for(int i=0;i<12;i++) {int before=h.getLevel().getBlockState(pos).getValue(SugarCaneBlock.AGE);h.getLevel().getBlockState(pos).randomTick(h.getLevel(),pos,random);applied+=h.getLevel().getBlockState(pos).getValue(SugarCaneBlock.AGE)-before;}
            h.assertTrue(applied<12,"A 0.25x pedestal field drops most random ticks: "+applied+" of 12");
            stop(h);
            h.getLevel().getBlockState(far).randomTick(h.getLevel(),far,random);
            h.assertTrue(h.getLevel().getBlockState(far).getValue(SugarCaneBlock.AGE)==2,"Random ticks are normal again afterwards");
        } finally {stop(h);TimeStopConfig.COMMON.fastForwardRate.set(ff);TimeStopConfig.COMMON.slowMotionRate.set(slow);
            for(var at:List.of(pos,far)) {h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),2);h.getLevel().setBlock(at.above(),Blocks.AIR.defaultBlockState(),2);h.getLevel().setBlock(at.below(),Blocks.AIR.defaultBlockState(),2);}owner.discard();}
        h.succeed();
    }

    /** A local field does not follow its owner into another dimension. */
    @GameTest(template="deceleration_empty",batch="edge_cases")
    public static void localFieldDoesNotFollowTheOwnerAcrossDimensions(GameTestHelper h) {
        var p=center(h);var owner=player(h,p);double old=TimeStopConfig.COMMON.fastForwardRate.get();
        var nether=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        try {
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);
            owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WATCH.get()));
            TemporalBubbleManager.startBubble(h.getLevel(),owner,100,TimeMode.SELF_ACCELERATION);
            h.assertTrue(SelfAcceleration.rate(owner)==5,"Owner is accelerated in the field's dimension");
            if(nether!=null && nether!=h.getLevel()) {
                owner.setServerLevel(nether);
                h.assertTrue(SelfAcceleration.rate(owner)==1 && PersonalTime.extraRate(owner)==1,"Same coordinates in another dimension are outside the field");
                owner.setServerLevel(h.getLevel());
            }
            owner.setPos(p.add(500,0,0));
            h.assertTrue(SelfAcceleration.rate(owner)==1,"Leaving the field ends the boost at once");
        } finally {if(owner.level()!=h.getLevel()) owner.setServerLevel(h.getLevel());stop(h);TimeStopConfig.COMMON.fastForwardRate.set(old);owner.discard();}h.succeed();
    }

    /** A remote server's synced speeds are borrowed: they must not reach this player's own config file or outlive the session. */
    @GameTest(template="deceleration_empty",batch="edge_cases")
    public static void remoteServerSpeedsNeverReachTheLocalConfigFile(GameTestHelper h) {
        double old=TimeStopConfig.COMMON.fastForwardRate.get();boolean water=TimeStopConfig.COMMON.enableWaterWalkingInStasis.get();
        try {
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);
            TimeStopConfig.beginRemoteSync();
            TimeStopConfig.COMMON.fastForwardRate.set(3.0);TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(!water);
            TimeStopConfig.beginRemoteSync();
            TimeStopConfig.save();
            var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("config","timestop.json"))).getAsJsonObject();
            double saved=json.getAsJsonObject("speed_multipliers").get("fastForwardRate").getAsDouble();
            h.assertTrue(saved==5.0,"The config file keeps the local Fast Forward speed, not the server's: "+saved);
            h.assertTrue(json.getAsJsonObject("mechanics").get("enableWaterWalkingInStasis").getAsBoolean()==water,"The config file keeps the local swimming setting");
            h.assertTrue(TimeStopConfig.COMMON.fastForwardRate.get()==3.0,"The server's speed is in effect while connected");
            TimeStopConfig.endRemoteSync();
            h.assertTrue(TimeStopConfig.COMMON.fastForwardRate.get()==5.0 && TimeStopConfig.COMMON.enableWaterWalkingInStasis.get()==water,"Local settings return after leaving");
            TimeStopConfig.endRemoteSync();TimeStopConfig.COMMON.fastForwardRate.set(4.0);TimeStopConfig.save();
            json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("config","timestop.json"))).getAsJsonObject();
            h.assertTrue(json.getAsJsonObject("speed_multipliers").get("fastForwardRate").getAsDouble()==4.0,"Own changes are saved normally afterwards");
        } catch(java.io.IOException e) {throw new RuntimeException(e);
        } finally {TimeStopConfig.endRemoteSync();TimeStopConfig.COMMON.fastForwardRate.set(old);TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(water);TimeStopConfig.save();}h.succeed();
    }
}
