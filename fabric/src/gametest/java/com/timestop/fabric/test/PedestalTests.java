package com.timestop.fabric.test;

import com.timestop.core.*;
import com.timestop.item.*;
import com.timestop.pedestal.*;
import com.timestop.combat.DecelerationFieldManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class PedestalTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestal_entity_clock")
    public void stationarySlowMotionBudgetsRealEntityTicks(GameTestHelper h) {
        Vec3 center=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,30,5)));
        var bubble=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),center,3,
                TimeMode.SLOW_MOTION,0,WatchTier.GILDED,null,0,Set.of()).stationary(true);
        var inside=new net.minecraft.world.entity.monster.Zombie(h.getLevel());
        var outside=new net.minecraft.world.entity.monster.Zombie(h.getLevel());
        var arrow=new Arrow(h.getLevel(),center.x,center.y,center.z,new ItemStack(Items.ARROW),null);
        inside.setPos(center); outside.setPos(center.add(10,0,0));
        inside.setNoAi(true); outside.setNoAi(true);
        inside.setNoGravity(true); outside.setNoGravity(true); arrow.setNoGravity(true);
        double previous=com.timestop.config.TimeStopConfig.COMMON.slowMotionRate.get();
        try {
            com.timestop.config.TimeStopConfig.COMMON.slowMotionRate.set(.25);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(),bubble,null);
            h.assertTrue(TimeStopManager.getServerTickMs()==50,"Pedestal must keep the normal server clock");
            for(int i=0;i<8;i++) {
                h.getLevel().tickNonPassenger(inside); h.getLevel().tickNonPassenger(outside);
                h.getLevel().tickNonPassenger(arrow);
            }
            h.assertTrue(inside.tickCount==2,"Inside mob must receive 2 of 8 ticks, got "+inside.tickCount);
            h.assertTrue(arrow.tickCount==2,"Inside projectile must receive 2 of 8 ticks, got "+arrow.tickCount);
            h.assertTrue(outside.tickCount==8,"Outside mob must tick normally");
        } finally {
            TemporalBubbleManager.stopBubble(h.getLevel(),bubble);
            inside.discard(); outside.discard(); arrow.discard();
            com.timestop.config.TimeStopConfig.COMMON.slowMotionRate.set(previous);
        }
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE, batch="pedestal_player_speed")
    public void stationaryFastForwardDoesNotAcceleratePlayers(GameTestHelper h) {
        var player=RewindRuneTests.survivalPlayer(h);
        Vec3 center=player.position().add(0,player.getBbHeight()*.5,0);
        var bubble=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),center,3,
                TimeMode.FAST_FORWARD,0,WatchTier.GILDED,null,0,Set.of()).stationary(true);
        float normalSpeed=player.getSpeed();
        try {
            TemporalBubbleManager.putStationaryBubble(h.getLevel(),bubble,null);
            h.assertTrue(FastForwardManager.localPlayerRate(player)==1,"Non-owner player must have normal movement/use/cooldown rate");
            h.assertTrue(FastForwardManager.modifyBreakSpeed(player,2)==2,"Mining speed must remain unchanged");
            h.assertTrue(player.getSpeed()==normalSpeed,"Actual movement speed must remain unchanged");
            h.assertTrue(bubble.getTimeDilationFactor(player)==1,"Player entity ticking must remain unchanged");
        } finally { TemporalBubbleManager.stopBubble(h.getLevel(),bubble); player.discard(); }
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE, batch="bullet_flow_exemption")
    public void bulletSweepRetainsExplicitFlowExemption(GameTestHelper h) {
        var player=RewindRuneTests.survivalPlayer(h);
        Vec3 center=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,30,5)));
        var freeze=new TemporalBubble(UUID.randomUUID(),player.getUUID(),h.getLevel().dimension(),center,2,TimeMode.TIME_STOP,0,WatchTier.DIAMOND,null,0,Set.of()).stationary(true);
        var flow=TimeStopManager.getProjectileStasisMode();
        boolean allowed=com.timestop.config.TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.get();
        var arrow=new Arrow(h.getLevel(),center.x-10,center.y-.25,center.z, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null); arrow.setOwner(player); arrow.setDeltaMovement(20,0,0);
        try {
            com.timestop.config.TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.set(true);
            TimeStopManager.setProjectileStasisMode(TimeStopManager.ProjectileStasisMode.FLOWING);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(),freeze,null);
            h.assertFalse(com.timestop.combat.ProjectileStasisSweep.intercept(arrow),"Owner's explicitly flowing bullet is exempt");
            TimeStopManager.setProjectileStasisMode(TimeStopManager.ProjectileStasisMode.SUSPENDED);
            h.assertTrue(com.timestop.combat.ProjectileStasisSweep.intercept(arrow),"Suspended mode stops even owner's bullet at entry");
        } finally {
            TemporalBubbleManager.stopBubble(h.getLevel(),freeze); arrow.discard(); player.discard();
            com.timestop.config.TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.set(allowed);
            TimeStopManager.setProjectileStasisMode(flow);
        }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="bullet_boundary")
    public void fastBulletStopsAtEntryAndResumes(GameTestHelper h) {
        Vec3 center=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,30,5)));
        var bubble=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),center,2,TimeMode.TIME_STOP,0,WatchTier.DIAMOND,null,0,Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(),bubble,null);
        var arrow=new Arrow(h.getLevel(),center.x-10,center.y-.25,center.z, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
        arrow.setDeltaMovement(20,0,0);
        try {
            h.assertTrue(com.timestop.combat.TaczProjectileCompat.beforeTick(arrow),"Native bullet hook cancels full path before hit detection");
            h.assertTrue(Math.abs(arrow.getX()-(center.x-2))<.0001,"High-speed bullet stops at entry, not several blocks inside");
            h.assertTrue(TimeStopManager.isProjectileSuspended(arrow) && arrow.getDeltaMovement().lengthSqr()==0,"Bullet is suspended with zero velocity");
            h.assertTrue(TimeStopManager.getSuspendedVelocity(arrow).equals(new Vec3(20,0,0)),"Full incoming velocity retained");
            TemporalBubbleManager.stopBubble(h.getLevel(),bubble);
            h.assertTrue(!TimeStopManager.isProjectileSuspended(arrow) && arrow.getDeltaMovement().x==20,"Power-off resumes saved bullet velocity");
        } finally { TemporalBubbleManager.stopBubble(h.getLevel(),bubble); arrow.discard(); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="bullet_overlap")
    public void bulletSweepRespectsStrongerOverlap(GameTestHelper h) {
        Vec3 center=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,30,5)));
        var freeze=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),center,4,TimeMode.TIME_STOP,0,WatchTier.DIAMOND,null,0,Set.of()).stationary(true);
        var fast=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),center.add(-4,0,0),4,TimeMode.FAST_FORWARD,0,WatchTier.NETHERITE,null,0,Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(),freeze,null); TemporalBubbleManager.putStationaryBubble(h.getLevel(),fast,null);
        var arrow=new Arrow(h.getLevel(),center.x-10,center.y-.25,center.z, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null); arrow.setDeltaMovement(20,0,0);
        try {
            h.assertTrue(com.timestop.combat.ProjectileStasisSweep.intercept(arrow),"Stops where dominant effect becomes freeze");
            h.assertTrue(Math.abs(arrow.getX()-center.x)<.0001,"Crosses stronger field and stops at its exit within weaker freeze");
        } finally { TemporalBubbleManager.stopBubble(h.getLevel(),freeze); TemporalBubbleManager.stopBubble(h.getLevel(),fast); arrow.discard(); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="bullet_collision_order")
    public void nativeImpactsCannotReachThroughSphere(GameTestHelper h) {
        Vec3 center=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,30,5)));
        var freeze=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),center,2,TimeMode.TIME_STOP,0,WatchTier.DIAMOND,null,0,Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(),freeze,null);
        var arrow=new Arrow(h.getLevel(),center.x-10,center.y-.25,center.z, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null); arrow.setDeltaMovement(20,0,0);
        try {
            h.assertFalse(com.timestop.combat.ProjectileStasisSweep.beforeImpact(arrow,arrow.position().add(3,0,0)),"Impact before sphere remains native");
            h.assertTrue(com.timestop.combat.ProjectileStasisSweep.beforeImpact(arrow,arrow.position().add(12,0,0)),"Impact inside sphere is canceled before damage, including piercing rounds");
            h.assertTrue(Math.abs(arrow.getX()-(center.x-2))<.0001,"Impact guard parks bullet at entry");
        } finally { TemporalBubbleManager.stopBubble(h.getLevel(),freeze); arrow.discard(); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="bullet_miss")
    public void bulletMissAndEarlierWallStayNative(GameTestHelper h) {
        Vec3 center=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,30,5)));
        var freeze=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),center,2,TimeMode.TIME_STOP,0,WatchTier.DIAMOND,null,0,Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(),freeze,null);
        var arrow=new Arrow(h.getLevel(),center.x-10,center.y-.25,center.z+3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null); arrow.setDeltaMovement(20,0,0);
        BlockPos wall=BlockPos.containing(center.add(-4,0,0));
        try {
            h.assertFalse(com.timestop.combat.ProjectileStasisSweep.intercept(arrow),"Near miss remains unaffected");
            arrow.setPos(center.x-10,center.y-.25,center.z);
            h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
            h.assertFalse(com.timestop.combat.ProjectileStasisSweep.intercept(arrow),"Wall before sphere must receive native collision");
            h.assertTrue(arrow.getX()==center.x-10,"No teleport through wall");
        } finally { h.getLevel().removeBlock(wall,false); TemporalBubbleManager.stopBubble(h.getLevel(),freeze); arrow.discard(); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestal_furnaces", timeoutTicks=100)
    public void furnaceSmeltingAcceleratesAndFreezes(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.DIAMOND_WATCH.get());
        var inside=p.getBlockPos().east();
        var outside=p.getBlockPos().east(5);
        for (var pos:List.of(inside,outside)) {
            h.getLevel().setBlockAndUpdate(pos,Blocks.FURNACE.defaultBlockState());
            var furnace=(net.minecraft.world.level.block.entity.FurnaceBlockEntity)h.getLevel().getBlockEntity(pos);
            furnace.setItem(0,new ItemStack(Items.RAW_IRON,8));
            furnace.setItem(1,new ItemStack(Items.COAL));
        }
        p.configure(TimeMode.FAST_FORWARD,2); power(h,p,true);
        h.runAfterDelay(50,() -> {
            var accelerated=(net.minecraft.world.level.block.entity.FurnaceBlockEntity)h.getLevel().getBlockEntity(inside);
            var normal=(net.minecraft.world.level.block.entity.FurnaceBlockEntity)h.getLevel().getBlockEntity(outside);
            h.assertTrue(accelerated.getItem(2).is(Items.IRON_INGOT),"Accelerated furnace smelts before 200 server ticks");
            h.assertTrue(normal.getItem(2).isEmpty(),"Furnace outside sphere retains normal smelting time");
            int progress=accelerated.saveWithoutMetadata(h.getLevel().registryAccess()).getShort("CookTime");
            p.configure(TimeMode.TIME_STOP,2); PedestalManager.serverTick();
            h.runAfterDelay(10,() -> {
                try {
                    h.assertTrue(accelerated.saveWithoutMetadata(h.getLevel().registryAccess()).getShort("CookTime")==progress,"Stasis freezes real furnace cooking progress");
                    h.succeed();
                } finally {
                    accelerated.clearContent(); normal.clearContent();
                    h.getLevel().removeBlock(inside,false); h.getLevel().removeBlock(outside,false); clean(h,p);
                }
            });
        });
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestal_radius_packet")
    public void radiusPacketRoundTripAndValidation(GameTestHelper h) {
        double originalRadius=com.timestop.config.TimeStopConfig.COMMON.creativeRadius.get();
        com.timestop.config.TimeStopConfig.COMMON.creativeRadius.set(128.0);
        var p=place(h,new BlockPos(1,2,1),ModItems.CREATIVE_WATCH.get());
        var player=RewindRuneTests.survivalPlayer(h);
        try {
            player.setPos(Vec3.atCenterOf(p.getBlockPos()).add(0,0,2)); player.openMenu(p);
            int id=player.containerMenu.containerId;
            var bytes=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                new com.timestop.network.SetPedestalRadiusPacket(id,128).toBytes(bytes);
                new com.timestop.network.SetPedestalRadiusPacket(bytes).handle(player);
            } finally { bytes.release(); }
            h.assertTrue(p.getRadius()==128,"Full creative radius survives network serialization");
            new com.timestop.network.SetPedestalRadiusPacket(id+1,2).handle(player);
            new com.timestop.network.SetPedestalRadiusPacket(id,-1).handle(player);
            new com.timestop.network.SetPedestalRadiusPacket(id,129).handle(player);
            h.assertTrue(p.getRadius()==128,"Stale menu and out-of-range packets rejected");
            player.setPos(player.position().add(30,0,0));
            new com.timestop.network.SetPedestalRadiusPacket(id,3).handle(player);
            h.assertTrue(p.getRadius()==128,"Distant player cannot change settings");
        } finally { player.closeContainer(); player.discard(); clean(h,p); com.timestop.config.TimeStopConfig.COMMON.creativeRadius.set(originalRadius); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestal_drops")
    public void breakingDropsExactlyOneUnmodifiedWatch(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.DIAMOND_WATCH.get());
        var marker=UUID.randomUUID().toString();
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, p.getWatch(), tag -> tag.putString("PedestalTest",marker)); p.getWatch().setDamageValue(31);
        p.configure(TimeMode.TIME_STOP,2); power(h,p,true);
        h.getLevel().destroyBlock(p.getBlockPos(),true);
        h.getLevel().destroyBlock(p.getBlockPos(),true);
        var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(p.getBlockPos()).inflate(5),
                item -> item.getItem().has(net.minecraft.core.component.DataComponents.CUSTOM_DATA) && marker.equals(item.getItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString("PedestalTest")));
        h.assertTrue(drops.stream().mapToInt(item -> item.getItem().getCount()).sum()==1,"Exactly one stored watch drops");
        h.assertTrue(drops.get(0).getItem().getDamageValue()==31,"Watch damage and custom NBT preserved");
        h.assertTrue(TemporalBubbleManager.getBubble(p.getFieldId())==null,"Destroyed source leaves no field");
        drops.forEach(net.minecraft.world.entity.Entity::discard); clean(h,p); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestal_rewind")
    public void rewindReconcilesFieldAndDoesNotDropWatch(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.DIAMOND_WATCH.get());
        try {
            p.configure(TimeMode.TIME_STOP,2); power(h,p,true);
            var original=p.saveWithFullMetadata(h.getLevel().registryAccess());
            p.configure(TimeMode.FAST_FORWARD,1); PedestalManager.serverTick();
            var frame=new com.timestop.core.rewind.data.TickFrame(0);
            frame.addBlockEntityDelta(com.timestop.core.rewind.data.BlockEntityDelta.create(h.getLevel().dimension(),p.getBlockPos(),ModPedestals.ENTITY.getId(),original,p.saveWithFullMetadata(h.getLevel().registryAccess())));
            var result=com.timestop.core.rewind.RewindExecutor.applyPlan(h.getLevel().getServer(),com.timestop.core.rewind.RewindExecutor.buildPlan(List.of(frame)),true);
            PedestalManager.serverTick();
            h.assertTrue(result.success() && p.getMode()==TimeMode.TIME_STOP && TemporalBubbleManager.getBubble(p.getFieldId()).getRadius()==2,"Actual rewind restores source settings");
            var marker=UUID.randomUUID().toString(); net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, p.getWatch(), tag -> tag.putString("PedestalTest",marker));
            var placement=new com.timestop.core.rewind.data.TickFrame(1);
            placement.addBlockDelta(com.timestop.core.rewind.data.BlockDelta.create(h.getLevel().dimension(),p.getBlockPos(),Blocks.AIR.defaultBlockState(),p.getBlockState(),null,p.saveWithFullMetadata(h.getLevel().registryAccess())));
            com.timestop.core.rewind.RewindExecutor.applyPlan(h.getLevel().getServer(),com.timestop.core.rewind.RewindExecutor.buildPlan(List.of(placement)),true);
            h.assertTrue(h.getLevel().getBlockState(p.getBlockPos()).isAir(),"Rewind removes placement");
            h.assertTrue(TemporalBubbleManager.getBubble(p.getFieldId())==null,"Rewind removes active field");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(p.getBlockPos()).inflate(5),
                    item -> item.getItem().has(net.minecraft.core.component.DataComponents.CUSTOM_DATA) && marker.equals(item.getItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString("PedestalTest"))).isEmpty(),"Rewind removal must not generate a duplicate watch drop");
        } finally { clean(h,p); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestal_crops")
    public void cropRandomTicksRespectSphereAndAcceleration(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.DIAMOND_WATCH.get());
        var inside=p.getBlockPos().east();
        var outside=p.getBlockPos().east(5);
        // Use vanilla wheat and deterministic successful growth rolls, after the lighting engine settles.
        for(var pos:List.of(inside,outside)) {
            h.getLevel().setBlockAndUpdate(pos.below(),Blocks.FARMLAND.defaultBlockState());
            h.getLevel().setBlockAndUpdate(pos,Blocks.WHEAT.defaultBlockState());
            h.getLevel().setBlockAndUpdate(pos.above(),Blocks.GLOWSTONE.defaultBlockState());
        }
        var random=new net.minecraft.world.level.levelgen.LegacyRandomSource(1) { @Override public int nextInt(int bound){return 0;} };
        h.runAfterDelay(3,() -> {
            try {
                p.configure(TimeMode.TIME_STOP,2); power(h,p,true);
                PedestalWorldTime.randomBlock(h.getLevel().getBlockState(inside),h.getLevel(),inside,random);
                h.assertTrue(h.getLevel().getBlockState(inside).getValue(net.minecraft.world.level.block.CropBlock.AGE)==0,"Frozen crop does not grow");
                h.getLevel().setBlock(outside,Blocks.WHEAT.defaultBlockState(),2);
                PedestalWorldTime.randomBlock(h.getLevel().getBlockState(outside),h.getLevel(),outside,random);
                h.assertTrue(h.getLevel().getBlockState(outside).getValue(net.minecraft.world.level.block.CropBlock.AGE)==1,"Outside crop grows normally in intersecting chunk");
                p.configure(TimeMode.FAST_FORWARD,2); PedestalManager.serverTick();
                PedestalWorldTime.randomBlock(h.getLevel().getBlockState(inside),h.getLevel(),inside,random);
                h.assertTrue(h.getLevel().getBlockState(inside).getValue(net.minecraft.world.level.block.CropBlock.AGE)==5,"Accelerated crop receives five growth ticks");
                h.succeed();
            } finally { clean(h,p); }
        });
    }
    private PedestalBlockEntity place(GameTestHelper h, BlockPos relative, Item watch) {
        BlockPos pos = h.absolutePos(relative);
        h.getLevel().setBlockAndUpdate(pos, ModPedestals.CREATIVE.get().defaultBlockState());
        var p = (PedestalBlockEntity)h.getLevel().getBlockEntity(pos);
        var owner = h.makeMockServerPlayerInLevel(); owner.setGameMode(GameType.SURVIVAL);
        p.setOwner(owner); p.inventory.setItem(0, new ItemStack(watch)); p.configure(TimeMode.SLOW_MOTION, 2);
        owner.discard(); // The recorded owner may be offline; do not leak mock inventories into later batches.
        return p;
    }
    private void power(GameTestHelper h, PedestalBlockEntity p, boolean powered) {
        h.getLevel().setBlockAndUpdate(p.getBlockPos().below(), powered ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState());
        PedestalManager.serverTick();
    }
    private void clean(GameTestHelper h, PedestalBlockEntity... pedestals) {
        for (var p : pedestals) { PedestalManager.untrack(p); p.inventory.clearContent(); h.getLevel().removeBlock(p.getBlockPos(),false); }
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void tiersAndModes(GameTestHelper h) {
        var p = new PedestalBlockEntity(BlockPos.ZERO,ModPedestals.COPPER.get().defaultBlockState());
        h.assertTrue(p.accepts(new ItemStack(ModItems.COPPER_WATCH.get())),"Copper accepts copper");
        h.assertFalse(p.accepts(new ItemStack(ModItems.CHRONOS_WATCH.get())),"Copper rejects gold");
        h.assertFalse(p.accepts(new ItemStack(Items.CLOCK)),"Vanilla clocks are not watches");
        p.inventory.setItem(0,new ItemStack(ModItems.COPPER_WATCH.get()));
        h.assertTrue(p.supports(TimeMode.FAST_FORWARD),"Copper unlocks acceleration");
        h.assertFalse(p.supports(TimeMode.TIME_STOP),"Copper cannot freeze");
        h.assertFalse(p.supports(TimeMode.REWIND),"Rewind excluded");
        p.configure(TimeMode.FAST_FORWARD,99999);
        h.assertTrue(p.getRadius()==(int)WatchTier.COPPER.getBubbleRadius(),"Radius clamped to watch");
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void stationaryContinuousAndOfflineOwner(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.DIAMOND_WATCH.get());
        try {
            power(h,p,true); var b=TemporalBubbleManager.getBubble(p.getFieldId());
            h.assertTrue(b!=null && b.isStationary(),"Powered pedestal creates stationary bubble");
            Vec3 center=b.getCenter();
            for(int i=0;i<1000;i++) b.tick(h.getLevel());
            TemporalBubbleManager.serverTick();
            h.assertTrue(TemporalBubbleManager.getBubble(p.getFieldId())==b,"Offline owner does not collapse field");
            h.assertTrue(center.equals(b.getCenter()) && b.getRemainingTicks()==0,"No following or expiry");
            h.assertTrue(TimeStopManager.getServerTickMs()==50,"Stationary slowdown must not change server tick interval");
            power(h,p,false); h.assertTrue(TemporalBubbleManager.getBubble(p.getFieldId())==null,"Power loss stops field");
        } finally { clean(h,p); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void stasisCanSwitchOffAndRemoveWatch(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.DIAMOND_WATCH.get());
        try {
            p.configure(TimeMode.TIME_STOP,2); power(h,p,true);
            h.assertTrue(TemporalBubbleManager.isPositionInStasis(h.getLevel().dimension(),Vec3.atCenterOf(p.getBlockPos())),"Pedestal lies inside its own stasis");
            power(h,p,false); h.assertFalse(p.isActive(),"Controls keep working in stasis");
            power(h,p,true); p.inventory.removeItem(0,1);
            h.assertTrue(TemporalBubbleManager.getBubble(p.getFieldId())==null,"Watch removal stops immediately");
        } finally { clean(h,p); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void overlapPrecedenceAndFallback(GameTestHelper h) {
        var a=place(h,new BlockPos(1,2,1),ModItems.DIAMOND_WATCH.get());
        var b=place(h,new BlockPos(3,2,1),ModItems.NETHERITE_WATCH.get());
        try {
            a.configure(TimeMode.TIME_STOP,3); b.configure(TimeMode.FAST_FORWARD,3); power(h,a,true); power(h,b,true);
            Vec3 overlap=Vec3.atCenterOf(a.getBlockPos());
            h.assertTrue(TemporalBubbleManager.getDominantBubble(h.getLevel().dimension(),overlap).getId().equals(b.getFieldId()),"Higher-tier acceleration wins");
            b.inventory.setItem(0,new ItemStack(ModItems.DIAMOND_WATCH.get())); b.configure(TimeMode.FAST_FORWARD,3); PedestalManager.serverTick();
            h.assertTrue(TemporalBubbleManager.getDominantBubble(h.getLevel().dimension(),overlap).getMode()==TimeMode.TIME_STOP,"Equal-tier freeze wins");
            power(h,a,false);
            h.assertTrue(TemporalBubbleManager.getDominantBubble(h.getLevel().dimension(),overlap).getId().equals(b.getFieldId()),"Remaining bubble takes over");
            b.configure(TimeMode.SLOW_MOTION,1); PedestalManager.serverTick();
            h.assertTrue(TemporalBubbleManager.getDominantBubble(h.getLevel().dimension(),overlap)==null,"Shrinking radius clears old coverage");
        } finally { clean(h,a,b); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void deterministicTies(GameTestHelper h) {
        UUID a=new UUID(0,1), b=new UUID(0,2);
        h.assertTrue(BubblePriority.compare(WatchTier.DIAMOND,TimeMode.SLOW_MOTION,a,WatchTier.DIAMOND,TimeMode.FAST_FORWARD,b)<0,"Stable tie order");
        h.assertTrue(BubblePriority.compare(WatchTier.DIAMOND,TimeMode.FAST_FORWARD,b,WatchTier.DIAMOND,TimeMode.SLOW_MOTION,a)>0,"Independent of iteration order");
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void sourceLifecycleAndWatchNbt(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.NETHERITE_WATCH.get());
        try {
            p.getWatch().setDamageValue(27); net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, p.getWatch(), tag -> tag.putString("CustomNameTest","Original"));
            p.configure(TimeMode.DECELERATION_FIELD,4); power(h,p,true);
            CompoundTag saved=p.saveWithFullMetadata(h.getLevel().registryAccess()); UUID id=p.getFieldId();
            p.setRemoved(); h.assertTrue(TemporalBubbleManager.getBubble(id)==null,"Unload hook removes field");
            p.clearRemoved(); p.loadWithComponents(saved, h.getLevel().registryAccess()); PedestalManager.serverTick();
            h.assertTrue(TemporalBubbleManager.getBubble(id)!=null,"Reload restores powered source");
            h.assertTrue(p.getWatch().getDamageValue()==27 && p.getWatch().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString("CustomNameTest").equals("Original"),"NBT round trip");
            h.assertTrue(p.getRadius()==4 && p.getMode()==TimeMode.DECELERATION_FIELD,"Settings round trip");
            CompoundTag restored=saved.copy(); restored.putString("Mode","TIME_STOP"); restored.putInt("Radius",1);
            p.loadWithComponents(restored, h.getLevel().registryAccess()); PedestalManager.serverTick();
            var b=TemporalBubbleManager.getBubble(id);
            h.assertTrue(b.getMode()==TimeMode.TIME_STOP && b.getRadius()==1,"Rewind-style NBT restoration reconciles active field");
            h.assertTrue(p.getWatch().getCount()==1,"Restoration keeps one watch");
        } finally { clean(h,p); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void playerSettingAndPermissions(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.DIAMOND_WATCH.get());
        boolean original=TimeStopSavedData.get().pedestalsAffectPlayers();
        try {
            var stranger=RewindRuneTests.survivalPlayer(h);
            // Mock players can share a profile: give the field an unrelated recorded owner.
            CompoundTag tag=p.saveWithFullMetadata(h.getLevel().registryAccess()); tag.putUUID("Owner",UUID.randomUUID()); p.loadWithComponents(tag, h.getLevel().registryAccess());
            p.configure(TimeMode.TIME_STOP,2); TimeStopSavedData.get().setPedestalsAffectPlayers(true); power(h,p,true);
            h.assertFalse(TemporalBubbleManager.getBubble(p.getFieldId()).canEntityAct(stranger),"Non-allied player affected by default");
            var commands=h.getLevel().getServer().getCommands();
            int denied=PortTestSupport.command(commands, stranger.createCommandSourceStack().withPermission(0),"timestop pedestal affectplayers false");
            h.assertTrue(denied==0 && TimeStopSavedData.get().pedestalsAffectPlayers(),"Non-operator cannot change policy");
            PortTestSupport.command(commands, h.getLevel().getServer().createCommandSourceStack(),"timestop pedestal affectplayers false");
            h.assertTrue(TemporalBubbleManager.getBubble(p.getFieldId()).canEntityAct(stranger),"Command updates existing fields");
            h.assertFalse(TimeStopSavedData.load(TimeStopSavedData.get().save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess()).pedestalsAffectPlayers(),"Policy saved with world");
            h.assertTrue(TimeStopSavedData.load(new CompoundTag(), h.getLevel().registryAccess()).pedestalsAffectPlayers(),"Old worlds default true");
            stranger.discard();
        } finally { TimeStopSavedData.get().setPedestalsAffectPlayers(original); clean(h,p); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void adminStopRequiresPowerCycle(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.COPPER_WATCH.get());
        try {
            power(h,p,true);
            h.getLevel().getServer().getCommands().performPrefixedCommand(h.getLevel().getServer().createCommandSourceStack(),"timestop stop");
            PedestalManager.serverTick();
            h.assertTrue(p.isDisarmed() && !p.isActive(),"Stop disarms powered sources");
            power(h,p,false); power(h,p,true); h.assertTrue(p.isActive(),"Power cycle rearms");
        } finally { clean(h,p); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void globalSuppressionAndResume(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.DIAMOND_WATCH.get());
        try {
            power(h,p,true); TimeStopManager.startGlobalTimeStop(h.getLevel(),null,10,TimeMode.TIME_STOP); PedestalManager.serverTick();
            h.assertTrue(!p.isActive() && TemporalBubbleManager.getBubble(p.getFieldId())==null,"Global effect suppresses sources");
            TimeStopManager.resumeTime(h.getLevel()); PedestalManager.serverTick(); h.assertTrue(p.isActive(),"Powered sources resume after global effect");
        } finally { TimeStopManager.resumeTime(h.getLevel()); clean(h,p); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestal_tick_rates")
    public void blockEntityTickRatesAndBoundary(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.DIAMOND_WATCH.get());
        class Probe implements TickingBlockEntity {
            int ticks; boolean removed; final BlockPos pos;
            Probe(BlockPos pos) {this.pos=pos;}
            public void tick(){ticks++;} public boolean isRemoved(){return removed;}
            public BlockPos getPos(){return pos;} public String getType(){return "pedestal_test";}
        }
        Probe inside=new Probe(p.getBlockPos().above()), outside=new Probe(p.getBlockPos().offset(4,0,0));
        h.getLevel().addBlockEntityTicker(inside); h.getLevel().addBlockEntityTicker(outside);
        p.configure(TimeMode.TIME_STOP,2); power(h,p,true);
        h.runAfterDelay(2, () -> {
            h.assertTrue(inside.ticks==0 && outside.ticks>0,"Stasis stops only tickers inside sphere");
            inside.ticks=outside.ticks=0; p.configure(TimeMode.FAST_FORWARD,2); PedestalManager.serverTick();
            h.runAfterDelay(2, () -> {
                h.assertTrue(inside.ticks==outside.ticks*5 && outside.ticks>0,"Acceleration runs five ticks only inside sphere");
                inside.ticks=outside.ticks=0; p.configure(TimeMode.SLOW_MOTION,2); PedestalManager.serverTick();
                h.runAfterDelay(4, () -> {
                    try {
                        h.assertTrue(inside.ticks==outside.ticks/4 && inside.ticks>0,"Slow motion runs one of four ticks");
                        h.succeed();
                    } finally { inside.removed=true; outside.removed=true; clean(h,p); }
                });
            });
        });
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void decelerationWithoutOnlineOwner(GameTestHelper h) {
        var p=place(h,new BlockPos(1,2,1),ModItems.CHRONOS_WATCH.get());
        try {
            p.configure(TimeMode.DECELERATION_FIELD,2); power(h,p,true);
            Arrow arrow=new Arrow(h.getLevel(),p.getBlockPos().getX()+.5,p.getBlockPos().getY()+1.5,p.getBlockPos().getZ()+.5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
            arrow.setNoGravity(true); arrow.setDeltaMovement(.2,0,0);
            h.assertTrue(DecelerationFieldManager.isPedestalDecelerated(arrow),"Stationary field does not require an online protecting player");
            double before=arrow.getX(); arrow.tick();
            h.assertTrue(arrow.getX()-before>0 && arrow.getX()-before<.06,"Arrow advances at 20% displacement, actual="+(arrow.getX()-before));
            h.assertTrue(arrow.getDeltaMovement().x>.15,"True velocity is preserved");
            arrow.setPos(Vec3.atCenterOf(p.getBlockPos()).add(10,0,0));
            h.assertFalse(DecelerationFieldManager.isPedestalDecelerated(arrow),"Outside sphere unaffected");
        } finally { clean(h,p); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE, batch="pedestals")
    public void slowMotionShortensMobInvulnerability(GameTestHelper h) {
        var p = place(h, new BlockPos(1,2,1), ModItems.DIAMOND_WATCH.get());
        p.configure(TimeMode.SLOW_MOTION, 3); power(h, p, true);
        BlockPos mobPos = h.absolutePos(new BlockPos(2,2,1));
        var zombie = new net.minecraft.world.entity.monster.Zombie(h.getLevel());
        zombie.setPos(mobPos.getX() + 0.5, mobPos.getY(), mobPos.getZ() + 0.5);
        h.getLevel().addFreshEntity(zombie);
        try {
            var bubble = TemporalBubbleManager.getBubble(p.getFieldId());
            h.assertTrue(bubble != null, "Bubble exists");
            h.assertTrue(bubble.getTimeDilationFactor(zombie) < 1.0F, "Zombie inside bubble has slowed rate");

            zombie.hurt(h.getLevel().damageSources().generic(), 2.0F);

            h.assertTrue(zombie.invulnerableTime <= 6, "Invulnerable time is scaled down in slow motion, actual=" + zombie.invulnerableTime);
            h.assertTrue(zombie.hurtDuration <= 4, "Hurt duration is scaled down in slow motion, actual=" + zombie.hurtDuration);

            int initialInvuln = zombie.invulnerableTime;
            for (int i = 0; i < 3; i++) {
                h.getLevel().tickNonPassenger(zombie);
            }
            h.assertTrue(zombie.invulnerableTime < initialInvuln, "Invulnerable time decremented on skipped server ticks");

            zombie.invulnerableTime = 0;
            boolean hurtAgain = zombie.hurt(h.getLevel().damageSources().generic(), 2.0F);
            h.assertTrue(hurtAgain, "Zombie can be damaged again rapidly in slow motion");
        } finally {
            zombie.discard();
            clean(h, p);
        }
        h.succeed();
    }
}
