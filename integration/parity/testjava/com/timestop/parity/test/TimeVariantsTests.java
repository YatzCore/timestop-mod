package com.timestop.parity.test;

import com.timestop.core.*;
import com.timestop.item.*;
import com.timestop.config.TimeStopConfig;
import com.timestop.network.*;
import com.timestop.prediction.PredictionTime;
import com.timestop.combat.DecelerationFieldManager;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class TimeVariantsTests {
    private static final String EMPTY_STRUCTURE="parity_empty";
    private static Vec3 center(GameTestHelper h) {
        var b=h.absolutePos(new BlockPos(4,4,4));var p=new Vec3(b.getX()+.5,240,b.getZ()+.5);
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) h.getLevel().getChunkAt(BlockPos.containing(p).offset(x*16,0,z*16));return p;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,Vec3 p,boolean op) {
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"variants-test"),net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override public boolean hasPermissions(int level) { return op; }
        };
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {}
        };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(p);return player;
    }
    private static void stop(GameTestHelper h) {
        if(TimeStopManager.isGlobalTimeStopActive()) TimeStopManager.resumeTime(h.getLevel());
        TemporalBubbleManager.stopAllBubbles(h.getLevel());
    }
    private static void worldTick(GameTestHelper h) {
        boolean old=h.getLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
        long game=h.getLevel().getGameTime(), day=h.getLevel().getDayTime();
        try {
            h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(true,h.getLevel().getServer());
            var method=net.minecraft.server.level.ServerLevel.class.getDeclaredMethod("tickTime");method.setAccessible(true);method.invoke(h.getLevel());
            h.assertTrue(h.getLevel().getGameTime()==game+1 && h.getLevel().getDayTime()==day+1,"World time advances by exactly one normal tick");
        } catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
        finally {h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(old,h.getLevel().getServer());h.getLevel().setDayTime(day);
            ((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(game);}
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="time_variants")
    public void variantsPreserveOrdinalsWatchSelectionAndNetwork(GameTestHelper h) {
        h.assertTrue(TimeMode.TIME_PREDICTION.ordinal()==7 && TimeMode.COMBAT_SLOW.ordinal()==8 && TimeMode.SELF_ACCELERATION.ordinal()==9,"Existing mode identities remain stable");
        var player=player(h,center(h),false);var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WATCH.get()));
            for(var mode:List.of(TimeMode.COMBAT_SLOW,TimeMode.SELF_ACCELERATION)) {
                for(var tier:WatchTier.values()) h.assertTrue(tier.isModeUnlocked(mode),"Variants inherit their base mode tier");
                var packet=new SelectTimeModePacket(mode,net.minecraft.world.InteractionHand.MAIN_HAND);buf.clear();packet.toBytes(buf);new SelectTimeModePacket(buf).handle(player);
                var copy=ItemStack.parseOptional(h.getLevel().registryAccess(),(net.minecraft.nbt.CompoundTag)player.getMainHandItem().save(h.getLevel().registryAccess()));
                h.assertTrue(AbstractWatchItem.getMode(copy)==mode && mode.alternate().alternate()==mode,"Variant selection survives packet and item persistence");
            }
        } finally {buf.release();player.discard();}h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="time_variants")
    public void combatSlowAffectsOnlyMobsAndAllProjectilesLocallyAndGlobally(GameTestHelper h) {
        var p=center(h);var owner=player(h,p,false);var other=player(h,p.add(1,0,0),false);
        var cow=EntityType.COW.create(h.getLevel());cow.setPos(p.add(2,0,0));
        var item=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),p.x,p.y,p.z,new ItemStack(Items.STONE));
        var tnt=new net.minecraft.world.entity.item.PrimedTnt(h.getLevel(),p.x,p.y,p.z,null);
        var arrow=new Arrow(h.getLevel(),p.x,p.y+1,p.z,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);arrow.setOwner(owner);
        double old=TimeStopConfig.COMMON.slowMotionRate.get();
        try {
            TimeStopConfig.COMMON.slowMotionRate.set(.25);
            var field=new TemporalBubble(UUID.randomUUID(),owner.getUUID(),h.getLevel().dimension(),p,8,TimeMode.COMBAT_SLOW,100,WatchTier.COPPER,null,0,Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(),field,null);
            h.assertTrue(field.getTimeDilationFactor(owner)==1 && field.getTimeDilationFactor(other)==1 && field.getTimeDilationFactor(item)==1 && field.getTimeDilationFactor(tnt)==1,"Players, items and TNT keep native clocks");
            h.assertTrue(PredictionTime.rate(cow)==.25 && DecelerationFieldManager.getTimeRate(arrow)==.25,"Mob and owner's own arrow are slowed");
            cow.setPos(p.add(10,0,0));h.assertTrue(PredictionTime.rate(cow)==1,"Local boundary restores normal motion");cow.setPos(p.add(2,0,0));
            stop(h);TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.COMBAT_SLOW);
            h.assertTrue(TimeStopManager.getServerTickMs()==50 && PredictionTime.rate(owner)==1 && PredictionTime.rate(other)==1 && PredictionTime.rate(item)==1,"Global combat mode does not throttle world or other players");
            h.assertTrue(PredictionTime.rate(cow)==.25 && DecelerationFieldManager.getTimeRate(arrow)==.25,"Global combat mode slows mobs and owner shots");
            worldTick(h);tnt.setNoGravity(true);tnt.setFuse(80);
            for(int i=0;i<20;i++) h.getLevel().tickNonPassenger(tnt);
            h.assertTrue(tnt.getFuse()==60,"Non-projectile TNT fuse keeps normal speed in combat slow");
        } finally {stop(h);TimeStopConfig.COMMON.slowMotionRate.set(old);owner.discard();other.discard();cow.discard();arrow.discard();item.discard();tnt.discard();}h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="time_variants")
    public void combatArrowMovementStaysContinuousAndCancelsImmediately(GameTestHelper h) {
        var p=center(h);var owner=player(h,p,false);var arrow=new Arrow(h.getLevel(),p.x,p.y,p.z,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);arrow.setNoGravity(true);arrow.setOwner(owner);arrow.setDeltaMovement(.2,0,0);
        double old=TimeStopConfig.COMMON.slowMotionRate.get();
        try {
            TimeStopConfig.COMMON.slowMotionRate.set(.25);TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.COMBAT_SLOW);
            double expected=0,velocity=.2;
            for(int i=0;i<20;i++) {
                var before=arrow.position();h.getLevel().tickNonPassenger(arrow);
                expected+=velocity*.25;velocity*=1+(.99-1)*.25;
                h.assertTrue(arrow.position().distanceTo(before)>.04,"Arrow moves on every real tick instead of freezing between steps");
            }
            h.assertTrue(Math.abs(arrow.getX()-p.x-expected)<.001,"Native continuous projectile physics uses combat rate");
            stop(h);h.assertTrue(DecelerationFieldManager.getTimeRate(arrow)==1,"Cancellation removes rate immediately");
            com.timestop.TimeStopMod.LOGGER.info("COMBAT_CONTINUOUS_PASS");
        } finally {stop(h);TimeStopConfig.COMMON.slowMotionRate.set(old);owner.discard();arrow.discard();}h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="time_variants")
    public void selfAccelerationRunsTheOwnersPersonalClock(GameTestHelper h) {
        var p=center(h);var owner=player(h,p,false);var other=player(h,p.add(3,0,0),false);double old=TimeStopConfig.COMMON.fastForwardRate.get();
        try {
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);
            for(var pl:List.of(owner,other)) {
                pl.setNoGravity(true);pl.resetAttackStrengthTicker();
                pl.getFoodData().setFoodLevel(10);pl.getFoodData().setSaturation(0);
                pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD));
                pl.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                pl.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,100));
                pl.getCooldowns().addCooldown(Items.ENDER_PEARL,20);
            }
            TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.SELF_ACCELERATION);
            h.assertTrue(SelfAcceleration.rate(owner)==5 && SelfAcceleration.rate(other)==1,"Only the owner is accelerated");
            owner.aiStep();other.aiStep();
            h.assertTrue(owner.getUseItemRemainingTicks()==other.getUseItemRemainingTicks()-4,"Eating runs 5x for the owner: "+owner.getUseItemRemainingTicks()+" vs "+other.getUseItemRemainingTicks());
            h.assertTrue(owner.getAttackStrengthScale(0)>other.getAttackStrengthScale(0)+.5F,"Attack recharge runs 5x for the owner");
            h.assertTrue(owner.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED).getDuration()==96
                    && other.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED).getDuration()==100,"Effect timers run 5x for the owner only");
            h.assertTrue(owner.getCooldowns().getCooldownPercent(Items.ENDER_PEARL,0)<other.getCooldowns().getCooldownPercent(Items.ENDER_PEARL,0),"Item cooldowns run 5x for the owner");
            for(int i=0;i<10;i++) {owner.aiStep();other.aiStep();}
            h.assertTrue(!owner.isUsingItem() && owner.getFoodData().getFoodLevel()>10 && other.isUsingItem(),"Owner finishes eating while the other player is still chewing");
            for(var pl:List.of(owner,other)) {pl.setHealth(10);pl.getFoodData().setFoodLevel(20);pl.getFoodData().setSaturation(5);}
            for(int i=0;i<3;i++) {owner.aiStep();other.aiStep();}
            h.assertTrue(owner.getHealth()>10 && other.getHealth()==10,"Natural healing runs 5x for the owner");
            var stone=Blocks.STONE.defaultBlockState();var at=h.absolutePos(new BlockPos(1,1,1));
            h.assertTrue(Math.abs(stone.getDestroyProgress(owner,h.getLevel(),at)-stone.getDestroyProgress(other,h.getLevel(),at)*5)<1e-6,"Block breaking progresses 5x faster for the owner");
            stop(h);
            h.assertTrue(SelfAcceleration.rate(owner)==1 && Math.abs(stone.getDestroyProgress(owner,h.getLevel(),at)-stone.getDestroyProgress(other,h.getLevel(),at))<1e-6,"Nothing stays accelerated after cancellation");
            com.timestop.TimeStopMod.LOGGER.info("SELF_ACCELERATION_PERSONAL_CLOCK_PASS");
        } finally {stop(h);TimeStopConfig.COMMON.fastForwardRate.set(old);owner.discard();other.discard();}h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="time_variants")
    public void selfAccelerationAlsoSpeedsUpHarmUnlessCasual(GameTestHelper h) {
        var p=center(h);var owner=player(h,p,false);var other=player(h,p.add(3,0,0),false);double old=TimeStopConfig.COMMON.fastForwardRate.get();
        try {
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);
            TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.SELF_ACCELERATION);
            for(boolean casual:new boolean[]{false,true}) {
                PersonalTime.setCasual(owner.getUUID(),casual);
                int harm=casual?0:4;
                for(var pl:List.of(owner,other)) {
                    pl.setNoGravity(true);pl.removeAllEffects();pl.clearFire();pl.setTicksFrozen(0);pl.isInPowderSnow=false;
                    pl.setAirSupply(pl.getMaxAirSupply());pl.invulnerableTime=0;pl.setHealth(pl.getMaxHealth());
                    pl.getFoodData().setFoodLevel(20);pl.getFoodData().setSaturation(5);
                }
                // Breath underwater (drowning) - water at feet and eyes.
                for(var pl:List.of(owner,other)) {var b=BlockPos.containing(pl.position());h.getLevel().setBlockAndUpdate(b,Blocks.WATER.defaultBlockState());h.getLevel().setBlockAndUpdate(b.above(),Blocks.WATER.defaultBlockState());}
                owner.baseTick();other.baseTick();owner.aiStep();other.aiStep();
                h.assertTrue(owner.getAirSupply()==other.getAirSupply()-harm,"Drowning "+(casual?"keeps normal speed in Casual":"runs 5x")+": "+owner.getAirSupply()+" vs "+other.getAirSupply());
                for(var pl:List.of(owner,other)) {var b=BlockPos.containing(pl.position());h.getLevel().setBlockAndUpdate(b,Blocks.AIR.defaultBlockState());h.getLevel().setBlockAndUpdate(b.above(),Blocks.AIR.defaultBlockState());pl.baseTick();}
                // Burning, freezing, hit immunity, harmful and helpful effects.
                for(var pl:List.of(owner,other)) {
                    pl.setRemainingFireTicks(100);pl.invulnerableTime=20;
                    pl.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,100));
                    pl.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION,100));
                }
                owner.aiStep();other.aiStep();
                h.assertTrue(owner.getRemainingFireTicks()==other.getRemainingFireTicks()-harm,"Burning "+(casual?"normal in Casual":"runs 5x")+": "+owner.getRemainingFireTicks()+" vs "+other.getRemainingFireTicks());
                h.assertTrue(owner.invulnerableTime==other.invulnerableTime-harm,"Hit immunity "+(casual?"normal in Casual":"runs 5x")+": "+owner.invulnerableTime+" vs "+other.invulnerableTime);
                h.assertTrue(owner.getEffect(net.minecraft.world.effect.MobEffects.POISON).getDuration()==other.getEffect(net.minecraft.world.effect.MobEffects.POISON).getDuration()-harm,"Harmful effects "+(casual?"normal in Casual":"run 5x"));
                h.assertTrue(owner.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION).getDuration()==other.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION).getDuration()-4,"Helpful effects always run 5x");
                // Freezing in powder snow (no fire: powder snow puts fire out).
                for(var pl:List.of(owner,other)) {pl.clearFire();pl.isInPowderSnow=true;pl.setTicksFrozen(0);}
                owner.aiStep();other.aiStep();
                h.assertTrue(owner.getTicksFrozen()==other.getTicksFrozen()+harm,"Freezing "+(casual?"normal in Casual":"runs 5x")+": "+owner.getTicksFrozen()+" vs "+other.getTicksFrozen());
            }
            h.assertTrue(PersonalTime.acceleratedFall(-.5,.08,5)<-.8 && PersonalTime.acceleratedFall(.3,.08,5)==.3 && PersonalTime.acceleratedFall(-7.9,.08,50)==PersonalTime.MAX_FALL_SPEED,"Falling speeds up only while falling, within the server movement limit");
            stop(h);PersonalTime.setCasual(owner.getUUID(),false);
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WATCH.get()));
            TemporalBubbleManager.startBubble(h.getLevel(),owner,100,TimeMode.FAST_FORWARD);
            h.assertTrue(PersonalTime.extraRate(owner)==5,"Your own Fast Forward field runs your personal clock 5x on the server");
            stop(h);TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.FAST_FORWARD);
            h.assertTrue(PersonalTime.extraRate(owner)==1,"Global Fast Forward already speeds the whole server clock");
            stop(h);h.assertTrue(PersonalTime.extraRate(owner)==1,"Nothing stays accelerated");
            com.timestop.TimeStopMod.LOGGER.info("SELF_ACCELERATION_HARM_AND_CASUAL_PASS");
        } finally {stop(h);PersonalTime.setCasual(owner.getUUID(),false);TimeStopConfig.COMMON.fastForwardRate.set(old);owner.discard();other.discard();}h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="time_variants")
    public void selfAccelerationOnlyChangesOwnerInputAndLeavesNoPersistentBoost(GameTestHelper h) {
        var p=center(h);var owner=player(h,p,false);var other=player(h,p.add(1,0,0),false);var cow=EntityType.COW.create(h.getLevel());cow.setPos(p);
        var arrow=new Arrow(h.getLevel(),p.x,p.y,p.z,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);arrow.setOwner(owner);double old=TimeStopConfig.COMMON.fastForwardRate.get();
        try {
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.SELF_ACCELERATION);
            worldTick(h);
            var attributes=owner.getAttributes().save();var abilities=new net.minecraft.nbt.CompoundTag();owner.getAbilities().addSaveData(abilities);
            h.assertTrue(TimeStopManager.getServerTickMs()==50 && PredictionTime.rate(cow)==1 && DecelerationFieldManager.getTimeRate(arrow)==1,"Everything else keeps normal time");
            owner.setDeltaMovement(Vec3.ZERO);other.setDeltaMovement(Vec3.ZERO);owner.moveRelative(.1F,new Vec3(0,0,1));other.moveRelative(.1F,new Vec3(0,0,1));
            h.assertTrue(Math.abs(owner.getDeltaMovement().length()/other.getDeltaMovement().length()-5)<.001,"Only the owner receives 5x voluntary movement");
            stop(h);owner.setDeltaMovement(Vec3.ZERO);owner.moveRelative(.1F,new Vec3(0,0,1));
            h.assertTrue(Math.abs(owner.getDeltaMovement().length()-.1)<.001 && SelfAcceleration.rate(owner)==1,"No boost survives cancellation");
            var after=new net.minecraft.nbt.CompoundTag();owner.getAbilities().addSaveData(after);
            h.assertTrue(attributes.equals(owner.getAttributes().save()) && abilities.equals(after),"No attributes or abilities were changed");
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WATCH.get()));
            TemporalBubbleManager.startBubble(h.getLevel(),owner,100,TimeMode.SELF_ACCELERATION);
            h.assertTrue(SelfAcceleration.rate(owner)==5 && SelfAcceleration.rate(other)==1,"Local owner-only acceleration");
            TemporalBubbleManager.stopAllBubbles(h.getLevel());h.assertTrue(SelfAcceleration.rate(owner)==1,"Local cancellation cleans up");
            com.timestop.TimeStopMod.LOGGER.info("SELF_ACCELERATION_PASS");
        } finally {stop(h);TimeStopConfig.COMMON.fastForwardRate.set(old);owner.discard();other.discard();cow.discard();arrow.discard();}h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="time_variants")
    public void stoppedWaterCanBeEnteredWhenSwimmingIsEnabled(GameTestHelper h) {
        var p=center(h);var b=BlockPos.containing(p);var owner=player(h,new Vec3(p.x,b.getY()+1.01,p.z),false);
        boolean old=TimeStopConfig.COMMON.enableWaterWalkingInStasis.get();
        try {
            h.getLevel().setBlock(b,Blocks.WATER.defaultBlockState(),3);TimeStopManager.startGlobalTimeStop(h.getLevel(),owner,100,TimeMode.TIME_STOP);
            TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(true);
            h.assertTrue(!h.getLevel().getBlockState(b).getCollisionShape(h.getLevel(),b,CollisionContext.of(owner)).isEmpty(),"Default stopped water supports walking");
            owner.move(MoverType.SELF,new Vec3(0,-.5,0));h.assertTrue(owner.getY()>=b.getY()+1-.001,"Frozen surface blocks entry in walking mode");
            TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(false);
            h.assertTrue(h.getLevel().getBlockState(b).getCollisionShape(h.getLevel(),b,CollisionContext.of(owner)).isEmpty(),"Swimming removes surface collision");
            owner.move(MoverType.SELF,new Vec3(0,-.5,0));h.assertTrue(owner.getY()<b.getY()+.6,"Actual native movement enters stopped water");
            h.assertTrue(TimeStopManager.isGlobalTimeStopActive(),"Swimming keeps Time Stop active");
            com.timestop.TimeStopMod.LOGGER.info("STASIS_SWIMMING_PASS");
        } finally {stop(h);TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(old);h.getLevel().setBlock(b,Blocks.AIR.defaultBlockState(),3);owner.discard();}h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="time_variants")
    public void swimmingSettingIsAuthorizedAndSynchronizedWithoutChangingSpeeds(GameTestHelper h) {
        var p=center(h);var regular=player(h,p,false);var op=player(h,p,true);boolean old=TimeStopConfig.COMMON.enableWaterWalkingInStasis.get();
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());double speed=TimeStopConfig.COMMON.slowMotionRate.get();
        try {
            TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(true);new UpdateSwimmingConfigPacket(true).handle(regular);
            h.assertTrue(TimeStopConfig.COMMON.enableWaterWalkingInStasis.get(),"Ordinary clients cannot change server fluid collisions");
            new UpdateSwimmingConfigPacket(true).toBytes(buf);new UpdateSwimmingConfigPacket(buf).handle(op);
            h.assertTrue(!TimeStopConfig.COMMON.enableWaterWalkingInStasis.get() && TimeStopConfig.COMMON.slowMotionRate.get()==speed,"Authorized swimming setting does not overwrite speeds");
            buf.clear();SyncSpeedConfigPacket.current().toBytes(buf);var copy=new SyncSpeedConfigPacket(buf);buf.clear();copy.toBytes(buf);
            h.assertTrue(!buf.getBoolean(buf.writerIndex()-1),"Server correction and join synchronization carry fluid collisions");
        } finally {buf.release();TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(old);TimeStopConfig.save();regular.discard();op.discard();}h.succeed();
    }
}
