package com.timestop.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.timestop.prediction.PredictionEngine;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** White animated navigation trails, translucent future poses and full flight arcs. */
public final class PredictionRenderer {
    private PredictionRenderer() {}
    public static void render(PoseStack poses, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        PredictionSilhouettes.beginFrame();
        PredictionClient.tick();
        if (!PredictionClient.active() || mc.options.hideGui || mc.screen != null || mc.level == null) return;
        Vec3 eye = camera.getPosition();
        var paths = PredictionClient.framePaths(partialTick);
        var outcomes=PredictionClient.frameOutcomes();
        if (paths.isEmpty() && outcomes.isEmpty()) return;
        long now = net.minecraft.Util.getMillis();
        // Spend the bounded ghost budget on the held weapon and imminent enemy shots first.
        var shots = new java.util.ArrayList<>(paths);
        shots.removeIf(p -> p.path().motion().living());
        shots.sort(java.util.Comparator.comparingInt(p -> p.aimed() ? 0 : p.prepared() ? 1 : p.threat() ? 2 : 3));
        int projectileGhosts = 0;
        for (var shot : shots) {
            var path = shot.path();
            double start = path.points().get(0).seconds(), duration = path.end().seconds() - start;
            for (double fraction : new double[]{.25, .65}) {
                if (projectileGhosts >= 8 || duration < .01) break;
                double time = start + duration * fraction;
                Vec3 position = path.positionAt(time);
                Vec3 direction = path.positionAt(Math.min(path.end().seconds(),time+.05)).subtract(path.positionAt(Math.max(start,time-.05)));
                int before = PredictionSilhouettes.projectileCount();
                PredictionSilhouettes.renderProjectile(path.motion(),position,direction,eye,poses,fraction == .25 ? .36F : .24F);
                if (PredictionSilhouettes.projectileCount() > before) projectileGhosts++;
            }
        }
        // Future bodies use the mob's renderer and model, without live entity position changes.
        for (var display : paths) {
            var path = display.path();
            if (!path.motion().living() || path.end().position().distanceToSqr(path.motion().position()) < .36) continue;
            if (!(mc.level.getEntity(path.motion().entityId()) instanceof LivingEntity entity)) continue;
            double sweep = Math.min(.4,1.5 / Math.max(.05,path.end().seconds()));
            for (double fraction : new double[]{sweep, .95}) {
                Vec3 pos = atFraction(display, fraction, now, partialTick);
                Vec3 next = atFraction(display, Math.min(1, fraction + .03), now, partialTick);
                PredictionSilhouettes.render(entity, pos, next.subtract(pos), eye, poses, partialTick, fraction == .95 ? .16F : .23F);
            }
        }
        for (var preview : PredictionClient.teleports()) {
            if (mc.level.getEntity(preview.entityId()) instanceof LivingEntity entity)
                PredictionSilhouettes.render(entity,preview.destination(),entity.getLookAngle(),eye,poses,partialTick,.28F);
        }
        int hitGhosts=0;
        for(var shot:paths) if(!shot.path().motion().living() && shot.path().hitEntity()>=0
                && shot.path().hitEntity()!=mc.player.getId() && hitGhosts++<2) {
            if(mc.level.getEntity(shot.path().hitEntity()) instanceof LivingEntity entity) {
                Vec3 pos=PredictionClient.targetPosition(entity.getId(),shot.path().end().seconds(),shot.aimed());
                if(pos!=null) PredictionSilhouettes.render(entity,pos,entity.getLookAngle(),eye,poses,partialTick,.35F);
            }
        }
        int affectedGhosts=0;
        var highlighted=new java.util.HashSet<Integer>();
        for(var display:outcomes) for(var affected:display.event().affected()) {
            if(affectedGhosts>=2) break;
            if(affected.entityId()==mc.player.getId() || !highlighted.add(affected.entityId())) continue;
            if(mc.level.getEntity(affected.entityId()) instanceof LivingEntity entity) {
                PredictionSilhouettes.render(entity,affected.position(),entity.getLookAngle(),eye,poses,partialTick,
                        display.alpha()*(.12F+.2F*affected.strength()),display.event().explosion()?0xFFAAA3:display.event().color());
                affectedGhosts++;
            }
        }
        PredictionSilhouettes.flush();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poses.last().pose();
        for (var display : paths) {
            var path = display.path();
            int color = display.threat() ? 0xFFD9A1 : path.hitEntity()>=0 ? 0xFFE3A6 : 0xFFFFFF;
            double travelled = 0;
            for (int i=1; i<path.points().size(); i++) {
                Vec3 a = position(display,i-1,now,partialTick), b = position(display,i,now,partialTick);
                double length = a.distanceTo(b);
                if (length < 1e-6) continue;
                // Distance subdivisions keep pulses and prepared-shot dashes even after packet compaction.
                int pieces=Math.max(1,Math.min(16,(int)Math.ceil(length/.22)));
                for(int j=0;j<pieces;j++) {
                    double distance=travelled+length*(j+.5)/pieces;
                    double pulse=Math.pow(.5+.5*Math.cos(distance*2.5-now*.006),10);
                    boolean dashed=display.prepared() || path.motion().kind()==com.timestop.prediction.PredictionMotion.Kind.UNKNOWN;
                    float fade=PredictionClient.opacity()*(dashed && ((int)(distance*4)&1)!=0?.2F:1);
                    Vec3 start=a.lerp(b,j/(double)pieces),finish=a.lerp(b,(j+1)/(double)pieces);
                    ribbon(buffer,matrix,eye,start,finish,color,(int)((20+pulse*20)*fade),path.motion().living()?4:3);
                    ribbon(buffer,matrix,eye,start,finish,color,(int)((110+pulse*110)*fade),path.motion().living()?1.5:1.1);
                }
                travelled += length;
            }
            Vec3 end = position(display,path.points().size()-1,now,partialTick);
            if (end.distanceToSqr(path.motion().position()) < .02) continue;
            if (path.impact() && !path.motion().living() && path.motion().kind()!=com.timestop.prediction.PredictionMotion.Kind.TNT) {
                // World-anchored impact ring: camera/entity interpolation never translates this endpoint.
                Vec3 last = path.points().get(Math.max(0,path.points().size()-2)).position();
                Vec3 direction = path.normal().lengthSqr()>.5?path.normal():path.end().position().subtract(last).normalize().reverse();
                Vec3 side = direction.cross(new Vec3(0,1,0));
                if (side.lengthSqr()<.01) side = new Vec3(1,0,0);
                side = side.normalize().scale(.18);
                Vec3 up = direction.cross(side).normalize().scale(.18);
                end = end.add(direction.scale(.015));
                for(int i=0;i<20;i++) {
                    double a=i*Math.PI/10,b=(i+1)*Math.PI/10;
                    ribbon(buffer,matrix,eye,end.add(side.scale(Math.cos(a))).add(up.scale(Math.sin(a))),
                            end.add(side.scale(Math.cos(b))).add(up.scale(Math.sin(b))),color,(int)(230*PredictionClient.opacity()),1.4);
                }
            }
        }
        for(var display:outcomes) {
            var event=display.event();Vec3 center=event.center();
            float alpha=display.alpha();
            double urgency=event.explosion()?Math.max(0,1-event.seconds()/3):.3;
            double pulse=.5+.5*Math.sin(now*.008);
            int color=event.color();double radius=event.radius();
            int light=((int)((color>>16&255)*.65+255*.35)<<16)|((int)((color>>8&255)*.65+255*.35)<<8)|(int)((color&255)*.65+255*.35);
            // Horizontal footprint plus a restrained equator conveys area without a filled screen.
            double groundY=center.y+.025;
            var floor=PredictionEngine.clip(mc.level,center.add(0,.1,0),center.add(0,-2,0));
            if(floor.getType()!=net.minecraft.world.phys.HitResult.Type.MISS) groundY=floor.getLocation().y+.025;
            for(int i=0;i<96;i++) {
                if(event.conditional() && i%4==3) continue;
                double a=i*Math.PI/48,b=(i+1)*Math.PI/48;
                Vec3 start=new Vec3(center.x+Math.cos(a)*radius,groundY,center.z+Math.sin(a)*radius);
                Vec3 finish=new Vec3(center.x+Math.cos(b)*radius,groundY,center.z+Math.sin(b)*radius);
                ribbon(buffer,matrix,eye,start,finish,color,(int)(alpha*35),4);
                ribbon(buffer,matrix,eye,start,finish,light,(int)(alpha*(180+urgency*45+pulse*25)),1.7);
                Vec3 insideA=new Vec3(center.x+Math.cos(a)*radius*.94,groundY,center.z+Math.sin(a)*radius*.94);
                Vec3 insideB=new Vec3(center.x+Math.cos(b)*radius*.94,groundY,center.z+Math.sin(b)*radius*.94);
                vertex(buffer,matrix,start.subtract(eye),color,(int)(alpha*16));
                vertex(buffer,matrix,finish.subtract(eye),color,(int)(alpha*16));
                vertex(buffer,matrix,insideB.subtract(eye),color,0);vertex(buffer,matrix,insideA.subtract(eye),color,0);
                if(event.explosion()) {
                    // Sparse vertical arcs indicate blast volume, not a block-destruction boundary.
                    for(int plane=0;plane<2;plane++) {
                        Vec3 u=new Vec3(plane==0?Math.cos(a)*radius:0,Math.sin(a)*radius,plane==1?Math.cos(a)*radius:0);
                        Vec3 v=new Vec3(plane==0?Math.cos(b)*radius:0,Math.sin(b)*radius,plane==1?Math.cos(b)*radius:0);
                        if(i%4<2) ribbon(buffer,matrix,eye,center.add(u),center.add(v),color,(int)(alpha*45),.8);
                    }
                }
            }
            double inner=radius*(.83+.03*pulse);
            for(int i=0;i<48;i++) {
                double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
                ribbon(buffer,matrix,eye,new Vec3(center.x+Math.cos(a)*inner,groundY,center.z+Math.sin(a)*inner),
                        new Vec3(center.x+Math.cos(b)*inner,groundY,center.z+Math.sin(b)*inner),color,(int)(alpha*35),1);
            }
        }
        for(var preview:PredictionClient.teleports()) {
            Vec3 from=preview.origin().add(0,.08,0), to=preview.destination().add(0,.08,0);
            for(int i=0;i<24;i+=2) ribbon(buffer,matrix,eye,from.lerp(to,i/24.0),from.lerp(to,(i+1)/24.0),0xDCCAFF,170,1.6);
            double radius=.55+.05*Math.sin(now*.009);
            for(int i=0;i<32;i++) {
                double a=i*Math.PI/16,b=(i+1)*Math.PI/16;
                ribbon(buffer,matrix,eye,to.add(Math.cos(a)*radius,0,Math.sin(a)*radius),
                        to.add(Math.cos(b)*radius,0,Math.sin(b)*radius),0xDCCAFF,245,2);
            }
        }
        // Model vertices already include the world pose matrix; avoid transforming their outlines twice.
        var identity=new Matrix4f();
        for(var edge:PredictionSilhouettes.edges()) ribbon(buffer,identity,Vec3.ZERO,edge.from(),edge.to(),edge.color(),edge.alpha(),.65);
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.disableBlend();
    }
    private static Vec3 position(PredictionClient.DisplayPath display,int index,long now,float partialTick) {
        Vec3 current = display.path().points().get(index).position();
        return display.path().motion().living() ? current.add(0,.06,0) : current;
    }
    private static Vec3 atFraction(PredictionClient.DisplayPath display,double fraction,long now,float partialTick) {
        return display.path().positionAt(fraction * display.path().end().seconds());
    }
    private static void ribbon(BufferBuilder b,Matrix4f matrix,Vec3 eye,Vec3 start,Vec3 end,int color,int alpha,double width) {
        // Keep the shot origin physically correct without drawing a broad ribbon through the near plane.
        double near=.3;
        if(start.distanceToSqr(eye)<near*near) {
            if(end.distanceToSqr(eye)<near*near) return;
            Vec3 delta=end.subtract(start),relative=start.subtract(eye);
            double aa=delta.lengthSqr(),bb=2*relative.dot(delta),cc=relative.lengthSqr()-near*near;
            if(aa<1e-12) return;
            double t=(-bb+Math.sqrt(Math.max(0,bb*bb-4*aa*cc)))/(2*aa);
            start=start.lerp(end,Math.max(0,Math.min(1,t)));
        }
        Vec3 axis=end.subtract(start),midpoint=start.lerp(end,.5);
        alpha=(int)(alpha*Math.max(0,Math.min(1,(midpoint.distanceTo(eye)-.3)/1.7)));
        if(alpha<=0) return;
        Vec3 side=axis.cross(eye.subtract(midpoint));
        if(side.lengthSqr()<1e-10) return;
        side=side.normalize().scale(Math.max(.0003,Math.min(.3,midpoint.distanceTo(eye)*.0009))*width);
        vertex(b,matrix,start.add(side).subtract(eye),color,alpha);
        vertex(b,matrix,start.subtract(side).subtract(eye),color,alpha);
        vertex(b,matrix,end.subtract(side).subtract(eye),color,alpha);
        vertex(b,matrix,end.add(side).subtract(eye),color,alpha);
    }
    private static void vertex(BufferBuilder b,Matrix4f matrix,Vec3 p,int color,int alpha) {
        b.vertex(matrix,(float)p.x,(float)p.y,(float)p.z).color(color>>16&255,color>>8&255,color&255,alpha).endVertex();
    }
}
