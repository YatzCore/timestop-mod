package com.timestop.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.timestop.config.TimeStopConfig;
import com.timestop.core.ClientBubbleManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

public class TemporalBubbleRenderer {

    private static final int LAT_SEGMENTS = 24;
    private static final int LON_SEGMENTS = 36;
    private static final int TOTAL_QUADS = LAT_SEGMENTS * LON_SEGMENTS;
    private static final int TOTAL_VERTICES = TOTAL_QUADS * 6; // 2 triangles per quad = 6 vertices

    // Precomputed unit sphere normals & vertices (Zero runtime trigonometry, zero GC allocations!)
    private static final float[] UNIT_X = new float[TOTAL_VERTICES];
    private static final float[] UNIT_Y = new float[TOTAL_VERTICES];
    private static final float[] UNIT_Z = new float[TOTAL_VERTICES];

    static {
        int vIndex = 0;
        for (int i = 0; i < LAT_SEGMENTS; i++) {
            double theta1 = Math.PI * i / LAT_SEGMENTS;
            double theta2 = Math.PI * (i + 1) / LAT_SEGMENTS;

            double sinT1 = Math.sin(theta1);
            double cosT1 = Math.cos(theta1);
            double sinT2 = Math.sin(theta2);
            double cosT2 = Math.cos(theta2);

            for (int j = 0; j < LON_SEGMENTS; j++) {
                double phi1 = 2.0 * Math.PI * j / LON_SEGMENTS;
                double phi2 = 2.0 * Math.PI * (j + 1) / LON_SEGMENTS;

                double cosP1 = Math.cos(phi1);
                double sinP1 = Math.sin(phi1);
                double cosP2 = Math.cos(phi2);
                double sinP2 = Math.sin(phi2);

                float x1 = (float) (sinT1 * cosP1);
                float y1 = (float) (cosT1);
                float z1 = (float) (sinT1 * sinP1);

                float x2 = (float) (sinT1 * cosP2);
                float y2 = (float) (cosT1);
                float z2 = (float) (sinT1 * sinP2);

                float x3 = (float) (sinT2 * cosP2);
                float y3 = (float) (cosT2);
                float z3 = (float) (sinT2 * sinP2);

                float x4 = (float) (sinT2 * cosP1);
                float y4 = (float) (cosT2);
                float z4 = (float) (sinT2 * sinP1);

                // Triangle 1: v1, v2, v3
                UNIT_X[vIndex] = x1; UNIT_Y[vIndex] = y1; UNIT_Z[vIndex] = z1; vIndex++;
                UNIT_X[vIndex] = x2; UNIT_Y[vIndex] = y2; UNIT_Z[vIndex] = z2; vIndex++;
                UNIT_X[vIndex] = x3; UNIT_Y[vIndex] = y3; UNIT_Z[vIndex] = z3; vIndex++;

                // Triangle 2: v1, v3, v4
                UNIT_X[vIndex] = x1; UNIT_Y[vIndex] = y1; UNIT_Z[vIndex] = z1; vIndex++;
                UNIT_X[vIndex] = x3; UNIT_Y[vIndex] = y3; UNIT_Z[vIndex] = z3; vIndex++;
                UNIT_X[vIndex] = x4; UNIT_Y[vIndex] = y4; UNIT_Z[vIndex] = z4; vIndex++;
            }
        }
    }

    // Static light direction vector (normalized)
    private static final float LIGHT_X = 0.57735F;
    private static final float LIGHT_Y = 0.7071F;
    private static final float LIGHT_Z = 0.40825F;

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!ClientBubbleManager.hasActiveBubbles()) return;
        if (!TimeStopConfig.CLIENT.enableBubbleRender.get()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        float gameTime = (System.currentTimeMillis() % 3600000) / 1000.0F;
        double userOpacity = TimeStopConfig.CLIENT.bubbleOpacity.get();
        boolean enableSpecular = TimeStopConfig.CLIENT.enableSpecularSheen.get();
        boolean enableGrid = TimeStopConfig.CLIENT.enableBubbleGrid.get();
        boolean enableEquator = TimeStopConfig.CLIENT.enableEquatorRing.get();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        float opacityScale = (float) (userOpacity / 0.35);

        for (ClientBubbleManager.ClientBubble bubble : ClientBubbleManager.getActiveBubbles()) {
            Vec3 center = bubble.getCenter(event.getPartialTick());
            float radius = (float) bubble.radius;
            int colorHex = bubble.tier.getThemeColorHex();

            float baseR = ((colorHex >> 16) & 0xFF) / 255.0F;
            float baseG = ((colorHex >> 8) & 0xFF) / 255.0F;
            float baseB = (colorHex & 0xFF) / 255.0F;

            poseStack.pushPose();
            poseStack.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);

            double toCamX = camPos.x - center.x;
            double toCamY = camPos.y - center.y;
            double toCamZ = camPos.z - center.z;
            double camDistSq = toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ;
            double camDist = Math.sqrt(camDistSq);

            float camDirX = 0.0F, camDirY = 1.0F, camDirZ = 0.0F;
            if (camDist > 1e-4) {
                camDirX = (float) (toCamX / camDist);
                camDirY = (float) (toCamY / camDist);
                camDirZ = (float) (toCamZ / camDist);
            }

            // Precompute Blinn-Phong Half Vector ONCE per bubble
            float halfX = camDirX + LIGHT_X;
            float halfY = camDirY + LIGHT_Y;
            float halfZ = camDirZ + LIGHT_Z;
            float halfLen = (float) Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);
            if (halfLen > 1e-4F) {
                halfX /= halfLen;
                halfY /= halfLen;
                halfZ /= halfLen;
            }

            Matrix4f matrix = poseStack.last().pose();

            // 1. RENDER 3D VOLUMETRIC CHRONO-SHELL (Ultra High-Performance Loop)
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            for (int v = 0; v < TOTAL_VERTICES; v++) {
                float nx = UNIT_X[v];
                float ny = UNIT_Y[v];
                float nz = UNIT_Z[v];

                float vx = nx * radius;
                float vy = ny * radius;
                float vz = nz * radius;

                // View dot product (Fresnel edge-glow)
                float viewDot = Math.abs(nx * camDirX + ny * camDirY + nz * camDirZ);
                if (viewDot > 1.0F) viewDot = 1.0F;
                float rim = 1.0F - viewDot;
                float rim2 = rim * rim;
                float fresnel = 0.08F + 0.52F * (rim2 * (float) Math.sqrt(rim));

                // 3D Specular highlight
                float specBoost = 0.0F;
                float specularAlpha = 0.0F;
                if (enableSpecular) {
                    float specDot = nx * halfX + ny * halfY + nz * halfZ;
                    if (specDot > 0.0F) {
                        float s4 = specDot * specDot * specDot * specDot;
                        float s16 = s4 * s4 * s4 * s4;
                        float s24 = s16 * (specDot * specDot * specDot * specDot * specDot * specDot * specDot * specDot);
                        specularAlpha = s24 * 0.45F;
                        specBoost = s16 * specDot * specDot * specDot * specDot * 0.4F;
                    }
                }

                float finalAlpha = (fresnel + specularAlpha) * opacityScale;
                if (finalAlpha < 0.02F) finalAlpha = 0.02F;
                else if (finalAlpha > 0.95F) finalAlpha = 0.95F;

                float r = Math.min(1.0F, baseR + specBoost);
                float g = Math.min(1.0F, baseG + specBoost);
                float b = Math.min(1.0F, baseB + specBoost);

                buffer.vertex(matrix, vx, vy, vz).color(r, g, b, finalAlpha).endVertex();
            }

            tesselator.end();

            // 2. RENDER ROTATING 3D EQUATOR & ORBIT ENERGY BANDS
            if (enableEquator) {
                renderOrbitBands(buffer, tesselator, matrix, radius, baseR, baseG, baseB, gameTime);
            }

            // 3. RENDER SCI-FI GRID LATTICE LINES
            if (enableGrid) {
                renderGridLattice(buffer, tesselator, matrix, radius, baseR, baseG, baseB);
            }

            poseStack.popPose();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void renderOrbitBands(BufferBuilder buffer, Tesselator tesselator, Matrix4f matrix, float radius, float r, float g, float b, float gameTime) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int ringSegments = 48;
        float ringRot1 = gameTime * 0.4F;
        float ringRot2 = -gameTime * 0.25F;

        // Equator Ring 1
        for (int i = 0; i < ringSegments; i++) {
            double a1 = 2.0 * Math.PI * i / ringSegments;
            double a2 = 2.0 * Math.PI * (i + 1) / ringSegments;

            float x1 = (float) (Math.cos(a1 + ringRot1) * radius * 1.002F);
            float z1 = (float) (Math.sin(a1 + ringRot1) * radius * 1.002F);
            float y1 = (float) (Math.sin(a1 * 2.0 + ringRot1) * radius * 0.06F);

            float x2 = (float) (Math.cos(a2 + ringRot1) * radius * 1.002F);
            float z2 = (float) (Math.sin(a2 + ringRot1) * radius * 1.002F);
            float y2 = (float) (Math.sin(a2 * 2.0 + ringRot1) * radius * 0.06F);

            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, 0.45F).endVertex();
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, 0.45F).endVertex();
        }

        // Polar Orbit Ring 2
        for (int i = 0; i < ringSegments; i++) {
            double a1 = 2.0 * Math.PI * i / ringSegments;
            double a2 = 2.0 * Math.PI * (i + 1) / ringSegments;

            float x1 = (float) (Math.cos(a1 + ringRot2) * radius * 1.002F);
            float z1 = (float) (Math.sin(a1 + ringRot2) * radius * 1.002F);
            float y1 = (float) (Math.sin(a1 * 3.0 + ringRot2) * radius * 0.04F);

            float x2 = (float) (Math.cos(a2 + ringRot2) * radius * 1.002F);
            float z2 = (float) (Math.sin(a2 + ringRot2) * radius * 1.002F);
            float y2 = (float) (Math.sin(a2 * 3.0 + ringRot2) * radius * 0.04F);

            float boostR = Math.min(1.0F, r + 0.3F);
            float boostG = Math.min(1.0F, g + 0.3F);
            float boostB = Math.min(1.0F, b + 0.3F);

            buffer.vertex(matrix, x1, y1, z1).color(boostR, boostG, boostB, 0.35F).endVertex();
            buffer.vertex(matrix, x2, y2, z2).color(boostR, boostG, boostB, 0.35F).endVertex();
        }

        tesselator.end();
    }

    private static void renderGridLattice(BufferBuilder buffer, Tesselator tesselator, Matrix4f matrix, float radius, float r, float g, float b) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        int latLines = 6;
        int circleSegs = 24;
        for (int i = 1; i < latLines; i++) {
            double theta = Math.PI * i / latLines;
            double sinT = Math.sin(theta);
            double cosT = Math.cos(theta);
            float y = (float) (radius * cosT);
            float rLayer = (float) (radius * sinT);

            for (int j = 0; j < circleSegs; j++) {
                double phi1 = 2.0 * Math.PI * j / circleSegs;
                double phi2 = 2.0 * Math.PI * (j + 1) / circleSegs;

                float x1 = (float) (rLayer * Math.cos(phi1));
                float z1 = (float) (rLayer * Math.sin(phi1));
                float x2 = (float) (rLayer * Math.cos(phi2));
                float z2 = (float) (rLayer * Math.sin(phi2));

                buffer.vertex(matrix, x1, y, z1).color(r, g, b, 0.22F).endVertex();
                buffer.vertex(matrix, x2, y, z2).color(r, g, b, 0.22F).endVertex();
            }
        }

        tesselator.end();
    }
}