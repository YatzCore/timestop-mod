package com.timestop.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.timestop.config.TimeStopConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

public class TimeStopSettingsScreen extends Screen {

    @Nullable
    private final Screen parentScreen;
    @Nullable
    private final InteractionHand hand;

    private boolean draggingOpacity = false;

    public TimeStopSettingsScreen(@Nullable Screen parentScreen, @Nullable InteractionHand hand) {
        super(Component.literal("Temporal Settings"));
        this.parentScreen = parentScreen;
        this.hand = hand;
    }

    public TimeStopSettingsScreen() {
        this(null, null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int modalWidth = 320;
        int modalHeight = 250;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        // Background dark-glass panel
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xEE0B0F19);
        guiGraphics.renderOutline(modalX, modalY, modalWidth, modalHeight, 0xFF38BDF8);

        // Header
        guiGraphics.drawString(this.font, "⚙ TEMPORAL ENGINE SETTINGS", modalX + 16, modalY + 12, 0xFF38BDF8, false);
        guiGraphics.fill(modalX + 12, modalY + 28, modalX + modalWidth - 12, modalY + 29, 0x33FFFFFF);

        int startY = modalY + 36;
        int rowH = 20;

        // 1. Render Sphere Toggle
        renderToggleRow(guiGraphics, modalX, startY, modalWidth, "Render Temporal Spheres",
                TimeStopConfig.CLIENT.enableBubbleRender.get(), mouseX, mouseY);

        // 2. Sci-Fi Grid Toggle
        renderToggleRow(guiGraphics, modalX, startY + rowH, modalWidth, "Sci-Fi Energy Grid",
                TimeStopConfig.CLIENT.enableBubbleGrid.get(), mouseX, mouseY);

        // 3. 3D Specular Sheen Toggle
        renderToggleRow(guiGraphics, modalX, startY + rowH * 2, modalWidth, "3D Volume Specular Sheen",
                TimeStopConfig.CLIENT.enableSpecularSheen.get(), mouseX, mouseY);

        // 4. Orbit Equator Ring Toggle
        renderToggleRow(guiGraphics, modalX, startY + rowH * 3, modalWidth, "Orbit Equator Rings",
                TimeStopConfig.CLIENT.enableEquatorRing.get(), mouseX, mouseY);

        // 5. Post-Processing Shaders Toggle
        renderToggleRow(guiGraphics, modalX, startY + rowH * 4, modalWidth, "Post-Processing Shaders",
                TimeStopConfig.CLIENT.enableShaders.get(), mouseX, mouseY);

        // 6. Sound FX Toggle
        renderToggleRow(guiGraphics, modalX, startY + rowH * 5, modalWidth, "Temporal Sound FX",
                TimeStopConfig.CLIENT.enableSounds.get(), mouseX, mouseY);

        // 7. Timer HUD Toggle
        renderToggleRow(guiGraphics, modalX, startY + rowH * 6, modalWidth, "Floating Timer HUD",
                TimeStopConfig.CLIENT.enableTimerHud.get(), mouseX, mouseY);

        // 8. Opacity Slider
        int sliderY = startY + rowH * 7;
        renderOpacitySlider(guiGraphics, modalX, sliderY, modalWidth, mouseX, mouseY);

        // Bottom Done / Back Button
        int btnW = 100;
        int btnH = 20;
        int btnX = modalX + (modalWidth - btnW) / 2;
        int btnY = modalY + modalHeight - 26;

        boolean isBtnHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        int btnBg = isBtnHovered ? 0xFF0284C7 : 0xFF0369A1;
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
        guiGraphics.renderOutline(btnX, btnY, btnW, btnH, 0xFF38BDF8);

        String btnText = this.parentScreen != null ? "BACK" : "DONE";
        int textX = btnX + (btnW - this.font.width(btnText)) / 2;
        guiGraphics.drawString(this.font, btnText, textX, btnY + 6, 0xFFFFFFFF, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderToggleRow(GuiGraphics guiGraphics, int modalX, int y, int modalWidth, String label, boolean enabled, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, label, modalX + 16, y + 4, 0xFFE2E8F0, false);

        int btnW = 60;
        int btnH = 14;
        int btnX = modalX + modalWidth - 16 - btnW;

        boolean isHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= y && mouseY <= y + btnH;
        int bg = enabled ? (isHovered ? 0xFF15803D : 0xFF16A34A) : (isHovered ? 0xFF991B1B : 0xFFDC2626);
        int border = isHovered ? 0xFFFFFFFF : (enabled ? 0xFF4ADE80 : 0xFFF87171);

        guiGraphics.fill(btnX, y, btnX + btnW, y + btnH, bg);
        guiGraphics.renderOutline(btnX, y, btnW, btnH, border);

        String text = enabled ? "ON" : "OFF";
        int textX = btnX + (btnW - this.font.width(text)) / 2;
        guiGraphics.drawString(this.font, text, textX, y + 3, 0xFFFFFFFF, false);
    }

    private void renderOpacitySlider(GuiGraphics guiGraphics, int modalX, int y, int modalWidth, int mouseX, int mouseY) {
        double opacity = TimeStopConfig.CLIENT.bubbleOpacity.get();
        int percent = (int) Math.round(opacity * 100);

        guiGraphics.drawString(this.font, "Sphere Opacity: " + percent + "%", modalX + 16, y + 4, 0xFFE2E8F0, false);

        int trackW = 100;
        int trackH = 10;
        int trackX = modalX + modalWidth - 16 - trackW;
        int trackY = y + 2;

        guiGraphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF1E293B);
        guiGraphics.renderOutline(trackX, trackY, trackW, trackH, 0xFF475569);

        // Fill progress
        int fillW = (int) Math.round(trackW * (opacity - 0.05) / 0.95);
        fillW = Math.max(0, Math.min(trackW, fillW));
        guiGraphics.fill(trackX, trackY, trackX + fillW, trackY + trackH, 0xFF38BDF8);

        // Handle
        int handleX = trackX + fillW - 2;
        guiGraphics.fill(handleX, trackY - 2, handleX + 4, trackY + trackH + 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int modalWidth = 320;
            int modalHeight = 250;
            int modalX = (this.width - modalWidth) / 2;
            int modalY = (this.height - modalHeight) / 2;
            int startY = modalY + 36;
            int rowH = 20;

            int btnW = 60;
            int btnH = 14;
            int btnX = modalX + modalWidth - 16 - btnW;

            // 1. Render Sphere
            if (isInside(mouseX, mouseY, btnX, startY, btnW, btnH)) {
                TimeStopConfig.CLIENT.enableBubbleRender.set(!TimeStopConfig.CLIENT.enableBubbleRender.get());
                saveAndPlaySound();
                return true;
            }

            // 2. Sci-Fi Grid
            if (isInside(mouseX, mouseY, btnX, startY + rowH, btnW, btnH)) {
                TimeStopConfig.CLIENT.enableBubbleGrid.set(!TimeStopConfig.CLIENT.enableBubbleGrid.get());
                saveAndPlaySound();
                return true;
            }

            // 3. 3D Specular
            if (isInside(mouseX, mouseY, btnX, startY + rowH * 2, btnW, btnH)) {
                TimeStopConfig.CLIENT.enableSpecularSheen.set(!TimeStopConfig.CLIENT.enableSpecularSheen.get());
                saveAndPlaySound();
                return true;
            }

            // 4. Orbit Equator
            if (isInside(mouseX, mouseY, btnX, startY + rowH * 3, btnW, btnH)) {
                TimeStopConfig.CLIENT.enableEquatorRing.set(!TimeStopConfig.CLIENT.enableEquatorRing.get());
                saveAndPlaySound();
                return true;
            }

            // 5. Shaders
            if (isInside(mouseX, mouseY, btnX, startY + rowH * 4, btnW, btnH)) {
                TimeStopConfig.CLIENT.enableShaders.set(!TimeStopConfig.CLIENT.enableShaders.get());
                if (!TimeStopConfig.CLIENT.enableShaders.get()) {
                    com.timestop.core.ClientTimeStopManager.removeShader();
                }
                saveAndPlaySound();
                return true;
            }

            // 6. Sounds
            if (isInside(mouseX, mouseY, btnX, startY + rowH * 5, btnW, btnH)) {
                TimeStopConfig.CLIENT.enableSounds.set(!TimeStopConfig.CLIENT.enableSounds.get());
                saveAndPlaySound();
                return true;
            }

            // 7. Timer HUD
            if (isInside(mouseX, mouseY, btnX, startY + rowH * 6, btnW, btnH)) {
                TimeStopConfig.CLIENT.enableTimerHud.set(!TimeStopConfig.CLIENT.enableTimerHud.get());
                saveAndPlaySound();
                return true;
            }

            // 8. Opacity Slider
            int sliderY = startY + rowH * 7;
            int trackW = 100;
            int trackH = 14;
            int trackX = modalX + modalWidth - 16 - trackW;
            if (isInside(mouseX, mouseY, trackX - 4, sliderY, trackW + 8, trackH)) {
                this.draggingOpacity = true;
                updateOpacityFromMouse(mouseX, trackX, trackW);
                return true;
            }

            // Bottom Done / Back Button
            int doneBtnW = 100;
            int doneBtnH = 20;
            int doneBtnX = modalX + (modalWidth - doneBtnW) / 2;
            int doneBtnY = modalY + modalHeight - 26;

            if (isInside(mouseX, mouseY, doneBtnX, doneBtnY, doneBtnW, doneBtnH)) {
                closeScreen();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingOpacity) {
            this.draggingOpacity = false;
            saveConfig();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingOpacity) {
            int modalWidth = 320;
            int modalX = (this.width - modalWidth) / 2;
            int trackW = 100;
            int trackX = modalX + modalWidth - 16 - trackW;
            updateOpacityFromMouse(mouseX, trackX, trackW);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateOpacityFromMouse(double mouseX, int trackX, int trackW) {
        double frac = (mouseX - trackX) / (double) trackW;
        frac = Math.max(0.0, Math.min(1.0, frac));
        double val = 0.05 + frac * 0.95;
        TimeStopConfig.CLIENT.bubbleOpacity.set(Math.round(val * 100.0) / 100.0);
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void saveAndPlaySound() {
        saveConfig();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.2F);
        }
    }

    private void saveConfig() {
        TimeStopConfig.CLIENT_SPEC.save();
    }

    private void closeScreen() {
        if (this.minecraft != null) {
            if (this.parentScreen != null) {
                this.minecraft.setScreen(this.parentScreen);
            } else {
                this.minecraft.setScreen(null);
            }
            if (this.minecraft.player != null) {
                this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_E) {
            closeScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}