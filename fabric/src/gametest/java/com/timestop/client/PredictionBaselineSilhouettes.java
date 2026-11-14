package com.timestop.client;



import com.mojang.blaze3d.platform.NativeImage;

import com.mojang.blaze3d.vertex.*;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.*;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;

import net.minecraft.client.renderer.texture.DynamicTexture;

import net.minecraft.client.renderer.texture.OverlayTexture;

import net.minecraft.client.renderer.texture.TextureAtlas;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.world.entity.LivingEntity;

import com.timestop.prediction.PredictionMotion;

import net.minecraft.world.item.ItemDisplayContext;

import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.Items;

import net.minecraft.world.entity.projectile.ThrowableItemProjectile;

import org.joml.Matrix4f;

import net.minecraft.world.phys.Vec3;



/** Draws the actual mob model as a white translucent projection; never moves or ticks an entity. */

public final class PredictionBaselineSilhouettes {

    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(4096));

    private static ResourceLocation white;

    private static int count, projectileCount;

    public record Edge(Vec3 from,Vec3 to,int color,int alpha) {}

    private static final java.util.List<Edge> EDGES=new java.util.ArrayList<>();

    private PredictionBaselineSilhouettes() {}

    public static void beginFrame() { count = 0; projectileCount = 0; EDGES.clear(); }

    public static java.util.List<Edge> edges() { return EDGES; }

    public static void flush() { BUFFER.endBatch(); }

    private static float fade(Vec3 position,Vec3 camera,float alpha) {

        return alpha*PredictionClient.opacity()*(float)Math.max(0,Math.min(1,(position.distanceTo(camera)-.75)/1.25));

    }

    public static int count() { return count; }

    public static int projectileCount() { return projectileCount; }



    private static RenderType type() {

        if (white == null) {

            var pixel = new NativeImage(64, 64, false);

            for(int y=0;y<64;y++) for(int x=0;x<64;x++)

                pixel.setPixelRGBA(x,y,((y%8==0?255:190)<<24)|0xFFFFFF);

            white = Minecraft.getInstance().getTextureManager().register("prediction_silhouette", new DynamicTexture(pixel));

        }

        return RenderType.entityTranslucent(white);

    }



    /** Standalone arrow geometry also works for shots that have no entity yet or are beyond tracking. */

    public static void renderProjectile(PredictionMotion motion, Vec3 position, Vec3 direction, Vec3 camera,

                                        PoseStack poses, float alpha) {

        alpha=fade(position,camera,alpha);

        if (alpha<=.005 || projectileCount>=8 || direction.lengthSqr() < 1e-8) return;

        var mc = Minecraft.getInstance();

        boolean arrow = motion.kind() == PredictionMotion.Kind.ARROW;

        if (!arrow && motion.kind() != PredictionMotion.Kind.THROWABLE && motion.kind() != PredictionMotion.Kind.FIREBALL) return;

        // Item sprites need their atlas alpha mask; whitening the texture turns bottles into squares.

        var type = arrow ? type() : RenderType.itemEntityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);

        var tint = new WhiteConsumer(BUFFER.getBuffer(type), Math.round(alpha * 255),0xFFFFFF,camera.y,arrow);

        poses.pushPose();

        try {

            Vec3 offset = position.subtract(camera); poses.translate(offset.x, offset.y, offset.z);

            if (arrow) {

                poses.mulPose(Axis.YP.rotationDegrees((float)Math.toDegrees(Math.atan2(direction.x, direction.z)) - 90));

                poses.mulPose(Axis.ZP.rotationDegrees((float)Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()))));

                // Shaft, pointed head and two fins retain the shape instead of whitening a rectangular texture plane.

                boolean trident = motion.waterDrag() > .9;

                float half = trident ? .7F : .43F;

                for (int i = 0; i < 4; i++) {

                    poses.mulPose(Axis.XP.rotationDegrees(90));

                    Matrix4f matrix = poses.last().pose();

                    quad(tint, matrix, -half, -.018F, half, .018F);

                    arrowHead(tint, matrix, half, trident ? .16F : .12F);

                    if (trident) {

                        quad(tint, matrix, half - .14F, .08F, half + .1F, .1F);

                        quad(tint, matrix, half - .14F, -.1F, half + .1F, -.08F);

                    } else quad(tint, matrix, -half, -.09F, -half + .18F, .09F);

                }

            } else {

                ItemStack item = ItemStack.EMPTY;

                if (mc.level.getEntity(motion.entityId()) instanceof ThrowableItemProjectile throwable) item = throwable.getItem();

                else if (motion.entityId() == -1) item = mc.player.isUsingItem() ? mc.player.getUseItem() : mc.player.getMainHandItem();

                if(item.isEmpty() && !motion.effects().item().equals(com.timestop.prediction.PredictionEffects.NONE.item()))

                    item=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(motion.effects().item()));

                if (item.isEmpty() && motion.kind() == PredictionMotion.Kind.FIREBALL) item = new ItemStack(Items.FIRE_CHARGE);

                if (!item.isEmpty()) {

                    poses.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

                    poses.scale(.8F, .8F, .8F);

                    MultiBufferSource source = requested -> requested.format() == DefaultVertexFormat.NEW_ENTITY ? tint : WhiteConsumer.DISCARD;

                    mc.getItemRenderer().renderStatic(item, ItemDisplayContext.GROUND, 15728880,

                            OverlayTexture.NO_OVERLAY, poses, source, mc.level, 0);

                }

            }

            if (tint.vertices > 0) projectileCount++;

        } finally { poses.popPose(); }

    }

    private static void quad(VertexConsumer b, Matrix4f m, float x0, float y0, float x1, float y1) {

        vertex(b,m,x0,y0);vertex(b,m,x1,y0);vertex(b,m,x1,y1);vertex(b,m,x0,y1);

    }

    private static void arrowHead(VertexConsumer b, Matrix4f m, float x, float size) {

        vertex(b,m,x-.07F,-size);vertex(b,m,x+.16F,0);vertex(b,m,x+.16F,0);vertex(b,m,x-.07F,size);

    }

    private static void vertex(VertexConsumer b, Matrix4f m, float x, float y) {

        b.vertex(m,x,y,0).color(255,255,255,255).uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY)

                .uv2(15728880).normal(0,0,1).endVertex();

    }



    @SuppressWarnings({"rawtypes", "unchecked"})

    public static void render(LivingEntity entity, Vec3 position, Vec3 direction, Vec3 camera,

                              PoseStack poses, float partialTick, float alpha) {

        render(entity,position,direction,camera,poses,partialTick,alpha,0xFFFFFF);

    }

    @SuppressWarnings({"rawtypes", "unchecked"})

    public static void render(LivingEntity entity,Vec3 position,Vec3 direction,Vec3 camera,

                              PoseStack poses,float partialTick,float alpha,int color) {

        alpha=fade(position,camera,alpha);

        if(alpha<=.005 || count>=12) return;

        var mc = Minecraft.getInstance();

        if (!(mc.getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer renderer)) return;

        var type = type();

        var tint = new WhiteConsumer(BUFFER.getBuffer(type), Math.round(alpha * 255),color,camera.y,true);

        MultiBufferSource source = requested -> requested.format() == DefaultVertexFormat.NEW_ENTITY ? tint : WhiteConsumer.DISCARD;

        poses.pushPose();

        try {

            Vec3 offset = position.subtract(camera); poses.translate(offset.x, offset.y, offset.z);

            if (direction.horizontalDistanceSqr() > 1e-6) {

                float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));

                poses.mulPose(Axis.YP.rotationDegrees(entity.yBodyRot - yaw));

            }

            renderer.render(entity, entity.getYRot(), partialTick, poses, source, 15728880);

            if (tint.vertices > 0) count++;

        } finally { poses.popPose(); }

    }

    private static final class WhiteConsumer implements VertexConsumer {

        static final WhiteConsumer DISCARD = new WhiteConsumer(null, 0,0xFFFFFF,0,false);

        final VertexConsumer output;

        final int alpha;

        final int color;

        final double cameraY;

        final boolean outline;

        final Vec3[] quad=new Vec3[4];

        Vec3 vertex=Vec3.ZERO;

        int vertices;

        WhiteConsumer(VertexConsumer output,int alpha,int color,double cameraY,boolean outline) {

            this.output=output;this.alpha=alpha;this.color=color;this.cameraY=cameraY;this.outline=outline;

        }

        @Override public VertexConsumer vertex(double x, double y, double z) { vertex=new Vec3(x,y,z);if(output!=null) output.vertex(x,y,z); return this; }

        @Override public VertexConsumer color(int r,int g,int b,int a) {

            double scan=Math.pow(.5+.5*Math.cos((vertex.y+cameraY)*24-net.minecraft.Util.getMillis()*.004),12);

            if(output!=null) output.color(color>>16&255,color>>8&255,color&255,(int)(alpha*(.6+.4*scan)));return this;

        }

        @Override public VertexConsumer uv(float u,float v) { if(output!=null) output.uv(u,v); return this; }

        @Override public VertexConsumer overlayCoords(int u,int v) { if(output!=null) output.overlayCoords(OverlayTexture.NO_OVERLAY); return this; }

        @Override public VertexConsumer uv2(int u,int v) { if(output!=null) output.uv2(15728880); return this; }

        @Override public VertexConsumer normal(float x,float y,float z) { if(output!=null) output.normal(x,y,z); return this; }

        @Override public void endVertex() {

            if(output!=null) {

                output.endVertex();quad[vertices%4]=vertex;vertices++;

                if(outline && vertices%4==0 && EDGES.size()<2048) for(int i=0;i<4;i++)

                    EDGES.add(new Edge(quad[i],quad[(i+1)%4],color,Math.min(210,alpha*2)));

            }

        }

        @Override public void defaultColor(int r,int g,int b,int a) { if(output!=null) output.defaultColor(255,255,255,alpha); }

        @Override public void unsetDefaultColor() { if(output!=null) output.unsetDefaultColor(); }

    }

}

