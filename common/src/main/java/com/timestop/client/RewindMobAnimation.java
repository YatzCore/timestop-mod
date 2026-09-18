package com.timestop.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.timestop.network.RewindMobPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import java.util.*;
/** Short visual-only unfolding and inward helix. Never changes entity physics or AI. */
public final class RewindMobAnimation {
 private static final Map<UUID,Integer> active=new LinkedHashMap<>();
 private static ClientLevel owner;
 private static final int DURATION=16, LIMIT=16;
 public static void begin(RewindMobPacket packet){
  var mc=Minecraft.getInstance();
  if(mc.level==null || !mc.level.dimension().location().equals(packet.dimension()))return;
  if(owner!=mc.level){clear();owner=mc.level;}
  if(active.size()<LIMIT)active.putIfAbsent(packet.entity(),0);
 }
 public static void clear(){active.clear();owner=null;}
 public static void tick(){
  var mc=Minecraft.getInstance();
  if(owner!=mc.level){clear();return;}
  if(owner==null)return;
  active.replaceAll((id,age)->age+1);
  active.values().removeIf(age->age>=DURATION);
  if(mc.options.particles().get()==net.minecraft.client.ParticleStatus.MINIMAL)return;
  for(var entity:owner.entitiesForRendering()) {
   Integer age=active.get(entity.getUUID());
   if(age==null || age%3!=0 || !(entity instanceof LivingEntity) || mc.player==null || entity.distanceToSqr(mc.player)>32*32)continue;
   float p=age/(float)DURATION;
   for(int strand=0;strand<2;strand++) {
    double angle=p*Math.PI*4+strand*Math.PI;
    double radius=entity.getBbWidth()*(0.8-0.4*p);
    owner.addParticle(new DustParticleOptions(new Vector3f(0.6F,0.35F,1F),0.75F),
     entity.getX()+Math.cos(angle)*radius,entity.getY()+p*entity.getBbHeight(),entity.getZ()+Math.sin(angle)*radius,0,0.015,0);
   }
  }
 }
 public static void transform(LivingEntity entity,PoseStack pose,float partial){
  Integer age=active.get(entity.getUUID());
  if(age==null || entity.level()!=owner)return;
  float p=Math.min(1,(age+partial)/DURATION);
  float remaining=(1-p)*(1-p)*(1-p);
  // Feet stay anchored while the body unfolds out of a small temporal twist.
  pose.mulPose(Axis.YP.rotationDegrees(remaining*35));
  pose.scale(1-remaining*0.25F,1-remaining*0.8F,1-remaining*0.25F);
 }
}
