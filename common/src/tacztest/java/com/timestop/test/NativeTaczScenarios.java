package com.timestop.test;

import com.timestop.combat.DecelerationFieldManager;
import com.timestop.config.TimeStopConfig;
import com.timestop.core.*;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import com.timestop.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Supplier;
import com.timestop.combat.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.HolderLookup;
import com.timestop.platform.EntityDataHelper;

public class NativeTaczScenarios {
    private static Projectile bullet(GameTestHelper h, Vec3 pos, double speed) throws Exception {
        var clazz = Class.forName("com.tacz.guns.entity.EntityKineticBullet");
        var type = (EntityType<?>) clazz.getField("TYPE").get(null);
        var bullet = (Projectile) clazz.getConstructor(EntityType.class, Level.class).newInstance(type, h.getLevel());
        var friction = clazz.getDeclaredField("friction"); friction.setAccessible(true); friction.setFloat(bullet, 0);
        var gravity = clazz.getDeclaredField("gravity"); gravity.setAccessible(true); gravity.setFloat(bullet, 0);
        for (String name : List.of("gunId", "gunDisplayId")) {
            var id = clazz.getDeclaredField(name); id.setAccessible(true); id.set(bullet, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tacz", "ak47"));
        }
        bullet.setPos(pos); bullet.setDeltaMovement(speed, 0, 0);
        h.getLevel().getChunkAt(BlockPos.containing(pos));
        return bullet;
    }
    private static Vec3 center(GameTestHelper h) { var pos = h.absolutePos(new BlockPos(8, 8, 8)); return new Vec3(pos.getX() + .5, 200, pos.getZ() + .5); }
    private static void field(GameTestHelper h, Vec3 center, double radius) {
        var bubble = new TemporalBubble(UUID.randomUUID(), UUID.randomUUID(), h.getLevel().dimension(), center,
                radius, TimeMode.DECELERATION_FIELD, 10000, WatchTier.CREATIVE, null, 0, Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(), bubble, null);
    }
    public static void realTaczBulletsSlowInStationaryWatchAndGlobalScopes(GameTestHelper h) throws Exception {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get();
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE); player.setPos(center(h));
        try {
            for (int scope = 0; scope < 3; scope++) {
                TimeStopConfig.COMMON.decelerationDrag.set(.25);
                Vec3 pos = center(h);
                if (scope == 0) field(h, pos, 12);
                if (scope == 1) {
                    var watch = new ItemStack(ModItems.CREATIVE_WATCH.get()); AbstractWatchItem.setBubbleRadius(watch, 12);
                    player.setItemInHand(InteractionHand.MAIN_HAND, watch);
                    var bubble = TemporalBubbleManager.startBubble(h.getLevel(), player, 10000, TimeMode.DECELERATION_FIELD);
                    pos = bubble.getCenter().add(8, 0, 0);
                }
                if (scope == 2) { TimeStopManager.startGlobalTimeStop(h.getLevel(), player, 10000, TimeMode.DECELERATION_FIELD); pos = pos.add(50, 0, 0); }
                var bullet = bullet(h, pos, .4); bullet.setOwner(player);
                try {
                    h.assertTrue(DecelerationFieldManager.hasMovementHook(bullet), "Actual TaCZ bullet must have the optional movement hook");
                    for (int i = 0; i < 5; i++) h.getLevel().tickNonPassenger(bullet);
                    h.assertTrue(bullet.isAlive() && Math.abs(bullet.getX() - pos.x - .5) < .002,
                            "Real TaCZ native tick must use configured quarter speed in scope " + scope + ": " + bullet.position());
                    h.assertTrue(Math.abs(bullet.getDeltaMovement().x - .4) < .002, "Native bullet must retain full momentum");
                    TimeStopConfig.COMMON.decelerationDrag.set(.4);
                    double before = bullet.getX(); h.getLevel().tickNonPassenger(bullet);
                    h.assertTrue(Math.abs(bullet.getX() - before - .16) < .002, "Native slowdown must follow speed configuration");
                    TimeStopConfig.COMMON.decelerationDrag.set(.25);
                    var friction = bullet.getClass().getDeclaredField("friction"); friction.setAccessible(true); friction.setFloat(bullet, .05f);
                    var gravity = bullet.getClass().getDeclaredField("gravity"); gravity.setAccessible(true); gravity.setFloat(bullet, .02f);
                    bullet.setDeltaMovement(.4, 0, 0); before = bullet.getX(); h.getLevel().tickNonPassenger(bullet);
                    h.assertTrue(Math.abs(bullet.getX() - before - .1) < .002
                                    && bullet.getDeltaMovement().distanceTo(new Vec3(.395, -.005, 0)) < .00001,
                            "TaCZ native gravity and friction must use elapsed field time");
                } finally { bullet.discard(); TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopManager.resumeTime(h.getLevel()); }
            }
            com.timestop.TimeStopMod.LOGGER.info("DECELERATION_TACZ_SCOPES_PASS");
        } finally { player.discard(); TimeStopConfig.COMMON.decelerationDrag.set(previous); }
        h.succeed();
    }
    public static void realTaczFastBulletCannotSkipTheBubbleAndRestoresSpeedOnExit(GameTestHelper h) throws Exception {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get(); var center = center(h);
        var bullet = bullet(h, center.add(-4, 0, 0), 20);
        try {
            TimeStopConfig.COMMON.decelerationDrag.set(.1); field(h, center, 2);
            h.getLevel().tickNonPassenger(bullet);
            h.assertTrue(Math.abs(bullet.getX() - center.x + .2) < .002, "Native swept step must slow immediately on bubble entry");
            h.assertTrue(Math.abs(bullet.getDeltaMovement().x - 20) < .002, "No permanent slowdown on entry");
            bullet.setPos(center.add(1.9, 0, 0)); bullet.setDeltaMovement(20, 0, 0);
            h.getLevel().tickNonPassenger(bullet);
            h.assertTrue(Math.abs(bullet.getX() - center.x - 21) < .003, "Native exit must restore speed during the same tick: " + bullet.position() + " expected " + center.add(21,0,0) + " alive " + bullet.isAlive());
            com.timestop.TimeStopMod.LOGGER.info("DECELERATION_TACZ_BOUNDARY_PASS");
        } finally { bullet.discard(); TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopConfig.COMMON.decelerationDrag.set(previous); }
        h.succeed();
    }
    public static void realTaczBulletDoesNotHitBeyondItsSlowedStep(GameTestHelper h) throws Exception {
        double previous = TimeStopConfig.COMMON.decelerationDrag.get(); var center = center(h);
        var wall = BlockPos.containing(center.add(2, 0, 0)); var bullet = bullet(h, center, 1);
        try {
            TimeStopConfig.COMMON.decelerationDrag.set(.1); field(h, center, 10);
            h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            for (int i = 0; i < 5; i++) h.getLevel().tickNonPassenger(bullet);
            h.assertTrue(bullet.isAlive() && bullet.getX() < wall.getX(), "Native raycast must not hit ahead of slowed movement");
            for (int i = 0; i < 40 && bullet.isAlive(); i++) h.getLevel().tickNonPassenger(bullet);
            h.assertFalse(bullet.isAlive(), "Native bullet must still collide when its slowed step reaches the wall");
            com.timestop.TimeStopMod.LOGGER.info("DECELERATION_TACZ_WALL_PASS");
        } finally {
            bullet.discard(); h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopConfig.COMMON.decelerationDrag.set(previous);
        }
        h.succeed();
    }

    private static void reset(GameTestHelper h) {
        TemporalBubbleManager.stopAllBubbles(h.getLevel());TimeStopManager.resumeTime(h.getLevel());
        TimeStopManager.setProjectileStasisMode(TimeStopManager.ProjectileStasisMode.SUSPENDED);
        KineticPalmManager.clearAll();OrbitalProjectileManager.clearAll();
    }
    private static void stoppedField(GameTestHelper h, Vec3 pos, double radius) {
        var b=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),pos,
                radius,TimeMode.TIME_STOP,10000,WatchTier.CREATIVE,null,0,Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(),b,null);
    }
    public static void fastStoppedBoundaryAndOverlap(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var p=bullet(h,c.add(-6,0,0),20);
        try {
            stoppedField(h,c,2);stoppedField(h,c.add(1,0,0),3);h.getLevel().tickNonPassenger(p);
            h.assertTrue(TimeStopManager.isProjectileSuspended(p),"Fast native bullet must freeze at first boundary");
            h.assertTrue(p.getX()<c.x-1.9 && p.getX()>c.x-2.1,"Bullet stops at entry");
            h.assertTrue(TimeStopManager.getSuspendedVelocity(p).distanceTo(new Vec3(20,0,0))<.001,"Freeze retains native momentum");
            double x=p.getX();h.getLevel().tickNonPassenger(p);h.assertTrue(p.getX()==x,"Suspended native tick cannot move");
            TemporalBubbleManager.stopAllBubbles(h.getLevel());TimeStopManager.resumeProjectiles(h.getLevel());
            h.assertTrue(p.getDeltaMovement().distanceTo(new Vec3(20,0,0))<.001,"Resume restores native momentum");
        }finally{p.discard();reset(h);}h.succeed();
    }
    public static void wallBeforeStoppedBoundary(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var p=bullet(h,c.add(-6,0,0),20);var wall=BlockPos.containing(c.add(-4,0,0));
        try {
            stoppedField(h,c,2);h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());h.getLevel().tickNonPassenger(p);
            h.assertFalse(p.isAlive(),"Earlier wall hit retains precedence");
            h.assertFalse(TimeStopManager.isProjectileSuspended(p),"Wall hit must not enter stasis");
        }finally{p.discard();h.getLevel().setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());reset(h);}h.succeed();
    }
    private static ItemStack gun(GameTestHelper h,String id,String mode,int ammo) throws Exception {
        Class<?> type=Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");Object b=type.getMethod("create").invoke(null);
        type.getMethod("setId",ResourceLocation.class).invoke(b,ResourceLocation.parse(id));
        type.getMethod("setAmmoCount",int.class).invoke(b,id.startsWith("tacz:") && ammo>0 ? ammo-1 : ammo);type.getMethod("setAmmoInBarrel",boolean.class).invoke(b,id.startsWith("tacz:") && ammo>0);
        Class<?> m=Class.forName("com.tacz.guns.api.item.gun.FireMode");type.getMethod("setFireMode",m).invoke(b,m.getField(mode).get(null));
        var result=(ItemStack)type.getMethod("build",HolderLookup.Provider.class).invoke(b,h.getLevel().registryAccess());
        h.assertFalse(result.isEmpty(),"Native pack must contain "+id);return result;
    }
    private static Object draw(LivingEntity p) throws Exception {
        Class<?> api=Class.forName("com.tacz.guns.api.entity.IGunOperator");Object op=api.getMethod("fromLivingEntity",LivingEntity.class).invoke(null,p);
        api.getMethod("draw",Supplier.class).invoke(op,(Supplier<ItemStack>)p::getMainHandItem);
        Object holder=api.getMethod("getDataHolder").invoke(op);
        // Fixture only: bypass wall-clock draw delay after native property initialization.
        holder.getClass().getField("drawTimestamp").setLong(holder,-1000000);holder.getClass().getField("shootTimestamp").setLong(holder,-1000000);return holder;
    }
    private static int ammo(ItemStack s) throws Exception {
        Class<?> api=Class.forName("com.tacz.guns.api.item.IGun");Object gun=api.getMethod("getIGunOrNull",ItemStack.class).invoke(null,s);
        return (int)api.getMethod("getCurrentAmmoCount",ItemStack.class).invoke(gun,s) + ((boolean)api.getMethod("hasBulletInBarrel",ItemStack.class).invoke(gun,s)?1:0);
    }
    public static void nativeSemiAutoAmmoAndPrecision(GameTestHelper h) throws Exception {
        reset(h);var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(center(h));p.setOldPosAndRot();
        var s=gun(h,"tacz:ak47","SEMI",5);p.setItemInHand(InteractionHand.MAIN_HAND,s);
        try {
            var holder=draw(p);var target=p.getEyePosition().add(40,2,0);
            h.assertTrue(TaczDeadEyeCompat.fire(p,target)==TaczDeadEyeCompat.Result.FIRED,"Native operator must accept shot");
            h.assertTrue(ammo(s)==4,"One native round consumes one cartridge");
            var bullets=h.getLevel().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(4),TaczProjectileCompat::isBullet);
            h.assertFalse(bullets.isEmpty(),"Operator spawns native bullet");
            for(var b:bullets){h.assertTrue(b.getOwner()==p && EntityDataHelper.getPersistentData(b).getBoolean("DeadEyeTaczPrecision"),"Native round inherits shooter and precision");
                h.assertTrue(b.getDeltaMovement().normalize().distanceTo(target.subtract(b.position()).normalize())<.00001,"Native aim is precise");b.discard();}
            // Keep the native cooldown active while reflection/class loading or
            // concurrent client QA can outlast the AK's short real-time delay.
            long base=holder.getClass().getField("baseTimestamp").getLong(holder);
            holder.getClass().getField("shootTimestamp").setLong(holder,System.currentTimeMillis()-base+1000);
            h.assertTrue(TaczDeadEyeCompat.fire(p,target)==TaczDeadEyeCompat.Result.RETRY,"Native cooldown controls retry");
            h.assertTrue(ammo(s)==4,"Retry consumes no ammo");
        }finally{p.discard();reset(h);}h.succeed();
    }
    public static void nativeEmptyAndWeaponSwitch(GameTestHelper h) throws Exception {
        reset(h);var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(center(h));p.setOldPosAndRot();
        try {var s=gun(h,"tacz:ak47","SEMI",0);p.setItemInHand(InteractionHand.MAIN_HAND,s);draw(p);
            h.assertTrue(TaczDeadEyeCompat.fire(p,p.position().add(30,1,0))==TaczDeadEyeCompat.Result.STOP,"Empty gun must not fire");h.assertTrue(ammo(s)==0,"Empty gun remains empty");
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            h.assertTrue(TaczDeadEyeCompat.fire(p,p.position().add(30,1,0))==TaczDeadEyeCompat.Result.STOP,"Weapon switching ends fire");
        }finally{p.discard();reset(h);}h.succeed();
    }
    public static void nativeBurstEveryRound(GameTestHelper h) throws Exception {
        reset(h);var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(center(h));p.setOldPosAndRot();
        var s=gun(h,"timestop_qa:controlled","BURST",9);p.setItemInHand(InteractionHand.MAIN_HAND,s);draw(p);
        stoppedField(h,p.getEyePosition(),4);
        h.assertTrue(TaczDeadEyeCompat.fire(p,p.getEyePosition().add(40,0,0))==TaczDeadEyeCompat.Result.FIRED,"Controlled native burst fires");
        h.succeedWhen(()->{try{
            var bullets=h.getLevel().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(80),TaczProjectileCompat::isBullet);
            h.assertTrue(bullets.size()==3,"Scheduled burst waits for three real-time rounds; got "+bullets.size());
            h.assertTrue(ammo(s)==6,"Burst consumes three cartridges");
            for(var b:bullets)h.assertTrue(EntityDataHelper.getPersistentData(b).getBoolean("DeadEyeTaczPrecision"),"Delayed rounds retain aim context");
            bullets.forEach(net.minecraft.world.entity.Entity::discard);p.discard();reset(h);
        }catch(net.minecraft.gametest.framework.GameTestAssertException retry){throw retry;}catch(Exception e){throw new RuntimeException(e);}});
    }

    public static void nativeShotgunEveryPellet(GameTestHelper h) throws Exception {
        reset(h);var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(center(h));p.setOldPosAndRot();
        var s=gun(h,"tacz:db_long","SEMI",2);p.setItemInHand(InteractionHand.MAIN_HAND,s);
        try {draw(p);h.assertTrue(TaczDeadEyeCompat.fire(p,p.getEyePosition().add(40,2,0))==TaczDeadEyeCompat.Result.FIRED,"Native shotgun fires");
            var bullets=h.getLevel().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(4),TaczProjectileCompat::isBullet);
            h.assertTrue(bullets.size()>1,"Native shotgun emits pellets");h.assertTrue(ammo(s)==1,"Pellets consume one shell");
            for(var b:bullets){h.assertTrue(EntityDataHelper.getPersistentData(b).getBoolean("DeadEyeTaczPrecision"),"Every pellet has precision context");b.discard();}
        }finally{p.discard();reset(h);}h.succeed();
    }
    public static void ammunitionIdentityAndSnatch(GameTestHelper h) throws Exception {
        reset(h);var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var b=bullet(h,center(h),3);
        try {var id=b.getClass().getDeclaredField("ammoId");id.setAccessible(true);id.set(b,ResourceLocation.parse("tacz:762x39"));
            var s=TaczProjectileCompat.ammunition(b);h.assertFalse(s.isEmpty(),"Native ammo is recoverable");
            var api=Class.forName("com.tacz.guns.api.item.IAmmo");h.assertTrue(ResourceLocation.parse("tacz:762x39").equals(api.getMethod("getAmmoId",ItemStack.class).invoke(s.getItem(),s)),"Original ammo ID retained");
            TimeStopManager.registerSuspendedProjectile(b,b.getDeltaMovement());TemporalInteractionEvents.snatchProjectile(b,p);
            h.assertFalse(b.isAlive() || TimeStopManager.isProjectileSuspended(b),"Snatch removes bullet and suspended state");
            h.assertTrue(p.getInventory().items.stream().anyMatch(i->ItemStack.isSameItemSameComponents(i,s)),"Inventory receives native cartridge");
        }finally{b.discard();p.discard();reset(h);}h.succeed();
    }
    public static void orbitCaptureRetainsNativeIdentity(GameTestHelper h) throws Exception {
        reset(h);var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(center(h));var b=bullet(h,center(h).add(2,0,0),3);
        try {b.setOwner(p);var id=b.getClass().getMethod("getAmmoId").invoke(b);OrbitalProjectileManager.captureProjectile(p,b,h.getLevel());
            h.assertTrue(OrbitalProjectileManager.getOrbitCount(p)==1 && b.isAlive(),"Orbit retains actual bullet");
            var pos=b.position();h.getLevel().tickNonPassenger(b);h.assertTrue(b.position().equals(pos),"Native captured tick cannot move");
            OrbitalProjectileManager.launchOrbitingProjectiles(p);
            h.assertTrue(b.isAlive() && b.getOwner()==p && !EntityDataHelper.getPersistentData(b).getBoolean("InStasisOrbit"),"Release preserves owner");
            h.assertTrue(id.equals(b.getClass().getMethod("getAmmoId").invoke(b)),"Release preserves ammo ID");
        }finally{b.discard();p.discard();reset(h);}h.succeed();
    }
    public static void shotScopeFinallyAndScheduledContext(GameTestHelper h) throws Exception {
        var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var b=bullet(h,center(h),3);b.setOwner(p);
        try {var aim=new TaczPrecision.Aim(p,()->center(h).add(20,0,0),null,false);
            try{TaczPrecision.wrapShot(()->{throw new IllegalStateException("expected");},aim,ItemStack.EMPTY).getAsBoolean();h.fail("Expected exception");}catch(IllegalStateException expected){}
            TaczPrecision.onBulletSpawn(b);h.assertFalse(EntityDataHelper.getPersistentData(b).getBoolean("DeadEyeTaczPrecision"),"Exception cleans shot scope");
            TaczPrecision.wrapShot(()->{TaczPrecision.onBulletSpawn(b);return false;},aim,ItemStack.EMPTY).getAsBoolean();
            h.assertTrue(EntityDataHelper.getPersistentData(b).getBoolean("DeadEyeTaczPrecision"),"Scheduled round restores captured aim");
            EntityDataHelper.getPersistentData(b).remove("DeadEyeTaczPrecision");TaczPrecision.onBulletSpawn(b);
            h.assertFalse(EntityDataHelper.getPersistentData(b).getBoolean("DeadEyeTaczPrecision"),"Cancelled round cleans scope");
        }finally{b.discard();p.discard();}h.succeed();
    }

    private static void nativeField(Projectile b,String name,Object value) throws Exception {
        var f=b.getClass().getDeclaredField(name);f.setAccessible(true);f.set(b,value);
    }
    private static Projectile damagingBullet(GameTestHelper h, LivingEntity owner,Vec3 pos,double speed,int pierce) throws Exception {
        var b=bullet(h,pos,speed);b.setOwner(owner);nativeField(b,"startPos",pos);nativeField(b,"distanceAmount",Float.MAX_VALUE);nativeField(b,"pierce",pierce);nativeField(b,"headShot",2f);
        Object pair=Class.forName("com.tacz.guns.resource.pojo.data.gun.ExtraDamage$DistanceDamagePair").getConstructor(float.class,float.class).newInstance(Float.MAX_VALUE,4f);
        nativeField(b,"damageAmount",new java.util.LinkedList<>(List.of(pair)));return b;
    }
    private static net.minecraft.world.entity.monster.Zombie zombie(GameTestHelper h,Vec3 pos) {
        h.getLevel().getChunkAt(BlockPos.containing(pos));
        var z=EntityType.ZOMBIE.create(h.getLevel());z.setNoAi(true);z.setNoGravity(true);z.setPos(pos);z.setOldPosAndRot();h.getLevel().addFreshEntity(z);return z;
    }
    public static void nativePiercingPreservesDamage(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var owner=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);owner.setPos(c.add(-10,0,0));
        var first=zombie(h,c.add(3,0,0));var second=zombie(h,c.add(6,0,0));var b=damagingBullet(h,owner,c.add(0,1,0),10,2);
        try {h.getLevel().tickNonPassenger(b);h.assertTrue(first.getHealth()<20 && second.getHealth()<20,"Native piercing hurts both targets");
            h.assertTrue(first.getHealth()>10 && second.getHealth()>10,"Handlers must not duplicate native damage");h.assertFalse(b.isAlive(),"Native penetration limit still discards bullet");
        }finally{b.discard();first.discard();second.discard();owner.discard();reset(h);}h.succeed();
    }
    public static void ricochetAfterNativeDamage(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var owner=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);owner.setPos(c.add(-10,0,0));
        var watch=new ItemStack(ModItems.CREATIVE_WATCH.get());AbstractWatchItem.setSocketedRune(watch,new ItemStack(ModItems.RUNE_RICOCHET.get()));owner.setItemInHand(InteractionHand.OFF_HAND,watch);
        var first=zombie(h,c.add(3,0,0));var second=zombie(h,c.add(3,0,4));var b=damagingBullet(h,owner,c.add(0,1,0),5,1);
        try {h.getLevel().tickNonPassenger(b);h.assertTrue(first.getHealth()<20,"Native damage precedes ricochet");
            h.assertTrue(b.isAlive() && second.getHealth()==20,"Ricochet cancels remaining native tick and premature discard");
            h.assertTrue(EntityDataHelper.getPersistentData(b).getInt("RicochetChainCount")==1,"One hit yields one ricochet dispatch");
            for(int i=0;i<4 && b.isAlive();i++)h.getLevel().tickNonPassenger(b);
            h.assertTrue(second.getHealth()<20,"Native ricochet leaves prior victim and reaches next target");
            for(int i=0;i<20 && b.isAlive();i++)h.getLevel().tickNonPassenger(b);
            var hits=EntityDataHelper.getPersistentData(b).getList("RicochetHitList",net.minecraft.nbt.Tag.TAG_INT);
            h.assertTrue(hits.size()==VoltaicRicochetHandler.MAX_CHAIN_COUNT && !b.isAlive(),"Ricochet completes native six-hit chain");
            for(int i=1;i<hits.size();i++)h.assertTrue(hits.getInt(i)!=hits.getInt(i-1),"Bounce must leave last victim before next impact");
        }finally{b.discard();first.discard();second.discard();owner.discard();reset(h);}h.succeed();
    }
    public static void nativeCoinSweepAndBufferedChain(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var owner=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);owner.setPos(c.add(-10,0,0));
        var first=new com.timestop.entity.ChronoCoinEntity(h.getLevel(),c.x+3,c.y,c.z);var second=new com.timestop.entity.ChronoCoinEntity(h.getLevel(),c.x+5,c.y,c.z+3);
        h.getLevel().addFreshEntity(first);h.getLevel().addFreshEntity(second);var victim=zombie(h,c.add(7,0,3));var b=bullet(h,c,20);b.setOwner(owner);
        try {stoppedField(h,c.add(7,0,3),2);h.getLevel().tickNonPassenger(b);
            h.assertFalse(first.isAlive() || second.isAlive() || b.isAlive(),"Fast native bullet triggers coin chain once");
            h.assertTrue(second.getMultiplier()==2 && TemporalDamageBuffer.hasRecord(victim.getUUID()),"Chained coin damage buffers for stopped target");
            h.assertTrue(victim.getHealth()==20,"Buffered coin hit cannot hurt frozen target early");
            TemporalBubbleManager.stopAllBubbles(h.getLevel());TemporalDamageBuffer.dischargeEntity(h.getLevel(),victim.getUUID());
            h.assertFalse(TemporalDamageBuffer.hasRecord(victim.getUUID()),"Resume discharges buffered damage exactly once");
        }finally{b.discard();first.discard();second.discard();victim.discard();owner.discard();reset(h);}h.succeed();
    }
    public static void nativeMovingTargetHeadGuidance(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var owner=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);owner.setPos(c.add(-10,0,0));
        var targetChunk=new net.minecraft.world.level.ChunkPos(BlockPos.containing(c.add(20,0,0)));
        var movedChunk=new net.minecraft.world.level.ChunkPos(BlockPos.containing(c.add(20,3,8)));
        h.getLevel().setChunkForced(targetChunk.x,targetChunk.z,true);h.getLevel().setChunkForced(movedChunk.x,movedChunk.z,true);
        // Peaceful GameTest worlds can remove hostile mobs during this wait.
        var target=new net.minecraft.world.entity.animal.Cow(EntityType.COW,h.getLevel());
        target.setPos(c.add(20,0,0));target.setNoAi(true);target.setPersistenceRequired();target.setOldPosAndRot();h.getLevel().addFreshEntity(target);
        var b=bullet(h,c,3);b.setOwner(owner);
        // Entity visibility is promoted on a level tick on 1.20.1. Wait before
        // testing guidance so the two versions use an observable native target.
        h.runAtTickTime(10,()->{try {var aim=new TaczPrecision.Aim(owner,target::getEyePosition,target.getUUID(),true);
            h.assertTrue(h.getLevel().getEntity(target.getUUID())==target,"Moving target fixture must be visible to native UUID lookup");
            TaczPrecision.wrapShot(()->{TaczPrecision.onBulletSpawn(b);return false;},aim,ItemStack.EMPTY).getAsBoolean();
            // Keep the target in its visible chunk while moving it. Chunk
            // promotion timing differs between the two Minecraft versions.
            target.setPos(c.add(20,3,0));TaczPrecision.guide(b);
            h.assertTrue(b.getDeltaMovement().normalize().distanceTo(target.getEyePosition().subtract(b.position()).normalize())<.00001,"Head guidance follows moving target: "+b.getDeltaMovement()+" visible="+(h.getLevel().getEntity(target.getUUID())==target)+" data="+EntityDataHelper.getPersistentData(b));
            Vec3 direction=target.getEyePosition().subtract(b.position()).normalize();
            stoppedField(h,c.add(direction.scale(1.5)),.25);
            b.setDeltaMovement(0,0,3); // stale aim misses the field; current aim crosses it
            h.getLevel().tickNonPassenger(b);
            h.assertTrue(TimeStopManager.isProjectileSuspended(b),"Native guidance must update before sweeping the current round's field crossings");
            TimeStopManager.removeSuspendedProjectile(b);
            target.discard();TaczPrecision.guide(b);h.assertFalse(EntityDataHelper.getPersistentData(b).getBoolean("DeadEyeTaczPrecision"),"Dead target ends guidance");
            h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}finally{b.discard();target.discard();owner.discard();h.getLevel().setChunkForced(targetChunk.x,targetChunk.z,false);h.getLevel().setChunkForced(movedChunk.x,movedChunk.z,false);reset(h);}});
    }
    public static void nativeStationaryTemporalModes(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);
        try {for(var mode:List.of(TimeMode.SLOW_MOTION,TimeMode.MATRIX,TimeMode.SUPERHOT,TimeMode.FAST_FORWARD)){
            var field=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),c,100,mode,10000,WatchTier.CREATIVE,null,0,Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(),field,null);var b=bullet(h,c,.1);
            try {float rate=field.getTimeDilationFactor(b);for(int i=0;i<20;i++)h.getLevel().tickNonPassenger(b);
                double expected=.1*Math.floor(20*rate+1e-4);h.assertTrue(Math.abs(b.getX()-c.x-expected)<.101,"Native stationary "+mode+" follows temporal tick budget: "+(b.getX()-c.x)+" expected "+expected);
                h.assertTrue(Math.abs(b.getDeltaMovement().x-.1)<.00001,"Temporal tick budget retains native velocity");
            }finally{b.discard();TemporalBubbleManager.stopAllBubbles(h.getLevel());}
        }}finally{reset(h);}h.succeed();
    }

    private static net.minecraft.server.level.ServerPlayer fixturePlayer(GameTestHelper h,Vec3 pos) {
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"NativeTaCZFixture");
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
            net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener callback) {}
        };
        player.setPos(pos);player.setYRot(90);player.setOldPosAndRot();h.getLevel().addNewPlayer(player);return player;
    }
    public static void nativeBarrierCaptureAndRepulse(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var defender=fixturePlayer(h,c.add(5,0,0));var owner=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var watch=new ItemStack(ModItems.NETHERITE_WATCH.get());AbstractWatchItem.setSocketedRune(watch,new ItemStack(ModItems.RUNE_BARRIER.get()));defender.setItemInHand(InteractionHand.OFF_HAND,watch);
        var b=damagingBullet(h,owner,c.add(-2,.8,0),20,1);
        try {KineticPalmManager.setGuarding(defender,true);var id=b.getClass().getMethod("getAmmoId").invoke(b);
            h.getLevel().tickNonPassenger(b);h.assertTrue(KineticPalmManager.getCapturedCount(defender)==1 && b.isAlive(),"Fast native bullet must be caught before hitting barrier owner");
            b.tick();h.assertTrue(b.isAlive() && EntityDataHelper.getPersistentData(b).getBoolean("KineticPalmCaptured"),"Native tick preserves capture");
            KineticPalmManager.dischargeRepulse(defender,new Vec3(-1,0,0));
            h.assertTrue(b.isAlive() && b.getOwner()==defender && !EntityDataHelper.getPersistentData(b).getBoolean("KineticPalmCaptured"),"Repulse retains native bullet with defender ownership");
            h.assertTrue(id.equals(b.getClass().getMethod("getAmmoId").invoke(b)),"Repulse retains ammunition identity");
        }finally{b.discard();defender.discard();owner.discard();reset(h);}h.succeed();
    }
    public static void nativeSweptDeflectionAndSnatching(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var defender=fixturePlayer(h,c.add(5,0,0));var owner=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        try {for(var rune:List.of(ModItems.RUNE_DEFLECTION.get(),ModItems.RUNE_SNATCHING.get())) {
            var watch=new ItemStack(ModItems.NETHERITE_WATCH.get());AbstractWatchItem.setSocketedRune(watch,new ItemStack(rune));defender.setItemInHand(InteractionHand.OFF_HAND,watch);
            var b=bullet(h,c.add(-2,.8,0),20);b.setOwner(owner);nativeField(b,"ammoId",ResourceLocation.parse("tacz:762x39"));
            try {for(int step=0;step<12 && b.isAlive() && b.getOwner()!=defender;step++)h.getLevel().tickNonPassenger(b);
                if(rune==ModItems.RUNE_DEFLECTION.get())h.assertTrue(b.isAlive() && b.getOwner()==defender,"Swept defense redirects before native impact");
                else h.assertFalse(b.isAlive(),"Swept snatching removes native bullet before impact");
                h.assertTrue(defender.getHealth()==20,"Defense prevents native damage");
            }finally{b.discard();}
        }}finally{defender.discard();owner.discard();reset(h);}h.succeed();
    }
    public static void flowingNativeHitBuffersOnce(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var owner=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);owner.setPos(c.add(-10,0,0));
        var field=new TemporalBubble(UUID.randomUUID(),owner.getUUID(),h.getLevel().dimension(),c,20,TimeMode.TIME_STOP,10000,WatchTier.CREATIVE,null,0,Set.of()).stationary(true);
        TemporalBubbleManager.putStationaryBubble(h.getLevel(),field,null);TimeStopManager.setProjectileStasisMode(TimeStopManager.ProjectileStasisMode.FLOWING);
        var victim=zombie(h,c.add(3,0,0));var b=damagingBullet(h,owner,c.add(0,1,0),5,1);
        try {h.getLevel().tickNonPassenger(b);
            h.assertTrue(victim.getHealth()==20 && TemporalDamageBuffer.hasRecord(victim.getUUID()),"Flowing exempt native round buffers damage for stopped target");
            h.assertFalse(b.isAlive(),"Native penetration/discard remains authoritative after buffered hit");
            TemporalBubbleManager.stopAllBubbles(h.getLevel());h.getLevel().tickNonPassenger(victim);float health=victim.getHealth();
            h.assertTrue(health<20 && !TemporalDamageBuffer.hasRecord(victim.getUUID()),"Resume releases native buffered damage once");
            h.getLevel().tickNonPassenger(victim);h.assertTrue(victim.getHealth()==health,"Second tick cannot release buffered hit again");
        }finally{b.discard();victim.discard();owner.discard();reset(h);}h.succeed();
    }
    public static void nativeBurstWeaponSwitchCancelsRounds(GameTestHelper h) throws Exception {
        reset(h);var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(center(h));p.setOldPosAndRot();
        var s=gun(h,"timestop_qa:controlled","BURST",9);p.setItemInHand(InteractionHand.MAIN_HAND,s);draw(p);stoppedField(h,p.getEyePosition(),4);
        h.assertTrue(TaczDeadEyeCompat.fire(p,p.getEyePosition().add(40,0,0))==TaczDeadEyeCompat.Result.FIRED,"Native initial burst fires");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);long began=System.currentTimeMillis();
        h.succeedWhen(()->{try {h.assertTrue(System.currentTimeMillis()-began>350,"Wait for native burst cancellation");
            h.assertTrue(ammo(s)==8,"Weapon switching cancels delayed rounds without consuming ammo");
            var bullets=h.getLevel().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(80),TaczProjectileCompat::isBullet);
            h.assertTrue(bullets.size()==1,"Only first native burst round exists after switch");bullets.forEach(net.minecraft.world.entity.Entity::discard);p.discard();reset(h);
        }catch(net.minecraft.gametest.framework.GameTestAssertException retry){throw retry;}catch(Exception e){throw new RuntimeException(e);}});
    }
    public static void nativeTransientRewindGuard(GameTestHelper h) throws Exception {
        reset(h);var b=bullet(h,center(h),3);
        try {var method=com.timestop.core.rewind.TickRecorder.class.getDeclaredMethod("snapshot",net.minecraft.world.entity.Entity.class);method.setAccessible(true);
            var snapshot=(net.minecraft.nbt.CompoundTag)method.invoke(null,b);
            h.assertTrue(snapshot.getBoolean("TimeStopTransient"),"Incomplete native NBT must remain transient during rewind");
            h.assertTrue(b.isAlive() && b.getDeltaMovement().x==3,"Snapshot cannot change surviving native motion");
        }finally{b.discard();reset(h);}h.succeed();
    }

    public static void nativeDecelerationGravityAndDrag(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var b=bullet(h,c,10);double previous=TimeStopConfig.COMMON.decelerationDrag.get();
        try {
            nativeField(b,"gravity",.4f);nativeField(b,"friction",.2f);
            TimeStopConfig.COMMON.decelerationDrag.set(.25);field(h,c,100);
            h.getLevel().tickNonPassenger(b);
            h.assertTrue(Math.abs(b.getX()-c.x-2.5)<.0001,"Only slowed native step advances position");
            h.assertTrue(b.getDeltaMovement().distanceTo(new Vec3(9.5,-.1,0))<.0001,"Native drag and gravity apply over quarter step without crushing momentum");
        }finally{b.discard();TimeStopConfig.COMMON.decelerationDrag.set(previous);reset(h);}h.succeed();
    }
    public static void nativeBoltAndReloadRules(GameTestHelper h) throws Exception {
        reset(h);var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(center(h));
        var s=gun(h,"tacz:m700","SEMI",2);p.setItemInHand(InteractionHand.MAIN_HAND,s);var holder=draw(p);
        try {
            h.assertTrue(TaczDeadEyeCompat.fire(p,p.getEyePosition().add(40,0,0))==TaczDeadEyeCompat.Result.FIRED,"Chambered manual-action native shot fires");
            h.assertTrue(ammo(s)==1,"Manual-action shot consumes exactly one cartridge");
            holder.getClass().getField("shootTimestamp").setLong(holder,-1000000);
            h.assertTrue(TaczDeadEyeCompat.fire(p,p.getEyePosition().add(40,0,0))==TaczDeadEyeCompat.Result.RETRY,"Empty chamber delegates native bolt then retries");
            h.assertTrue(holder.getClass().getField("isBolting").getBoolean(holder) && ammo(s)==1,"Native bolting consumes no extra ammunition");
            holder.getClass().getField("isBolting").setBoolean(holder,false);
            var state=Class.forName("com.tacz.guns.api.entity.ReloadState$StateType");
            holder.getClass().getField("reloadStateType").set(holder,state.getField("EMPTY_RELOAD_FEEDING").get(null));
            h.assertTrue(TaczDeadEyeCompat.fire(p,p.getEyePosition().add(40,0,0))==TaczDeadEyeCompat.Result.STOP && ammo(s)==1,"Reloading blocks Dead Eye without consuming ammo");
        }finally{h.getLevel().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(80),TaczProjectileCompat::isBullet).forEach(net.minecraft.world.entity.Entity::discard);p.discard();reset(h);}h.succeed();
    }
    public static void nativeBurstDeathCancelsRounds(GameTestHelper h) throws Exception {
        reset(h);var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(center(h));p.setOldPosAndRot();
        var s=gun(h,"timestop_qa:controlled","BURST",9);p.setItemInHand(InteractionHand.MAIN_HAND,s);draw(p);stoppedField(h,p.getEyePosition(),4);
        h.assertTrue(TaczDeadEyeCompat.fire(p,p.getEyePosition().add(40,0,0))==TaczDeadEyeCompat.Result.FIRED,"Initial native burst fires");
        p.setHealth(0);long began=System.currentTimeMillis();
        h.succeedWhen(()->{try{
            h.assertTrue(System.currentTimeMillis()-began>350,"Wait for native death cancellation");
            h.assertTrue(ammo(s)==8,"Death cancels delayed native rounds");
            h.getLevel().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(80),TaczProjectileCompat::isBullet).forEach(net.minecraft.world.entity.Entity::discard);p.discard();reset(h);
        }catch(net.minecraft.gametest.framework.GameTestAssertException retry){throw retry;}catch(Exception e){throw new RuntimeException(e);}});
    }
    public static void nativePhasingAndCreativeMatrix(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var p=fixturePlayer(h,c.add(5,0,0));var owner=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var watch=new ItemStack(ModItems.NETHERITE_WATCH.get());AbstractWatchItem.setSocketedRune(watch,new ItemStack(ModItems.RUNE_PHASING.get()));p.setItemInHand(InteractionHand.OFF_HAND,watch);
        var b=damagingBullet(h,owner,c.add(-2,.8,0),20,1);h.getLevel().addFreshEntity(b);
        try {
            for(int i=0;i<20 && b.isAlive();i++)h.getLevel().tickNonPassenger(b);
            h.assertFalse(b.isAlive(),"Phasing dispels native bullet before damage");h.assertTrue(p.getHealth()==20,"Phasing protects player");
            watch=new ItemStack(ModItems.CREATIVE_WATCH.get());AbstractWatchItem.setMatrixRune(watch,com.timestop.item.rune.RuneType.DEFLECTION,true);p.setItemInHand(InteractionHand.OFF_HAND,watch);
            b=bullet(h,c.add(-2,.8,0),20);b.setOwner(owner);h.getLevel().addFreshEntity(b);
            for(int i=0;i<20 && b.isAlive() && b.getOwner()!=p;i++)h.getLevel().tickNonPassenger(b);
            h.assertTrue(b.isAlive() && b.getOwner()==p,"Creative matrix native defense uses shared rune resolver");
        }finally{b.discard();p.discard();owner.discard();RuneManager.clearAllCooldowns();reset(h);}h.succeed();
    }
    public static void nativeVectorSlapAndTransposition(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var p=fixturePlayer(h,c);p.setYRot(-90);var b=bullet(h,c.add(6,p.getEyeHeight(),0),3);
        h.getLevel().addFreshEntity(b);nativeField(b,"ammoId",ResourceLocation.parse("tacz:762x39"));var id=b.getClass().getMethod("getAmmoId").invoke(b);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.RUNE_VECTOR.get()));stoppedField(h,b.position(),2);TimeStopManager.registerSuspendedProjectile(b,b.getDeltaMovement());
            h.assertTrue(TemporalInteractionEvents.onAttackEntity(p,b),"Suspended native bullet accepts player slap");
            h.assertTrue(b.getOwner()==p && TimeStopManager.isProjectileSuspended(b),"Slap retains suspended native bullet and updates ownership");
            TemporalBubbleManager.stopAllBubbles(h.getLevel());TimeStopManager.resumeProjectiles(h.getLevel());
            h.assertTrue(b.getDeltaMovement().normalize().dot(p.getLookAngle())>.999,"Vector slap resumes along crosshair ray");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.RUNE_TRANSPOSITION.get()));var old=p.position();var target=b.position();var velocity=b.getDeltaMovement();
            TranspositionManager.executeSwap(p,false);
            h.assertTrue(p.position().distanceTo(target)<.001 && b.position().distanceTo(old.add(0,1.2,0))<.001,"Transposition swaps native bullet and player");
            h.assertTrue(b.getDeltaMovement().distanceTo(velocity.reverse())<.001 && b.getOwner()==p,"Transposition reverses native bullet and retains attribution");
            h.assertTrue(id.equals(b.getClass().getMethod("getAmmoId").invoke(b)),"Slap and swap preserve ammo identity");
        }finally{b.discard();p.discard();TranspositionManager.clearAllCooldowns();reset(h);}h.succeed();
    }
    public static void nativeVolatileImpactOnce(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var b=bullet(h,c,3);var wall=BlockPos.containing(c.add(2,0,0));
        try {
            EntityDataHelper.getPersistentData(b).putBoolean("VolatileStasis",true);h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());h.getLevel().tickNonPassenger(b);
            h.assertFalse(b.isAlive(),"Volatile native block impact retains native discard");
            h.assertFalse(EntityDataHelper.getPersistentData(b).getBoolean("VolatileStasis"),"Native impact consumes volatile tag once");
            h.assertFalse(VolatileStasisHandler.onProjectileImpact(b),"Duplicate impact cannot detonate again");
            h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"Volatile blast preserves wall");
        }finally{b.discard();h.getLevel().setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());reset(h);}h.succeed();
    }
    public static void nativeRewindMotionAndInventory(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var p=fixturePlayer(h,c);var s=gun(h,"tacz:ak47","SEMI",4);p.setItemInHand(InteractionHand.MAIN_HAND,s);
        var saved=com.timestop.core.rewind.data.PlayerDelta.fromPlayer(p);var b=bullet(h,c.add(12,0,0),3);b.setOwner(p);h.getLevel().addFreshEntity(b);
        var snapshot=com.timestop.core.rewind.TickRecorder.class.getDeclaredMethod("snapshot",net.minecraft.world.entity.Entity.class);snapshot.setAccessible(true);
        var old=(net.minecraft.nbt.CompoundTag)snapshot.invoke(null,b);
        try {
            draw(p);h.assertTrue(TaczDeadEyeCompat.fire(p,p.getEyePosition().add(40,0,0))==TaczDeadEyeCompat.Result.FIRED,"Native gun consumes round before rewind");
            h.assertTrue(ammo(s)==3,"Native ammunition changed");saved.restoreTo(p,true);
            h.assertTrue(ammo(p.getMainHandItem())==4,"Rewind restores native magazine and chamber components");
            b.setPos(c.add(13,0,0));b.setDeltaMovement(1,-1,2);
            var frame=new com.timestop.core.rewind.data.TickFrame(h.getLevel().getGameTime());frame.captureEnvironment(h.getLevel());
            frame.addEntityDelta(com.timestop.core.rewind.data.EntityDelta.update(h.getLevel().dimension(),b.getUUID(),old,(net.minecraft.nbt.CompoundTag)snapshot.invoke(null,b)));
            var plan=com.timestop.core.rewind.RewindExecutor.buildPlan(List.of(frame));com.timestop.core.rewind.RewindExecutor.applyPlan(h.getLevel().getServer(),plan,true);
            h.assertTrue(b.isAlive() && b.position().distanceTo(c.add(12,0,0))<.001 && b.getDeltaMovement().distanceTo(new Vec3(3,0,0))<.001,"Rewind restores surviving native motion: alive="+b.isAlive()+" pos="+b.position()+" motion="+b.getDeltaMovement()+" old="+old);
        }finally{h.getLevel().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(80),TaczProjectileCompat::isBullet).forEach(net.minecraft.world.entity.Entity::discard);b.discard();p.discard();reset(h);}h.succeed();
    }
    public static void nativeCancelledFireAndCoinCharges(GameTestHelper h) throws Exception {
        reset(h);var c=center(h);var p=fixturePlayer(h,c);var s=gun(h,"timestop_qa:controlled","SEMI",3);p.setItemInHand(InteractionHand.MAIN_HAND,s);draw(p);
        try(var listener=NativeTaczEvents.cancel("GunFireEvent",p)) {
            TaczDeadEyeCompat.fire(p,p.getEyePosition().add(40,0,0));
            h.assertTrue(ammo(s)==3,"Cancelled native firing event consumes no ammo");
            h.assertTrue(h.getLevel().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(80),TaczProjectileCompat::isBullet).isEmpty(),"Cancelled firing creates no bullet");
        }
        try {
            p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ModItems.RUNE_COIN.get()));
            for(int i=0;i<CoinManager.MAX_CHARGES;i++)h.assertTrue(CoinManager.consumeCharge(p),"Each coin charge can be consumed");
            h.assertFalse(CoinManager.consumeCharge(p),"Zero charges cannot duplicate a coin");
            var victim=zombie(h,c.add(3,0,0));var b=damagingBullet(h,p,c.add(0,1,0),5,1);victim.setHealth(1);
            try {h.getLevel().tickNonPassenger(b);h.assertTrue(victim.isDeadOrDying() && CoinManager.getCharges(p)==1,"Native bullet kill keeps player attribution and refunds one coin charge");}
            finally{victim.discard();b.discard();}
        }finally{p.discard();reset(h);}h.succeed();
    }
    public static void nativePredictionProfilesAndTimeVariants(GameTestHelper h) throws Exception {
        reset(h);var pos=center(h);var owner=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);owner.setPos(pos);
        double old=TimeStopConfig.COMMON.predictionRate.get(),slow=TimeStopConfig.COMMON.slowMotionRate.get();
        try {
            var gun=gun(h,"tacz:ak47","SEMI",5);owner.setItemInHand(InteractionHand.MAIN_HAND,gun);
            var saved=gun.save(h.getLevel().registryAccess());owner.getRandom().setSeed(125467L);
            var profile=com.timestop.prediction.TaczPredictionCompat.heldProfile(owner);
            h.assertTrue(profile!=null && profile.kind()==com.timestop.prediction.PredictionMotion.Kind.NATIVE && profile.velocity().length()>0,"Native held gun must provide a read-only pre-release flight profile");
            h.assertTrue(saved.equals(gun.save(h.getLevel().registryAccess())) && ammo(gun)==5,"Held prediction cannot consume or change native ammunition");
            h.assertTrue(owner.getRandom().nextInt()==net.minecraft.util.RandomSource.create(125467L).nextInt(),"Held gun prediction must leave RNG unchanged");
            for(var mode:List.of(TimeMode.TIME_PREDICTION,TimeMode.COMBAT_SLOW,TimeMode.SELF_ACCELERATION)) {
                TimeStopConfig.COMMON.predictionRate.set(.25);TimeStopConfig.COMMON.slowMotionRate.set(.25);
                var field=new TemporalBubble(UUID.randomUUID(),owner.getUUID(),h.getLevel().dimension(),pos,16,mode,10000,WatchTier.CREATIVE,null,0,Set.of()).stationary(true);
                TemporalBubbleManager.putStationaryBubble(h.getLevel(),field,null);
                var bullet=bullet(h,pos,.4);bullet.setOwner(owner);
                try {
                    var friction=bullet.getClass().getDeclaredField("friction");friction.setAccessible(true);friction.setFloat(bullet,.05f);
                    var gravity=bullet.getClass().getDeclaredField("gravity");gravity.setAccessible(true);gravity.setFloat(bullet,.02f);
                    var before=bullet.saveWithoutId(new net.minecraft.nbt.CompoundTag());var motion=com.timestop.prediction.PredictionServer.capture(bullet);
                    h.assertTrue(motion.kind()==com.timestop.prediction.PredictionMotion.Kind.NATIVE,"Native bullet must expose captured gravity and friction");
                    var path=com.timestop.prediction.PredictionEngine.forecast(h.getLevel(),motion,p -> com.timestop.prediction.PredictionServer.timeRateAt(bullet,p),Map.of());
                    h.assertTrue(before.equals(bullet.saveWithoutId(new net.minecraft.nbt.CompoundTag())),"Forecasting cannot tick or mutate a native bullet");
                    h.getLevel().tickNonPassenger(bullet);
                    h.assertTrue(path.positionAt(.05).distanceTo(bullet.position())<.00001,"Native bullet prediction must match actual movement in "+mode);
                    h.assertTrue(Math.abs(bullet.getX()-pos.x-(mode==TimeMode.SELF_ACCELERATION?.4:.1))<.00001,"Native projectile time rate must respect "+mode);
                } finally {bullet.discard();TemporalBubbleManager.stopAllBubbles(h.getLevel());}
            }
        } finally {owner.discard();TimeStopConfig.COMMON.predictionRate.set(old);TimeStopConfig.COMMON.slowMotionRate.set(slow);reset(h);}
        h.succeed();
    }
}
