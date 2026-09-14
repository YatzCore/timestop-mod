package com.timestop.client.renderer;

import com.timestop.combat.CoinManager;
import com.timestop.combat.RuneManager;
import com.timestop.item.rune.RuneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

public class ChronoCoinOverlay {

    public static final LayeredDraw.Layer HUD_CHRONO_COIN = (guiGraphics, deltaTracker) -> {
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // Display if the Marksman rune is equipped
        if (!RuneManager.hasRune(mc.player, RuneType.RICOSHOT)) return;

        int charges = mc.player.isCreative() ? CoinManager.MAX_CHARGES : CoinManager.getCharges(mc.player);
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Render 4 Ultrakill-style coin charge pips to the right of the crosshair
        int startX = centerX + 16;
        int startY = centerY - 10;
        int pipW = 7;
        int pipH = 4;
        int spacing = 5;

        guiGraphics.drawString(mc.font, charges + "/" + CoinManager.MAX_CHARGES,
                startX + pipW + 4, centerY - 4, 0xFFFEF08A, true);

        for (int i = 0; i < CoinManager.MAX_CHARGES; i++) {
            int y = startY + i * spacing;
            boolean ready = i < charges;

            if (ready) {
                // Bright golden glow
                guiGraphics.fill(startX, y, startX + pipW, y + pipH, 0xFFF59E0B);
                guiGraphics.renderOutline(startX, y, pipW, pipH, 0xFFFEF08A);
            } else {
                // Empty / recharging dark slot
                guiGraphics.fill(startX, y, startX + pipW, y + pipH, 0xAA1C1917);
                guiGraphics.renderOutline(startX, y, pipW, pipH, 0x6678716C);
            }
        }
    };
}
