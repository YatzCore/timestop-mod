package com.timestop.fabric.test;

import com.timestop.client.gui.CreativeWatchRadiusScreen;
import com.timestop.client.gui.TimeModeSelectionScreen;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;

/** Opt-in isolated client screenshots and an end-to-end radius edit; excluded from release jars. */
public class WatchRadiusVisualQa implements ClientModInitializer {
    private boolean loading;
    private int ticks;
    private int bootTicks;

    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("timestop.radiusVisualQa")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (!loading && ++bootTicks > 80 && mc.level == null && mc.getOverlay() == null && !(mc.screen instanceof TitleScreen)) {
            com.timestop.TimeStopMod.LOGGER.info("Radius QA dismissing initial screen: {}", mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
            mc.setScreen(new TitleScreen());
        }
        if (!loading && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            loading = true;
            mc.options.guiScale().set(3);
            mc.options.renderDistance().set(4);
            mc.options.framerateLimit().set(60);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            if (Files.exists(mc.gameDirectory.toPath().resolve("saves/WatchRadiusQA/level.dat"))) mc.createWorldOpenFlows().openWorld("WatchRadiusQA", () -> mc.setScreen(new TitleScreen()));
            else mc.createWorldOpenFlows().createFreshLevel("WatchRadiusQA",
                    new LevelSettings("Watch Radius QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        ticks++;
        if (ticks == 20) mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var watch = new ItemStack(ModItems.CREATIVE_WATCH.get());
            AbstractWatchItem.setGlobalScope(watch, false);
            player.setItemInHand(InteractionHand.OFF_HAND, watch);
            player.containerMenu.broadcastChanges();
        });
        if (ticks == 50) mc.setScreen(new TimeModeSelectionScreen(InteractionHand.OFF_HAND));
        if (ticks == 55) {
            var screen = mc.screen;
            // Enable the matrix and then disable one rune, leaving several active together.
            screen.mouseClicked((screen.width - 330) / 2 + 135, (screen.height - 277) / 2 + 58, 0);
        }
        if (ticks == 60) {
            var screen = mc.screen;
            screen.mouseClicked((screen.width - 330) / 2 + 24, (screen.height - 277) / 2 + 78, 0);
        }
        if (ticks == 65) {
            var screen = mc.screen;
            screen.mouseClicked((screen.width - 330) / 2 + 125, (screen.height - 277) / 2 + 238, 0);
        }
        if (ticks == 70) capture(mc, "01-creative-radius-control.png");
        if (ticks == 75) {
            var screen = mc.screen;
            screen.mouseClicked((screen.width - 330) / 2.0 + 30, (screen.height - 277) / 2 + 39, 0);
            if (!(mc.screen instanceof CreativeWatchRadiusScreen)) throw new IllegalStateException("Radius control did not open editor");
        }
        if (ticks == 95) capture(mc, "02-creative-radius-editor.png");
        if (ticks == 100) {
            for (var widget : mc.screen.children()) if (widget instanceof EditBox box) box.setValue("37");
            mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }
        if (ticks == 120) mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if (AbstractWatchItem.getBubbleRadius(player.getOffhandItem()) != 37) throw new IllegalStateException("Radius edit did not synchronize to server");
            if (AbstractWatchItem.getActiveMatrixRuneCount(player.getOffhandItem()) != 14) throw new IllegalStateException("Matrix toggles did not synchronize");
            if (!AbstractWatchItem.isRewindInitiatorExempt(player.getOffhandItem())) throw new IllegalStateException("Rewind self exemption did not synchronize");
            com.timestop.TimeStopMod.LOGGER.info("CREATIVE_WATCH_UI_PASS: 14 runes and self-exemption saved");
            com.timestop.TimeStopMod.LOGGER.info("WATCH_RADIUS_UI_PASS: offhand radius saved as 37 blocks");
        });
        if (ticks == 125) capture(mc, "03-creative-radius-saved.png");
        if (ticks == 140) mc.stop();
    }

    private void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> com.timestop.TimeStopMod.LOGGER.info("Radius QA screenshot: {}", message.getString()));
    }
}
