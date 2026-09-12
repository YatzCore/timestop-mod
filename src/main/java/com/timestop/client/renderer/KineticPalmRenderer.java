package com.timestop.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.timestop.combat.KineticPalmManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

/** Only the incoming bullet disturbs the air. There is no visible shield surface. */
public class KineticPalmRenderer {
    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var bullets = mc.level.getEntitiesOfClass(Projectile.class, mc.player.getBoundingBox().inflate(40),
                p -> p.getPersistentData().getBoolean("KineticPalmCaptured")
                        && p.getPersistentData().getInt("NeoWakeAge") < 12);
        if (bullets.isEmpty()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var tessellator = Tesselator.getInstance();
        var buffer = tessellator.getBuilder();
        var matrix = event.getPoseStack().last().pose();
        Vec3 camera = event.getCamera().getPosition();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Projectile bullet : bullets) {
            float age = bullet.getPersistentData().getInt("NeoWakeAge") + event.getPartialTick();
            float fade = Math.max(0, 1 - age / 12f);
            Vec3 direction = bullet.getLookAngle().normalize();
            Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize();
            if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
            Vec3 up = right.cross(direction).normalize();
            Vec3 position = bullet.getPosition(event.getPartialTick()).subtract(camera);
            for (int ring = 0; ring < 2; ring++) {
                Vec3 center = position.subtract(direction.scale(0.12 + ring * 0.18));
                double radius = 0.045 + age * 0.009 + ring * 0.035;
                // Broad, faint neutral edges suggest a brief pressure wake, never a glowing outline.
                ripple(buffer, matrix, center, right, up, radius, fade * 0.10f / (ring + 1));
            }
        }
        tessellator.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void ripple(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                               double radius, float alpha) {
        for (int band = 0; band < 2; band++) {
            double inner = radius + (band - 1) * 0.025;
            double outer = inner + 0.025;
            for (int i = 0; i < 32; i++) {
                double a = i * Math.PI / 16;
                double b = (i + 1) * Math.PI / 16;
                vertex(buffer, matrix, center, right, up, a, inner, band == 0 ? 0 : alpha);
                vertex(buffer, matrix, center, right, up, b, inner, band == 0 ? 0 : alpha);
                vertex(buffer, matrix, center, right, up, b, outer, band == 0 ? alpha : 0);
                vertex(buffer, matrix, center, right, up, a, outer, band == 0 ? alpha : 0);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                               double angle, double radius, float alpha) {
        Vec3 point = center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(0.78f, 0.78f, 0.76f, alpha).endVertex();
    }

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !KineticPalmManager.isGuarding(mc.player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        float side = mc.player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        PoseStack pose = event.getPoseStack();
        // Bring the defending hand inward beneath the crosshair, including left-handed settings.
        pose.translate(-0.30 * side, 0.12, -0.22);
        pose.mulPose(Axis.XP.rotationDegrees(-18));
        pose.mulPose(Axis.YP.rotationDegrees(10 * side));
        pose.mulPose(Axis.ZP.rotationDegrees(5 * side));
    }
}
