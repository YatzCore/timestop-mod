package com.timestop.client;

import com.timestop.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;

public class RewindFadeOverlay {
    private static int fadeAge = -1;
    private static boolean shouldPlaySound = false;
    private static final int TOTAL_FADE_TICKS = 16;
    private static final int BLACK_HOLD_TICKS = 10; // 0.5s in black

    public static void start(boolean playSound) {
        fadeAge = 0;
        shouldPlaySound = playSound;
    }

    public static void tick() {
        if (fadeAge >= 0) {
            fadeAge++;
            if (fadeAge == BLACK_HOLD_TICKS && shouldPlaySound) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.playSound(ModSounds.RAMIEL_SCREAM.get(), 2.5F, 1.0F);
                    mc.player.playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.5F, 1.6F);
                    mc.player.playSound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 1.8F);
                }
            }
            if (fadeAge > TOTAL_FADE_TICKS) {
                fadeAge = -1;
                shouldPlaySound = false;
            }
        }
    }

    public static void render(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (fadeAge < 0) return;

        float currentProgress = fadeAge + partialTick;
        float alpha;

        if (currentProgress <= 4.0F) {
            // Fade into black: 0 to 4 ticks (~0.2s)
            alpha = Math.min(1.0F, currentProgress / 4.0F);
        } else if (currentProgress <= BLACK_HOLD_TICKS) {
            // Hold solid black: 4 to 10 ticks (~0.3s -> total 0.5s in black)
            alpha = 1.0F;
        } else {
            // Fade back out: 10 to 16 ticks (~0.3s fade out)
            float outProgress = (currentProgress - BLACK_HOLD_TICKS) / (float) (TOTAL_FADE_TICKS - BLACK_HOLD_TICKS);
            alpha = Math.max(0.0F, 1.0F - outProgress);
        }

        int a = Math.round(alpha * 255.0F);
        if (a > 0) {
            int color = (a << 24); // Solid black with alpha
            guiGraphics.fill(0, 0, screenWidth, screenHeight, color);
        }
    }

    public static void clear() {
        fadeAge = -1;
        shouldPlaySound = false;
    }
}
