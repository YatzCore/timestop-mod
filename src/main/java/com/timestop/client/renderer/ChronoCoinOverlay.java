package com.timestop.client.renderer;

import com.timestop.combat.CoinManager;
import com.timestop.combat.RuneManager;
import com.timestop.item.rune.RuneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class ChronoCoinOverlay {

    public static final IGuiOverlay HUD_CHRONO_COIN = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // Only display if TACZ is loaded and the Marksman rune is equipped
        if (!net.minecraftforge.fml.ModList.get().isLoaded("tacz") || !RuneManager.hasRune(mc.player, RuneType.RICOSHOT)) return;

        int charges = CoinManager.getCharges(mc.player);
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Render 4 Ultrakill-style coin charge pips to the right of the crosshair
        int startX = centerX + 16;
        int startY = centerY - 10;
        int pipW = 7;
        int pipH = 4;
        int spacing = 5;

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
