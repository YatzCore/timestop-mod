package com.timestop.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.timestop.combat.TranspositionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * Handles target outlining and dimensional warp visual feedback for the Spatial Transposition Rune.
 * All intrusive HUD tabs and text have been removed in favor of native glowing entity outlines.
 */
public class TranspositionRenderer {

    private static final ResourceLocation VIGNETTE_LOCATION = new ResourceLocation("textures/misc/vignette.png");
    private static int swapFlashTicks = 0;

    public static void triggerSwapFlash() {
        swapFlashTicks = 6;
    }

    /**
     * Checks if a given entity should render with a glowing outline based on active Transposition targeting.
     */
    public static boolean isTargetOutlined(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return false;
        if (!TranspositionManager.hasTranspositionRune(player)) return false;

        List<Entity> candidates = TranspositionManager.getClientSwapCandidates(player);
        if (candidates.isEmpty()) return false;

        boolean isSneak = player.isCrouching();
        if (isSneak) {
            // Outline top 2 candidates in dual-swap mode
            if (candidates.size() >= 1 && candidates.get(0) == entity) return true;
            if (candidates.size() >= 2 && candidates.get(1) == entity) return true;
            return false;
        } else {
            // Outline primary target
            return candidates.get(0) == entity;
        }
    }

    /**
     * Returns the outline color for the targeted entity:
     * - Normal swap: Vibrant arcane violet (0xC084FC)
     * - Dual swap: Gold (0xF59E0B) for target 1, Yellow (0xFCD34D) for target 2
     */
    public static int getTargetOutlineColor(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return -1;
        if (!TranspositionManager.hasTranspositionRune(player)) return -1;

        List<Entity> candidates = TranspositionManager.getClientSwapCandidates(player);
        if (candidates.isEmpty()) return -1;

        boolean isSneak = player.isCrouching();
        if (isSneak) {
            if (candidates.size() >= 1 && candidates.get(0) == entity) {
                return 0xF59E0B; // Luminous Gold for target 1
            }
            if (candidates.size() >= 2 && candidates.get(1) == entity) {
                return 0xFCD34D; // Luminous Yellow for target 2
            }
        } else {
            if (candidates.get(0) == entity) {
                return 0xC084FC; // Vibrant Arcane Violet for target
            }
        }
        return -1;
    }

    /**
     * Fullscreen dimensional warp flash feedback when clapping/swapping.
     * The previous 2D HUD tab has been completely removed per user request.
     */
    public static final IGuiOverlay HUD_TRANSPOSITION = (gui, guiGraphics, partialTick, width, height) -> {
        // Subtle violet dimensional warp flash on swap
        if (swapFlashTicks > 0) {
            float alpha = (swapFlashTicks / 6.0F) * 0.38F;
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            guiGraphics.setColor(0.78F, 0.48F, 1.0F, alpha);
            guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            swapFlashTicks--;
        }
    };
}
