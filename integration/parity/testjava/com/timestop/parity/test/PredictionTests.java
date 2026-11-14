package com.timestop.parity.test;

import com.timestop.config.TimeStopConfig;
import com.timestop.core.*;
import com.timestop.item.*;
import com.timestop.network.*;
import com.timestop.prediction.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.FriendlyByteBuf;

public class PredictionTests {
    private static final String EMPTY_STRUCTURE="parity_empty";
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction_arrow_interaction")
    public void hostileArrowRedirectUsesPacketInPredictionAndRejectsDistantOrBlockedShots(GameTestHelper h) {
        var pos=center(h);var player=RewindRuneTests.survivalPlayer(h);player.setPos(pos);
        var wall = BlockPos.containing(pos.add(0,1,1));
        h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);
        var skeleton=EntityType.SKELETON.create(h.getLevel());skeleton.setNoAi(true);skeleton.setPos(pos.add(0,0,6));
        h.getLevel().addFreshEntity(skeleton);
        var arrow=new Arrow(h.getLevel(),pos.x,pos.y+1.2,pos.z+2,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);arrow.setOwner(skeleton);arrow.setDeltaMovement(0,0,-.2);h.getLevel().addFreshEntity(arrow);
        var saved=TimeStopSavedData.get();boolean previous=saved.isRedirectToLook();saved.setRedirectToLook(false);
        try {
            field(h,pos,16);
            new SlapProjectilePacket(arrow.getId(),Vec3.ZERO).handle(player);
            h.assertTrue(arrow.getOwner()==player && arrow.getDeltaMovement().z>0,"Prediction's attack packet must reflect a hostile arrow back to its shooter");
            h.assertTrue(arrow.getPierceLevel()==1 && arrow.isCritArrow(),"Arrow reflects once, preserving native damage handling");
            var path=PredictionEngine.forecast(h.getLevel(),PredictionServer.capture(arrow),p -> .25,List.of());
            h.assertTrue(path.motion().ownerId()==player.getId() && path.motion().velocity().z>0,"Forecast must immediately use reflected ownership and direction");
            arrow.setOwner(skeleton);arrow.setPos(pos.add(0,1.2,7));arrow.setDeltaMovement(0,0,-.2);
            new SlapProjectilePacket(arrow.getId(),Vec3.ZERO).handle(player);
            h.assertTrue(arrow.getOwner()==skeleton,"Custom packet cannot redirect an out-of-reach shot");
            arrow.setPos(pos.add(0,1.2,3));h.getLevel().setBlock(wall,Blocks.STONE.defaultBlockState(),3);
            new SlapProjectilePacket(arrow.getId(),Vec3.ZERO).handle(player);
            h.assertTrue(arrow.getOwner()==skeleton,"Custom packet cannot redirect through walls");
            com.timestop.TimeStopMod.LOGGER.info("PREDICTION_REDIRECT_PACKET_PASS");
        } finally {h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);saved.setRedirectToLook(previous);TemporalBubbleManager.stopAllBubbles(h.getLevel());arrow.discard();skeleton.discard();h.getLevel().getServer().getPlayerList().remove(player);player.discard();}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction_prepared")
    public void preparedSkeletonShotMatchesNativeAimWithoutShootingOrConsumingRandomness(GameTestHelper h) {
        var pos=center(h);var skeleton=EntityType.SKELETON.create(h.getLevel());skeleton.setNoAi(true);skeleton.setPos(pos);
        var target=EntityType.COW.create(h.getLevel());target.setNoAi(true);target.setPos(pos.add(8,0,0));
        skeleton.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.BOW));skeleton.setTarget(target);
        h.getLevel().addFreshEntity(skeleton);h.getLevel().addFreshEntity(target);
        try {
            h.assertTrue(PredictionPreparedShots.capture(skeleton)==null,"Idle archers must not invent a shot");
            skeleton.startUsingItem(InteractionHand.MAIN_HAND);
            var before=skeleton.saveWithoutId(new CompoundTag());skeleton.getRandom().setSeed(314159L);
            var preview=PredictionPreparedShots.capture(skeleton);
            h.assertTrue(preview!=null && preview.finite() && PredictionPreparedShots.prepared(preview),"Drawing skeleton has a standalone arrow forecast");
            h.assertTrue(before.equals(skeleton.saveWithoutId(new CompoundTag())),"Preparing a forecast must not mutate the archer");
            long observed=skeleton.getRandom().nextLong();skeleton.getRandom().setSeed(314159L);
            h.assertTrue(observed==skeleton.getRandom().nextLong(),"Forecast does not consume the native shot's random spread");
            var arrow=new Arrow(h.getLevel(),skeleton,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);double dx=target.getX()-skeleton.getX(),dz=target.getZ()-skeleton.getZ();
            arrow.shoot(dx,target.getY(1.0/3)-arrow.getY()+Math.sqrt(dx*dx+dz*dz)*(double).2F,dz,1.6F,0);
            h.assertTrue(preview.position().distanceTo(arrow.position())<1e-8 && preview.velocity().distanceTo(arrow.getDeltaMovement())<1e-7,"Preview uses the native muzzle and central aim");
            var path=PredictionEngine.forecast(h.getLevel(),preview,p -> 1,List.of(PredictionServer.capture(target)));
            var packet=new PredictionSnapshotPacket(UUID.randomUUID(),h.getLevel().dimension().location(),750,List.of(preview),null,List.of(path.compact()),List.of());
            var buf=new FriendlyByteBuf(Unpooled.buffer());packet.toBytes(buf);var copy=new PredictionSnapshotPacket(buf);buf.release();
            h.assertTrue(copy.forecasts().get(0).end().position().distanceTo(path.end().position())<.001,"Pre-release flight survives the bounded network format");
            skeleton.stopUsingItem();h.assertTrue(PredictionPreparedShots.capture(skeleton)==null,"Aborted bow charge clears its preview");
            arrow.discard();com.timestop.TimeStopMod.LOGGER.info("PREDICTION_PREPARED_SHOT_PASS");
        } finally {skeleton.discard();target.discard();}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction_prepared")
    public void preparedPillagerCrossbowExcludesFireworksAndAbortedCharge(GameTestHelper h) {
        var pos=center(h);var pillager=EntityType.PILLAGER.create(h.getLevel());pillager.setNoAi(true);pillager.setPos(pos);
        var target=EntityType.COW.create(h.getLevel());target.setNoAi(true);target.setPos(pos.add(0,0,8));
        h.getLevel().addFreshEntity(pillager);h.getLevel().addFreshEntity(target);pillager.setTarget(target);
        var crossbow=new ItemStack(net.minecraft.world.item.Items.CROSSBOW);pillager.setItemInHand(InteractionHand.MAIN_HAND,crossbow);
        try {
            h.assertTrue(PredictionPreparedShots.capture(pillager)==null,"Unprepared crossbow has no shot");
            pillager.startUsingItem(InteractionHand.MAIN_HAND);
            h.assertTrue(PredictionPreparedShots.capture(pillager)!=null,"Charging crossbow has an arrow preview before release");
            pillager.stopUsingItem();crossbow.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,net.minecraft.world.item.component.ChargedProjectiles.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW)));
            h.assertTrue(PredictionPreparedShots.capture(pillager)!=null,"Charged crossbow retains the imminent shot preview");
            crossbow.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,net.minecraft.world.item.component.ChargedProjectiles.of(new ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET)));
            h.assertTrue(PredictionPreparedShots.capture(pillager)==null,"Fireworks cannot be misrepresented as ballistic arrows");
        } finally {pillager.discard();target.discard();}
        h.succeed();
    }
    private static Vec3 center(GameTestHelper h) {
        var p = h.absolutePos(new BlockPos(4,4,4));
        var center = new Vec3(p.getX()+.5, 200, p.getZ()+.5);
        h.getLevel().getChunkAt(BlockPos.containing(center));
        return center;
    }
    private static TemporalBubble field(GameTestHelper h, Vec3 center, double radius) {
        var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(), center,
                radius, TimeMode.TIME_PREDICTION, 10000, WatchTier.NETHERITE, null, 0, Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);
        return bubble;
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction")
    public void unlocksSelectionAndExistingOrdinalsRemainStable(GameTestHelper h) {
        h.assertTrue(TimeMode.REWIND.ordinal()==6 && TimeMode.TIME_PREDICTION.ordinal()==7, "Existing packet ordinals must remain stable");
        for (var tier : WatchTier.values()) h.assertTrue(tier.isModeUnlocked(TimeMode.TIME_PREDICTION)
                == (tier==WatchTier.NETHERITE || tier==WatchTier.CREATIVE), "Prediction unlock: "+tier);
        var player = RewindRuneTests.survivalPlayer(h);
        try {
            var watch = new ItemStack(ModItems.NETHERITE_WATCH.get());
            player.setItemInHand(InteractionHand.OFF_HAND, watch);
            new SelectTimeModePacket(TimeMode.TIME_PREDICTION, InteractionHand.OFF_HAND).handle(player);
            h.assertTrue(AbstractWatchItem.getMode(watch)==TimeMode.TIME_PREDICTION, "Offhand watch selection must persist");
            var saved = new CompoundTag(); saved= (CompoundTag)watch.save(h.getLevel().registryAccess());
            h.assertTrue(AbstractWatchItem.getMode(ItemStack.parseOptional(h.getLevel().registryAccess(),saved))==TimeMode.TIME_PREDICTION, "Watch NBT round trip");
            var copper = new ItemStack(ModItems.COPPER_WATCH.get());
            player.setItemInHand(InteractionHand.OFF_HAND, copper);
            new SelectTimeModePacket(TimeMode.TIME_PREDICTION, InteractionHand.OFF_HAND).handle(player);
            h.assertFalse(AbstractWatchItem.getMode(copper)==TimeMode.TIME_PREDICTION, "Lower tier cannot select prediction");
        } finally { player.discard(); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction")
    public void continuousLocalSlowdownMatchesTheForecast(GameTestHelper h) {
        double previous=TimeStopConfig.COMMON.predictionRate.get();
        var pos=center(h); var arrow=new Arrow(h.getLevel(),pos.x,pos.y,pos.z,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);
        arrow.setNoGravity(true); arrow.setDeltaMovement(.08,0,0);
        try {
            TimeStopConfig.COMMON.predictionRate.set(.25); field(h,pos,16);
            h.getLevel().tickNonPassenger(arrow);
            var seed=PredictionServer.capture(arrow);
            h.assertTrue(arrow.getX()>pos.x, "Slowed arrows advance on every server tick, without freeze frames");
            h.assertTrue(seed.phase()==0, "Native projectiles use continuous physics instead of skipped ticks");
            var prediction=PredictionEngine.trace(h.getLevel(),seed,p -> .25,List.of());
            var before=new CompoundTag(); arrow.saveWithoutId(before);
            PredictionEngine.trace(h.getLevel(),seed,p -> .25,List.of());
            var after=new CompoundTag(); arrow.saveWithoutId(after);
            h.assertTrue(before.equals(after), "Forecasting must not mutate live entities");
            for(int i=0;i<40;i++) h.getLevel().tickNonPassenger(arrow);
            h.assertTrue(arrow.position().distanceTo(prediction.end().position())<.002, "Quarter speed forecast must match actual native motion");
            arrow.setPos(pos.add(20,0,0));
            h.assertTrue(PredictionServer.timeRate(arrow)==1, "Entities outside the field remain normal");
            h.assertTrue(TimeStopManager.getServerTickMs()==50, "Prediction bubble must preserve server 20 TPS");
        } finally { arrow.discard(); TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopConfig.COMMON.predictionRate.set(previous); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction")
    public void projectileFamiliesForecastGravityDragAndAcceleration(GameTestHelper h) {
        var pos=center(h);
        for(var type : List.of(EntityType.ARROW,EntityType.TRIDENT,EntityType.SNOWBALL,EntityType.EGG,
                EntityType.ENDER_PEARL,EntityType.POTION,EntityType.EXPERIENCE_BOTTLE,EntityType.SMALL_FIREBALL)) {
            var projectile=(Projectile)type.create(h.getLevel()); projectile.setPos(pos); projectile.setDeltaMovement(.025,.1,0);
            if(projectile instanceof AbstractHurtingProjectile fireball) { fireball.accelerationPower=.002; }
            try {
                var seed=PredictionServer.capture(projectile);
                var forecast=PredictionEngine.trace(h.getLevel(),seed,p -> 1,List.of());
                for(int i=0;i<40;i++) h.getLevel().tickNonPassenger(projectile);
                h.assertTrue(projectile.position().distanceTo(forecast.end().position())<.003,
                        "Vanilla ballistic forecast: "+type+" error="+projectile.position().distanceTo(forecast.end().position()));
            } finally { projectile.discard(); }
        }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction")
    public void waterDragMatchesActualArrowMotion(GameTestHelper h) {
        var pos=center(h); var base=BlockPos.containing(pos);
        for(int x=0;x<4;x++) h.getLevel().setBlock(base.offset(x,0,0),Blocks.WATER.defaultBlockState(),3);
        var arrow=new Arrow(h.getLevel(),pos.x,pos.y+.1,pos.z,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null); arrow.setNoGravity(true); arrow.setDeltaMovement(.08,0,0);
        try {
            var seed=PredictionServer.capture(arrow);
            var forecast=PredictionEngine.trace(h.getLevel(),seed,p -> 1,List.of());
            for(int i=0;i<40;i++) h.getLevel().tickNonPassenger(arrow);
            h.assertTrue(arrow.position().distanceTo(forecast.end().position())<.003,"Water drag forecast must match native arrow");
        } finally { arrow.discard(); for(int x=0;x<4;x++) h.getLevel().setBlock(base.offset(x,0,0),Blocks.AIR.defaultBlockState(),3); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction")
    public void wallAndMovingTargetSweepsStopThePath(GameTestHelper h) {
        var pos=center(h); var wall=BlockPos.containing(pos).offset(2,0,0);
        var arrow=new Arrow(h.getLevel(),pos.x,pos.y+.4,pos.z,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null); arrow.setNoGravity(true); arrow.setDeltaMovement(.08,0,0);
        try {
            h.getLevel().setBlock(wall,Blocks.STONE.defaultBlockState(),3);
            var seed=PredictionServer.capture(arrow);
            var forecast=PredictionEngine.trace(h.getLevel(),seed,p -> 1,List.of());
            h.assertTrue(forecast.impact() && Math.abs(forecast.end().position().x-wall.getX())<.002,"Path stops at solid collision surface");
            h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);
            for(int x=0;x<4;x++) h.getLevel().setBlock(BlockPos.containing(pos).below().offset(x,0,0),Blocks.STONE.defaultBlockState(),3);
            var target=new PredictionMotion(987,-1,PredictionMotion.Kind.LIVING,pos.add(1,0,0),new Vec3(0,-.0784,0),Vec3.ZERO,
                    .08,.98,.8,.6F,1.8F,true,false,false,0,.25);
            forecast=PredictionEngine.trace(h.getLevel(),seed,p -> 1,List.of(target));
            h.assertTrue(forecast.hitEntity()==987,"Swept projectile collision selects nearest entity");
        } finally { arrow.discard(); h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);
            for(int x=0;x<4;x++) h.getLevel().setBlock(BlockPos.containing(pos).below().offset(x,0,0),Blocks.AIR.defaultBlockState(),3); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction")
    public void personalOwnershipGlobalTimingAndIndependentSpeed(GameTestHelper h) {
        var owner=RewindRuneTests.survivalPlayer(h); var other=RewindRuneTests.survivalPlayer(h);
        other.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        double previous=TimeStopConfig.COMMON.predictionRate.get();
        try {
            owner.setPos(center(h)); other.setPos(center(h).add(2,0,0));
            var watch=new ItemStack(ModItems.NETHERITE_WATCH.get()); owner.setItemInHand(InteractionHand.MAIN_HAND,watch);
            var bubble=TemporalBubbleManager.startBubble(h.getLevel(),owner,100,TimeMode.TIME_PREDICTION);
            h.assertTrue(bubble!=null && PredictionServer.ownsPrediction(owner),"Watch owner receives forecasts");
            h.assertFalse(PredictionServer.ownsPrediction(other),"Another player cannot see owner's prediction");
            h.assertTrue(bubble.getTimeDilationFactor(owner)==1,"Owner remains at normal speed");
            TemporalBubbleManager.stopAllBubbles(h.getLevel());
            h.assertTrue(owner.getCooldowns().isOnCooldown(watch.getItem()),"Prediction uses watch cooldown");
            TimeStopConfig.COMMON.predictionRate.set(.25);
            TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.TIME_PREDICTION);
            h.assertTrue(TimeStopManager.getServerTickMs()==50,"Global prediction preserves responsive server clock");
            h.assertTrue(PredictionTime.rate(other)==.25,"Global prediction slows affected entities independently");
            h.assertFalse(PredictionServer.ownsPrediction(other),"Global mode remains a personal forecast");
            TimeStopConfig.COMMON.predictionRate.set(.5);
            h.assertTrue(TimeStopManager.getServerTickMs()==50 && PredictionTime.rate(other)==.5,"Entity speed changes without throttling server");
        } finally { TimeStopManager.resumeTime(h.getLevel()); TemporalBubbleManager.stopAllBubbles(h.getLevel());
            TimeStopConfig.COMMON.predictionRate.set(previous); h.getLevel().getServer().getPlayerList().remove(owner); h.getLevel().getServer().getPlayerList().remove(other); owner.discard(); other.discard(); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction")
    public void snapshotsRoundTripAndRejectUnboundedCounts(GameTestHelper h) {
        var pos=center(h); var arrow=new Arrow(h.getLevel(),pos.x,pos.y,pos.z,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null); arrow.setDeltaMovement(.2,.1,0);
        var motion=PredictionServer.capture(arrow);
        var packet=new PredictionSnapshotPacket(UUID.randomUUID(),h.getLevel().dimension().location(),750,List.of(motion),null);
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buf); var decoded=new PredictionSnapshotPacket(buf);
            h.assertTrue(decoded.field().equals(packet.field()) && decoded.motions().equals(packet.motions()),"Motion snapshot round trip");
            buf.clear(); buf.writeUUID(packet.field()); buf.writeResourceLocation(packet.dimension()); buf.writeVarLong(750); buf.writeVarInt(5000);
            boolean rejected=false; try { new PredictionSnapshotPacket(buf); } catch(IllegalArgumentException ex) { rejected=true; }
            h.assertTrue(rejected,"Snapshot count is bounded");
            h.assertTrue(TimeStopConfig.clampPrediction(Double.NaN)==.25,"Nonfinite speed uses safe default");
        } finally { buf.release(); arrow.discard(); }
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE, batch="prediction")
    public void fullArrowArcReachesTheActualLandingBeyondTwoSeconds(GameTestHelper h) {
        var pos=center(h); var base=BlockPos.containing(pos).offset(0,-4,0);
        var arrow=new Arrow(h.getLevel(),pos.x,pos.y,pos.z,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null); arrow.setDeltaMovement(.35,.22,0);
        try {
            for(int x=0;x<24;x++) h.getLevel().setBlock(base.offset(x,0,0),Blocks.STONE.defaultBlockState(),3);
            field(h,pos,32);
            var seed=PredictionServer.capture(arrow);
            var full=PredictionEngine.forecast(h.getLevel(),seed,p -> .25,List.of());
            h.assertTrue(full.impact() && full.end().seconds()>2,"Full trajectory continues through slowdown to landing");
            for(int tick=0;tick<1000 && !((com.timestop.mixin.AbstractArrowAccessor)arrow).timestop$isInGround();tick++) h.getLevel().tickNonPassenger(arrow);
            h.assertTrue(((com.timestop.mixin.AbstractArrowAccessor)arrow).timestop$isInGround(),"Real arrow reaches floor");
            h.assertTrue(arrow.position().distanceTo(full.end().position())<.1,"Full arc predicts actual native collision");
        } finally {
            arrow.discard(); TemporalBubbleManager.stopAllBubbles(h.getLevel());
            for(int x=0;x<24;x++) h.getLevel().setBlock(base.offset(x,0,0),Blocks.AIR.defaultBlockState(),3);
        }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction")
    public void plannedMobRouteTurnsAndSerializesWithoutChangingNavigation(GameTestHelper h) {
        var pos=center(h); var floor=BlockPos.containing(pos).below();
        for(int x=0;x<=5;x++) for(int z=0;z<=5;z++) h.getLevel().setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        var route=List.of(pos.add(1,0,0),pos.add(1,0,1));
        var seed=new PredictionMotion(9,-1,PredictionMotion.Kind.LIVING,pos,new Vec3(.2,0,0),Vec3.ZERO,
                .08,.98,.8,.6F,1.8F,true,false,false,0,.25).withRoute(route,.2);
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            seed.write(buf); h.assertTrue(seed.equals(PredictionMotion.read(buf)),"Navigation waypoints round trip");
            var path=PredictionEngine.forecast(h.getLevel(),seed,p -> .25,List.of());
            h.assertTrue(path.end().position().distanceTo(route.get(1))<.01,"Forecast follows destination after the turn");
            h.assertTrue(path.points().stream().anyMatch(p -> p.position().x>pos.x+.9 && p.position().z>pos.z+.5),"Mob forecast is more than current straight-line velocity");
        } finally {
            buf.release(); for(int x=0;x<=5;x++) for(int z=0;z<=5;z++) h.getLevel().setBlock(floor.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);
        }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="prediction")
    public void slowedMobsKeepContinuousMovementAndTwentyEntityTicks(GameTestHelper h) {
        var pos=center(h); var cow=EntityType.COW.create(h.getLevel()); cow.setPos(pos); cow.setNoGravity(true);
        try {
            field(h,pos,16); int tickCount=cow.tickCount;
            for(int i=0;i<20;i++) {
                cow.setDeltaMovement(.1,0,0); double previous=cow.getX(); h.getLevel().tickNonPassenger(cow);
                h.assertTrue(cow.getX()>previous+.005 && cow.getX()<previous+.04,"Each slowed mob frame advances a small continuous step, tick="+i+" dx="+(cow.getX()-previous));
            }
            h.assertTrue(cow.tickCount-tickCount==20,"Mob entity physics no longer skips three out of four ticks");
        } finally { cow.discard(); TemporalBubbleManager.stopAllBubbles(h.getLevel()); }
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="prediction")
    public void arrowInterceptUsesTheTurningRouteAndRelativeSweep(GameTestHelper h) {
        var pos=center(h); var floor=BlockPos.containing(pos).below();
        try {
            for(int x=-1;x<6;x++) for(int z=-2;z<5;z++) h.getLevel().setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
            var shot=new PredictionMotion(10,-1,PredictionMotion.Kind.ARROW,pos.add(0,.8,0),new Vec3(.15,0,0),Vec3.ZERO,
                    0,1,1,.1F,.1F,false,true,false,0,1);
            var target=new PredictionMotion(11,-1,PredictionMotion.Kind.LIVING,pos.add(3,0,1),new Vec3(0,0,-.1),Vec3.ZERO,
                    .08,.98,.8,.4F,1.8F,true,false,false,0,1)
                    .withRoute(List.of(pos.add(3,0,0),pos.add(5,0,0)),.1);
            var path=PredictionEngine.forecast(h.getLevel(),shot,p -> 1,List.of(target));
            h.assertTrue(path.hitEntity()==11 && path.end().seconds()>.5,"Intercept must follow the target's turn, rather than extrapolate its old heading");
            var body=PredictionEngine.forecast(h.getLevel(),target,p -> 1,List.of());
            h.assertTrue(PredictionEngine.bounds(target,body.positionAt(path.end().seconds())).inflate(.36).contains(path.end().position()),"Hit marker and visible future body agree");
            var crossing=target.at(pos.add(.05,0,-1),new Vec3(0,0,2)).withRoute(List.of(),0);
            var fast=shot.at(pos.add(0,.8,0),new Vec3(.1,0,0));
            var swept=PredictionEngine.trace(h.getLevel(),fast,p -> 1,List.of(crossing));
            h.assertTrue(swept.hitEntity()==11 && swept.end().seconds()<.05,"A target crossing the arrow between ticks cannot be missed");
            var crossed=PredictionEngine.forecast(h.getLevel(),crossing,p -> 1,List.of());
            h.assertTrue(PredictionEngine.bounds(crossing,crossed.positionAt(swept.end().seconds())).inflate(.31).contains(swept.end().position()),"Fast-crossing hit silhouette uses the fractional contact time");
        } finally {for(int x=-1;x<6;x++) for(int z=-2;z<5;z++) h.getLevel().setBlock(floor.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction")
    public void distantOwnedFlightRetainsServerGeometryAndTargets(GameTestHelper h) {
        var owner=RewindRuneTests.survivalPlayer(h);var pos=center(h);owner.setPos(pos);
        var arrow=new Arrow(h.getLevel(),pos.x+80,pos.y+.8,pos.z,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);arrow.setOwner(owner);arrow.setNoGravity(true);arrow.setDeltaMovement(1,0,0);
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(pos.add(84,0,0));
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        // 1.21.1 promotes newly loaded entity sections on the following server ticks.
        // Forecasts may only query tracked, loaded entities, so wait for that native promotion.
        var forced=new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        for(int x=0;x<=96;x+=16) {
            var chunk=new net.minecraft.world.level.ChunkPos(BlockPos.containing(pos.add(x,0,0)));
            if(!h.getLevel().getForcedChunks().contains(chunk.toLong())) {
                h.getLevel().setChunkForced(chunk.x,chunk.z,true);forced.add(chunk);
            }
        }
        h.runAfterDelay(5, () -> {
            try {
                h.getLevel().addFreshEntity(arrow);h.getLevel().addFreshEntity(cow);
                field(h,pos,16);
                h.assertTrue(PredictionServer.eligible(owner,arrow),"Own projectile remains tracked after leaving the bubble and 48-block discovery range");
                h.assertTrue(h.getLevel().getEntities(owner,owner.getBoundingBox().inflate(256),e -> true).contains(arrow),"Distant fixture must reach a tracked loaded entity section");
                var packet=PredictionServer.snapshot(owner);
                var path=packet.forecasts().stream().filter(p -> p.motion().entityId()==arrow.getId()).findFirst().orElseThrow();
                h.assertTrue(path.hitEntity()==cow.getId(),"A far shot includes a target beyond the old discovery range");
                packet.toBytes(buf);var decoded=new PredictionSnapshotPacket(buf);
                var wire=decoded.forecasts().stream().filter(p -> p.motion().entityId()==arrow.getId()).findFirst().orElseThrow();
                h.assertTrue(wire.hitEntity()==cow.getId() && wire.end().position().distanceTo(path.end().position())<.0001,"Server-computed distant geometry survives without client entity/chunk access");
                h.assertTrue(buf.writerIndex()<100000,"Forecast packet stays bounded");
            } finally {
                buf.release();arrow.discard();cow.discard();TemporalBubbleManager.stopAllBubbles(h.getLevel());
                h.getLevel().getServer().getPlayerList().remove(owner);owner.discard();
                for(var chunk:forced) h.getLevel().setChunkForced(chunk.x,chunk.z,false);
            }
            h.succeed();
        });
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="prediction")
    public void nearMobForecastMatchesNativeNavigationMovement(GameTestHelper h) {
        var pos=center(h);var floor=BlockPos.containing(pos).below();
        int[] controls={0};
        var cow=new net.minecraft.world.entity.animal.Cow(EntityType.COW,h.getLevel()) {
            {
                goalSelector.removeAllGoals(g -> true);targetSelector.removeAllGoals(g -> true);
                moveControl=new net.minecraft.world.entity.ai.control.MoveControl(this) {
                    @Override public void tick() {controls[0]++;super.tick();}
                };
            }
        };
        cow.setPos(pos);
        try {
            for(int x=-2;x<14;x++) for(int z=-2;z<9;z++) h.getLevel().setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
            field(h,pos,32);cow.setOnGround(true);cow.getNavigation().moveTo(pos.x+8,pos.y,pos.z+4,1);
            Vec3 last=pos,step=Vec3.ZERO;
            for(int i=0;i<24;i++) {h.getLevel().tickNonPassenger(cow);step=cow.position().subtract(last);last=cow.position();}
            h.assertTrue(controls[0]==24,"Navigation/move controls advance on every slowed entity frame");
            var seed=PredictionPhysics.capture(cow,step,false,0,.25);
            h.assertTrue(!seed.route().isEmpty() && seed.routeSpeed()>0,"Capture an actual walking navigation plan: route="+seed.route()+" speed="+seed.routeSpeed()+" pos="+cow.position());
            var forecast=PredictionEngine.forecast(h.getLevel(),seed,p -> .25,List.of());
            for(int i=1;i<=40;i++) {
                h.getLevel().tickNonPassenger(cow);
                if(i==10 || i==20 || i==40) {
                    double error=cow.position().distanceTo(forecast.positionAt(i*.05));
                    h.assertTrue(error<.35,"Near-future mob projection must track real navigation, ticks="+i+" error="+error);
                }
            }
        } finally {cow.discard();TemporalBubbleManager.stopAllBubbles(h.getLevel());for(int x=-2;x<14;x++) for(int z=-2;z<9;z++) h.getLevel().setBlock(floor.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction")
    public void highSpeedRangeLimitStillChecksTheNearerWall(GameTestHelper h) {
        var pos=center(h);var block=BlockPos.containing(pos).offset(4,0,0);
        try {
            h.getLevel().setBlock(block,Blocks.STONE.defaultBlockState(),3);
            var shot=new PredictionMotion(99,-1,PredictionMotion.Kind.NATIVE,pos.add(0,.5,0),new Vec3(300,0,0),Vec3.ZERO,
                    0,1,1,.1F,.1F,false,true,false,0,1);
            var path=PredictionEngine.forecast(h.getLevel(),shot,p -> 1,List.of());
            h.assertTrue(path.impact() && Math.abs(path.end().position().x-block.getX())<.001,"High-speed segment clips its nearer wall before the range cutoff");
        } finally {h.getLevel().setBlock(block,Blocks.AIR.defaultBlockState(),3);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="prediction_teleport",timeoutTicks=60)
    public void endermanTellRevealsTheExactReservedLanding(GameTestHelper h) {
        var owner=RewindRuneTests.survivalPlayer(h);var pos=center(h);owner.setPos(pos.add(0,0,-3));
        var mob=EntityType.ENDERMAN.create(h.getLevel());mob.setPos(pos);mob.setNoAi(true);
        var ground=BlockPos.containing(pos).below();
        for(int x=-1;x<7;x++) for(int z=-4;z<2;z++) h.getLevel().setBlock(ground.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        h.getLevel().addFreshEntity(mob);
        var watch=new ItemStack(ModItems.CREATIVE_WATCH.get());owner.setItemInHand(InteractionHand.MAIN_HAND,watch);
        AbstractWatchItem.setBubbleRadius(watch,16);TemporalBubbleManager.startBubble(h.getLevel(),owner,100,TimeMode.TIME_PREDICTION);
        try {
            boolean selected=((com.timestop.mixin.EndermanTeleportAccessor)mob).timestop$teleport(pos.x+4,pos.y+3,pos.z);
            h.assertTrue(selected && mob.position().distanceTo(pos)<.001,"Watched teleport gets a brief tell before the actual jump");
            var previews=PredictionTeleports.previews(owner);
            h.assertTrue(previews.size()==1 && previews.get(0).destination().distanceTo(pos.add(4,0,0))<.001,"Ghost uses the validated native destination");
            h.runAfterDelay(6,() -> {
                try {h.assertTrue(PredictionTeleports.pending(mob) && mob.position().distanceTo(pos)<.001,"Tell stays visible during its reserved windup");}
                catch(Throwable t) {cleanupTeleport(h,owner,mob,ground);throw t;}
            });
            h.runAfterDelay(14,() -> {
                try {
                    h.assertTrue(mob.position().distanceTo(pos.add(4,0,0))<.001 && !PredictionTeleports.pending(mob),"Enderman lands exactly at its promised destination");
                    TemporalBubbleManager.stopAllBubbles(h.getLevel());
                    boolean normal=((com.timestop.mixin.EndermanTeleportAccessor)mob).timestop$teleport(pos.x,pos.y+3,pos.z);
                    h.assertTrue(normal && mob.position().distanceTo(pos)<.001,"Ordinary teleports remain immediate outside prediction");
                    h.succeed();
                } finally {cleanupTeleport(h,owner,mob,ground);}
            });
        } catch(Throwable t) {cleanupTeleport(h,owner,mob,ground);throw t;}
    }
    private static void cleanupTeleport(GameTestHelper h,net.minecraft.server.level.ServerPlayer owner,net.minecraft.world.entity.monster.EnderMan mob,BlockPos ground) {
        PredictionTeleports.clear();mob.discard();TemporalBubbleManager.stopAllBubbles(h.getLevel());h.getLevel().getServer().getPlayerList().remove(owner);owner.discard();
        for(int x=-1;x<7;x++) for(int z=-4;z<2;z++) h.getLevel().setBlock(ground.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);
    }
}
