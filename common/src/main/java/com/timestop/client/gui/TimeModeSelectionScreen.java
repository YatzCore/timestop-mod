package com.timestop.client.gui;

import com.timestop.core.TimeMode;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import com.timestop.item.ModItems;
import com.timestop.network.ModMessages;
import com.timestop.network.SelectTimeModePacket;
import com.timestop.network.SetRewindExemptPacket;
import com.timestop.network.SocketSpecificRunePacket;
import com.timestop.network.ToggleMatrixRunePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Calm, minimalist, tier-tailored control interface for temporal pocket watches.
 * Rune transfers wait for authoritative server inventory updates.
 */
public class TimeModeSelectionScreen extends Screen {
    private static final List<RuneType> MATRIX_RUNES = List.of(
            RuneType.DEFLECTION,
            RuneType.SNATCHING,
            RuneType.PHASING,
            RuneType.KINETIC,
            RuneType.VAMPIRISM,
            RuneType.VOLATILE,
            RuneType.TACHYON,
            RuneType.DEAD_EYE,
            RuneType.RICOCHET,
            RuneType.ORBITAL,
            RuneType.TRANSPOSITION,
            RuneType.VECTOR,
            RuneType.KINETIC_BARRIER,
            RuneType.RICOSHOT,
            RuneType.REWIND
    );

    private final InteractionHand hand;
    private TimeMode currentMode = TimeMode.TIME_STOP;
    private WatchTier currentTier = WatchTier.COPPER;
    private ItemStack watchStack = ItemStack.EMPTY;
    private ItemStack socketedRune = ItemStack.EMPTY;
    private boolean runeRequestPending;

    public static class InventoryRuneEntry {
        public final int slotIndex;
        public final ItemStack stack;
        public final RuneType type;

        public InventoryRuneEntry(int slotIndex, ItemStack stack, RuneType type) {
            this.slotIndex = slotIndex;
            this.stack = stack;
            this.type = type;
        }
    }

    private final List<InventoryRuneEntry> availableRunes = new ArrayList<>();
    private int trayScrollOffset = 0;

    // Visual theme palette per watch tier
    private record TierTheme(int bg, int border, int header, int accent, int cardBg, int cardHover, int cardActive, int cardBorder) {}

    private static final TierTheme THEME_COPPER = new TierTheme(
            0xF215110E, 0xFF9A3412, 0xFFEA580C, 0xFFF97316, 0x4426170E, 0x773F2213, 0x884D1F08, 0x44F97316
    );
    private static final TierTheme THEME_GOLD = new TierTheme(
            0xF216140D, 0xFFB45309, 0xFFF59E0B, 0xFFFBBF24, 0x44241B08, 0x773D2E0A, 0x884E3A06, 0x44FBBF24
    );
    private static final TierTheme THEME_DIAMOND = new TierTheme(
            0xF20B151C, 0xFF0369A1, 0xFF38BDF8, 0xFF0EA5E9, 0x44081C2B, 0x770D2D44, 0x880C3D5E, 0x4438BDF8
    );
    private static final TierTheme THEME_NETHERITE = new TierTheme(
            0xF2150E1C, 0xFF6B21A8, 0xFFA855F7, 0xFFC084FC, 0x441E0F2B, 0x77301647, 0x88421A63, 0x44C084FC
    );
    private static final TierTheme THEME_CREATIVE = new TierTheme(
            0xF2190E18, 0xFFBE185D, 0xFFEC4899, 0xFFF472B6, 0x44280E23, 0x77421438, 0x885A144B, 0x44F472B6
    );

    public TimeModeSelectionScreen(InteractionHand hand) {
        super(Component.literal("Watch Interface"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        super.init();
        refreshState();
    }

    @Override
    public void tick() {
        super.tick();
        // Continuously keep inventory tray and socketed state 100% in sync with player inventory
        refreshState();
    }

    public void onServerSync(ItemStack newSocketedRune) {
        runeRequestPending = false;
        this.socketedRune = newSocketedRune != null ? newSocketedRune.copy() : ItemStack.EMPTY;
        refreshState();
    }

    public void refreshState() {
        Player player = Minecraft.getInstance().player;
        this.availableRunes.clear();

        if (player != null) {
            this.watchStack = player.getItemInHand(this.hand);
            this.currentMode = AbstractWatchItem.getMode(this.watchStack);
            this.socketedRune = AbstractWatchItem.getSocketedRune(this.watchStack);
            if (this.watchStack.getItem() instanceof AbstractWatchItem watch) {
                this.currentTier = watch.getTier();
            }

            // Scan inventory for usable tactical runes if watch supports sockets
            if (this.currentTier.hasRuneSocket()) {
                int watchSlot = (this.hand == InteractionHand.MAIN_HAND) ? player.getInventory().selected : 40;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (i == watchSlot) continue; // Never scan the watch itself
                    ItemStack invStack = player.getInventory().getItem(i);
                    if (!invStack.isEmpty() && invStack.getCount() > 0 && invStack.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() != RuneType.BLANK) {
                        this.availableRunes.add(new InventoryRuneEntry(i, invStack, runeItem.getType()));
                    }
                }
            }
        }
    }

    private TierTheme getTheme() {
        return switch (this.currentTier) {
            case COPPER -> THEME_COPPER;
            case GILDED -> THEME_GOLD;
            case DIAMOND -> THEME_DIAMOND;
            case NETHERITE -> THEME_NETHERITE;
            case CREATIVE -> THEME_CREATIVE;
        };
    }

    private List<TimeMode> getDisplayModes() {
        return new ArrayList<>(this.currentTier.getUnlockedModes());
    }

    private int getModalWidth() {
        return this.currentTier == WatchTier.COPPER ? 250 : 330;
    }

    private int getModalHeight() {
        if (this.currentTier == WatchTier.COPPER) {
            return 140; // Compact 2-card layout without rune tray
        }
        int modeCount = getDisplayModes().size();
        int rows = Math.max(1, (modeCount + 1) / 2);
        int extra = (this.currentTier == WatchTier.CREATIVE) ? 20 : 0;
        return 224 + (rows - 2) * 37 + extra;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        TierTheme theme = getTheme();
        int modalWidth = getModalWidth();
        int modalHeight = getModalHeight();
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        // 1. Modal Background & Outer Border
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, theme.bg);
        guiGraphics.renderOutline(modalX, modalY, modalWidth, modalHeight, theme.border);

        // 2. Header Area
        int headerY = modalY + 10;
        if (!this.watchStack.isEmpty()) {
            guiGraphics.renderItem(this.watchStack, modalX + 12, headerY - 2);
        }

        int titleX = modalX + 34;
        String titleText = this.currentTier.getDisplayName().toUpperCase();
        guiGraphics.drawString(this.font, titleText, titleX, headerY, theme.header, false);

        String statBadge = getStatBadge();
        int statWidth = this.font.width(statBadge);
        guiGraphics.drawString(this.font, statBadge, modalX + modalWidth - 14 - statWidth, headerY + 1, 0xFF94A3B8, false);

        // Creative Watch Scope Toggle Button
        if (this.currentTier == WatchTier.CREATIVE) {
            boolean isGlobal = AbstractWatchItem.isGlobalScope(this.watchStack);
            int scopeBtnW = 105;
            int scopeBtnH = 14;
            int scopeBtnX = modalX + modalWidth - 14 - statWidth - scopeBtnW - 8;
            int scopeBtnY = headerY - 2;

            boolean isScopeHovered = mouseX >= scopeBtnX && mouseX <= scopeBtnX + scopeBtnW
                    && mouseY >= scopeBtnY && mouseY <= scopeBtnY + scopeBtnH;

            int scopeBg = isScopeHovered ? 0x88421438 : 0x44280E23;
            int scopeBorder = isScopeHovered ? 0xFFF472B6 : (isGlobal ? 0xFFEC4899 : 0xFF38BDF8);
            guiGraphics.fill(scopeBtnX, scopeBtnY, scopeBtnX + scopeBtnW, scopeBtnY + scopeBtnH, scopeBg);
            guiGraphics.renderOutline(scopeBtnX, scopeBtnY, scopeBtnW, scopeBtnH, scopeBorder);

            String scopeLabel = isGlobal ? "🌐 Full Server" : "🔮 Local Bubble";
            int labelColor = isGlobal ? 0xFFF472B6 : 0xFF38BDF8;
            int labelX = scopeBtnX + (scopeBtnW - this.font.width(scopeLabel)) / 2;
            guiGraphics.drawString(this.font, scopeLabel, labelX, scopeBtnY + 3, labelColor, false);

            if (isScopeHovered) {
                Component tip = isGlobal
                        ? Component.literal("Scope: FULL SERVER (Freezes time across all dimensions)")
                        : Component.literal("Scope: LOCAL BUBBLE (Creates a localized temporal sphere)");
                guiGraphics.renderTooltip(this.font, tip, mouseX, mouseY);
            }
        }

        // Settings Button [ ⚙ ]
        int settingsBtnW = 16;
        int settingsBtnH = 14;
        int extraRightOffset = (this.currentTier == WatchTier.CREATIVE) ? 115 : 0;
        int settingsBtnX = modalX + modalWidth - 14 - statWidth - extraRightOffset - settingsBtnW - 4;
        int settingsBtnY = headerY - 2;

        boolean isSettingsHovered = mouseX >= settingsBtnX && mouseX <= settingsBtnX + settingsBtnW
                && mouseY >= settingsBtnY && mouseY <= settingsBtnY + settingsBtnH;

        int setBg = isSettingsHovered ? 0x88252D3D : 0x44171B24;
        int setBorder = isSettingsHovered ? 0xFF38BDF8 : 0x4438BDF8;
        guiGraphics.fill(settingsBtnX, settingsBtnY, settingsBtnX + settingsBtnW, settingsBtnY + settingsBtnH, setBg);
        guiGraphics.renderOutline(settingsBtnX, settingsBtnY, settingsBtnW, settingsBtnH, setBorder);
        guiGraphics.drawString(this.font, "⚙", settingsBtnX + 4, settingsBtnY + 3, isSettingsHovered ? 0xFF38BDF8 : 0xFF94A3B8, false);

        if (isSettingsHovered) {
            guiGraphics.renderTooltip(this.font, Component.literal("Temporal Engine Settings"), mouseX, mouseY);
        }

        // Header Divider
        guiGraphics.fill(modalX + 12, modalY + 26, modalX + modalWidth - 12, modalY + 27, 0x22FFFFFF);

        int currentY = modalY + 32;

        // 3. Socket Row & Rune Tray (or Rune Matrix for Creative Watch)
        if (this.currentTier == WatchTier.CREATIVE) {
            int matrixStartX = modalX + 14;
            int matrixStartY = currentY;

            guiGraphics.drawString(this.font, "RUNE MATRIX", matrixStartX, matrixStartY + 2, 0xFFF1F5F9, false);

            int activeCount = AbstractWatchItem.getActiveMatrixRuneCount(this.watchStack);
            String countText = activeCount + " / 15 Active";
            int countWidth = this.font.width(countText);

            Component enableAllBtn = Component.literal("[Enable All]").withStyle(ChatFormatting.GREEN);
            Component disableAllBtn = Component.literal("[Disable All]").withStyle(ChatFormatting.RED);
            int enableW = this.font.width(enableAllBtn);
            int disableW = this.font.width(disableAllBtn);

            int rightEdge = modalX + modalWidth - 14;
            int countX = rightEdge - countWidth;
            int disableX = countX - 10 - disableW;
            int enableX = disableX - 8 - enableW;

            boolean isEnableHovered = mouseX >= enableX - 2 && mouseX <= enableX + enableW + 2
                    && mouseY >= matrixStartY && mouseY <= matrixStartY + 12;
            boolean isDisableHovered = mouseX >= disableX - 2 && mouseX <= disableX + disableW + 2
                    && mouseY >= matrixStartY && mouseY <= matrixStartY + 12;

            guiGraphics.drawString(this.font, enableAllBtn, enableX, matrixStartY + 2, isEnableHovered ? 0xFF86EFAC : 0xFF22C55E, false);
            guiGraphics.drawString(this.font, disableAllBtn, disableX, matrixStartY + 2, isDisableHovered ? 0xFFFCA5A5 : 0xFFEF4444, false);
            guiGraphics.drawString(this.font, countText, countX, matrixStartY + 2, activeCount > 0 ? 0xFF38BDF8 : 0xFF64748B, false);

            int chipSize = 20;
            int chipGap = 3;
            int chipsY1 = matrixStartY + 14;
            int chipsY2 = chipsY1 + chipSize + chipGap;

            RuneType hoveredRune = null;

            for (int i = 0; i < MATRIX_RUNES.size(); i++) {
                RuneType type = MATRIX_RUNES.get(i);
                int row = i < 8 ? 0 : 1;
                int col = i < 8 ? i : (i - 8);

                int chipX = matrixStartX + col * (chipSize + chipGap);
                int chipY = row == 0 ? chipsY1 : chipsY2;

                boolean active = AbstractWatchItem.isMatrixRuneActive(this.watchStack, type);
                boolean isHovered = mouseX >= chipX && mouseX <= chipX + chipSize
                        && mouseY >= chipY && mouseY <= chipY + chipSize;

                int chipBg = active
                        ? (isHovered ? 0x881E3A5F : 0x6616253B)
                        : (isHovered ? 0x44252D3D : 0x33171B24);
                int chipBorder = active
                        ? (isHovered ? 0xFF38BDF8 : 0xFF0284C7)
                        : (isHovered ? 0x88FFFFFF : 0x22FFFFFF);

                guiGraphics.fill(chipX, chipY, chipX + chipSize, chipY + chipSize, chipBg);
                guiGraphics.renderOutline(chipX, chipY, chipSize, chipSize, chipBorder);

                ItemStack runeStack = new ItemStack(ModItems.getRuneItem(type));
                guiGraphics.renderItem(runeStack, chipX + 2, chipY + 2);

                if (active) {
                    guiGraphics.fill(chipX + chipSize - 5, chipY + chipSize - 5, chipX + chipSize - 1, chipY + chipSize - 1, 0xFF38BDF8);
                }

                if (isHovered) {
                    hoveredRune = type;
                }
            }

            if (hoveredRune != null) {
                boolean active = AbstractWatchItem.isMatrixRuneActive(this.watchStack, hoveredRune);
                Component stateComp = active
                        ? Component.literal("[ONLINE]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : Component.literal("[OFFLINE]").withStyle(ChatFormatting.DARK_GRAY);

                Component tooltip = Component.literal(hoveredRune.getDisplayName()).withStyle(hoveredRune.getFormat(), ChatFormatting.BOLD)
                        .append(Component.literal(" "))
                        .append(stateComp)
                        .append(Component.literal("\n" + hoveredRune.getDescription()).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("\nClick to " + (active ? "Disable" : "Enable")).withStyle(ChatFormatting.DARK_AQUA));
                guiGraphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
            }

            guiGraphics.fill(modalX + 12, chipsY2 + chipSize + 6, modalX + modalWidth - 12, chipsY2 + chipSize + 7, 0x22FFFFFF);
            currentY = chipsY2 + chipSize + 12;
        } else if (this.currentTier.hasRuneSocket()) {
            int socketSlotX = modalX + 14;
            int socketSlotY = currentY;
            int slotSize = 20;

            boolean isSocketHovered = mouseX >= socketSlotX && mouseX <= socketSlotX + slotSize
                    && mouseY >= socketSlotY && mouseY <= socketSlotY + slotSize;

            int slotBg = isSocketHovered ? 0x88252D3D : 0x55171B24;
            int slotBorder = isSocketHovered ? theme.accent : 0x25FFFFFF;
            guiGraphics.fill(socketSlotX, socketSlotY, socketSlotX + slotSize, socketSlotY + slotSize, slotBg);
            guiGraphics.renderOutline(socketSlotX, socketSlotY, slotSize, slotSize, slotBorder);

            int textX = socketSlotX + slotSize + 8;
            if (this.socketedRune.isEmpty()) {
                guiGraphics.drawString(this.font, "◇", socketSlotX + 6, socketSlotY + 6, 0xFF64748B, false);
                guiGraphics.drawString(this.font, "Empty Socket", textX, socketSlotY + 6, 0xFF94A3B8, false);
            } else {
                guiGraphics.renderItem(this.socketedRune, socketSlotX + 2, socketSlotY + 2);

                RuneType type = RuneType.BLANK;
                if (this.socketedRune.getItem() instanceof TemporalRuneItem runeItem) {
                    type = runeItem.getType();
                }

                guiGraphics.drawString(this.font, type.getDisplayName(), textX, socketSlotY + 6, 0xFFF1F5F9, false);

                Component ejectBtn = Component.literal("[Eject]").withStyle(ChatFormatting.GRAY);
                int ejectX = modalX + modalWidth - 14 - this.font.width(ejectBtn);
                boolean isEjectHovered = mouseX >= ejectX - 2 && mouseX <= ejectX + this.font.width(ejectBtn) + 2
                        && mouseY >= socketSlotY + 2 && mouseY <= socketSlotY + 16;
                int ejectColor = isEjectHovered ? 0xFFEF4444 : 0xFF64748B;
                guiGraphics.drawString(this.font, ejectBtn, ejectX, socketSlotY + 6, ejectColor, false);

                if (type == RuneType.RICOCHET) {
                    com.timestop.combat.ChainTargetFilter filter = TemporalRuneItem.getTargetFilter(this.socketedRune);
                    Component modeBadge = Component.literal("[" + filter.getDisplayName() + "]").withStyle(filter.getColor());
                    int badgeX = ejectX - this.font.width(modeBadge) - 6;
                    guiGraphics.drawString(this.font, modeBadge, badgeX, socketSlotY + 6, 0xFFFFFFFF, false);
                }

                if (isSocketHovered) {
                    guiGraphics.renderTooltip(this.font, Component.literal(type.getDisplayName() + ": " + getCleanRuneTag(type)), mouseX, mouseY);
                }
            }

            // Available Rune Tray Chips
            int trayY = currentY + 24;
            int chipStartX = modalX + 14;
            if (this.availableRunes.isEmpty()) {
                guiGraphics.drawString(this.font, "No runes in bag", chipStartX, trayY + 6, 0xFF475569, false);
            } else {
                int maxVisible = 10;
                int totalRunes = this.availableRunes.size();
                int maxOffset = Math.max(0, totalRunes - maxVisible);
                this.trayScrollOffset = Math.max(0, Math.min(maxOffset, this.trayScrollOffset));

                int visibleCount = Math.min(totalRunes - this.trayScrollOffset, maxVisible);
                for (int i = 0; i < visibleCount; i++) {
                    int runeIndex = this.trayScrollOffset + i;
                    if (runeIndex >= this.availableRunes.size()) break;

                    InventoryRuneEntry entry = this.availableRunes.get(runeIndex);
                    int chipX = chipStartX + (i * 24);
                    int chipY = trayY;
                    int chipSize = 20;

                    boolean isChipHovered = mouseX >= chipX && mouseX <= chipX + chipSize
                            && mouseY >= chipY && mouseY <= chipY + chipSize;

                    int chipBg = isChipHovered ? 0x88252D3D : 0x44171B24;
                    int chipBorder = isChipHovered ? theme.accent : 0x20FFFFFF;

                    guiGraphics.fill(chipX, chipY, chipX + chipSize, chipY + chipSize, chipBg);
                    guiGraphics.renderOutline(chipX, chipY, chipSize, chipSize, chipBorder);
                    guiGraphics.renderItem(entry.stack, chipX + 2, chipY + 2);
                    guiGraphics.renderItemDecorations(this.font, entry.stack, chipX + 2, chipY + 2);

                    if (isChipHovered) {
                        guiGraphics.renderTooltip(this.font, Component.literal("Socket " + entry.type.getDisplayName()), mouseX, mouseY);
                    }
                }
            }

            // Divider before mode cards
            guiGraphics.fill(modalX + 12, currentY + 48, modalX + modalWidth - 12, currentY + 49, 0x22FFFFFF);
            currentY += 54;
        }

        // 4. Mode Cards Grid
        List<TimeMode> displayModes = getDisplayModes();
        TimeMode hoveredMode = null;
        Component hoveredChipTooltip = null;

        if (this.currentTier == WatchTier.COPPER) {
            int cardW = modalWidth - 24;
            int cardH = 32;
            int gapY = 8;

            for (int i = 0; i < displayModes.size(); i++) {
                TimeMode mode = displayModes.get(i);
                int cy = currentY + i * (cardH + gapY);
                boolean isHovered = mouseX >= modalX + 12 && mouseX <= modalX + 12 + cardW && mouseY >= cy && mouseY <= cy + cardH;
                boolean isSelected = (mode == this.currentMode);

                renderModeCard(guiGraphics, theme, modalX + 12, cy, cardW, cardH, mode, i + 1, isSelected, isHovered);
                if (isHovered) hoveredMode = mode;
            }
        } else {
            int cardW = (modalWidth - 32) / 2;
            int cardH = 28;
            int gapX = 8;
            int gapY = 6;

            for (int i = 0; i < displayModes.size(); i++) {
                TimeMode mode = displayModes.get(i);
                int col = i % 2;
                int row = i / 2;

                int cx = modalX + 12 + col * (cardW + gapX);
                int cy = currentY + row * (cardH + gapY);

                boolean isHovered = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH;
                boolean isSelected = (mode == this.currentMode);

                renderModeCard(guiGraphics, theme, cx, cy, cardW, cardH, mode, i + 1, isSelected, isHovered);

                if (this.currentTier == WatchTier.CREATIVE && mode == TimeMode.REWIND) {
                    boolean exemptAllowed = com.timestop.core.TimeStopManager.isRewindExemptAllowed();
                    boolean exempt = AbstractWatchItem.isRewindInitiatorExempt(this.watchStack);
                    String chipText = !exemptAllowed ? "[Self: LOCKED]" : (exempt ? "[Self: OFF]" : "[Self: ON]");
                    int chipW = this.font.width(chipText) + 8;
                    int chipH = 14;
                    int chipX = isSelected ? (cx + cardW - chipW - 16) : (cx + cardW - chipW - 6);
                    int chipY = cy + (cardH - chipH) / 2;

                    boolean isChipHovered = mouseX >= chipX && mouseX <= chipX + chipW && mouseY >= chipY && mouseY <= chipY + chipH;
                    int chipBg = isChipHovered ? 0x88252D3D : 0x55171B24;
                    int chipBorder = !exemptAllowed ? 0x44EF4444 : (isChipHovered ? 0xFFFFFFFF : (exempt ? 0xFFF59E0B : 0xFF22C55E));
                    int textColor = !exemptAllowed ? 0xFF94A3B8 : (exempt ? 0xFFFBBF24 : 0xFF4ADE80);

                    guiGraphics.fill(chipX, chipY, chipX + chipW, chipY + chipH, chipBg);
                    guiGraphics.renderOutline(chipX, chipY, chipW, chipH, chipBorder);
                    guiGraphics.drawString(this.font, chipText, chipX + 4, chipY + 3, textColor, false);

                    if (isChipHovered) {
                        if (!exemptAllowed) {
                            hoveredChipTooltip = Component.literal("Rewind Self: DISABLED BY SERVER").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                    .append(Component.literal("\nServer admin policy disabled initiator exemption.")
                                            .append("\nUse §f/timestop rewind allowexempt true§7 to enable.")
                                            .withStyle(ChatFormatting.GRAY));
                        } else {
                            hoveredChipTooltip = Component.literal("Rewind Self: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                    .append(exempt
                                            ? Component.literal("OFF (Exempt)").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                                            : Component.literal("ON (Included)").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                                    .append(Component.literal(exempt
                                            ? "\nWorld and entities rewind backward in time, but you retain your position, inventory, and health."
                                            : "\nYou rewind backward in time along with the rest of the world.")
                                            .withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal("\nClick to switch to: " + (exempt ? "ON (Included)" : "OFF (Exempt)")).withStyle(ChatFormatting.DARK_AQUA));
                        }
                    }
                }

                if (isHovered && hoveredChipTooltip == null) hoveredMode = mode;
            }
        }

        // 5. Minimalist Footer
        int footerY = modalY + modalHeight - 13;
        guiGraphics.drawCenteredString(this.font, "ESC to close", modalX + modalWidth / 2, footerY, 0xFF64748B);

        // 6. Tooltip for hovered mode or chip
        if (hoveredChipTooltip != null) {
            guiGraphics.renderTooltip(this.font, hoveredChipTooltip, mouseX, mouseY);
        } else if (hoveredMode != null) {
            guiGraphics.renderTooltip(this.font, Component.literal(getCleanDescription(hoveredMode)), mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderModeCard(GuiGraphics guiGraphics, TierTheme theme, int x, int y, int w, int h,
                                TimeMode mode, int hotkeyNum, boolean isSelected, boolean isHovered) {
        int bg = isSelected ? theme.cardActive : (isHovered ? theme.cardHover : theme.cardBg);
        int border = isSelected ? theme.accent : (isHovered ? 0x66FFFFFF : theme.cardBorder);

        guiGraphics.fill(x, y, x + w, y + h, bg);
        guiGraphics.renderOutline(x, y, w, h, border);

        if (isSelected) {
            guiGraphics.fill(x, y, x + 3, y + h, theme.accent);
        }

        int textX = x + (isSelected ? 9 : 8);
        int textY = y + (h - 8) / 2;

        String keyText = "[" + hotkeyNum + "] ";
        int keyW = this.font.width(keyText);
        guiGraphics.drawString(this.font, keyText, textX, textY, 0xFF64748B, false);

        int nameColor = isSelected ? theme.accent : (isHovered ? 0xFFFFFFFF : 0xFFE2E8F0);
        guiGraphics.drawString(this.font, mode.getDisplayName(), textX + keyW, textY, nameColor, false);

        if (isSelected) {
            guiGraphics.drawString(this.font, "●", x + w - 12, textY, theme.accent, false);
        }
    }

    private String getStatBadge() {
        return switch (this.currentTier) {
            case COPPER -> "6s • 25s CD";
            case GILDED -> "10s • 18s CD";
            case DIAMOND -> "14s • 12s CD";
            case NETHERITE -> "20s • 6s CD";
            case CREATIVE -> "Infinite";
        };
    }

    private static String getCleanDescription(TimeMode mode) {
        return switch (mode) {
            case TIME_STOP -> "Freezes all entity movement and physical interactions.";
            case SLOW_MOTION -> "Slows world entities to 25% speed while you remain accelerated.";
            case MATRIX -> "Grants hyper-speed player reflexes and attack speed.";
            case SUPERHOT -> "Time moves only when you physically move or attack.";
            case DECELERATION_FIELD -> "Creates an 80% projectile slowing field around you.";
            case FAST_FORWARD -> "Accelerates world time and crop growth by 500%.";
            case REWIND -> "Rewinds blocks, entities, and player state backwards in time.";
        };
    }

    private static String getCleanRuneTag(RuneType type) {
        return switch (type) {
            case VECTOR -> "Direct struck shots and barrier volleys where you look";
            case DEFLECTION -> "Auto-parries incoming projectiles";
            case SNATCHING -> "Auto-collects incoming projectiles into inventory";
            case PHASING -> "Auto-teleports away on imminent hit";
            case KINETIC -> "2.5x kinetic launch force on hits";
            case VAMPIRISM -> "Siphons time up to double cap";
            case VOLATILE -> "Delayed kinetic concussion bombs";
            case TACHYON -> "Flurry attacks in Slow-Mo & Matrix";
            case DEAD_EYE -> "RDR2 Dead Eye: paints targets for volley";
            case RICOCHET -> "Chain Lightning ricochets between mobs";
            case ORBITAL -> "Catches and returns enemy projectiles";
            case TRANSPOSITION -> "Boogie Woogie: swap position with clap (G)";
            default -> "Empty socket";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            int modalWidth = getModalWidth();
            int modalHeight = getModalHeight();
            int modalX = (this.width - modalWidth) / 2;
            int modalY = (this.height - modalHeight) / 2;
            int currentY = modalY + 32;

            // Settings Button Click
            String statBadge = getStatBadge();
            int statWidth = this.font.width(statBadge);
            int settingsBtnW = 16;
            int settingsBtnH = 14;
            int extraRightOffset = (this.currentTier == WatchTier.CREATIVE) ? 115 : 0;
            int settingsBtnX = modalX + modalWidth - 14 - statWidth - extraRightOffset - settingsBtnW - 4;
            int settingsBtnY = modalY + 10 - 2;

            if (mouseX >= settingsBtnX && mouseX <= settingsBtnX + settingsBtnW && mouseY >= settingsBtnY && mouseY <= settingsBtnY + settingsBtnH) {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new com.timestop.client.gui.TimeStopSettingsScreen(this, this.hand));
                    if (this.minecraft.player != null) {
                        this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.2F);
                    }
                }
                return true;
            }

            // 0. Check Creative Watch Scope Button Click
            if (this.currentTier == WatchTier.CREATIVE) {
                int scopeBtnW = 105;
                int scopeBtnH = 14;
                int scopeBtnX = modalX + modalWidth - 14 - statWidth - scopeBtnW - 8;
                int scopeBtnY = modalY + 10 - 2;

                if (mouseX >= scopeBtnX && mouseX <= scopeBtnX + scopeBtnW && mouseY >= scopeBtnY && mouseY <= scopeBtnY + scopeBtnH) {
                    boolean newScope = !AbstractWatchItem.isGlobalScope(this.watchStack);
                    AbstractWatchItem.setGlobalScope(this.watchStack, newScope);
                    ModMessages.sendToServer(new com.timestop.network.SetWatchScopePacket(this.hand, newScope));
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, newScope ? 1.4F : 1.0F);
                    }
                    return true;
                }
            }

            if (this.currentTier == WatchTier.CREATIVE) {
                int matrixStartX = modalX + 14;
                int matrixStartY = currentY;

                Component enableAllBtn = Component.literal("[Enable All]");
                Component disableAllBtn = Component.literal("[Disable All]");
                int enableW = this.font.width(enableAllBtn);
                int disableW = this.font.width(disableAllBtn);

                int activeCount = AbstractWatchItem.getActiveMatrixRuneCount(this.watchStack);
                String countText = activeCount + " / 15 Active";
                int countWidth = this.font.width(countText);

                int rightEdge = modalX + modalWidth - 14;
                int countX = rightEdge - countWidth;
                int disableX = countX - 10 - disableW;
                int enableX = disableX - 8 - enableW;

                if (mouseY >= matrixStartY && mouseY <= matrixStartY + 12) {
                    if (mouseX >= enableX - 4 && mouseX <= enableX + enableW + 4) {
                        AbstractWatchItem.setAllMatrixRunes(this.watchStack, true);
                        ModMessages.sendToServer(new ToggleMatrixRunePacket(this.hand, null, true, true));
                        if (this.minecraft != null && this.minecraft.player != null) {
                            this.minecraft.player.playSound(SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0F, 1.2F);
                        }
                        return true;
                    }
                    if (mouseX >= disableX - 4 && mouseX <= disableX + disableW + 4) {
                        AbstractWatchItem.setAllMatrixRunes(this.watchStack, false);
                        ModMessages.sendToServer(new ToggleMatrixRunePacket(this.hand, null, false, true));
                        if (this.minecraft != null && this.minecraft.player != null) {
                            this.minecraft.player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 1.0F, 0.8F);
                        }
                        return true;
                    }
                }

                int chipSize = 20;
                int chipGap = 3;
                int chipsY1 = matrixStartY + 14;
                int chipsY2 = chipsY1 + chipSize + chipGap;

                for (int i = 0; i < MATRIX_RUNES.size(); i++) {
                    RuneType type = MATRIX_RUNES.get(i);
                    int row = i < 8 ? 0 : 1;
                    int col = i < 8 ? i : (i - 8);

                    int chipX = matrixStartX + col * (chipSize + chipGap);
                    int chipY = row == 0 ? chipsY1 : chipsY2;

                    if (mouseX >= chipX && mouseX <= chipX + chipSize && mouseY >= chipY && mouseY <= chipY + chipSize) {
                        boolean currentState = AbstractWatchItem.isMatrixRuneActive(this.watchStack, type);
                        boolean newState = !currentState;
                        AbstractWatchItem.setMatrixRune(this.watchStack, type, newState);
                        ModMessages.sendToServer(new ToggleMatrixRunePacket(this.hand, type, newState, false));
                        if (this.minecraft != null && this.minecraft.player != null) {
                            this.minecraft.player.playSound(newState ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.ARMOR_EQUIP_GENERIC, 1.0F, newState ? 1.2F : 0.8F);
                        }
                        return true;
                    }
                }

                currentY = chipsY2 + chipSize + 12;
            } else if (this.currentTier.hasRuneSocket()) {
                int socketSlotX = modalX + 14;
                int socketSlotY = currentY;
                int slotSize = 20;

                // 1. Check Eject or Socket Click
                if (!this.socketedRune.isEmpty()) {
                    Component ejectBtn = Component.literal("[Eject]");
                    int ejectX = modalX + modalWidth - 14 - this.font.width(ejectBtn);
                    if ((mouseX >= socketSlotX && mouseX <= socketSlotX + slotSize && mouseY >= socketSlotY && mouseY <= socketSlotY + slotSize)
                            || (mouseX >= ejectX - 4 && mouseX <= ejectX + this.font.width(ejectBtn) + 4 && mouseY >= socketSlotY && mouseY <= socketSlotY + slotSize)) {
                        ejectRune();
                        return true;
                    }

                    // Check Ricochet filter toggle
                    if (this.socketedRune.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() == RuneType.RICOCHET) {
                        com.timestop.combat.ChainTargetFilter filter = TemporalRuneItem.getTargetFilter(this.socketedRune);
                        Component modeBadge = Component.literal("[" + filter.getDisplayName() + "]");
                        int badgeX = ejectX - this.font.width(modeBadge) - 6;
                        if (mouseX >= badgeX - 2 && mouseX <= badgeX + this.font.width(modeBadge) + 2 && mouseY >= socketSlotY && mouseY <= socketSlotY + slotSize) {
                            com.timestop.combat.ChainTargetFilter next = filter.next();
                            TemporalRuneItem.setTargetFilter(this.socketedRune, next);
                            ModMessages.sendToServer(new com.timestop.network.CycleRuneModePacket());
                            if (this.minecraft != null && this.minecraft.player != null) {
                                this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.8F);
                            }
                            return true;
                        }
                    }
                }

                // 2. Check Rune Tray Click
                int trayY = currentY + 24;
                int chipStartX = modalX + 14;
                int maxVisible = 10;
                int totalRunes = this.availableRunes.size();
                int visibleCount = Math.min(totalRunes - this.trayScrollOffset, maxVisible);

                for (int i = 0; i < visibleCount; i++) {
                    int runeIndex = this.trayScrollOffset + i;
                    if (runeIndex >= this.availableRunes.size()) break;

                    InventoryRuneEntry entry = this.availableRunes.get(runeIndex);
                    int chipX = chipStartX + (i * 24);
                    int chipY = trayY;
                    int chipSize = 20;

                    if (mouseX >= chipX && mouseX <= chipX + chipSize && mouseY >= chipY && mouseY <= chipY + chipSize) {
                        socketSpecificRune(entry);
                        return true;
                    }
                }

                currentY += 54;
            }

            // 3. Check Mode Cards Click
            List<TimeMode> displayModes = getDisplayModes();

            if (this.currentTier == WatchTier.COPPER) {
                int cardW = modalWidth - 24;
                int cardH = 32;
                int gapY = 8;

                for (int i = 0; i < displayModes.size(); i++) {
                    int cy = currentY + i * (cardH + gapY);
                    if (mouseX >= modalX + 12 && mouseX <= modalX + 12 + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                        selectMode(displayModes.get(i));
                        return true;
                    }
                }
            } else {
                int cardW = (modalWidth - 32) / 2;
                int cardH = 28;
                int gapX = 8;
                int gapY = 6;

                for (int i = 0; i < displayModes.size(); i++) {
                    int col = i % 2;
                    int row = i / 2;
                    int cx = modalX + 12 + col * (cardW + gapX);
                    int cy = currentY + row * (cardH + gapY);
                    TimeMode mode = displayModes.get(i);

                    if (this.currentTier == WatchTier.CREATIVE && mode == TimeMode.REWIND) {
                        boolean isSelected = (mode == this.currentMode);
                        boolean exemptAllowed = com.timestop.core.TimeStopManager.isRewindExemptAllowed();
                        boolean exempt = AbstractWatchItem.isRewindInitiatorExempt(this.watchStack);
                        String chipText = !exemptAllowed ? "[Self: LOCKED]" : (exempt ? "[Self: OFF]" : "[Self: ON]");
                        int chipW = this.font.width(chipText) + 8;
                        int chipH = 14;
                        int chipX = isSelected ? (cx + cardW - chipW - 16) : (cx + cardW - chipW - 6);
                        int chipY = cy + (cardH - chipH) / 2;

                        if (mouseX >= chipX && mouseX <= chipX + chipW && mouseY >= chipY && mouseY <= chipY + chipH) {
                            if (exemptAllowed) {
                                boolean newExempt = !exempt;
                                AbstractWatchItem.setRewindInitiatorExempt(this.watchStack, newExempt);
                                ModMessages.sendToServer(new SetRewindExemptPacket(this.hand, newExempt));
                                if (this.minecraft != null && this.minecraft.player != null) {
                                    this.minecraft.player.playSound(newExempt ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.ARMOR_EQUIP_GENERIC, 1.0F, newExempt ? 1.2F : 0.8F);
                                }
                            } else {
                                if (this.minecraft != null && this.minecraft.player != null) {
                                    this.minecraft.player.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
                                }
                            }
                            return true;
                        }
                    }

                    if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                        selectMode(displayModes.get(i));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectMode(TimeMode mode) {
        ModMessages.sendToServer(new SelectTimeModePacket(mode, this.hand));
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack stack = player.getItemInHand(this.hand);
            AbstractWatchItem.setMode(stack, mode);
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.3F));
        this.onClose();
    }

    private void ejectRune() {
        if (runeRequestPending || socketedRune.isEmpty()) return;
        runeRequestPending = true;
        ModMessages.sendToServer(new SocketSpecificRunePacket(this.hand, -1, RuneType.BLANK));
    }

    private void socketSpecificRune(InventoryRuneEntry entry) {
        if (runeRequestPending) return;
        runeRequestPending = true;
        ModMessages.sendToServer(new SocketSpecificRunePacket(this.hand, entry.slotIndex, entry.type));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        List<TimeMode> displayModes = getDisplayModes();
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < displayModes.size()) {
                selectMode(displayModes.get(index));
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.availableRunes.size() > 10) {
            if (delta > 0) {
                this.trayScrollOffset = Math.max(0, this.trayScrollOffset - 1);
            } else if (delta < 0) {
                this.trayScrollOffset = Math.min(Math.max(0, this.availableRunes.size() - 10), this.trayScrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
