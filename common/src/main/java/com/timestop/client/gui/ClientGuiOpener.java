package com.timestop.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public class ClientGuiOpener {
    public static void openEquippedWatch() {
        var minecraft = Minecraft.getInstance();
        var target = com.timestop.item.WatchTarget.forInterface(minecraft.player);
        if (target != null && minecraft.screen == null) minecraft.setScreen(new TimeModeSelectionScreen(target));
    }

    public static void openModeSelection(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new TimeModeSelectionScreen(hand));
    }
}
