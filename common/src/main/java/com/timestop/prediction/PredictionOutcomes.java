package com.timestop.prediction;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Potential outcomes on current loaded terrain, not damage or destruction guarantees. */
public final class PredictionOutcomes {
    public record Affected(int entityId,Vec3 position,float strength) {}
    public record Event(int sourceId,PredictionEffects.Kind kind,Vec3 center,float radius,double seconds,
                        int color,boolean conditional,List<Affected> affected) {
        public Event {
            affected=List.copyOf(affected);
            if(affected.size()>17 || !Double.isFinite(center.lengthSqr()) || !Float.isFinite(radius) || radius<=0 || radius>256
                    || !Double.isFinite(seconds) || seconds<0 || seconds>60.01)
                throw new IllegalArgumentException("Invalid prediction outcome");
            for(var a:affected) if(!Double.isFinite(a.position().lengthSqr()) || !Float.isFinite(a.strength()) || a.strength()<0 || a.strength()>1)
                throw new IllegalArgumentException("Invalid affected target");
        }
        public boolean explosion() {return kind==PredictionEffects.Kind.EXPLOSION || kind==PredictionEffects.Kind.TNT || kind==PredictionEffects.Kind.CREEPER;}
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(sourceId);b.writeEnum(kind);PredictionMotion.writeVec(b,center);b.writeFloat(radius);
            b.writeFloat((float)seconds);b.writeInt(color);b.writeBoolean(conditional);b.writeVarInt(affected.size());
            for(var a:affected) {b.writeVarInt(a.entityId());PredictionMotion.writeVec(b,a.position());b.writeFloat(a.strength());}
        }
        public static Event read(FriendlyByteBuf b) {
            int id=b.readVarInt();var kind=b.readEnum(PredictionEffects.Kind.class);var center=PredictionMotion.readVec(b);
            float radius=b.readFloat();double seconds=b.readFloat();int color=b.readInt();boolean conditional=b.readBoolean();int count=b.readVarInt();
            if(count<0 || count>17) throw new IllegalArgumentException("Invalid outcome targets");
            var targets=new ArrayList<Affected>();
            for(int i=0;i<count;i++) targets.add(new Affected(b.readVarInt(),PredictionMotion.readVec(b),b.readFloat()));
            return new Event(id,kind,center,radius,seconds,color,conditional,targets);
        }
    }
    private PredictionOutcomes() {}
    public static List<Event> events(Level level,List<PredictionEngine.Path> paths,Map<Integer,PredictionEngine.Path> targets,int viewer,Vec3 position) {
        var candidates=new ArrayList<PredictionEngine.Path>(paths);
        candidates.removeIf(p -> p.motion().effects().kind()==PredictionEffects.Kind.NONE);
        // Cheap geometry-only ranking happens before costly exposure rays. Incoming danger wins
        // even when the owner has several distant explosive shots already in flight.
        candidates.sort(Comparator.comparingDouble(p -> {
            var preview=event(level,p,Map.of());
            return preview==null?Double.MAX_VALUE:priority(preview,viewer,position)+(p.motion().ownerId()==viewer?-1000:0);
        }));
        var result=new ArrayList<Event>();
        for(var path:candidates) {
            var event=event(level,path,targets);
            if(event!=null) result.add(event);
            if(result.size()==4) break;
        }
        result.sort(Comparator.comparingDouble(e -> priority(e,viewer,position)));
        return List.copyOf(result);
    }
    public static double priority(Event e,int viewer,Vec3 position) {
        boolean danger=e.explosion() && (e.center().distanceToSqr(position)<e.radius()*e.radius()
                || e.affected().stream().anyMatch(a -> a.entityId()==viewer));
        return (danger?-100000:0)+(e.sourceId()==-1?-50000:0)+e.seconds()*100+e.center().distanceToSqr(position);
    }
    public static Event event(Level level,PredictionEngine.Path path,Map<Integer,PredictionEngine.Path> targets) {
        var motion=path.motion();var profile=motion.effects();var kind=profile.kind();
        if(kind==PredictionEffects.Kind.NONE) return null;
        double seconds=path.end().seconds();Vec3 center=path.end().position();float radius;
        boolean explosion=kind==PredictionEffects.Kind.EXPLOSION || kind==PredictionEffects.Kind.TNT || kind==PredictionEffects.Kind.CREEPER;
        if(kind==PredictionEffects.Kind.CREEPER) {
            if(!(level.getEntity(motion.entityId()) instanceof Creeper c) || !c.isAlive() || !c.isIgnited() && c.getSwellDir()<=0) return null;
            // Living ticks remain continuous in prediction; swelling is not movement-scaled.
            seconds=profile.fuse()*.05;
            if(seconds>3) return null;
            center=path.positionAt(seconds);
        } else if(!path.impact()) return null;
        if(kind==PredictionEffects.Kind.TNT) {if(seconds>3) return null;center=center.add(0,motion.height()*.0625,0);}
        radius=explosion?profile.power()*2:kind==PredictionEffects.Kind.LINGERING?3:4;
        if(radius<=0) return null;
        var affected=new ArrayList<Affected>();
        // A lingering area is its initial footprint. Later entrants/effect timing are unknown.
        if(kind!=PredictionEffects.Kind.LINGERING) for(var target:targets.values()) {
            if(target.motion().entityId()==motion.entityId()) continue;
            var living=level.getEntity(target.motion().entityId());
            if(!(living instanceof LivingEntity entity) || !entity.isAlive() || entity.isSpectator()) continue;
            boolean stationary=target.motion().velocity().lengthSqr()<1e-8 && target.motion().routeSpeed()==0;
            boolean plannedStop=!target.motion().route().isEmpty() && target.end().position().distanceToSqr(target.motion().route().get(target.motion().route().size()-1))<.01;
            if(seconds>target.end().seconds() && !stationary && !plannedStop && !target.impact()) continue;
            Vec3 pos=target.positionAt(seconds);double distance=pos.distanceTo(center);
            boolean rehydrate=kind==PredictionEffects.Kind.WATER && entity instanceof net.minecraft.world.entity.animal.axolotl.Axolotl;
            if(distance>=radius && !rehydrate) continue;
            float strength=rehydrate?1:(float)(1-distance/radius);
            if(explosion) {
                strength*=exposure(level,center,PredictionEngine.bounds(target.motion(),pos));
            } else {
                if(!PredictionEngine.bounds(target.motion(),pos).intersects(PredictionEngine.bounds(motion,center).inflate(4,2,4))) continue;
                if(kind==PredictionEffects.Kind.WATER) {
                    if(!entity.isSensitiveToWater() && !entity.isOnFire() && !(entity instanceof net.minecraft.world.entity.animal.axolotl.Axolotl)) continue;
                } else {
                    if(!entity.isAffectedByPotions()) continue;
                    if(path.hitEntity()==entity.getId()) strength=1;
                    final float intensity=strength;
                    boolean effective=profile.effects().stream().anyMatch(e -> {
                        var type=BuiltInRegistries.MOB_EFFECT.get(e.id());
                        return type!=null && (type.isInstantenous() || acceptsVanillaEffect(entity,type)
                                && (e.duration()==-1 || (int)(intensity*e.duration()+.5)>20));
                    });
                    if(!effective) continue;
                }
            }
            if(strength>0) affected.add(new Affected(entity.getId(),pos,strength));
        }
        affected.sort(Comparator.comparingDouble(a -> -a.strength()));
        return new Event(motion.entityId(),kind,center,radius,seconds,profile.color(),profile.conditional(),affected.stream().limit(17).toList());
    }
    private static boolean acceptsVanillaEffect(LivingEntity entity,net.minecraft.world.effect.MobEffect effect) {
        // Forge's canBeAffected dispatches an application event. Prediction must never invoke
        // gameplay callbacks; display native eligibility, with modded effect overrides uncertain.
        if(effect==net.minecraft.world.effect.MobEffects.POISON.value() && entity instanceof net.minecraft.world.entity.monster.Spider) return false;
        if(entity.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)
                && (effect==net.minecraft.world.effect.MobEffects.POISON.value() || effect==net.minecraft.world.effect.MobEffects.REGENERATION.value())) return false;
        return effect!=net.minecraft.world.effect.MobEffects.WITHER.value()
                || !(entity instanceof net.minecraft.world.entity.monster.WitherSkeleton || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss);
    }
    /** Native exposure sampling translated to a future box, without translating the live entity. */
    public static float exposure(Level level,Vec3 center,AABB box) {
        double dx=1/((box.maxX-box.minX)*2+1),dy=1/((box.maxY-box.minY)*2+1),dz=1/((box.maxZ-box.minZ)*2+1);
        double ox=(1-Math.floor(1/dx)*dx)/2,oz=(1-Math.floor(1/dz)*dz)/2;int seen=0,total=0;
        for(double x=0;x<=1;x+=dx) for(double y=0;y<=1;y+=dy) for(double z=0;z<=1;z+=dz) {
            Vec3 sample=new Vec3(box.minX+(box.maxX-box.minX)*x+ox,box.minY+(box.maxY-box.minY)*y,box.minZ+(box.maxZ-box.minZ)*z+oz);
            if(PredictionEngine.loadedSegment(level,sample,center) && PredictionEngine.clip(level,sample,center).getType()==HitResult.Type.MISS) seen++;
            total++;
        }
        return total==0?0:(float)seen/total;
    }
}
