package com.timestop.client;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class SuperhotRenderer {

    public static final IGuiOverlay HUD_SUPERHOT = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
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
        boolean isMoving = activity > 0.15F;

        String status = isMoving ? "▶ TIME IN MOTION" : "■ TIME DILATED";
        int statusColor = isMoving ? 0xFFFF3333 : 0xFFEEEEEE;
        int statusWidth = font.width(status);
        int statusX = (screenWidth - statusWidth) / 2;
        int statusY = screenHeight - 48;

        guiGraphics.fill(statusX - 6, statusY - 3, statusX + statusWidth + 6, statusY + 11, 0xAA000000);
        guiGraphics.drawString(font, status, statusX, statusY, statusColor, true);
    };
}
