package com.timestop.fabric.client;

import com.timestop.client.*;
import com.timestop.client.renderer.*;
import com.timestop.entity.ModEntities;
import com.timestop.fabric.network.FabricNetworkHelper;
import com.timestop.platform.Services;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public class TimeStopFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1. Networking
        FabricNetworkHelper.registerClientReceivers();

        // 2. Keybindings
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.TIME_STOP_KEY);
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.RELEASE_PROJECTILES_KEY);
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.TRANSPOSITION_KEY);
        if (ModKeyBindings.FLIP_COIN_KEY != null) {
            KeyBindingHelper.registerKeyBinding(ModKeyBindings.FLIP_COIN_KEY);
        }
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.PROJECTILE_FLOW_TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.KINETIC_BARRIER_KEY);

        // 3. Entity Renderers
        EntityRendererRegistry.register(ModEntities.CHRONO_COIN.get(), ChronoCoinRenderer::new);

        // 4. Tick and Logout
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Vanilla maps a physical key to only one binding; middle click also binds Pick Block.
            var key = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.KINETIC_BARRIER_KEY);
            long window = client.getWindow().getWindow();
            boolean down = false;
            if (client.isWindowActive() && client.screen == null && key.getValue() >= 0) {
                if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
                    down = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                } else if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
                    down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, key.getValue());
                } else {
                    down = ModKeyBindings.KINETIC_BARRIER_KEY.isDown();
                }
            }
            ModKeyBindings.KINETIC_BARRIER_KEY.setDown(down);
            ModClientEvents.onClientTick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ModClientEvents.onLoggingOut());

        // 5. HUD Overlays
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();

            ChronoOverlay.render(guiGraphics, tickDelta, w, h);
            CapturedProjectilesOverlay.render(guiGraphics, tickDelta, w, h);
            TranspositionRenderer.render(guiGraphics, tickDelta, w, h);
            ChronoCoinOverlay.render(guiGraphics, tickDelta, w, h);
            DeadEyeRenderer.renderHud(guiGraphics, tickDelta, w, h);
            SuperhotRenderer.render(guiGraphics, tickDelta, w, h);
        });

        // 6. World Rendering & Render Ticks
        WorldRenderEvents.START.register(context -> ModClientEvents.onRenderTick(context.tickDelta()));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            DeadEyeRenderer.renderWorld(context.matrixStack(), context.camera(), context.tickDelta());
            TemporalBubbleRenderer.renderLevel(context.matrixStack(), context.camera(), context.tickDelta());
            KineticPalmRenderer.renderLevel(context.matrixStack(), context.camera(), context.tickDelta());
        });
    }
}