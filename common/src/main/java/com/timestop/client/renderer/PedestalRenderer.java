package com.timestop.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.timestop.item.WatchTier;
import com.timestop.pedestal.PedestalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class PedestalRenderer implements BlockEntityRenderer<PedestalBlockEntity> {
    private record Assembly(ArmillaryMesh[] rings, RenderType material) {}
    private static final Assembly[] MODELS = createModels();

    private static Assembly[] createModels() {
        Assembly[] models=new Assembly[WatchTier.values().length];
        for(WatchTier tier:WatchTier.values()) {
            var profile=ArmillaryGeometry.forTier(tier);
            ArmillaryMesh[] rings=new ArmillaryMesh[3];
            for(int i=0;i<3;i++) rings[i]=new ArmillaryMesh(profile.rings()[i],profile.centerY());
            models[tier.ordinal()]=new Assembly(rings,RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath("timestop","textures/block/"+profile.texture()+"_active.png")));
        }
        return models;
    }

    public PedestalRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(PedestalBlockEntity pedestal, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (pedestal.getLevel()==null) return;
        var profile=ArmillaryGeometry.forTier(pedestal.pedestalTier());
        if (!cageVisible(pose,profile)) return;
        var assembly=MODELS[pedestal.pedestalTier().ordinal()];
        var animation=ArmillaryAnimation.state(pedestal);
        double time=ArmillaryAnimation.seconds();
        double distance=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(pedestal.getBlockPos().getX()+.5,pedestal.getBlockPos().getY()+profile.centerY(),pedestal.getBlockPos().getZ()+.5);
        int detail=distance>400?0:distance>100?1:2;
        var buffer=buffers.getBuffer(assembly.material());
        for(int i=0;i<3;i++) {
            pose.pushPose(); pose.translate(.5,profile.centerY(),.5);
            float angle=(float)animation.angles[i];
            pose.mulPose((i==0?Axis.YP:i==1?Axis.ZP:Axis.XP).rotationDegrees(angle));
            pose.mulPose((i==0?Axis.XP:i==1?Axis.YP:Axis.ZP).rotationDegrees((float)(Math.sin(Math.toRadians(angle))*(18+i*6))));
            pose.mulPose((i==0?Axis.ZP:i==1?Axis.XP:Axis.YP).rotationDegrees(angle));
            assembly.rings()[i].render(pose.last(),buffer,light,overlay,animation.glow,time,i,detail);
            pose.popPose();
        }
        if(pedestal.getWatch().isEmpty()) return;
        pose.pushPose();
        pose.translate(.5,ArmillaryAnimation.watchY(pedestal),.5);
        pose.mulPose(Axis.YP.rotationDegrees((float)animation.watchAngle));
        pose.scale(profile.watchScale(),profile.watchScale(),profile.watchScale());
        Minecraft.getInstance().getItemRenderer().renderStatic(pedestal.getWatch(),ItemDisplayContext.FIXED,light,overlay,pose,buffers,pedestal.getLevel(),0);
        pose.popPose();
    }

    /** Test the full cage in the same clip space used by the entity shader. */
    private static boolean cageVisible(PoseStack pose, ArmillaryGeometry.Profile profile) {
        Matrix4f clip=new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        float half=(float)profile.width()/2;
        int outside=63;
        Vector4f point=new Vector4f();
        for(int i=0;i<8;i++) {
            point.set(.5F+((i&1)==0?-half:half),(i&2)==0?0:(float)profile.height(),.5F+((i&4)==0?-half:half),1).mul(clip);
            int planes=(point.x < -point.w?1:0)|(point.x > point.w?2:0)
                    |(point.y < -point.w?4:0)|(point.y > point.w?8:0)
                    |(point.z < -point.w?16:0)|(point.z > point.w?32:0);
            outside &= planes;
        }
        return outside==0;
    }

    // Bypass the owning-block test; cageVisible tests the actual oversized assembly instead.
    @Override public boolean shouldRenderOffScreen(PedestalBlockEntity pedestal) { return true; }
    @Override public int getViewDistance() { return 64; }
}
