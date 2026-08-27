package com.timestop.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.timestop.core.ClientTimeStopManager;
import com.timestop.network.ModMessages;
import com.timestop.network.ToggleTimeStopPacket;
import net.minecraft.client.KeyMapping;
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

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClientSetup::registerKeys);
        modEventBus.addListener(ClientSetup::registerOverlays);
        MinecraftForge.EVENT_BUS.register(new ClientForgeEvents());
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TIME_STOP_KEY);
    }

    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "chrono_meter", ChronoOverlay.HUD_CHRONO);
    }

    public static class ClientForgeEvents {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                ClientTimeStopManager.clientTick();

                while (TIME_STOP_KEY.consumeClick()) {
                    ModMessages.sendToServer(new ToggleTimeStopPacket());
                }
            }
        }
    }
}
