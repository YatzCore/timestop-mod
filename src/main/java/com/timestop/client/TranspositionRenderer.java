package com.timestop.client;

import com.timestop.combat.TranspositionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class TranspositionRenderer {

    public static final IGuiOverlay HUD_TRANSPOSITION = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        if (!TranspositionManager.hasTranspositionRune(player)) return;

        Entity target = TranspositionManager.findSwapTargetClient(player);
        if (target == null) return;

        Font font = mc.font;
        int xCenter = width / 2;
        int yPos = (height / 2) + 12; // Centered directly below crosshair

        boolean isSneak = player.isCrouching();
        MutableComponent text;

        if (isSneak) {
            text = Component.literal("[ ⇄ DUAL SWAP (G) ]")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else if (target instanceof Projectile) {
            text = Component.literal("[ ⇄ ARROW SWAP (G) ]")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        } else {
            String name = target.getDisplayName().getString();
            text = Component.literal("[ ⇄ SWAP: " + name + " (G) ]")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        }

        int textWidth = font.width(text);
        int drawX = xCenter - (textWidth / 2);

        guiGraphics.drawString(font, text, drawX, yPos, 0xFFFFFFFF, true);
    };
}
