package com.timestop.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.timestop.client.*;
import com.timestop.client.renderer.*;
import com.timestop.entity.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class ForgeClientSetup {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ForgeClientSetup::clientSetup);
        modEventBus.addListener(ForgeClientSetup::registerKeys);
        modEventBus.addListener(ForgeClientSetup::registerOverlays);
        modEventBus.addListener(ForgeClientSetup::registerEntityRenderers);
        MinecraftForge.EVENT_BUS.register(new ForgeClientEvents());
    }

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.timestop.pedestal.ModPedestals.ENTITY.get(), com.timestop.client.renderer.PedestalRenderer::new);
        event.registerEntityRenderer(ModEntities.CHRONO_COIN.get(), ChronoCoinRenderer::new);
    }

    private static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(com.timestop.pedestal.ModPedestals.MENU.get(), com.timestop.client.gui.PedestalScreen::new));
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
    }

    public static void registerOverlays(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
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

    public static class ForgeClientEvents {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
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
        }

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                float partialTick = event.getTimer() != null ? event.getTimer().getGameTimeDeltaPartialTick(false) : 1.0F;
                ModClientEvents.onRenderTick(partialTick);
            }
        }

        @SubscribeEvent
        public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ModClientEvents.onLoggingOut();
        }

        @SubscribeEvent
        public void onPlaySound(PlaySoundEvent event) {
            if (event.getSound() != null && ChronoAudioHandler.shouldMute(event.getSound())) {
                event.setSound(null);
            }
        }

        @SubscribeEvent
        public void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
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
        public void onRenderLevelStage(RenderLevelStageEvent event) {
            // Render once. The chunk-layer stage supplies a different matrix than the
            // particle stage in Forge 1.21.1 and produced a second, camera-drifting field.
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                // Forge 52 supplies realtime frame duration here, not tick interpolation.
                // Match the camera's interpolation so a moving owner's sphere cannot jitter.
                float tickDelta = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
                PoseStack poseStack = new PoseStack();
                poseStack.mulPose(event.getPoseStack());
                DeadEyeRenderer.renderWorld(poseStack, event.getCamera(), tickDelta);
                TemporalBubbleRenderer.renderLevel(poseStack, event.getCamera(), tickDelta);
                KineticPalmRenderer.renderLevel(poseStack, event.getCamera(), tickDelta);
                RewindBlockRenderer.render(poseStack, event.getCamera(), tickDelta);
            }
        }

        @SubscribeEvent
        public void onRenderHand(RenderHandEvent event) {
            KineticPalmRenderer.renderHand(event.getHand(), event.getPoseStack());
        }
    }
}
