package com.timestop.forge.client;

import com.timestop.client.*;
import com.timestop.client.renderer.ChronoCoinOverlay;
import com.timestop.client.renderer.ChronoCoinRenderer;
import com.timestop.client.renderer.KineticPalmRenderer;
import com.timestop.client.renderer.TemporalBubbleRenderer;
import com.timestop.entity.ModEntities;
import com.timestop.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ForgeClientSetup {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ForgeClientSetup::registerKeys);
        modEventBus.addListener(ForgeClientSetup::registerOverlays);
        modEventBus.addListener(ForgeClientSetup::registerEntityRenderers);
        MinecraftForge.EVENT_BUS.register(new ForgeClientEvents());
    }

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CHRONO_COIN.get(), ChronoCoinRenderer::new);
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.TIME_STOP_KEY);
        event.register(ModKeyBindings.RELEASE_PROJECTILES_KEY);
        event.register(ModKeyBindings.TRANSPOSITION_KEY);
        if (ModKeyBindings.FLIP_COIN_KEY != null) {
            event.register(ModKeyBindings.FLIP_COIN_KEY);
        }
        event.register(ModKeyBindings.PROJECTILE_FLOW_TOGGLE_KEY);
        event.register(ModKeyBindings.KINETIC_BARRIER_KEY);
        event.register(ModKeyBindings.OPEN_SETTINGS_KEY);
    }

    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "chrono_meter", (gui, guiGraphics, partialTick, w, h) ->
                ChronoOverlay.render(guiGraphics, partialTick, w, h));

        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "captured_projectiles_hud", (gui, guiGraphics, partialTick, w, h) ->
                CapturedProjectilesOverlay.render(guiGraphics, partialTick, w, h));

        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "transposition_hud", (gui, guiGraphics, partialTick, w, h) ->
                TranspositionRenderer.render(guiGraphics, partialTick, w, h));

        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "chrono_coin_hud", (gui, guiGraphics, partialTick, w, h) ->
                ChronoCoinOverlay.render(guiGraphics, partialTick, w, h));

        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "dead_eye_hud", (gui, guiGraphics, partialTick, w, h) ->
                DeadEyeRenderer.renderHud(guiGraphics, partialTick, w, h));

        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "superhot_hud", (gui, guiGraphics, partialTick, w, h) ->
                SuperhotRenderer.render(guiGraphics, partialTick, w, h));
    }

    public static class ForgeClientEvents {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                ModClientEvents.onClientTick();
            }
        }

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                ModClientEvents.onRenderTick(event.renderTickTime);
            }
        }

        @SubscribeEvent
        public void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            ModClientEvents.onLoggingOut();
        }

        @SubscribeEvent
        public void onPlaySound(PlaySoundEvent event) {
            if (ChronoAudioHandler.shouldMute(event.getSound())) {
                event.setSound(null);
            }
        }

        @SubscribeEvent
        public void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (event.isAttack()) {
                if (ClientInteractionHandler.onAttackKey(mc)) {
                    event.setCanceled(true);
                    event.setSwingHand(true);
                }
            } else if (event.isUseItem()) {
                if (ClientInteractionHandler.onUseItemKey(mc, event.getHand())) {
                    event.setCanceled(true);
                    event.setSwingHand(true);
                }
            }
        }

        @SubscribeEvent
        public void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
            ClientInteractionHandler.onLeftClickEmpty();
        }

        @SubscribeEvent
        public void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                DeadEyeRenderer.renderWorld(event.getPoseStack(), event.getCamera(), event.getPartialTick());
                TemporalBubbleRenderer.renderLevel(event.getPoseStack(), event.getCamera(), event.getPartialTick());
                KineticPalmRenderer.renderLevel(event.getPoseStack(), event.getCamera(), event.getPartialTick());
                com.timestop.client.RewindBlockRenderer.render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
            }
        }

        @SubscribeEvent
        public void onRenderHand(RenderHandEvent event) {
            KineticPalmRenderer.renderHand(event.getHand(), event.getPoseStack());
        }
    }
}