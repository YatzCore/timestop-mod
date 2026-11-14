package com.timestop.fabric.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.timestop.client.*;
import com.timestop.prediction.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.Projectile;

/** Paired CPU submission/forecast measurements against the saved 1.7.4 implementation. */
final class PredictionPerformanceQa {
    private static final List<Long> legacy=new ArrayList<>(),current=new ArrayList<>();
    static void overlay(Minecraft mc,int tick) {
        if(tick<176 || tick>215) return;
        var camera=mc.gameRenderer.getMainCamera();var poses=new PoseStack();
        poses.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        poses.mulPose(Axis.YP.rotationDegrees(camera.getYRot()+180));
        long a,b,c;
        if(tick%2==0) {
            a=System.nanoTime();PredictionBaselineRenderer.render(poses,camera,mc.getFrameTime());b=System.nanoTime();
            PredictionRenderer.render(poses,camera,mc.getFrameTime());c=System.nanoTime();
            if(tick>=184) {legacy.add(b-a);current.add(c-b);}
        } else {
            a=System.nanoTime();PredictionRenderer.render(poses,camera,mc.getFrameTime());b=System.nanoTime();
            PredictionBaselineRenderer.render(poses,camera,mc.getFrameTime());c=System.nanoTime();
            if(tick>=184) {current.add(b-a);legacy.add(c-b);}
        }
        if(tick==215) com.timestop.TimeStopMod.LOGGER.info("PREDICTION_OVERLAY_CPU_BENCH baselineMedianMs={} currentMedianMs={} baselineP95Ms={} currentP95Ms={} fps={} samples={}",
                quantile(legacy,.5),quantile(current,.5),quantile(legacy,.95),quantile(current,.95),mc.getFps(),current.size());
    }
    static void forecast(net.minecraft.server.level.ServerPlayer player) {
        var level=player.serverLevel();var projectiles=level.getEntitiesOfClass(Projectile.class,player.getBoundingBox().inflate(40));
        if(projectiles.isEmpty()) throw new IllegalStateException("Performance scene needs a real projectile");
        var entity=projectiles.get(0);var motion=PredictionServer.capture(entity);
        var targets=new HashMap<Integer,PredictionEngine.Path>();var oldTargets=new HashMap<Integer,PredictionBaselineEngine.Path>();
        for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,player.getBoundingBox().inflate(40)).stream().limit(16).toList()) {
            var m=PredictionServer.capture(e);var p=PredictionEngine.forecast(level,m,v -> PredictionServer.timeRateAt(e,v),Map.of());targets.put(e.getId(),p);
            oldTargets.put(e.getId(),new PredictionBaselineEngine.Path(m,p.points().stream().map(v -> new PredictionBaselineEngine.Point(v.position(),v.seconds())).toList(),p.impact(),p.hitEntity()));
        }
        var bodies=level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,player.getBoundingBox().inflate(40)).stream()
                .map(e -> PredictionServer.capture(e)).limit(16).toList();
        var bodyOld=new ArrayList<Long>();var bodyNow=new ArrayList<Long>();
        for(int i=0;i<160;i++) {
            long a,b,c;
            if(i%2==0) {
                a=System.nanoTime();for(var m:bodies) PredictionBaselineEngine.forecast(level,m,v -> m.timeRate(),Map.of());b=System.nanoTime();
                for(var m:bodies) PredictionEngine.forecast(level,m,v -> m.timeRate(),Map.of());c=System.nanoTime();
                if(i>=128) {bodyOld.add(b-a);bodyNow.add(c-b);}
            } else {
                a=System.nanoTime();for(var m:bodies) PredictionEngine.forecast(level,m,v -> m.timeRate(),Map.of());b=System.nanoTime();
                for(var m:bodies) PredictionBaselineEngine.forecast(level,m,v -> m.timeRate(),Map.of());c=System.nanoTime();
                if(i>=128) {bodyNow.add(b-a);bodyOld.add(c-b);}
            }
        }
        com.timestop.TimeStopMod.LOGGER.info("PREDICTION_BODY_FORECAST_CPU_BENCH baselineMedianMs={} currentMedianMs={} bodies={} samples={}",quantile(bodyOld,.5),quantile(bodyNow,.5),bodies.size(),bodyNow.size());
        var before=entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());var old=new ArrayList<Long>();var now=new ArrayList<Long>();
        for(int i=0;i<640;i++) {
            long a,b,c;
            if(i%2==0) {
                a=System.nanoTime();PredictionBaselineEngine.forecast(level,motion,p -> PredictionServer.timeRateAt(entity,p),oldTargets);b=System.nanoTime();
                PredictionEngine.forecast(level,motion,p -> PredictionServer.timeRateAt(entity,p),targets);c=System.nanoTime();
                if(i>=512) {old.add(b-a);now.add(c-b);}
            } else {
                a=System.nanoTime();PredictionEngine.forecast(level,motion,p -> PredictionServer.timeRateAt(entity,p),targets);b=System.nanoTime();
                PredictionBaselineEngine.forecast(level,motion,p -> PredictionServer.timeRateAt(entity,p),oldTargets);c=System.nanoTime();
                if(i>=512) {now.add(b-a);old.add(c-b);}
            }
        }
        if(!before.equals(entity.saveWithoutId(new net.minecraft.nbt.CompoundTag()))) throw new IllegalStateException("Benchmark changed live projectile");
        com.timestop.TimeStopMod.LOGGER.info("PREDICTION_FORECAST_CPU_BENCH baselineMedianMs={} currentMedianMs={} samples={}",quantile(old,.5),quantile(now,.5),now.size());
    }
    private static double quantile(List<Long> list,double q) {var sorted=new ArrayList<>(list);Collections.sort(sorted);return sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*q)))/1e6;}
}
