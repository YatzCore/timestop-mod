package com.timestop.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Cuboids are transformed once, then submitted as cached textured vertices. */
final class ArmillaryMesh {
    private record Part(float[] vertices, int material, int segment, int detail) {}
    private final Part[] parts;
    private static final int[][] FACES = {
            {2,3,1,0}, {7,6,4,5}, {3,7,5,1}, {6,2,0,4}, {6,7,3,2}, {0,1,5,4}
    };
    private static final float[][] NORMALS = {{0,0,-1},{0,0,1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}};
    private static final int[][] UV_AXES = {{0,1},{0,1},{2,1},{2,1},{0,2},{0,2}};

    ArmillaryMesh(float[][] cubes, double centerY) {
        parts = new Part[cubes.length];
        for (int c=0; c<cubes.length; c++) {
            float[] p=cubes[c]; int mat=(int)p[8];
            Quaternionf rot = new Quaternionf().rotationAxis((float)Math.toRadians(p[7]), p[6]==0?1:0, p[6]==1?1:0, p[6]==2?1:0);
            Vector3f[] corners = new Vector3f[8];
            for (int i=0;i<8;i++) corners[i]=new Vector3f(p[(i&1)==0?0:3]-p[10],p[(i&2)==0?1:4]-p[11],p[(i&4)==0?2:5]-p[12])
                    .rotate(rot).add(p[10]-8,p[11]-(float)(centerY*16),p[12]-8).div(16);
            float[] vertices=new float[24*8]; int k=0;
            for(int face=0;face<6;face++) {
                Vector3f normal=new Vector3f(NORMALS[face]).rotate(rot);
                int ua=UV_AXES[face][0],va=UV_AXES[face][1];
                float du=p[ua+3]-p[ua],dv=p[va+3]-p[va];
                for(int v=0;v<4;v++) {
                    Vector3f point=corners[FACES[face][v]];
                    vertices[k++]=point.x; vertices[k++]=point.y; vertices[k++]=point.z;
                    vertices[k++]=((mat%4)*16+(v==1||v==2?du:0))/64;
                    vertices[k++]=((mat/4)*16+(v>=2?dv:0))/64;
                    vertices[k++]=normal.x; vertices[k++]=normal.y; vertices[k++]=normal.z;
                }
            }
            parts[c]=new Part(vertices,mat,(int)p[9],(int)p[13]);
        }
    }

    void render(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, double glow, double time, int ring, int detail) {
        Vector3f position=new Vector3f(), normal=new Vector3f();
        for (Part part:parts) {
            if(part.detail>detail) continue;
            float tint=1; int illumination=light;
            if (part.material==5) {
                double pulse=Math.pow(Math.max(0,Math.cos(part.segment*Math.PI/8-time*2+ring)),8);
                tint=(float)(.35+glow*(.4+.25*pulse));
                illumination=LightTexture.pack(Math.max(LightTexture.block(light),(int)(15*glow)),LightTexture.sky(light));
            }
            float[] v=part.vertices;
            for(int face=0;face<v.length;face+=32) {
                normal.set(v[face+5],v[face+6],v[face+7]).mul(pose.normal());
                for(int i=face;i<face+32;i+=8) {
                    position.set(v[i],v[i+1],v[i+2]).mulPosition(pose.pose());
                    buffer.vertex(position.x,position.y,position.z,tint,tint,tint,1,v[i+3],v[i+4],overlay,illumination,normal.x,normal.y,normal.z);
                }
            }
        }
    }
}
