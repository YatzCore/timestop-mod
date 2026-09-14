package com.timestop.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import com.timestop.combat.DeadEyeManager;
import com.timestop.combat.DeadEyeTag;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class DeadEyeRenderer {

    private static final ResourceLocation VIGNETTE_LOCATION = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/vignette.png");

    public static final LayeredDraw.Layer HUD_DEAD_EYE = (guiGraphics, deltaTracker) -> {
        renderHud(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false), guiGraphics.guiWidth(), guiGraphics.guiHeight());
    };

    public static void renderHud(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!DeadEyeClient.clientAiming) return;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.setColor(0.60F, 0.40F, 0.20F, 0.22F);
        guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, screenWidth, screenHeight, screenWidth, screenHeight);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;

        int taggedCount = DeadEyeClient.clientTags.size();
        int available = DeadEyeManager.getAvailableArrowCount(mc.player);

        Component text;
        if (available == 0) {
            text = Component.literal("[ NO ARROWS ] 0/0").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD);
        } else {
            StringBuilder sb = new StringBuilder("[ ");
            for (int i = 0; i < available; i++) {
                if (i < taggedCount) {
                    sb.append("x ");
                } else {
                    sb.append("o ");
                }
            }
            sb.append("] ").append(taggedCount).append("/").append(available);
            text = Component.literal(sb.toString()).withStyle(
                    taggedCount == available ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.YELLOW,
                    net.minecraft.ChatFormatting.BOLD
            );
        }

        int textX = screenWidth / 2 + 16;
        int textY = screenHeight / 2 + 12;
        guiGraphics.drawString(font, text, textX, textY, 0xFFFFFF, true);
    }

    public static void renderWorld(PoseStack poseStack, Camera camera, float partialTick) {
        if (!DeadEyeClient.clientAiming || DeadEyeClient.clientTags.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 camPos = camera.getPosition();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (DeadEyeTag tag : DeadEyeClient.clientTags) {
            if (mc.level == null) continue;
            var entity = mc.level.getEntity(tag.entityId);
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()) continue;
            Vec3 target = tag.isHead ? DeadEyeClient.headPosition(living, partialTick)
                    : living.getPosition(partialTick).add(0, living.getBbHeight() * 0.65, 0);

            poseStack.pushPose();
            poseStack.translate(target.x - camPos.x, target.y - camPos.y, target.z - camPos.z);
            poseStack.mulPose(camera.rotation());
            Matrix4f matrix = poseStack.last().pose();
            float size = Math.min(0.14F, Math.max(0.075F, living.getBbWidth() * 0.15F));
            drawMark(buffer, matrix, size + 0.008F, 0.017F, 0xD02A0806);
            drawMark(buffer, matrix, size, 0.013F, 0xFFFF0000);

            poseStack.popPose();
        }

        var mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawMark(BufferBuilder buffer, Matrix4f matrix, float size, float width, int color) {
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                float nx = -y * width, ny = x * width;
                buffer.addVertex(matrix, nx, ny, 0).setColor(color);
                buffer.addVertex(matrix, x * size + nx * 0.35F, y * size + ny * 0.35F, 0).setColor(color);
                buffer.addVertex(matrix, x * size - nx * 0.35F, y * size - ny * 0.35F, 0).setColor(color);
                buffer.addVertex(matrix, -nx, -ny, 0).setColor(color);
            }
        }
    }
}
