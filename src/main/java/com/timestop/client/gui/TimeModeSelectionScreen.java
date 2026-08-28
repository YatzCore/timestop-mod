package com.timestop.client.gui;

import com.timestop.core.TimeMode;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import com.timestop.network.ModMessages;
import com.timestop.network.SelectTimeModePacket;
import com.timestop.network.SocketSpecificRunePacket;
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
 * Modern, minimalist Obsidian & Slate console for temporal mode & rune socket management.
 * Features an interactive Rune Tray for picking and socketing exact runes from inventory.
 */
public class TimeModeSelectionScreen extends Screen {
    private final InteractionHand hand;
    private TimeMode currentMode = TimeMode.TIME_STOP;
    private WatchTier currentTier = WatchTier.CREATIVE;
    private ItemStack socketedRune = ItemStack.EMPTY;

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

    // Palette constants
    private static final int MODAL_BG = 0xF20F1218;       // Deep matte obsidian
    private static final int MODAL_BORDER = 0xFF272D3B;   // Clean 1px slate rim
    private static final int DIVIDER_COLOR = 0xFF1E2430;   // Subtle separator
    private static final int CARD_RESTING = 0x55171B24;   // Translucent slate card
    private static final int CARD_HOVER = 0x88252D3D;     // Subtle hover lift
    private static final int CARD_ACTIVE = 0x881A2A40;    // Deep active card
    private static final int CARD_LOCKED = 0x33101216;    // Dimmed locked card
    private static final int ACCENT_CYAN = 0xFF38BDF8;    // Clean temporal cyan accent
    private static final int ACCENT_GOLD = 0xFFF59E0B;    // Rune socket accent
    private static final int TEXT_PRIMARY = 0xFFF1F5F9;   // Crisp white/silver
    private static final int TEXT_MUTED = 0xFF94A3B8;     // Soft secondary slate
    private static final int TEXT_DIM = 0xFF64748B;       // Tertiary / hotkey gray
    private static final int TEXT_LOCKED = 0xFFF87171;    // Muted coral for lock requirements

    public TimeModeSelectionScreen(InteractionHand hand) {
        super(Component.literal("Chronos Frequency Selector"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        super.init();
        refreshState();
    }

    private void refreshState() {
        Player player = Minecraft.getInstance().player;
        this.availableRunes.clear();

        if (player != null) {
            ItemStack stack = player.getItemInHand(this.hand);
            this.currentMode = AbstractWatchItem.getMode(stack);
            this.socketedRune = AbstractWatchItem.getSocketedRune(stack);
            if (stack.getItem() instanceof AbstractWatchItem watch) {
                this.currentTier = watch.getTier();
            }

            // Scan inventory for tactical runes
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack invStack = player.getInventory().getItem(i);
                if (invStack.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() != RuneType.BLANK) {
                    this.availableRunes.add(new InventoryRuneEntry(i, invStack, runeItem.getType()));
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int modalWidth = 350;
        int modalHeight = 268;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        // 1. Modal Background & Outer Rim
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, MODAL_BG);
        guiGraphics.renderOutline(modalX, modalY, modalWidth, modalHeight, MODAL_BORDER);

        // 2. Header Area
        int headerY = modalY + 10;
        Component title = Component.literal("CHRONOS CONTROL").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(this.font, title, modalX + 16, headerY, TEXT_PRIMARY, false);

        String tierBadge = this.currentTier.getDisplayName().toUpperCase();
        int tierWidth = this.font.width(tierBadge);
        guiGraphics.drawString(this.font, tierBadge, modalX + modalWidth - 16 - tierWidth, headerY, TEXT_MUTED, false);

        // Header Divider Line
        guiGraphics.fill(modalX + 16, modalY + 23, modalX + modalWidth - 16, modalY + 24, DIVIDER_COLOR);

        // 3. Socket Status Row
        int runeRowY = modalY + 28;
        int runeSlotX = modalX + 16;
        int runeSlotY = runeRowY;
        int runeSlotSize = 22;

        boolean isSocketHovered = mouseX >= runeSlotX && mouseX <= runeSlotX + runeSlotSize
                && mouseY >= runeSlotY && mouseY <= runeSlotY + runeSlotSize;

        int slotBg = isSocketHovered ? 0x88252D3D : 0x55171B24;
        int slotBorder = isSocketHovered ? (this.currentTier.hasRuneSocket() ? ACCENT_GOLD : 0x44EF4444) : 0x22FFFFFF;
        guiGraphics.fill(runeSlotX, runeSlotY, runeSlotX + runeSlotSize, runeSlotY + runeSlotSize, slotBg);
        guiGraphics.renderOutline(runeSlotX, runeSlotY, runeSlotSize, runeSlotSize, slotBorder);

        int runeTextX = runeSlotX + runeSlotSize + 8;
        if (!this.currentTier.hasRuneSocket()) {
            guiGraphics.drawString(this.font, "—", runeSlotX + 8, runeSlotY + 7, 0xFF475569, false);
            guiGraphics.drawString(this.font, "Rune Socket: Locked", runeTextX, runeSlotY + 2, 0xFF64748B, false);
            guiGraphics.drawString(this.font, "Requires Gilded Watch (Tier 2+)", runeTextX, runeSlotY + 12, 0xFF475569, false);
        } else if (this.socketedRune.isEmpty()) {
            guiGraphics.drawString(this.font, "◇", runeSlotX + 7, runeSlotY + 7, TEXT_DIM, false);
            guiGraphics.drawString(this.font, "Rune Socket: Empty", runeTextX, runeSlotY + 2, TEXT_MUTED, false);
            guiGraphics.drawString(this.font, "Select a rune below to socket", runeTextX, runeSlotY + 12, TEXT_DIM, false);
        } else {
            guiGraphics.renderItem(this.socketedRune, runeSlotX + 3, runeSlotY + 3);

            RuneType type = RuneType.BLANK;
            if (this.socketedRune.getItem() instanceof TemporalRuneItem runeItem) {
                type = runeItem.getType();
            }

            int nameColor = switch (type) {
                case DEFLECTION -> 0xFF38BDF8;
                case SNATCHING -> 0xFFF59E0B;
                case PHASING -> 0xFFC084FC;
                case KINETIC -> 0xFFF97316;
                case VAMPIRISM -> 0xFFEF4444;
                case VOLATILE -> 0xFFEAB308;
                case TACHYON -> 0xFF06B6D4;
                case DEAD_EYE -> 0xFFFF2A2A;
                case RICOCHET -> 0xFFFACC15;
                case ORBITAL -> 0xFF38BDF8;
                case TRANSPOSITION -> 0xFFC084FC;
                default -> 0xFFE2E8F0;
            };

            guiGraphics.drawString(this.font, type.getDisplayName(), runeTextX, runeSlotY + 2, nameColor, false);
            guiGraphics.drawString(this.font, getCleanRuneTag(type), runeTextX, runeSlotY + 12, TEXT_MUTED, false);

            Component ejectHint = Component.literal("[Click to Eject]").withStyle(ChatFormatting.DARK_GRAY);
            int ejectX = modalX + modalWidth - 16 - this.font.width(ejectHint);
            guiGraphics.drawString(this.font, ejectHint, ejectX, runeSlotY + 7, 0xFF64748B, false);

            if (type == RuneType.RICOCHET) {
                com.timestop.combat.ChainTargetFilter filter = com.timestop.item.rune.TemporalRuneItem.getTargetFilter(this.socketedRune);
                Component modeBadge = Component.literal("[Mode: " + filter.getDisplayName() + "]").withStyle(filter.getColor(), ChatFormatting.BOLD);
                int badgeW = this.font.width(modeBadge);
                int badgeX = ejectX - badgeW - 8;
                guiGraphics.drawString(this.font, modeBadge, badgeX, runeSlotY + 7, 0xFFFFFFFF, false);
            }
        }

        // 4. Rune Tray (Inventory Runes)
        int trayY = modalY + 54;
        guiGraphics.drawString(this.font, "INVENTORY RUNES:", modalX + 16, trayY + 6, TEXT_DIM, false);

        int trayStartX = modalX + 115;
        if (this.currentTier.hasRuneSocket()) {
            if (this.availableRunes.isEmpty()) {
                guiGraphics.drawString(this.font, "None found in bag", trayStartX, trayY + 6, 0xFF475569, false);
            } else {
                for (int i = 0; i < this.availableRunes.size(); i++) {
                    InventoryRuneEntry entry = this.availableRunes.get(i);
                    int chipX = trayStartX + (i * 26);
                    int chipY = trayY;
                    int chipSize = 22;

                    boolean isChipHovered = mouseX >= chipX && mouseX <= chipX + chipSize
                            && mouseY >= chipY && mouseY <= chipY + chipSize;

                    int chipBg = isChipHovered ? 0x88252D3D : 0x55171B24;
                    int chipBorder = isChipHovered ? ACCENT_CYAN : 0x22FFFFFF;

                    guiGraphics.fill(chipX, chipY, chipX + chipSize, chipY + chipSize, chipBg);
                    guiGraphics.renderOutline(chipX, chipY, chipSize, chipSize, chipBorder);
                    guiGraphics.renderItem(entry.stack, chipX + 3, chipY + 3);
                    guiGraphics.renderItemDecorations(this.font, entry.stack, chipX + 3, chipY + 3);

                    if (isChipHovered) {
                        guiGraphics.renderTooltip(this.font, Component.literal("Click to socket ").append(entry.type.getFormattedComponent()), mouseX, mouseY);
                    }
                }
            }
        }

        // Section Divider Line
        guiGraphics.fill(modalX + 16, modalY + 80, modalX + modalWidth - 16, modalY + 81, DIVIDER_COLOR);

        // 5. Grid of Mode Cards (2 Columns x 3 Rows)
        TimeMode[] modes = TimeMode.values();
        int cardWidth = 151;
        int cardHeight = 42;
        int gapX = 16;
        int gapY = 8;
        int gridX = modalX + 16;
        int gridY = modalY + 88;

        for (int i = 0; i < modes.length; i++) {
            TimeMode mode = modes[i];
            int col = i % 2;
            int row = i / 2;

            int cx = gridX + col * (cardWidth + gapX);
            int cy = gridY + row * (cardHeight + gapY);

            boolean isHovered = mouseX >= cx && mouseX <= cx + cardWidth && mouseY >= cy && mouseY <= cy + cardHeight;
            boolean isSelected = (mode == this.currentMode);
            boolean isUnlocked = this.currentTier.isModeUnlocked(mode);

            int bg;
            int border;

            if (!isUnlocked) {
                bg = isHovered ? 0x551C1315 : CARD_LOCKED;
                border = isHovered ? 0x44EF4444 : 0x1AFFFFFF;
            } else if (isSelected) {
                bg = CARD_ACTIVE;
                border = ACCENT_CYAN;
            } else if (isHovered) {
                bg = CARD_HOVER;
                border = 0x66FFFFFF;
            } else {
                bg = CARD_RESTING;
                border = 0x1FFFFFFF;
            }

            guiGraphics.fill(cx, cy, cx + cardWidth, cy + cardHeight, bg);
            guiGraphics.renderOutline(cx, cy, cardWidth, cardHeight, border);

            if (isUnlocked && isSelected) {
                guiGraphics.fill(cx, cy, cx + 3, cy + cardHeight, ACCENT_CYAN);
            }

            int textX = cx + (isSelected ? 9 : 8);

            if (isUnlocked) {
                String hotkey = "[" + (i + 1) + "] ";
                int hotkeyWidth = this.font.width(hotkey);
                guiGraphics.drawString(this.font, hotkey, textX, cy + 7, TEXT_DIM, false);

                int nameColor = isSelected ? ACCENT_CYAN : (isHovered ? 0xFFFFFF : TEXT_PRIMARY);
                guiGraphics.drawString(this.font, mode.getDisplayName(), textX + hotkeyWidth, cy + 7, nameColor, false);

                String tag = getCleanTag(mode);
                guiGraphics.drawString(this.font, tag, textX, cy + 22, TEXT_MUTED, false);

                if (isSelected) {
                    guiGraphics.drawString(this.font, "●", cx + cardWidth - 14, cy + 7, ACCENT_CYAN, false);
                }
            } else {
                WatchTier required = WatchTier.getMinimumTierFor(mode);

                String hotkey = "[" + (i + 1) + "] ";
                int hotkeyWidth = this.font.width(hotkey);
                guiGraphics.drawString(this.font, hotkey, textX, cy + 7, 0xFF475569, false);
                guiGraphics.drawString(this.font, mode.getDisplayName(), textX + hotkeyWidth, cy + 7, 0xFF64748B, false);

                String lockHint = "Req: " + required.getDisplayName();
                guiGraphics.drawString(this.font, lockHint, textX, cy + 22, TEXT_LOCKED, false);
            }
        }

        // 6. Footer Hint
        int footerY = modalY + modalHeight - 14;
        Component hint = Component.literal("Select Mode: 1–6   •   Socket/Eject: Click Rune   •   Close: ESC").withStyle(ChatFormatting.GRAY);
        guiGraphics.drawCenteredString(this.font, hint, modalX + modalWidth / 2, footerY, TEXT_DIM);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static String getCleanTag(TimeMode mode) {
        return switch (mode) {
            case TIME_STOP -> "Complete stasis freeze";
            case SLOW_MOTION -> "World runs at 25% speed";
            case MATRIX -> "Hyper-speed player motion";
            case SUPERHOT -> "Time moves when you move";
            case DECELERATION_FIELD -> "Projectiles slow by 80%";
            case FAST_FORWARD -> "5x world acceleration";
        };
    }

    private static String getCleanRuneTag(RuneType type) {
        return switch (type) {
            case DEFLECTION -> "Auto-parries incoming projectiles";
            case SNATCHING -> "Auto-collects incoming projectiles";
            case PHASING -> "Auto-teleports away on imminent hit";
            case KINETIC -> "2.5x kinetic launch force on hits";
            case VAMPIRISM -> "Siphons time up to double cap";
            case VOLATILE -> "Delayed kinetic concussion bombs";
            case TACHYON -> "3x mining & flurry in Slow-Mo/Matrix";
            case DEAD_EYE -> "RDR2 Dead Eye: paints targets for volley";
            case RICOCHET -> "Chain Lightning: ricochets between mobs";
            case ORBITAL -> "Orbital Stasis: catches & returns projectiles";
            case TRANSPOSITION -> "Boogie Woogie: swaps position with clap (G)";
            default -> "Empty slot";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            int modalWidth = 350;
            int modalHeight = 268;
            int modalX = (this.width - modalWidth) / 2;
            int modalY = (this.height - modalHeight) / 2;

            // 1. Check Socket Eject Click & Mode Toggle Click
            int runeSlotX = modalX + 16;
            int runeSlotY = modalY + 28;
            int runeSlotSize = 22;
            if (mouseX >= runeSlotX && mouseX <= runeSlotX + runeSlotSize && mouseY >= runeSlotY && mouseY <= runeSlotY + runeSlotSize) {
                if (!this.socketedRune.isEmpty()) {
                    ejectRune();
                    return true;
                }
            }

            // Check Mode Badge click for RICOCHET rune
            if (!this.socketedRune.isEmpty() && this.socketedRune.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() == RuneType.RICOCHET) {
                com.timestop.combat.ChainTargetFilter filter = TemporalRuneItem.getTargetFilter(this.socketedRune);
                Component modeBadge = Component.literal("[Mode: " + filter.getDisplayName() + "]");
                int badgeW = this.font.width(modeBadge);
                int ejectW = this.font.width("[Click to Eject]");
                int badgeX = modalX + modalWidth - 16 - ejectW - badgeW - 8;
                if (mouseX >= badgeX - 2 && mouseX <= badgeX + badgeW + 2 && mouseY >= runeSlotY + 3 && mouseY <= runeSlotY + 17) {
                    com.timestop.combat.ChainTargetFilter next = filter.next();
                    TemporalRuneItem.setTargetFilter(this.socketedRune, next);
                    com.timestop.network.ModMessages.sendToServer(new com.timestop.network.CycleRuneModePacket());
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.8F);
                    }
                    return true;
                }
            }

            // 2. Check Rune Tray Click
            int trayY = modalY + 54;
            int trayStartX = modalX + 115;
            for (int i = 0; i < this.availableRunes.size(); i++) {
                InventoryRuneEntry entry = this.availableRunes.get(i);
                int chipX = trayStartX + (i * 26);
                int chipY = trayY;
                int chipSize = 22;

                if (mouseX >= chipX && mouseX <= chipX + chipSize && mouseY >= chipY && mouseY <= chipY + chipSize) {
                    socketSpecificRune(entry);
                    return true;
                }
            }

            // 3. Check Mode Cards Click
            int cardWidth = 151;
            int cardHeight = 42;
            int gapX = 16;
            int gapY = 8;
            int gridX = modalX + 16;
            int gridY = modalY + 88;

            TimeMode[] modes = TimeMode.values();
            for (int i = 0; i < modes.length; i++) {
                int col = i % 2;
                int row = i / 2;

                int cx = gridX + col * (cardWidth + gapX);
                int cy = gridY + row * (cardHeight + gapY);

                if (mouseX >= cx && mouseX <= cx + cardWidth && mouseY >= cy && mouseY <= cy + cardHeight) {
                    handleSelectionAttempt(modes[i]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void ejectRune() {
        ModMessages.sendToServer(new SocketSpecificRunePacket(this.hand, -1));
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack watchStack = player.getItemInHand(this.hand);
            AbstractWatchItem.setSocketedRune(watchStack, ItemStack.EMPTY);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 0.8F));
        }
        refreshState();
    }

    private void socketSpecificRune(InventoryRuneEntry entry) {
        ModMessages.sendToServer(new SocketSpecificRunePacket(this.hand, entry.slotIndex));
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack watchStack = player.getItemInHand(this.hand);
            ItemStack copy = entry.stack.copy();
            copy.setCount(1);
            AbstractWatchItem.setSocketedRune(watchStack, copy);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_NETHERITE, 1.2F));
        }
        refreshState();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_6) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            TimeMode[] modes = TimeMode.values();
            if (index < modes.length) {
                handleSelectionAttempt(modes[index]);
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void handleSelectionAttempt(TimeMode mode) {
        Player player = Minecraft.getInstance().player;
        if (!this.currentTier.isModeUnlocked(mode)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 1.4F));
            if (player != null) {
                WatchTier required = WatchTier.getMinimumTierFor(mode);
                player.displayClientMessage(Component.literal("Locked: Requires " + required.getDisplayName()).withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        ModMessages.sendToServer(new SelectTimeModePacket(mode, this.hand));

        if (player != null) {
            ItemStack stack = player.getItemInHand(this.hand);
            AbstractWatchItem.setMode(stack, mode);
        }

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.3F));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
