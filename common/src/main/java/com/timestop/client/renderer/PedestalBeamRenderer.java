package com.timestop.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.timestop.core.ClientBubbleManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Unlit, depth-tested energy geometry, rendered in the sphere's world pass. */
public final class PedestalBeamRenderer {
    private static final double TAU = Math.PI * 2;
    private record Tint(float r, float g, float b) {
        Tint bright(float amount) { return new Tint(r + (1-r)*amount, g + (1-g)*amount, b + (1-b)*amount); }
    }
    private PedestalBeamRenderer() {}

    /** Matrix origin is the synchronized field center, not the owner or camera. */
    public static void render(BufferBuilder buffer, Tesselator tessellator, Matrix4f matrix,
                              ClientBubbleManager.ClientBubble bubble, double ticks) {
        if (!bubble.stationary) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var source = net.minecraft.core.BlockPos.containing(bubble.center);
        if (level == null || !level.hasChunkAt(source)
                || !(level.getBlockEntity(source) instanceof com.timestop.pedestal.PedestalBlockEntity pedestal)
                || !pedestal.getFieldId().equals(bubble.bubbleId) || pedestal.getWatch().isEmpty() || !pedestal.isActive()) return;
        double top = bubble.radius;
        double bottom = source.getY() + ArmillaryAnimation.watchY(pedestal) - bubble.center.y;
        double length = top - bottom;
        if (length <= .02) return;

        if (bubble.activationTicks < 0) {
            bubble.activationTicks = ticks;
        }
        double age = Math.max(0.0, ticks - bubble.activationTicks);
        double shootDuration = Math.min(18.0, Math.max(8.0, 5.0 + Math.sqrt(length) * 1.5));
        double rawProgress = shootDuration <= 0 ? 1.0 : Math.min(1.0, age / shootDuration);
        double progress = rawProgress * (2.0 - rawProgress);

        double currentTop = bottom + length * progress;
        double currentLength = currentTop - bottom;
        if (currentLength <= .02 && progress < 1.0) {
            double time = ticks / 20;
            int hex = bubble.tier.getThemeColorHex();
            Tint color = new Tint(((hex >> 16) & 255)/255f, ((hex >> 8) & 255)/255f, (hex & 255)/255f);
            Tint hot = color.bright(.82f);
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            diamond(buffer, matrix, bottom, .08, .08, hot, 1.0f);
            tessellator.end();
            RenderSystem.defaultBlendFunc();
            return;
        }

        double time = ticks / 20;
        int hex = bubble.tier.getThemeColorHex();
        Tint color = new Tint(((hex >> 16) & 255)/255f, ((hex >> 8) & 255)/255f, (hex & 255)/255f);
        Tint hot = color.bright(.82f);
        double width = Math.min(.19, .07 + length * .014);
        double phase = time * 1.5 + (bubble.bubbleId.getLeastSignificantBits() & 255) * .025;

        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // Layered light: a fine white core, colored sheath, and soft outer halo.
        tube(buffer,matrix,bottom,currentTop,.027,hot,.78f);
        tube(buffer,matrix,bottom,currentTop,.065,color,.17f);
        tube(buffer,matrix,bottom,currentTop,width*1.7,color,.035f);

        // Two rotating filaments converge into the shell or rising beam head.
        int segments = Math.min(112, Math.max(12, (int)(currentLength * 10)));
        double turns = Math.min(7, Math.max(.4, currentLength / 2.4));
        for (int strand=0; strand<2; strand++) for (int i=0; i<segments; i++) {
            double t0=(double)i/segments, t1=(double)(i+1)/segments;
            double a0=phase+strand*Math.PI+t0*turns*TAU, a1=phase+strand*Math.PI+t1*turns*TAU;
            double r0=width*(.3+.7*Math.sin(Math.PI*t0)), r1=width*(.3+.7*Math.sin(Math.PI*t1));
            ribbon(buffer,matrix,bottom+currentLength*t0,bottom+currentLength*t1,a0,a1,r0,r1,.065,color,.12f);
            ribbon(buffer,matrix,bottom+currentLength*t0,bottom+currentLength*t1,a0,a1,r0,r1,.016,color.bright(.3f),.85f);
        }

        // Small packets of energy visibly climb the continuous beam.
        int pulses=Math.max(1,Math.min(6,(int)(currentLength/2)));
        for (int i=0; i<pulses; i++) {
            double t=(time*Math.min(8,currentLength*.6)/currentLength+(double)i/pulses)%1;
            float alpha=(float)Math.sin(Math.PI*t);
            double y=bottom+currentLength*t;
            ring(buffer,matrix,y,width*1.05,.024,phase,TAU,hot,alpha*.7f);
            diamond(buffer,matrix,y,.055,Math.min(.20,currentLength*.15),hot,alpha*.9f);
        }

        // Counter-rotating broken clock rings and twelve index marks above the watch.
        double collar=Math.min(.30,length*.65), collarY=bottom+Math.min(.13,length*.25);
        for (int arc=0; arc<3; arc++) {
            ring(buffer,matrix,collarY,collar,.018,phase+arc*TAU/3,TAU*.23,color,.8f);
            ring(buffer,matrix,collarY+.035,collar*.75,.013,-phase+arc*TAU/3,TAU*.18,hot,.65f);
        }
        for (int mark=0; mark<12; mark++)
            ring(buffer,matrix,collarY,collar*1.25,.035,-phase*.35+mark*TAU/12,.075,color,.65f);

        // Leading projectile tip while shooting; terminal flare and expanding rings once arrived
        if (progress < 1.0) {
            diamond(buffer, matrix, currentTop, width * 1.6, Math.min(.25, currentLength * .4), hot, 1.0f);
            ring(buffer, matrix, currentTop, width * 1.3, .025, phase * 2, TAU, hot, .9f);
        } else {
            float impact = (float) Math.max(0.0, 1.0 - (age - shootDuration) / 6.0);
            diamond(buffer,matrix,top,.10 + .12 * impact,.15 + .18 * impact,hot,.9f + .1f * impact);
            if (impact > 0) {
                ring(buffer,matrix,top,width * 1.8 * impact,.028,phase*2,TAU,hot,impact * .9f);
            }
            double maxAngle=Math.min(.60,2.4/top);
            for (int wave=0; wave<3; wave++) {
                double t=(time*.55+wave/3.0)%1;
                double angle=.025+t*maxAngle;
                double radius=top*1.003;
                ring(buffer,matrix,radius*Math.cos(angle),radius*Math.sin(angle),.018+top*.001,
                        phase*.2,TAU,color,(float)((1-t)*.58));
            }
        }
        tessellator.end();
        RenderSystem.defaultBlendFunc();
    }

    private static void tube(BufferBuilder b, Matrix4f m, double bottom, double top, double radius, Tint c, float alpha) {
        for(int i=0;i<8;i++) {
            double a=i*TAU/8, z=(i+1)*TAU/8;
            vertex(b,m,Math.cos(a)*radius,bottom,Math.sin(a)*radius,c,alpha);
            vertex(b,m,Math.cos(z)*radius,bottom,Math.sin(z)*radius,c,alpha);
            vertex(b,m,Math.cos(z)*radius,top,Math.sin(z)*radius,c,alpha);
            vertex(b,m,Math.cos(a)*radius,top,Math.sin(a)*radius,c,alpha);
        }
    }
    private static void ribbon(BufferBuilder b, Matrix4f m, double y0, double y1, double a0, double a1,
                               double r0, double r1, double width, Tint c, float alpha) {
        vertex(b,m,Math.cos(a0)*(r0-width),y0,Math.sin(a0)*(r0-width),c,alpha);
        vertex(b,m,Math.cos(a0)*(r0+width),y0,Math.sin(a0)*(r0+width),c,alpha);
        vertex(b,m,Math.cos(a1)*(r1+width),y1,Math.sin(a1)*(r1+width),c,alpha);
        vertex(b,m,Math.cos(a1)*(r1-width),y1,Math.sin(a1)*(r1-width),c,alpha);
    }
    private static void ring(BufferBuilder b, Matrix4f m, double y, double radius, double width,
                             double start, double sweep, Tint c, double alpha) {
        int segments=Math.max(2,(int)Math.ceil(48*sweep/TAU));
        for(int i=0;i<segments;i++) {
            double a=start+sweep*i/segments, z=start+sweep*(i+1)/segments;
            vertex(b,m,Math.cos(a)*(radius-width),y,Math.sin(a)*(radius-width),c,(float)alpha);
            vertex(b,m,Math.cos(a)*(radius+width),y,Math.sin(a)*(radius+width),c,(float)alpha);
            vertex(b,m,Math.cos(z)*(radius+width),y,Math.sin(z)*(radius+width),c,(float)alpha);
            vertex(b,m,Math.cos(z)*(radius-width),y,Math.sin(z)*(radius-width),c,(float)alpha);
        }
    }
    private static void diamond(BufferBuilder b, Matrix4f m, double y, double width, double height, Tint c, float alpha) {
        for(int i=0;i<3;i++) {
            double a=i*Math.PI/3, x=Math.cos(a)*width, z=Math.sin(a)*width;
            vertex(b,m,0,y-height,0,c,alpha); vertex(b,m,x,y,z,c,alpha);
            vertex(b,m,0,y+height,0,c,alpha); vertex(b,m,-x,y,-z,c,alpha);
        }
    }
    private static void vertex(BufferBuilder b, Matrix4f m, double x, double y, double z, Tint c, float alpha) {
        b.vertex(m,(float)x,(float)y,(float)z).color(c.r,c.g,c.b,alpha).endVertex();
    }
}
