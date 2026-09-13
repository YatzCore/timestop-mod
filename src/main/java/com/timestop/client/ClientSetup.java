package com.timestop.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.timestop.core.ClientTimeStopManager;
import com.timestop.network.ModMessages;
import com.timestop.network.ToggleTimeStopPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public class ClientSetup {
    public static final KeyMapping TIME_STOP_KEY = new KeyMapping(
            "key.timestop.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.timestop"
    );

    public static final KeyMapping RELEASE_PROJECTILES_KEY = new KeyMapping(
            "key.timestop.release_projectiles",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.timestop"
    );

    public static final KeyMapping TRANSPOSITION_KEY = new KeyMapping(
            "key.timestop.transposition",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.timestop"
    );

    public static final KeyMapping FLIP_COIN_KEY = new KeyMapping(
            "key.timestop.flip_coin",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.timestop"
    );

    public static final KeyMapping PROJECTILE_FLOW_TOGGLE_KEY = new KeyMapping(
            "key.timestop.toggle_projectile_flow",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.timestop"
    );

    public static final KeyMapping KINETIC_BARRIER_KEY = new KeyMapping(
            "key.timestop.kinetic_barrier",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.categories.timestop"
    );

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClientSetup::registerKeys);
        modEventBus.addListener(ClientSetup::registerOverlays);
        modEventBus.addListener(ClientSetup::registerEntityRenderers);
        MinecraftForge.EVENT_BUS.register(new ClientForgeEvents());
        MinecraftForge.EVENT_BUS.addListener(com.timestop.combat.KineticPalmManager::onClientTick);
        MinecraftForge.EVENT_BUS.register(new ChronoAudioHandler());
        MinecraftForge.EVENT_BUS.register(new ClientInteractionHandler());
        MinecraftForge.EVENT_BUS.register(new DeadEyeRenderer());
        MinecraftForge.EVENT_BUS.register(new ClientOrbitalHandler());
        MinecraftForge.EVENT_BUS.register(new com.timestop.client.renderer.TemporalBubbleRenderer());
        MinecraftForge.EVENT_BUS.register(new com.timestop.client.renderer.KineticPalmRenderer());
    }

    public static void registerEntityRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.timestop.entity.ModEntities.CHRONO_COIN.get(), com.timestop.client.renderer.ChronoCoinRenderer::new);
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TIME_STOP_KEY);
        event.register(RELEASE_PROJECTILES_KEY);
        event.register(TRANSPOSITION_KEY);
        event.register(FLIP_COIN_KEY);
        event.register(PROJECTILE_FLOW_TOGGLE_KEY);
        event.register(KINETIC_BARRIER_KEY);
    }

    public static void registerOverlays(AddGuiOverlayLayersEvent event) {
        // Keep HUD layers in the same depth stack as the crosshair and hotbar.
        ForgeLayeredDraw draw = event.getLayeredDraw().locateStack(ForgeLayeredDraw.PRE_SLEEP_STACK).orElseThrow();
        draw.add(ResourceLocation.fromNamespaceAndPath("timestop", "chrono_meter"), ChronoOverlay.HUD_CHRONO);
        draw.add(ResourceLocation.fromNamespaceAndPath("timestop", "captured_projectiles_hud"), CapturedProjectilesOverlay.HUD_ORBIT);
        draw.add(ResourceLocation.fromNamespaceAndPath("timestop", "transposition_hud"), TranspositionRenderer.HUD_TRANSPOSITION);
        draw.add(ResourceLocation.fromNamespaceAndPath("timestop", "chrono_coin_hud"), com.timestop.client.renderer.ChronoCoinOverlay.HUD_CHRONO_COIN);
        draw.add(ResourceLocation.fromNamespaceAndPath("timestop", "dead_eye_hud"), DeadEyeRenderer.HUD_DEAD_EYE);
        draw.add(ResourceLocation.fromNamespaceAndPath("timestop", "superhot_hud"), SuperhotRenderer.HUD_SUPERHOT);
    }

    public static class ClientForgeEvents {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                ClientTimeStopManager.clientTick();
                com.timestop.core.ClientBubbleManager.clientTick();
                com.timestop.combat.DeadEyeManager.clientTick(Minecraft.getInstance());

                while (TIME_STOP_KEY.consumeClick()) {
                    ModMessages.sendToServer(new ToggleTimeStopPacket());
                }

                while (RELEASE_PROJECTILES_KEY.consumeClick()) {
                    ModMessages.sendToServer(new com.timestop.network.ReleaseProjectilesPacket());
                }

                while (TRANSPOSITION_KEY.consumeClick()) {
                    Minecraft mc = Minecraft.getInstance();
                    if (!com.timestop.combat.TranspositionManager.hasTranspositionRune(mc.player)
                            || com.timestop.combat.TranspositionManager.findSwapTargetClient(mc.player) == null) continue;
                    boolean isSneak = mc.player != null && mc.player.isCrouching();
                    ModMessages.sendToServer(new com.timestop.network.TranspositionSwapPacket(isSneak));
                    TranspositionRenderer.triggerSwapFlash();
                }

                if (FLIP_COIN_KEY != null) {
                    while (FLIP_COIN_KEY.consumeClick()) {
                        var player = Minecraft.getInstance().player;
                        if (player != null && com.timestop.combat.CoinManager.hasCharge(player)) {
                            ModMessages.sendToServer(new com.timestop.network.FlipCoinPacket());
                        }
                    }
                }

                while (PROJECTILE_FLOW_TOGGLE_KEY.consumeClick()) {
                    ModMessages.sendToServer(new com.timestop.network.ToggleProjectileFlowPacket());
                }
            }
        }

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                ClientTimeStopManager.onRenderFrameMotion();
            }
        }

        @SubscribeEvent
        public void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            com.timestop.core.ClientBubbleManager.reset();
            com.timestop.core.ClientTimeStopManager.reset();
        }
    }
}
