package com.timestop.client;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

public class SuperhotRenderer {

    public static final LayeredDraw.Layer HUD_SUPERHOT = (guiGraphics, deltaTracker) -> {
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        if (!ClientTimeStopManager.isTimeStopped() || ClientTimeStopManager.getCurrentMode() != TimeMode.SUPERHOT) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;

        // 1. Top Title: "S U P E R . H O T" in iconic bold font
        String title = "S U P E R . H O T";
        int titleWidth = font.width(title);
        int titleX = (screenWidth - titleWidth) / 2;
        int titleY = 14;

        // Sleek dark pill behind title
        guiGraphics.fill(titleX - 8, titleY - 4, titleX + titleWidth + 8, titleY + 12, 0xCC000000);
        guiGraphics.renderOutline(titleX - 8, titleY - 4, titleWidth + 16, 16, 0xFFFF2020);
        guiGraphics.drawString(font, title, titleX, titleY, 0xFFFFFFFF, false);

        // 2. Motion Status Indicator:
        float activity = ClientTimeStopManager.getSuperhotActivity();
        String status;
        int statusColor;
        if (activity >= 0.70F) {
            status = "▶ TIME IN MOTION";
            statusColor = 0xFFFF3333; // Bright Superhot red
        } else if (activity >= 0.15F) {
            status = "◐ SLOW MOTION";
            statusColor = 0xFFFBBF24; // Amber / Warm gold for mouse aiming
        } else {
            status = "■ TIME FROZEN";
            statusColor = 0xFF94A3B8; // Cool gray for standstill
        }

        int statusWidth = font.width(status);
        int statusX = (screenWidth - statusWidth) / 2;
        int statusY = screenHeight - 48;

        guiGraphics.fill(statusX - 6, statusY - 3, statusX + statusWidth + 6, statusY + 11, 0xAA000000);
        guiGraphics.drawString(font, status, statusX, statusY, statusColor, true);
    };
}
