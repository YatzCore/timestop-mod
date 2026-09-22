package com.timestop.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.timestop.client.*;
import com.timestop.client.renderer.*;
import com.timestop.entity.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public class TimeStopNeoForgeClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(TimeStopNeoForgeClient::onRegisterKeyMappings);
        modEventBus.addListener(TimeStopNeoForgeClient::onRegisterRenderers);
        modEventBus.addListener(TimeStopNeoForgeClient::onRegisterGuiLayers);

        NeoForge.EVENT_BUS.register(ClientForgeEvents.class);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.TIME_STOP_KEY);
        event.register(ModKeyBindings.RELEASE_PROJECTILES_KEY);
        event.register(ModKeyBindings.TRANSPOSITION_KEY);
        if (ModKeyBindings.FLIP_COIN_KEY != null) {
            event.register(ModKeyBindings.FLIP_COIN_KEY);
        }
        event.register(ModKeyBindings.PROJECTILE_FLOW_TOGGLE_KEY);
        event.register(ModKeyBindings.KINETIC_BARRIER_KEY);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CHRONO_COIN.get(), ChronoCoinRenderer::new);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath("timestop", "hud_overlays"),
                (guiGraphics, deltaTracker) -> {
                    ChronoOverlay.HUD_CHRONO.render(guiGraphics, deltaTracker);
                    CapturedProjectilesOverlay.HUD_ORBIT.render(guiGraphics, deltaTracker);
                    TranspositionRenderer.HUD_TRANSPOSITION.render(guiGraphics, deltaTracker);
                    ChronoCoinOverlay.HUD_CHRONO_COIN.render(guiGraphics, deltaTracker);
                    DeadEyeRenderer.HUD_DEAD_EYE.render(guiGraphics, deltaTracker);
                    SuperhotRenderer.HUD_SUPERHOT.render(guiGraphics, deltaTracker);
                }
        );
    }

    public static class ClientForgeEvents {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            var key = ModKeyBindings.KINETIC_BARRIER_KEY;
            long window = mc.getWindow().getWindow();
            boolean down = false;
            if (mc.isWindowActive() && mc.screen == null && key.getKey().getValue() >= 0) {
                if (key.getKey().getType() == InputConstants.Type.MOUSE) {
                    down = GLFW.glfwGetMouseButton(window, key.getKey().getValue()) == GLFW.GLFW_PRESS;
                } else if (key.getKey().getType() == InputConstants.Type.KEYSYM) {
                    down = InputConstants.isKeyDown(window, key.getKey().getValue());
                } else {
                    down = key.isDown();
                }
            }
            key.setDown(down);

            ModClientEvents.onClientTick();
        }

        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                float tickDelta = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                ModClientEvents.onRenderTick(tickDelta);
                DeadEyeRenderer.renderWorld(event.getPoseStack(), event.getCamera(), tickDelta);
                TemporalBubbleRenderer.renderLevel(event.getPoseStack(), event.getCamera(), tickDelta);
                KineticPalmRenderer.renderLevel(event.getPoseStack(), event.getCamera(), tickDelta);
                RewindBlockRenderer.render(event.getPoseStack(), event.getCamera(), tickDelta);
            }
        }

        @SubscribeEvent
        public static void onRenderHand(RenderHandEvent event) {
            KineticPalmRenderer.renderHand(event.getHand(), event.getPoseStack());
        }

        @SubscribeEvent
        public static void onPlaySound(PlaySoundEvent event) {
            if (event.getSound() != null && ChronoAudioHandler.shouldMute(event.getSound())) {
                event.setSound(null);
            }
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (event.isAttack()) {
                if (ClientInteractionHandler.onAttackKey(mc)) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
            } else if (event.isUseItem()) {
                if (ClientInteractionHandler.onUseItemKey(mc, event.getHand())) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ModClientEvents.onLoggingOut();
        }
    }
}
