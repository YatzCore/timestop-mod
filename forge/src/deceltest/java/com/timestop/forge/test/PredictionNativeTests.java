package com.timestop.forge.test;

import com.timestop.config.TimeStopConfig;
import com.timestop.core.*;
import com.timestop.item.WatchTier;
import com.timestop.prediction.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("timestop")
@PrefixGameTestTemplate(false)
public class PredictionNativeTests {
    @GameTest(template="deceleration_empty",batch="native_prediction")
    public static void realNativeBulletForecastMatchesSlowedGravityAndDrag(GameTestHelper h) throws Exception {
        var p=h.absolutePos(new BlockPos(8,8,8)); var pos=new Vec3(p.getX()+.5,200,p.getZ()+.5);
        h.getLevel().getChunkAt(BlockPos.containing(pos));
        var clazz=Class.forName("com.tacz.guns.entity.EntityKineticBullet");
        var bullet=(Projectile)clazz.getConstructor(EntityType.class,Level.class).newInstance(clazz.getField("TYPE").get(null),h.getLevel());
        for(String name:List.of("gunId","gunDisplayId")) { var field=clazz.getDeclaredField(name);field.setAccessible(true);field.set(bullet,new ResourceLocation("tacz","ak47")); }
        var gravity=clazz.getDeclaredField("gravity"); gravity.setAccessible(true);gravity.setFloat(bullet,.02F);
        var friction=clazz.getDeclaredField("friction"); friction.setAccessible(true);friction.setFloat(bullet,.04F);
        bullet.setPos(pos); bullet.setDeltaMovement(.08,.05,0);
        double old=TimeStopConfig.COMMON.predictionRate.get();
        try {
            TimeStopConfig.COMMON.predictionRate.set(.25);
            var bubble=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),h.getLevel().dimension(),pos,16,
                    TimeMode.TIME_PREDICTION,10000,WatchTier.NETHERITE,null,0,Set.of()).stationary(true);
            TemporalBubbleManager.putStationaryBubble(h.getLevel(),bubble,null);
            h.assertTrue(bullet instanceof NativePredictionPhysics,"Real native bullet exposes read-only physics");
            var seed=PredictionServer.capture(bullet);
            h.assertTrue(seed.kind()==PredictionMotion.Kind.NATIVE,"TaCZ snapshot uses native ballistic model");
            var path=PredictionEngine.trace(h.getLevel(),seed,x -> .25,List.of());
            for(int i=0;i<40;i++) h.getLevel().tickNonPassenger(bullet);
            h.assertTrue(bullet.position().distanceTo(path.end().position())<.003,"Native bullet forecast error="+bullet.position().distanceTo(path.end().position()));
            com.timestop.TimeStopMod.LOGGER.info("PREDICTION_TACZ_PHYSICS_PASS");
        } finally { bullet.discard(); TemporalBubbleManager.stopAllBubbles(h.getLevel()); TimeStopConfig.COMMON.predictionRate.set(old); }
        h.succeed();
    }
    @GameTest(template="deceleration_empty",batch="native_prediction")
    public static void heldGunPreviewReadsDataWithoutConsumingAmmo(GameTestHelper h) throws Exception {
        var player=h.makeMockPlayer();
        var builderClass=Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
        Object builder=builderClass.getMethod("create").invoke(null);
        builderClass.getMethod("setId",ResourceLocation.class).invoke(builder,new ResourceLocation("tacz","ak47"));
        builderClass.getMethod("setAmmoCount",int.class).invoke(builder,20);
        ItemStack gun=(ItemStack)builderClass.getMethod("build").invoke(builder);
        player.setItemInHand(InteractionHand.MAIN_HAND,gun);
        var before=gun.save(new net.minecraft.nbt.CompoundTag());
        try {
            var profile=TaczPredictionCompat.heldProfile(player);
            h.assertTrue(profile!=null && profile.finite() && profile.kind()==PredictionMotion.Kind.NATIVE,"Installed gun pack supplies nominal shot physics");
            h.assertTrue(profile.velocity().length()>1,"TaCZ gun speed is converted to native steps");
            h.assertTrue(before.equals(gun.save(new net.minecraft.nbt.CompoundTag())),"Aim forecast never shoots or consumes ammunition");
            com.timestop.TimeStopMod.LOGGER.info("PREDICTION_TACZ_PREVIEW_PASS speed={}",profile.velocity().length());
        } finally { player.discard(); }
        h.succeed();
    }
}
