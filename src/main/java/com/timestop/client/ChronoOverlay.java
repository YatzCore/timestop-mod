package com.timestop.client;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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

        String statusText;
        if (totalDuration <= 0) {
            statusText = mode.getFormattedName() + " §7[ §e∞ ACTIVE §7]";
        } else {
            float seconds = remainingTicks / 20.0F;
            statusText = String.format("%s §7[ §e%.1fs §7]", mode.getFormattedName(), seconds);
        }

        int textWidth = font.width(statusText);
        guiGraphics.drawString(font, statusText, x - textWidth / 2, y, 0xFFFFFF, true);

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
