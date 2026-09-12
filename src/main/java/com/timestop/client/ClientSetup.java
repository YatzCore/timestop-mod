package com.timestop.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.timestop.core.ClientTimeStopManager;
import com.timestop.network.ModMessages;
import com.timestop.network.ToggleTimeStopPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

    public static final KeyMapping FLIP_COIN_KEY = net.minecraftforge.fml.ModList.get().isLoaded("tacz")
            ? new KeyMapping(
                    "key.timestop.flip_coin",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_C,
                    "key.categories.timestop"
            )
            : null;

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
        if (FLIP_COIN_KEY != null) {
            event.register(FLIP_COIN_KEY);
        }
        event.register(PROJECTILE_FLOW_TOGGLE_KEY);
        event.register(KINETIC_BARRIER_KEY);
    }

    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "chrono_meter", ChronoOverlay.HUD_CHRONO);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "captured_projectiles_hud", CapturedProjectilesOverlay.HUD_ORBIT);
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "transposition_hud", TranspositionRenderer.HUD_TRANSPOSITION);
        if (net.minecraftforge.fml.ModList.get().isLoaded("tacz")) {
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "chrono_coin_hud", com.timestop.client.renderer.ChronoCoinOverlay.HUD_CHRONO_COIN);
        }
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "dead_eye_hud", DeadEyeRenderer.HUD_DEAD_EYE);
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "superhot_hud", SuperhotRenderer.HUD_SUPERHOT);
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
