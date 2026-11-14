package com.timestop.parity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.timestop.client.*;
import com.timestop.prediction.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.Projectile;
/** Isolated 1.21.1 CPU measurements. No 1.20 baseline is executed in this runtime. */
final class PredictionPerformanceQa {
    private static final List<Long> current=new ArrayList<>();
    static void overlay(Minecraft mc,int tick) {
        if(tick<176 || tick>215) return;
        var camera=mc.gameRenderer.getMainCamera();var poses=new PoseStack();
        poses.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        poses.mulPose(Axis.YP.rotationDegrees(camera.getYRot()+180));
        long start=System.nanoTime();PredictionRenderer.render(poses,camera,mc.getTimer().getGameTimeDeltaPartialTick(false));
        if(tick>=184) current.add(System.nanoTime()-start);
        if(tick==215) com.timestop.TimeStopMod.LOGGER.info("PREDICTION_OVERLAY_CPU_BENCH currentMedianMs={} currentP95Ms={} fps={} samples={}",
                quantile(current,.5),quantile(current,.95),mc.getFps(),current.size());
    }
    static void forecast(net.minecraft.server.level.ServerPlayer player) {
        var level=player.serverLevel();var projectiles=level.getEntitiesOfClass(Projectile.class,player.getBoundingBox().inflate(40));
        if(projectiles.isEmpty()) throw new IllegalStateException("Performance scene needs a real projectile");
        var entity=projectiles.get(0);var motion=PredictionServer.capture(entity);
        var targets=new HashMap<Integer,PredictionEngine.Path>();
        var bodies=level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,player.getBoundingBox().inflate(40)).stream().limit(16).toList();
        for(var e:bodies) targets.put(e.getId(),PredictionEngine.forecast(level,PredictionServer.capture(e),v -> PredictionServer.timeRateAt(e,v),Map.of()));
        var before=entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());var times=new ArrayList<Long>();var bodyTimes=new ArrayList<Long>();
        for(int i=0;i<320;i++) {
            long start=System.nanoTime();PredictionEngine.forecast(level,motion,p -> PredictionServer.timeRateAt(entity,p),targets);
            if(i>=256) times.add(System.nanoTime()-start);
        }
        for(int i=0;i<96;i++) {
            long start=System.nanoTime();for(var body:bodies) PredictionEngine.forecast(level,PredictionServer.capture(body),p -> PredictionServer.timeRateAt(body,p),Map.of());
            if(i>=64) bodyTimes.add(System.nanoTime()-start);
        }
        if(!before.equals(entity.saveWithoutId(new net.minecraft.nbt.CompoundTag()))) throw new IllegalStateException("Benchmark changed live projectile");
        com.timestop.TimeStopMod.LOGGER.info("PREDICTION_FORECAST_CPU_BENCH currentMedianMs={} currentP95Ms={} samples={}",quantile(times,.5),quantile(times,.95),times.size());
        com.timestop.TimeStopMod.LOGGER.info("PREDICTION_BODY_FORECAST_CPU_BENCH currentMedianMs={} bodies={} samples={}",quantile(bodyTimes,.5),bodies.size(),bodyTimes.size());
    }
    private static double quantile(List<Long> list,double q) {var sorted=new ArrayList<>(list);Collections.sort(sorted);return sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*q)))/1e6;}
}
