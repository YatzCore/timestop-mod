package com.timestop.client;

import com.timestop.combat.RuneManager;
import com.timestop.item.rune.RuneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class CapturedProjectilesOverlay {

    private static int orbitCount = 0;

    public static void setOrbitCount(int count) {
        orbitCount = Math.max(0, Math.min(16, count));
    }

    public static int getOrbitCount() {
        return orbitCount;
    }

    public static final IGuiOverlay HUD_ORBIT = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        boolean hasRune = RuneManager.getSocketedRuneType(player) == RuneType.ORBITAL;
        if (!hasRune && orbitCount <= 0) return;

        Font font = mc.font;
        int xCenter = width / 2;
        int yPos = height - 58; // Just above hotbar and armor/health

        // Construct formatted HUD gauge for 16 projectiles
        MutableComponent hud = Component.literal("[ ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY);

        for (int i = 0; i < 16; i++) {
            if (i == 8) {
                hud.append(Component.literal("· ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            if (i < orbitCount) {
                hud.append(Component.literal("●").withStyle(net.minecraft.ChatFormatting.AQUA));
            } else {
                hud.append(Component.literal("○").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }

        if (orbitCount >= 16) {
            hud.append(Component.literal(" ] ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
               .append(Component.literal("16/16 FULL RING ").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD))
               .append(Component.literal("[LMB: Snipe • R: Barrage]").withStyle(net.minecraft.ChatFormatting.YELLOW));
        } else if (orbitCount > 0) {
            hud.append(Component.literal(" ] ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
               .append(Component.literal(orbitCount + "/16 ").withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD))
               .append(Component.literal("[LMB: Snipe • R: Barrage]").withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            hud.append(Component.literal(" ] ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
               .append(Component.literal("0/16 ORBIT READY").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        }

        int textWidth = font.width(hud);
        int drawX = xCenter - (textWidth / 2);

        // Draw translucent dark backdrop pill
        guiGraphics.fill(drawX - 4, yPos - 2, drawX + textWidth + 4, yPos + 10, 0x88000000);
        guiGraphics.drawString(font, hud, drawX, yPos, 0xFFFFFF, false);
    };
}
