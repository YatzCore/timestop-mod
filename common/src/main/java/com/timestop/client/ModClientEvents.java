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
        com.timestop.client.PredictionClient.tick();
        RewindBlockRenderer.tick();
        RewindMobAnimation.tick();
        RewindFadeOverlay.tick();
        ClientTimeStopManager.clientTick();
        ClientTimeStopManager.onRenderFrameMotion();
        ClientBubbleManager.clientTick();
        com.timestop.client.DeadEyeClient.clientTick(Minecraft.getInstance());
        ChronoAudioHandler.clientTick();
        KineticPalmClient.clientTick();

        while (ModKeyBindings.OPEN_WATCH_KEY.consumeClick()) {
            com.timestop.client.gui.ClientGuiOpener.openEquippedWatch();
        }

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

        syncCasualPreference();

        while (ModKeyBindings.TOGGLE_VARIANT_KEY.consumeClick()) {
            toggleEquippedVariant();
        }
    }

    private static Object casualSyncedFor;

    /** Sends the Casual mode preference whenever a new local player exists (join, respawn, dimension change). */
    private static void syncCasualPreference() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { casualSyncedFor = null; return; }
        if (casualSyncedFor == mc.player) return;
        casualSyncedFor = mc.player;
        ModMessages.sendToServer(new com.timestop.network.SetCasualAccelerationPacket(com.timestop.config.TimeStopConfig.CLIENT.casualAcceleration.get()));
    }

    /** Switches the equipped watch between World and Combat (Slow Motion) or World and Self (Fast Forward). */
    private static void toggleEquippedVariant() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        // Edit the watch that V will activate, so B followed by V always uses the shown variant.
        var target = com.timestop.item.WatchTarget.forActivation(mc.player);
        if (target == null) return;
        net.minecraft.world.item.ItemStack stack = target.resolve(mc.player);
        com.timestop.core.TimeMode current = com.timestop.item.AbstractWatchItem.getMode(stack);
        if (!current.hasVariant()) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(current.getDisplayName()
                    + " has no variant. Select Slow Motion or Fast Forward first.").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }
        com.timestop.core.TimeMode next = current.alternate();
        com.timestop.item.AbstractWatchItem.setMode(stack, next);
        ModMessages.sendToServer(new com.timestop.network.SelectTimeModePacket(next, target));
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(next.baseMode().getDisplayName() + ": ")
                .withStyle(net.minecraft.ChatFormatting.GRAY)
                .append(net.minecraft.network.chat.Component.literal(next.variantLabel()).withStyle(next.getTitleColor(), net.minecraft.ChatFormatting.BOLD))
                .append(net.minecraft.network.chat.Component.literal(anyTimeEffectActive(next) ? "  (applies on next activation)" : "").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), true);
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.6F));
    }

    private static boolean anyTimeEffectActive(com.timestop.core.TimeMode next) {
        return ClientTimeStopManager.isTimeStopped() || ClientBubbleManager.hasActiveBubbles();
    }

    public static void onRenderTick(float partialTick) {
        com.timestop.client.renderer.ArmillaryAnimation.frame();
        ClientTimeStopManager.onRenderFrameMotion();
        ClientOrbitalHandler.onRenderTick(partialTick);
    }

    public static void onLoggingOut() {
        com.timestop.config.TimeStopConfig.endRemoteSync();
        DeadEyeHeadGeometry.clear();
        com.timestop.client.PredictionClient.clear();
        com.timestop.client.renderer.ArmillaryAnimation.clear();
        RewindBlockRenderer.clear();
        RewindMobAnimation.clear();
        RewindFadeOverlay.clear();
        ClientBubbleManager.reset();
        ClientTimeStopManager.reset();
        ClientOrbitalHandler.onClientLogout();
    }
}
