package com.timestop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.timestop.combat.DeadEyeManager;
import com.timestop.combat.DeadEyeTag;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class DeadEyeRenderer {

    public static final IGuiOverlay HUD_DEAD_EYE = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (!DeadEyeManager.clientAiming) return;

        // 1. Warm Sepia / Amber Vignette Overlay on Screen Borders
        int vignetteColor = 0x2E78350F; // Deep translucent amber/sepia
        guiGraphics.fill(0, 0, screenWidth, 30, vignetteColor);
        guiGraphics.fill(0, screenHeight - 30, screenWidth, screenHeight, vignetteColor);
        guiGraphics.fill(0, 30, 30, screenHeight - 30, vignetteColor);
        guiGraphics.fill(screenWidth - 30, 30, screenWidth, screenHeight - 30, vignetteColor);

        // 2. RDR2 Dead Eye Marked Cylinder Counter
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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!DeadEyeManager.clientAiming && DeadEyeManager.clientTags.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        Font font = mc.font;

        for (DeadEyeTag tag : DeadEyeManager.clientTags) {
            Vec3 target = tag.targetPos;
            if (mc.level != null) {
                net.minecraft.world.entity.Entity e = mc.level.getEntity(tag.entityId);
                if (e instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                    target = tag.isHead
                            ? living.getEyePosition(event.getPartialTick())
                            : living.getPosition(event.getPartialTick()).add(0, living.getBbHeight() * 0.5, 0);
                }
            }

            poseStack.pushPose();
            poseStack.translate(target.x - camPos.x, target.y - camPos.y, target.z - camPos.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-0.045F, -0.045F, 0.045F);

            // Red glowing [ X ] crosshair marker
            String markerText = tag.isHead ? "☠ [X]" : "[X]";
            int color = tag.isHead ? 0xFFFF1744 : 0xFFFF5252;

            int w = font.width(markerText);
            font.drawInBatch(markerText, -w / 2.0F, -4, color, false,
                    poseStack.last().pose(), mc.renderBuffers().bufferSource(),
                    Font.DisplayMode.SEE_THROUGH, 0, 15728880);

            poseStack.popPose();
        }
    }
}
