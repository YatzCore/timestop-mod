package com.timestop.client.gui;

import com.timestop.config.TimeStopConfig;
import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeStopManager;
import com.timestop.network.ModMessages;
import com.timestop.network.ToggleProjectileFlowPacket;
import com.timestop.network.UpdateMechanicsConfigPacket;
import com.timestop.network.UpdateSpeedConfigPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.Locale;

public class TimeStopSettingsScreen extends Screen {

    @Nullable
    private final Screen parentScreen;
    @Nullable
    private final InteractionHand hand;

    // Tab state: 0 = Visuals & FX, 1 = Mechanics, 2 = Speed Calibration
    private int activeTab = 0;

    private boolean draggingOpacity = false;
    private int draggingSpeedIndex = -1;
    private int focusedSpeedIndex = -1;
    private String inputBuffer = "";

    @Nullable
    private Component activeTooltip = null;

    private static final String[] SPEED_LABELS = {
            "Fast Forward", "Slow Motion", "Matrix Dilation", "Superhot Idle", "Decel Drag"
    };
    private static final String[] SPEED_SUBS = {
            "1.1x - 50.0x (def 5.0x)",
            "0.01x - 0.99x (def 0.25x)",
            "0.01x - 0.99x (def 0.25x)",
            "0.005x - 0.80x (def 0.05x)",
            "0.001x - 0.95x (def 0.10x)"
    };
    private static final double[] SPEED_MINS = { 1.1, 0.01, 0.01, 0.005, 0.001 };
    private static final double[] SPEED_MAXS = { 50.0, 0.99, 0.99, 0.80, 0.95 };
    private static final double[] SPEED_DEFS = { 5.0, 0.25, 0.25, 0.05, 0.10 };

    public TimeStopSettingsScreen(@Nullable Screen parentScreen, @Nullable InteractionHand hand) {
        super(Component.literal("Temporal Engine Settings"));
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

    public void onSpeedConfigSynced() {
        // Automatically reflected by TimeStopConfig.COMMON reads
    }

    public void onMechanicsConfigSynced() {
        // Automatically reflected by TimeStopConfig.COMMON reads
    }

    private boolean canEditServerSettings() {
        if (this.minecraft == null || this.minecraft.player == null) return false;
        if (this.minecraft.isSingleplayer()) return true;
        return this.minecraft.player.hasPermissions(2);
    }

    private double getSpeedValue(int index) {
        return switch (index) {
            case 0 -> TimeStopConfig.COMMON.fastForwardRate.get();
            case 1 -> TimeStopConfig.COMMON.slowMotionRate.get();
            case 2 -> TimeStopConfig.COMMON.matrixRate.get();
            case 3 -> TimeStopConfig.COMMON.superhotIdleRate.get();
            case 4 -> TimeStopConfig.COMMON.decelerationDrag.get();
            default -> 1.0;
        };
    }

    private void setSpeedValue(int index, double val) {
        switch (index) {
            case 0 -> TimeStopConfig.COMMON.fastForwardRate.set(TimeStopConfig.clampFastForward(val));
            case 1 -> TimeStopConfig.COMMON.slowMotionRate.set(TimeStopConfig.clampSlowMotion(val));
            case 2 -> TimeStopConfig.COMMON.matrixRate.set(TimeStopConfig.clampMatrix(val));
            case 3 -> TimeStopConfig.COMMON.superhotIdleRate.set(TimeStopConfig.clampSuperhotIdle(val));
            case 4 -> TimeStopConfig.COMMON.decelerationDrag.set(TimeStopConfig.clampDecelerationDrag(val));
        }
    }

    private void resetSpeedValue(int index) {
        setSpeedValue(index, SPEED_DEFS[index]);
    }

    private String formatSpeed(int index, double val) {
        if (index == 0) return String.format(Locale.ROOT, "%.2fx", val);
        if (index == 1 || index == 2) return String.format(Locale.ROOT, "%.2fx", val);
        return String.format(Locale.ROOT, "%.3fx", val);
    }

    private String formatSpeedRaw(int index, double val) {
        if (index == 0) return String.format(Locale.ROOT, "%.2f", val);
        if (index == 1 || index == 2) return String.format(Locale.ROOT, "%.2f", val);
        return String.format(Locale.ROOT, "%.3f", val);
    }

    private void commitSpeedInput() {
        if (this.focusedSpeedIndex >= 0) {
            int idx = this.focusedSpeedIndex;
            try {
                double parsed = Double.parseDouble(this.inputBuffer.trim());
                setSpeedValue(idx, parsed);
                TimeStopConfig.save();
                sendCurrentSpeedsToServer();
            } catch (NumberFormatException ignored) {}
            this.focusedSpeedIndex = -1;
            this.inputBuffer = "";
        }
    }

    private void cancelSpeedInput() {
        this.focusedSpeedIndex = -1;
        this.inputBuffer = "";
    }

    private void sendCurrentSpeedsToServer() {
        ModMessages.sendToServer(new UpdateSpeedConfigPacket(
                TimeStopConfig.COMMON.fastForwardRate.get(),
                TimeStopConfig.COMMON.slowMotionRate.get(),
                TimeStopConfig.COMMON.matrixRate.get(),
                TimeStopConfig.COMMON.superhotIdleRate.get(),
                TimeStopConfig.COMMON.decelerationDrag.get()
        ));
    }

    private void sendCurrentMechanicsToServer() {
        ModMessages.sendToServer(new UpdateMechanicsConfigPacket(
                TimeStopConfig.COMMON.enableWaterWalkingInStasis.get(),
                TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.get()
        ));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.activeTooltip = null;

        int modalWidth = 330;
        int modalHeight = 285;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        // Background dark-glass panel
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xEE0B0F19);
        guiGraphics.renderOutline(modalX, modalY, modalWidth, modalHeight, 0xFF38BDF8);

        // Header Title
        guiGraphics.drawString(this.font, "TEMPORAL ENGINE CONFIGURATION", modalX + 16, modalY + 11, 0xFF38BDF8, false);

        // Tab Selector Buttons (3 Tabs)
        int tabW = 94;
        int tabGap = 8;
        int tabsStartX = modalX + 16;
        int tab0X = tabsStartX;
        int tab1X = tabsStartX + tabW + tabGap;
        int tab2X = tabsStartX + (tabW + tabGap) * 2;
        int tabY = modalY + 25;
        int tabH = 16;

        // Tab 0 button: Visuals & FX
        renderTabButton(guiGraphics, tab0X, tabY, tabW, tabH, "Visuals & FX", activeTab == 0, mouseX, mouseY);

        // Tab 1 button: Mechanics
        renderTabButton(guiGraphics, tab1X, tabY, tabW, tabH, "Mechanics", activeTab == 1, mouseX, mouseY);

        // Tab 2 button: Speed Calibration
        renderTabButton(guiGraphics, tab2X, tabY, tabW, tabH, "Calibration", activeTab == 2, mouseX, mouseY);

        // Horizontal divider under tabs
        guiGraphics.fill(modalX + 12, modalY + 44, modalX + modalWidth - 12, modalY + 45, 0x33FFFFFF);

        if (activeTab == 0) {
            renderVisualsTab(guiGraphics, modalX, modalY, modalWidth, modalHeight, mouseX, mouseY);
        } else if (activeTab == 1) {
            renderMechanicsTab(guiGraphics, modalX, modalY, modalWidth, modalHeight, mouseX, mouseY);
        } else {
            renderSpeedsTab(guiGraphics, modalX, modalY, modalWidth, modalHeight, mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.activeTooltip != null) {
            guiGraphics.renderTooltip(this.font, this.activeTooltip, mouseX, mouseY);
        }
    }

    private void renderTabButton(GuiGraphics guiGraphics, int x, int y, int w, int h, String text, boolean active, int mouseX, int mouseY) {
        boolean hovered = isInside(mouseX, mouseY, x, y, w, h);
        int bg = active ? 0xFF0284C7 : (hovered ? 0xFF1E293B : 0xFF0F172A);
        int border = active ? 0xFF38BDF8 : 0xFF334155;
        int textColor = active ? 0xFFFFFFFF : (hovered ? 0xFFE2E8F0 : 0xFF94A3B8);

        guiGraphics.fill(x, y, x + w, y + h, bg);
        guiGraphics.renderOutline(x, y, w, h, border);
        int tX = x + (w - this.font.width(text)) / 2;
        guiGraphics.drawString(this.font, text, tX, y + 4, textColor, false);
    }

    private void renderVisualsTab(GuiGraphics guiGraphics, int modalX, int modalY, int modalWidth, int modalHeight, int mouseX, int mouseY) {
        int startY = modalY + 49;
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

        // 8. Superhot Mob Tint (HOSTILE / PASSIVE / ALL)
        renderCycleRow(guiGraphics, modalX, startY + rowH * 7, modalWidth, "Superhot Mob Tint",
                TimeStopConfig.CLIENT.superhotMobTarget.get(), mouseX, mouseY);

        // 9. Opacity Slider
        int sliderY = startY + rowH * 8 + 2;
        renderOpacitySlider(guiGraphics, modalX, sliderY, modalWidth, mouseX, mouseY);

        // Bottom Done / Back Button
        renderDoneButton(guiGraphics, modalX, modalY, modalWidth, modalHeight, mouseX, mouseY);
    }

    private void renderMechanicsTab(GuiGraphics guiGraphics, int modalX, int modalY, int modalWidth, int modalHeight, int mouseX, int mouseY) {
        int startY = modalY + 54;
        int rowH = 42;
        boolean canEdit = canEditServerSettings();

        // Row 1: Water Walking in Stasis
        renderMechanicRow(guiGraphics, modalX, startY, modalWidth,
                "Water Walking in Stasis",
                "Walk across water and lava during temporal stasis",
                TimeStopConfig.COMMON.enableWaterWalkingInStasis.get(),
                canEdit, mouseX, mouseY);

        // Row 2: Player Projectiles in Stasis
        renderMechanicRow(guiGraphics, modalX, startY + rowH, modalWidth,
                "Player Projectiles in Stasis",
                "Allow players to fire arrows & shots while frozen",
                TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.get(),
                canEdit, mouseX, mouseY);

        // Row 3: Projectile Flow Mode
        boolean isFlowing = ClientTimeStopManager.getProjectileMode() == TimeStopManager.ProjectileStasisMode.FLOWING;
        renderMechanicModeRow(guiGraphics, modalX, startY + rowH * 2, modalWidth,
                "Projectiles Flow in Stasis",
                "Instant ballistic flight (FLOW) vs suspended matrix (SUSPEND)",
                isFlowing ? "FLOWING" : "SUSPENDED",
                isFlowing, mouseX, mouseY);

        // Informational tip line
        if (canEdit) {
            guiGraphics.drawString(this.font, "Tip: Mechanics apply universally across server stasis", modalX + 16, modalY + 224, 0xFF64748B, false);
        } else {
            guiGraphics.drawString(this.font, "Notice: Read-only mode (Server Operator level 2 required)", modalX + 16, modalY + 224, 0xFFEF4444, false);
        }

        // Bottom Done / Back Button
        renderDoneButton(guiGraphics, modalX, modalY, modalWidth, modalHeight, mouseX, mouseY);
    }

    private void renderMechanicRow(GuiGraphics guiGraphics, int modalX, int y, int modalWidth, String title, String subtitle, boolean enabled, boolean canEdit, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, title, modalX + 16, y + 2, 0xFFE2E8F0, false);
        guiGraphics.drawString(this.font, subtitle, modalX + 16, y + 14, 0xFF64748B, false);

        int btnW = 60;
        int btnH = 16;
        int btnX = modalX + modalWidth - 16 - btnW;
        int btnY = y + 4;

        boolean isHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        int bg = canEdit
                ? (enabled ? (isHovered ? 0xFF15803D : 0xFF16A34A) : (isHovered ? 0xFF991B1B : 0xFFDC2626))
                : (enabled ? 0xFF166534 : 0xFF7F1D1D);
        int border = isHovered && canEdit ? 0xFFFFFFFF : (enabled ? 0xFF4ADE80 : 0xFFF87171);

        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        guiGraphics.renderOutline(btnX, btnY, btnW, btnH, border);

        String text = enabled ? "ON" : "OFF";
        int textX = btnX + (btnW - this.font.width(text)) / 2;
        guiGraphics.drawString(this.font, text, textX, btnY + 4, canEdit ? 0xFFFFFFFF : 0xFF94A3B8, false);

        if (isHovered && !canEdit) {
            this.activeTooltip = Component.literal("Requires Server Operator (Level 2) permissions");
        }
    }

    private void renderMechanicModeRow(GuiGraphics guiGraphics, int modalX, int y, int modalWidth, String title, String subtitle, String modeText, boolean isFlowing, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, title, modalX + 16, y + 2, 0xFFE2E8F0, false);
        guiGraphics.drawString(this.font, subtitle, modalX + 16, y + 14, 0xFF64748B, false);

        int btnW = 76;
        int btnH = 16;
        int btnX = modalX + modalWidth - 16 - btnW;
        int btnY = y + 4;

        boolean isHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        int bg = isFlowing ? (isHovered ? 0xFF0284C7 : 0xFF0369A1) : (isHovered ? 0xFF0D9488 : 0xFF0F766E);
        int border = isHovered ? 0xFFFFFFFF : (isFlowing ? 0xFF38BDF8 : 0xFF2DD4BF);

        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        guiGraphics.renderOutline(btnX, btnY, btnW, btnH, border);

        int textX = btnX + (btnW - this.font.width(modeText)) / 2;
        guiGraphics.drawString(this.font, modeText, textX, btnY + 4, 0xFFFFFFFF, false);

        if (isHovered) {
            this.activeTooltip = Component.literal("Toggle projectile flow state (Keybind: K)");
        }
    }

    private void renderSpeedsTab(GuiGraphics guiGraphics, int modalX, int modalY, int modalWidth, int modalHeight, int mouseX, int mouseY) {
        int startY = modalY + 50;
        int rowH = 34;
        boolean canEdit = canEditServerSettings();

        for (int i = 0; i < 5; i++) {
            int rowY = startY + i * rowH;

            // Mode Title
            guiGraphics.drawString(this.font, SPEED_LABELS[i], modalX + 16, rowY + 2, 0xFFE2E8F0, false);
            // Range & Default Subtitle
            guiGraphics.drawString(this.font, SPEED_SUBS[i], modalX + 16, rowY + 13, 0xFF64748B, false);

            int trackW = 80;
            int trackH = 10;
            int trackX = modalX + 145;
            int trackY = rowY + 5;

            // Slider track
            guiGraphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF1E293B);
            guiGraphics.renderOutline(trackX, trackY, trackW, trackH, 0xFF475569);

            double frac = (getSpeedValue(i) - SPEED_MINS[i]) / (SPEED_MAXS[i] - SPEED_MINS[i]);
            int fillW = (int) Math.round(trackW * Math.max(0.0, Math.min(1.0, frac)));
            int barColor = canEdit ? (i == 0 ? 0xFFF59E0B : 0xFF38BDF8) : 0xFF64748B;
            guiGraphics.fill(trackX, trackY, trackX + fillW, trackY + trackH, barColor);

            int handleX = trackX + fillW - 2;
            int handleColor = canEdit ? 0xFFFFFFFF : 0xFF94A3B8;
            guiGraphics.fill(handleX, trackY - 2, handleX + 4, trackY + trackH + 2, handleColor);

            if (isInside(mouseX, mouseY, trackX - 2, trackY - 2, trackW + 4, trackH + 4)) {
                if (!canEdit) {
                    this.activeTooltip = Component.literal("Requires Server Operator (Level 2) permissions");
                }
            }

            // Digit Box
            int boxW = 48;
            int boxH = 16;
            int boxX = modalX + 235;
            int boxY = rowY + 2;

            boolean isFocused = (this.focusedSpeedIndex == i);
            boolean isBoxHovered = isInside(mouseX, mouseY, boxX, boxY, boxW, boxH);
            int boxBg = isFocused ? 0xFF0F172A : (isBoxHovered && canEdit ? 0xFF1E293B : 0xFF0B132B);
            int boxBorder = isFocused ? 0xFFFACC15 : (isBoxHovered && canEdit ? 0xFF38BDF8 : 0xFF475569);
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, boxBg);
            guiGraphics.renderOutline(boxX, boxY, boxW, boxH, boxBorder);

            String dispStr;
            if (isFocused) {
                dispStr = this.inputBuffer + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
            } else {
                dispStr = formatSpeed(i, getSpeedValue(i));
            }
            int textColor = isFocused ? 0xFFFACC15 : (canEdit ? 0xFFFFFFFF : 0xFF94A3B8);
            int textX = boxX + (boxW - this.font.width(dispStr)) / 2;
            guiGraphics.drawString(this.font, dispStr, textX, boxY + 4, textColor, false);

            if (isBoxHovered) {
                if (!canEdit) {
                    this.activeTooltip = Component.literal("Requires Server Operator (Level 2) permissions");
                } else if (!isFocused) {
                    this.activeTooltip = Component.literal("Click to type exact multiplier");
                }
            }

            // Reset Button
            int btnW = 16;
            int btnH = 16;
            int btnX = modalX + 293;
            int btnY = rowY + 2;

            boolean isResetHovered = isInside(mouseX, mouseY, btnX, btnY, btnW, btnH);
            int rBg = isResetHovered && canEdit ? 0xFF0284C7 : 0xFF1E293B;
            int rBorder = isResetHovered && canEdit ? 0xFF38BDF8 : 0xFF475569;
            guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, rBg);
            guiGraphics.renderOutline(btnX, btnY, btnW, btnH, rBorder);
            guiGraphics.drawString(this.font, "R", btnX + 5, btnY + 4, canEdit ? 0xFFFFFFFF : 0xFF64748B, false);

            if (isResetHovered) {
                if (!canEdit) {
                    this.activeTooltip = Component.literal("Requires Server Operator (Level 2) permissions");
                } else {
                    this.activeTooltip = Component.literal("Reset to default (" + SPEED_DEFS[i] + "x)");
                }
            }
        }

        // Informational tip line
        if (canEdit) {
            guiGraphics.drawString(this.font, "Tip: Drag sliders or click boxes to type exact digits", modalX + 16, modalY + 224, 0xFF64748B, false);
        } else {
            guiGraphics.drawString(this.font, "Notice: Read-only mode (Server Operator level 2 required)", modalX + 16, modalY + 224, 0xFFEF4444, false);
        }

        // Bottom Reset All Button
        int rAllW = 90;
        int rAllH = 20;
        int rAllX = modalX + 16;
        int rAllY = modalY + modalHeight - 26;

        boolean hoverRAll = isInside(mouseX, mouseY, rAllX, rAllY, rAllW, rAllH);
        int rAllBg = canEdit ? (hoverRAll ? 0xFFB91C1C : 0xFF991B1B) : 0xFF334155;
        int rAllBorder = canEdit ? 0xFFF87171 : 0xFF475569;
        guiGraphics.fill(rAllX, rAllY, rAllX + rAllW, rAllY + rAllH, rAllBg);
        guiGraphics.renderOutline(rAllX, rAllY, rAllW, rAllH, rAllBorder);
        int rAllTextX = rAllX + (rAllW - this.font.width("RESET ALL")) / 2;
        guiGraphics.drawString(this.font, "RESET ALL", rAllTextX, rAllY + 6, canEdit ? 0xFFFFFFFF : 0xFF94A3B8, false);

        if (hoverRAll) {
            if (canEdit) {
                this.activeTooltip = Component.literal("Reset all 5 multipliers to default calibration");
            } else {
                this.activeTooltip = Component.literal("Requires Server Operator (Level 2) permissions");
            }
        }

        // Bottom Done / Back Button
        int doneW = 90;
        int doneH = 20;
        int doneX = modalX + modalWidth - 16 - doneW;
        int doneY = modalY + modalHeight - 26;

        boolean isDoneHovered = isInside(mouseX, mouseY, doneX, doneY, doneW, doneH);
        int doneBg = isDoneHovered ? 0xFF0284C7 : 0xFF0369A1;
        guiGraphics.fill(doneX, doneY, doneX + doneW, doneY + doneH, doneBg);
        guiGraphics.renderOutline(doneX, doneY, doneW, doneH, 0xFF38BDF8);

        String btnText = this.parentScreen != null ? "BACK" : "DONE";
        int textX = doneX + (doneW - this.font.width(btnText)) / 2;
        guiGraphics.drawString(this.font, btnText, textX, doneY + 6, 0xFFFFFFFF, false);
    }

    private void renderDoneButton(GuiGraphics guiGraphics, int modalX, int modalY, int modalWidth, int modalHeight, int mouseX, int mouseY) {
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

    private void renderCycleRow(GuiGraphics guiGraphics, int modalX, int y, int modalWidth, String label, String value, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, label, modalX + 16, y + 4, 0xFFE2E8F0, false);

        int btnW = 60;
        int btnH = 14;
        int btnX = modalX + modalWidth - 16 - btnW;

        boolean isHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= y && mouseY <= y + btnH;
        int bg;
        int border;
        if ("HOSTILE".equalsIgnoreCase(value)) {
            bg = isHovered ? 0xFFB91C1C : 0xFF991B1B;
            border = isHovered ? 0xFFFFFFFF : 0xFFF87171;
        } else if ("PASSIVE".equalsIgnoreCase(value)) {
            bg = isHovered ? 0xFF15803D : 0xFF16A34A;
            border = isHovered ? 0xFFFFFFFF : 0xFF4ADE80;
        } else {
            bg = isHovered ? 0xFFD97706 : 0xFFB45309;
            border = isHovered ? 0xFFFFFFFF : 0xFFFBBF24;
        }

        guiGraphics.fill(btnX, y, btnX + btnW, y + btnH, bg);
        guiGraphics.renderOutline(btnX, y, btnW, btnH, border);

        int textX = btnX + (btnW - this.font.width(value)) / 2;
        guiGraphics.drawString(this.font, value, textX, y + 3, 0xFFFFFFFF, false);

        if (isHovered) {
            this.activeTooltip = Component.literal("Crystal red mobs in Superhot: " + value + " (click to cycle)");
        }
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
            int modalWidth = 330;
            int modalHeight = 285;
            int modalX = (this.width - modalWidth) / 2;
            int modalY = (this.height - modalHeight) / 2;

            // Tab headers
            int tabW = 94;
            int tabGap = 8;
            int tabsStartX = modalX + 16;
            int tab0X = tabsStartX;
            int tab1X = tabsStartX + tabW + tabGap;
            int tab2X = tabsStartX + (tabW + tabGap) * 2;
            int tabY = modalY + 25;
            int tabH = 16;

            if (isInside(mouseX, mouseY, tab0X, tabY, tabW, tabH)) {
                if (this.activeTab != 0) {
                    commitSpeedInput();
                    this.activeTab = 0;
                    playClickSound();
                }
                return true;
            }

            if (isInside(mouseX, mouseY, tab1X, tabY, tabW, tabH)) {
                if (this.activeTab != 1) {
                    commitSpeedInput();
                    this.activeTab = 1;
                    playClickSound();
                }
                return true;
            }

            if (isInside(mouseX, mouseY, tab2X, tabY, tabW, tabH)) {
                if (this.activeTab != 2) {
                    commitSpeedInput();
                    this.activeTab = 2;
                    playClickSound();
                }
                return true;
            }

            if (this.activeTab == 0) {
                // TAB 0: Visuals & FX
                int startY = modalY + 49;
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
                        ClientTimeStopManager.removeShader();
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

                // 8. Superhot Mob Tint
                if (isInside(mouseX, mouseY, btnX, startY + rowH * 7, btnW, btnH)) {
                    String cur = TimeStopConfig.CLIENT.superhotMobTarget.get().toUpperCase(Locale.ROOT);
                    String next = switch (cur) {
                        case "HOSTILE" -> "PASSIVE";
                        case "PASSIVE" -> "ALL";
                        default -> "HOSTILE";
                    };
                    TimeStopConfig.CLIENT.superhotMobTarget.set(next);
                    saveAndPlaySound();
                    return true;
                }

                // 9. Opacity Slider
                int sliderY = startY + rowH * 8 + 2;
                int trackW = 100;
                int trackH = 14;
                int trackX = modalX + modalWidth - 16 - trackW;
                if (isInside(mouseX, mouseY, trackX - 4, sliderY, trackW + 8, trackH)) {
                    this.draggingOpacity = true;
                    updateOpacityFromMouse(mouseX, trackX, trackW);
                    return true;
                }

                // Done / Back Button
                int doneBtnW = 100;
                int doneBtnH = 20;
                int doneBtnX = modalX + (modalWidth - doneBtnW) / 2;
                int doneBtnY = modalY + modalHeight - 26;

                if (isInside(mouseX, mouseY, doneBtnX, doneBtnY, doneBtnW, doneBtnH)) {
                    closeScreen();
                    return true;
                }
            } else if (this.activeTab == 1) {
                // TAB 1: Mechanics
                int startY = modalY + 54;
                int rowH = 42;
                boolean canEdit = canEditServerSettings();

                // 1. Water Walking
                int btn1W = 60;
                int btn1H = 16;
                int btn1X = modalX + modalWidth - 16 - btn1W;
                int btn1Y = startY + 4;
                if (isInside(mouseX, mouseY, btn1X, btn1Y, btn1W, btn1H)) {
                    if (canEdit) {
                        TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(!TimeStopConfig.COMMON.enableWaterWalkingInStasis.get());
                        TimeStopConfig.save();
                        sendCurrentMechanicsToServer();
                        saveAndPlaySound();
                    }
                    return true;
                }

                // 2. Player Projectiles
                int btn2Y = startY + rowH + 4;
                if (isInside(mouseX, mouseY, btn1X, btn2Y, btn1W, btn1H)) {
                    if (canEdit) {
                        TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.set(!TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.get());
                        TimeStopConfig.save();
                        sendCurrentMechanicsToServer();
                        saveAndPlaySound();
                    }
                    return true;
                }

                // 3. Projectile Flow Mode
                int btn3W = 76;
                int btn3H = 16;
                int btn3X = modalX + modalWidth - 16 - btn3W;
                int btn3Y = startY + rowH * 2 + 4;
                if (isInside(mouseX, mouseY, btn3X, btn3Y, btn3W, btn3H)) {
                    TimeStopManager.ProjectileStasisMode current = ClientTimeStopManager.getProjectileMode();
                    TimeStopManager.ProjectileStasisMode next = (current == TimeStopManager.ProjectileStasisMode.FLOWING)
                            ? TimeStopManager.ProjectileStasisMode.SUSPENDED
                            : TimeStopManager.ProjectileStasisMode.FLOWING;
                    ClientTimeStopManager.setProjectileFlow(next, TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.get());
                    ModMessages.sendToServer(new ToggleProjectileFlowPacket(next));
                    saveAndPlaySound();
                    return true;
                }

                // Done / Back Button
                int doneBtnW = 100;
                int doneBtnH = 20;
                int doneBtnX = modalX + (modalWidth - doneBtnW) / 2;
                int doneBtnY = modalY + modalHeight - 26;

                if (isInside(mouseX, mouseY, doneBtnX, doneBtnY, doneBtnW, doneBtnH)) {
                    closeScreen();
                    return true;
                }
            } else {
                // TAB 2: Speeds
                int startY = modalY + 50;
                int rowH = 34;
                boolean canEdit = canEditServerSettings();

                for (int i = 0; i < 5; i++) {
                    int rowY = startY + i * rowH;

                    int trackW = 80;
                    int trackH = 10;
                    int trackX = modalX + 145;
                    int trackY = rowY + 5;

                    int boxW = 48;
                    int boxH = 16;
                    int boxX = modalX + 235;
                    int boxY = rowY + 2;

                    int btnW = 16;
                    int btnH = 16;
                    int btnX = modalX + 293;
                    int btnY = rowY + 2;

                    // Slider track clicked
                    if (isInside(mouseX, mouseY, trackX - 4, trackY - 2, trackW + 8, trackH + 4)) {
                        if (canEdit) {
                            commitSpeedInput();
                            this.draggingSpeedIndex = i;
                            updateSpeedFromSlider(i, mouseX, trackX, trackW);
                        }
                        return true;
                    }

                    // Digit box clicked
                    if (isInside(mouseX, mouseY, boxX, boxY, boxW, boxH)) {
                        if (canEdit) {
                            if (this.focusedSpeedIndex != i) {
                                commitSpeedInput();
                                this.focusedSpeedIndex = i;
                                this.inputBuffer = formatSpeedRaw(i, getSpeedValue(i));
                            }
                        }
                        return true;
                    }

                    // Reset button clicked
                    if (isInside(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
                        if (canEdit) {
                            commitSpeedInput();
                            resetSpeedValue(i);
                            TimeStopConfig.save();
                            sendCurrentSpeedsToServer();
                            saveAndPlaySound();
                        }
                        return true;
                    }
                }

                // If user clicked elsewhere, commit any active text edit
                commitSpeedInput();

                // Reset All Button
                int rAllW = 90;
                int rAllH = 20;
                int rAllX = modalX + 16;
                int rAllY = modalY + modalHeight - 26;
                if (isInside(mouseX, mouseY, rAllX, rAllY, rAllW, rAllH)) {
                    if (canEdit) {
                        TimeStopConfig.resetSpeedsToDefaults();
                        TimeStopConfig.save();
                        ModMessages.sendToServer(UpdateSpeedConfigPacket.reset());
                        saveAndPlaySound();
                    }
                    return true;
                }

                // Done / Back Button
                int doneW = 90;
                int doneH = 20;
                int doneX = modalX + modalWidth - 16 - doneW;
                int doneY = modalY + modalHeight - 26;
                if (isInside(mouseX, mouseY, doneX, doneY, doneW, doneH)) {
                    closeScreen();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (this.draggingOpacity) {
                this.draggingOpacity = false;
                saveConfig();
            }
            if (this.draggingSpeedIndex >= 0) {
                this.draggingSpeedIndex = -1;
                TimeStopConfig.save();
                sendCurrentSpeedsToServer();
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingOpacity) {
            int modalWidth = 330;
            int modalX = (this.width - modalWidth) / 2;
            int trackW = 100;
            int trackX = modalX + modalWidth - 16 - trackW;
            updateOpacityFromMouse(mouseX, trackX, trackW);
            return true;
        }
        if (this.draggingSpeedIndex >= 0) {
            int modalWidth = 330;
            int modalX = (this.width - modalWidth) / 2;
            int trackX = modalX + 145;
            int trackW = 80;
            updateSpeedFromSlider(this.draggingSpeedIndex, mouseX, trackX, trackW);
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

    private void updateSpeedFromSlider(int index, double mouseX, int trackX, int trackW) {
        double frac = (mouseX - trackX) / (double) trackW;
        frac = Math.max(0.0, Math.min(1.0, frac));
        double min = SPEED_MINS[index];
        double max = SPEED_MAXS[index];
        double val = min + frac * (max - min);

        if (index == 0) {
            val = Math.round(val * 10.0) / 10.0;
        } else if (index == 1 || index == 2) {
            val = Math.round(val * 100.0) / 100.0;
        } else {
            val = Math.round(val * 1000.0) / 1000.0;
        }
        setSpeedValue(index, val);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.focusedSpeedIndex >= 0) {
            if ((codePoint >= '0' && codePoint <= '9') || codePoint == '.') {
                if (this.inputBuffer.length() < 7) {
                    this.inputBuffer += codePoint;
                    return true;
                }
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.focusedSpeedIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitSpeedInput();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelSpeedInput();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!this.inputBuffer.isEmpty()) {
                    this.inputBuffer = this.inputBuffer.substring(0, this.inputBuffer.length() - 1);
                }
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || (this.focusedSpeedIndex < 0 && keyCode == GLFW.GLFW_KEY_E)) {
            closeScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void playClickSound() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
        }
    }

    private void saveAndPlaySound() {
        saveConfig();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.2F);
        }
    }

    private void saveConfig() {
        TimeStopConfig.save();
    }

    private void closeScreen() {
        if (this.minecraft != null) {
            if (this.parentScreen != null) {
                this.minecraft.setScreen(this.parentScreen);
            } else {
                this.minecraft.setScreen(null);
            }
            playClickSound();
        }
    }
}