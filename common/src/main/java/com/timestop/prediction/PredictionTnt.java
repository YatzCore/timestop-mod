package com.timestop.prediction;

import java.util.*;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Native TNT steps on copied numbers, including its independent fuse clock. */
public final class PredictionTnt {
    private PredictionTnt() {}
    public static PredictionEngine.Path forecast(Level level,PredictionMotion motion,ToDoubleFunction<Vec3> rate) {
        var points=new ArrayList<PredictionEngine.Point>();
        Vec3 pos=motion.position(),velocity=motion.velocity();double credit=motion.phase();int fuse=motion.effects().fuse();
        points.add(new PredictionEngine.Point(pos,0));
        for(int tick=1;tick<=60;tick++) {
            double factor=Math.max(0,Math.min(50,rate.applyAsDouble(pos)));
            // ServerLevel drops tick credit when an entity returns to a full native tick rate.
            if(factor>=1) credit=0;
            credit+=factor;int steps=Math.min(50,(int)(credit+1e-7));credit-=steps;
            for(int step=0;step<steps;step++) {
                if(!motion.noGravity()) velocity=velocity.add(0,-motion.gravity(),0);
                AABB box=PredictionEngine.bounds(motion,pos);
                if(!loaded(level,box.expandTowards(velocity).inflate(1)))
                    return new PredictionEngine.Path(motion,points,false,-1);
                var source=level.getEntity(motion.entityId());
                var obstacles=new ArrayList<>(level.getEntityCollisions(source,box.expandTowards(velocity)));
                obstacles.add(level.getWorldBorder().getCollisionShape());
                Vec3 movement=Entity.collideBoundingBox(null,velocity,box,level,obstacles);
                boolean ground=movement.y!=velocity.y && velocity.y<0;
                pos=pos.add(movement);
                var floor=level.getBlockState(BlockPos.containing(pos.x,pos.y-.2,pos.z));
                double y=velocity.y;
                if(movement.y!=velocity.y) {
                    if(floor.is(Blocks.SLIME_BLOCK)) y=y<0?-y*.8:y;
                    else if(floor.getBlock() instanceof net.minecraft.world.level.block.BedBlock) y=y<0?-y*.66:y;
                    else y=0;
                }
                velocity=new Vec3(movement.x!=velocity.x?0:velocity.x,y,movement.z!=velocity.z?0:velocity.z);
                // Native movement applies block speed factors before TNT's own friction.
                var state=level.getBlockState(BlockPos.containing(pos));
                // These callbacks alter the next native tick's copied state. A speculative
                // detonation after crossing them would be less useful than an incomplete path.
                var fluid=level.getFluidState(BlockPos.containing(pos));
                if(state.is(Blocks.COBWEB) || state.is(Blocks.BUBBLE_COLUMN) || state.is(Blocks.SWEET_BERRY_BUSH)
                        || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")
                        || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(floor.getBlock()).getNamespace().equals("minecraft")
                        || !fluid.isEmpty() && !net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getType()).getNamespace().equals("minecraft")) {
                    points.add(new PredictionEngine.Point(pos,tick*.05));
                    return new PredictionEngine.Path(motion,points,false,-1);
                }
                float speed=state.getBlock().getSpeedFactor();
                if(speed==1 && !state.is(Blocks.WATER) && !state.is(Blocks.BUBBLE_COLUMN)) speed=floor.getBlock().getSpeedFactor();
                velocity=velocity.multiply(speed,1,speed).scale(motion.drag());
                if(ground) velocity=velocity.multiply(.7,-.5,.7);
                if(--fuse<=0) {
                    points.add(new PredictionEngine.Point(pos,tick*.05));
                    return new PredictionEngine.Path(motion,points,true,-1);
                }
                velocity=fluidPush(level,PredictionEngine.bounds(motion,pos),velocity);
                if(pos.distanceToSqr(motion.position())>256*256) return new PredictionEngine.Path(motion,points,false,-1);
            }
            points.add(new PredictionEngine.Point(pos,tick*.05));
        }
        return new PredictionEngine.Path(motion,points,false,-1);
    }
    private static boolean loaded(Level level,AABB box) {
        for(int x=Mth.floor(box.minX)>>4;x<=Mth.floor(box.maxX)>>4;x++)
            for(int z=Mth.floor(box.minZ)>>4;z<=Mth.floor(box.maxZ)>>4;z++)
                if(!level.hasChunk(x,z)) return false;
        return true;
    }
    private static Vec3 fluidPush(Level level,AABB bounds,Vec3 velocity) {
        AABB box=bounds.deflate(.001);
        for(var tag:List.of(FluidTags.WATER,FluidTags.LAVA)) {
            Vec3 flow=Vec3.ZERO;double depth=0;int count=0;
            for(int x=Mth.floor(box.minX);x<Mth.ceil(box.maxX);x++)
                for(int y=Mth.floor(box.minY);y<Mth.ceil(box.maxY);y++)
                    for(int z=Mth.floor(box.minZ);z<Mth.ceil(box.maxZ);z++) {
                        var p=new BlockPos(x,y,z);var fluid=level.getFluidState(p);
                        if(!fluid.is(tag)) continue;
                        double surface=y+fluid.getHeight(level,p);
                        if(surface<box.minY) continue;
                        depth=Math.max(depth,surface-box.minY);
                        Vec3 current=fluid.getFlow(level,p);
                        flow=flow.add(depth<.4?current.scale(depth):current);count++;
                    }
            if(count>0 && flow.lengthSqr()>0) {
                double scale=tag==FluidTags.WATER?.014:level.dimensionType().ultraWarm()?.007:.0023333333333333335;
                flow=flow.scale(1.0/count).normalize().scale(scale);
                if(Math.abs(velocity.x)<.003 && Math.abs(velocity.z)<.003 && flow.length()<.0045) flow=flow.normalize().scale(.0045);
                velocity=velocity.add(flow);
            }
        }
        return velocity;
    }
}
