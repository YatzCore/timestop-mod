package com.timestop.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public class ClientGuiOpener {
    public static void openModeSelection(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new TimeModeSelectionScreen(hand));
    }
}
