package com.timestop.client;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class ChronoOverlay {
    public static final LayeredDraw.Layer HUD_CHRONO = (guiGraphics, deltaTracker) -> {
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        int remainingTicks = 0;
        int totalDuration = 0;
        TimeMode mode = TimeMode.TIME_STOP;
        boolean active = false;

        if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
            com.timestop.core.ClientBubbleManager.ClientBubble bubble = com.timestop.core.ClientBubbleManager.getCameraBubble();
            if (bubble != null) {
                active = true;
                remainingTicks = bubble.remainingTicks;
                totalDuration = bubble.totalDuration;
                mode = bubble.mode;
            }
        } else if (ClientTimeStopManager.isTimeStopped()) {
            active = true;
            remainingTicks = ClientTimeStopManager.getRemainingTicks();
            totalDuration = ClientTimeStopManager.getTotalDuration();
            mode = ClientTimeStopManager.getCurrentMode();
        }

        RewindFadeOverlay.render(guiGraphics, partialTick, screenWidth, screenHeight);

        if (!active || !com.timestop.config.TimeStopConfig.CLIENT.enableTimerHud.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int x = screenWidth / 2;
        int y = 20;

        Component statusComponent;
        if (totalDuration <= 0) {
            String label = (mode == TimeMode.REWIND) ? "« REWINDING »" : "ACTIVE";
            statusComponent = mode.getFormattedComponent()
                    .copy()
                    .append(Component.literal(" [ ").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(Component.literal(label).withStyle(mode == TimeMode.REWIND ? net.minecraft.ChatFormatting.LIGHT_PURPLE : net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD))
                    .append(Component.literal(" ]").withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            float seconds = remainingTicks / 20.0F;
            String timeStr = (mode == TimeMode.REWIND) ? String.format("-%.1fs", seconds) : String.format("%.1fs", seconds);
            statusComponent = mode.getFormattedComponent()
                    .copy()
                    .append(Component.literal(" [ ").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(Component.literal(timeStr).withStyle(mode == TimeMode.REWIND ? net.minecraft.ChatFormatting.LIGHT_PURPLE : net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD))
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
                case REWIND:
                    color = 0xFFC084FC; // Twilight Purple
                    break;
                default:
                    color = 0xFFFFD700; // Gold
                    break;
            }
            guiGraphics.fill(barX, barY, barX + filledWidth, barY + barHeight, color);
        }
    };
}
