package com.timestop.client;

import com.timestop.combat.CoinManager;
import com.timestop.combat.TranspositionManager;
import com.timestop.core.ClientBubbleManager;
import com.timestop.core.ClientTimeStopManager;
import com.timestop.network.FlipCoinPacket;
import com.timestop.network.ModMessages;
import com.timestop.network.ReleaseProjectilesPacket;
import com.timestop.network.ToggleProjectileFlowPacket;
import com.timestop.network.ToggleTimeStopPacket;
import com.timestop.network.TranspositionSwapPacket;
import net.minecraft.client.Minecraft;

public class ModClientEvents {

    public static void onClientTick() {
        ClientTimeStopManager.clientTick();
        ClientTimeStopManager.onRenderFrameMotion();
        ClientBubbleManager.clientTick();
        com.timestop.client.DeadEyeClient.clientTick(Minecraft.getInstance());
        ChronoAudioHandler.clientTick();
        KineticPalmClient.clientTick();

        while (ModKeyBindings.TIME_STOP_KEY.consumeClick()) {
            ModMessages.sendToServer(new ToggleTimeStopPacket());
        }

        while (ModKeyBindings.RELEASE_PROJECTILES_KEY.consumeClick()) {
            ModMessages.sendToServer(new ReleaseProjectilesPacket());
        }

        while (ModKeyBindings.TRANSPOSITION_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (!TranspositionManager.hasTranspositionRune(mc.player)
                    || TranspositionManager.findSwapTargetClient(mc.player) == null) continue;
            boolean isSneak = mc.player != null && mc.player.isCrouching();
            ModMessages.sendToServer(new TranspositionSwapPacket(isSneak));
            TranspositionRenderer.triggerSwapFlash();
        }

        if (ModKeyBindings.FLIP_COIN_KEY != null) {
            while (ModKeyBindings.FLIP_COIN_KEY.consumeClick()) {
                var player = Minecraft.getInstance().player;
                if (player != null && CoinManager.hasCharge(player)) {
                    ModMessages.sendToServer(new FlipCoinPacket());
                }
            }
        }

        while (ModKeyBindings.PROJECTILE_FLOW_TOGGLE_KEY.consumeClick()) {
            ModMessages.sendToServer(new ToggleProjectileFlowPacket());
        }
    }

    public static void onRenderTick(float partialTick) {
        ClientTimeStopManager.onRenderFrameMotion();
        ClientOrbitalHandler.onRenderTick(partialTick);
    }

    public static void onLoggingOut() {
        ClientBubbleManager.reset();
        ClientTimeStopManager.reset();
        ClientOrbitalHandler.onClientLogout();
    }
}