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
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class DeadEyeRenderer {

    private static final ResourceLocation VIGNETTE_LOCATION = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/vignette.png");

    public static final LayeredDraw.Layer HUD_DEAD_EYE = (guiGraphics, deltaTracker) -> {
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        if (!DeadEyeManager.clientAiming) return;

        // 1. Subtle, clear cinematic vignette (center 100% transparent and clear)
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.setColor(0.60F, 0.40F, 0.20F, 0.22F); // Gentle warm amber tint on edges only
        guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, screenWidth, screenHeight, screenWidth, screenHeight);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        // 3. RDR2 Dead Eye Marked Cylinder Counter
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;

        int taggedCount = DeadEyeManager.clientTags.size();
        int available = DeadEyeManager.getAvailableArrowCount(mc.player);

        Component text;
        if (available == 0) {
            text = Component.literal("[ NO ARROWS ] 0/0").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD);
        } else {
            StringBuilder sb = new StringBuilder("[ ");
            for (int i = 0; i < DeadEyeManager.MAX_TAGS; i++) {
                if (i < taggedCount) {
                    sb.append("● "); // Painted
                } else if (i < available) {
                    sb.append("○ "); // Available ammo
                } else {
                    sb.append("· "); // Out of ammo slot
                }
            }
            sb.append("] ").append(taggedCount).append("/").append(available).append(" PAINTED");
            text = Component.literal(sb.toString()).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD);
        }

        int textWidth = font.width(text);
        int cx = screenWidth / 2;
        int cy = screenHeight / 2 + 35;

        // Subtle dark backing
        guiGraphics.fill(cx - textWidth / 2 - 4, cy - 2, cx + textWidth / 2 + 4, cy + 10, 0x88000000);
        int textColor = available == 0 ? 0xFFEF4444 : 0xFFF59E0B;
        guiGraphics.drawString(font, text, cx - textWidth / 2, cy, textColor, true);
    };

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (DeadEyeManager.clientTags.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = new PoseStack();
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        // Draw the crosses directly: text batches can be occluded or flushed into
        // a different render target by the 1.21 world rendering pipeline.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (DeadEyeTag tag : DeadEyeManager.clientTags) {
            if (mc.level == null) continue;
            var entity = mc.level.getEntity(tag.entityId);
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()) continue;
            Vec3 target = tag.isHead ? DeadEyeManager.headPosition(living, partialTick)
                    : living.getPosition(partialTick).add(0, living.getBbHeight() * 0.65, 0);

            poseStack.pushPose();
            poseStack.translate(target.x - camPos.x, target.y - camPos.y, target.z - camPos.z);
            poseStack.mulPose(camera.rotation());
            Matrix4f matrix = poseStack.last().pose();
            // A small vermilion ink cross with a hairline dark edge, no brackets or icons.
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
        // Four tapered arms resemble a painted X while remaining legible on light and dark skins.
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
