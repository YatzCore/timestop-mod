package com.timestop.client;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class ChronoOverlay {
    public static final IGuiOverlay HUD_CHRONO = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (!ClientTimeStopManager.isTimeStopped()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int remainingTicks = ClientTimeStopManager.getRemainingTicks();
        int totalDuration = ClientTimeStopManager.getTotalDuration();
        TimeMode mode = ClientTimeStopManager.getCurrentMode();

        int x = screenWidth / 2;
        int y = 20;

        Component statusComponent;
        if (totalDuration <= 0) {
            statusComponent = mode.getFormattedComponent()
                    .copy()
                    .append(Component.literal(" [ ").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(Component.literal("ACTIVE").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD))
                    .append(Component.literal(" ]").withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            float seconds = remainingTicks / 20.0F;
            statusComponent = mode.getFormattedComponent()
                    .copy()
                    .append(Component.literal(" [ ").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.1fs", seconds)).withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD))
                    .append(Component.literal(" ]").withStyle(net.minecraft.ChatFormatting.GRAY));
        }

        int textWidth = font.width(statusComponent);
        guiGraphics.drawString(font, statusComponent, x - textWidth / 2, y, 0xFFFFFF, true);

        // Render progress bar if finite duration
        if (totalDuration > 0) {
            int barWidth = 120;
            int barHeight = 4;
            int barX = x - barWidth / 2;
            int barY = y + 12;

            // Background
            guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x88000000);

            // Fill
            float progress = Math.max(0.0F, Math.min(1.0F, (float) remainingTicks / totalDuration));
            int filledWidth = (int) (barWidth * progress);
            int color;
            switch (mode) {
                case SLOW_MOTION:
                    color = 0xFF00B4D8;
                    break;
                case MATRIX:
                    color = 0xFF2EC4B6;
                    break;
                case SUPERHOT:
                    color = 0xFFFF2A2A;
                    break;
                case FAST_FORWARD:
                    color = 0xFFFF0054;
                    break;
                default:
                    color = 0xFFFFD700; // Gold
                    break;
            }
            guiGraphics.fill(barX, barY, barX + filledWidth, barY + barHeight, color);
        }
    };
}
