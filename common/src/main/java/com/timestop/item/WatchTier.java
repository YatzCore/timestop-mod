package com.timestop.item;

import com.timestop.core.TimeMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public enum WatchTier {
    COPPER(
            1,
            "Copper Watch",
            ChatFormatting.GOLD,
            120, // 6 seconds duration
            500, // 25 seconds cooldown
            0.0, // No offhand passive
            12.0, // 12m localized bubble
            0xD97706, // Amber
            false,
            false, // No rune socket
            Set.of(TimeMode.SLOW_MOTION, TimeMode.FAST_FORWARD)
    ),
    GILDED(
            2,
            "Golden Watch",
            ChatFormatting.YELLOW,
            200, // 10 seconds duration
            360, // 18 seconds cooldown
            3.5, // 3.5m bullet-dodge
            24.0, // 24m localized bubble
            0xF59E0B, // Luminous Gold
            true,
            true, // 1 Rune Socket
            Set.of(TimeMode.SLOW_MOTION, TimeMode.FAST_FORWARD, TimeMode.DECELERATION_FIELD, TimeMode.SUPERHOT)
    ),
    DIAMOND(
            3,
            "Diamond Watch",
            ChatFormatting.AQUA,
            280, // 14 seconds duration
            240, // 12 seconds cooldown
            4.5, // 4.5m bullet-dodge
            42.0, // 42m localized bubble
            0x06B6D4, // Electric Cyan
            true,
            true, // 1 Rune Socket
            Set.of(TimeMode.SLOW_MOTION, TimeMode.FAST_FORWARD, TimeMode.DECELERATION_FIELD, TimeMode.SUPERHOT, TimeMode.MATRIX, TimeMode.TIME_STOP)
    ),
    NETHERITE(
            4,
            "Netherite Watch",
            ChatFormatting.DARK_PURPLE,
            400, // 20 seconds duration
            120, // 6 seconds cooldown (Rapid recharge!)
            5.5, // 5.5m bullet-dodge
            72.0, // 72m sovereign domain
            0x8B5CF6, // Twilight Purple
            true,
            true, // 1 Rune Socket
            Set.of(TimeMode.values()) // All modes unlocked
    ),
    CREATIVE(
            5,
            "Creative Watch",
            ChatFormatting.LIGHT_PURPLE,
            0, // Infinite duration
            0, // Zero cooldown
            6.0, // 6.0m bullet-dodge
            128.0, // 128m realm-wide domain
            0xEC4899, // Cosmic Pink
            true,
            true, // 1 Rune Socket
            Set.of(TimeMode.values())
    );

    private final int tierLevel;
    private final String displayName;
    private final ChatFormatting titleColor;
    private final int durationTicks;
    private final int cooldownTicks;
    private final double decelerationRadius;
    private final double bubbleRadius;
    private final int themeColorHex;
    private final boolean hasOffhandPassive;
    private final boolean hasRuneSocket;
    private final Set<TimeMode> unlockedModes;

    WatchTier(int tierLevel, String displayName, ChatFormatting titleColor, int durationTicks, int cooldownTicks,
              double decelerationRadius, double bubbleRadius, int themeColorHex, boolean hasOffhandPassive, boolean hasRuneSocket, Set<TimeMode> unlockedModes) {
        this.tierLevel = tierLevel;
        this.displayName = displayName;
        this.titleColor = titleColor;
        this.durationTicks = durationTicks;
        this.cooldownTicks = cooldownTicks;
        this.decelerationRadius = decelerationRadius;
        this.bubbleRadius = bubbleRadius;
        this.themeColorHex = themeColorHex;
        this.hasOffhandPassive = hasOffhandPassive;
        this.hasRuneSocket = hasRuneSocket;
        this.unlockedModes = Collections.unmodifiableSet(new LinkedHashSet<>(unlockedModes));
    }

    public int getTierLevel() {
        return tierLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatFormatting getTitleColor() {
        return titleColor;
    }

    public Component getFormattedName() {
        return Component.literal(displayName).withStyle(titleColor, ChatFormatting.BOLD);
    }

    public int getDurationTicks() {
        if (com.timestop.config.TimeStopConfig.COMMON_SPEC.isLoaded()) {
            return switch (this) {
                case COPPER -> com.timestop.config.TimeStopConfig.COMMON.copperDuration.get() * 20;
                case GILDED -> com.timestop.config.TimeStopConfig.COMMON.gildedDuration.get() * 20;
                case DIAMOND -> com.timestop.config.TimeStopConfig.COMMON.diamondDuration.get() * 20;
                case NETHERITE -> com.timestop.config.TimeStopConfig.COMMON.netheriteDuration.get() * 20;
                case CREATIVE -> com.timestop.config.TimeStopConfig.COMMON.creativeDuration.get() * 20;
            };
        }
        return durationTicks;
    }

    public int getCooldownTicks() {
        if (com.timestop.config.TimeStopConfig.COMMON_SPEC.isLoaded()) {
            return switch (this) {
                case COPPER -> com.timestop.config.TimeStopConfig.COMMON.copperCooldown.get() * 20;
                case GILDED -> com.timestop.config.TimeStopConfig.COMMON.gildedCooldown.get() * 20;
                case DIAMOND -> com.timestop.config.TimeStopConfig.COMMON.diamondCooldown.get() * 20;
                case NETHERITE -> com.timestop.config.TimeStopConfig.COMMON.netheriteCooldown.get() * 20;
                case CREATIVE -> com.timestop.config.TimeStopConfig.COMMON.creativeCooldown.get() * 20;
            };
        }
        return cooldownTicks;
    }

    public double getDecelerationRadius() {
        return decelerationRadius;
    }

    public double getBubbleRadius() {
        if (com.timestop.config.TimeStopConfig.COMMON_SPEC.isLoaded()) {
            return switch (this) {
                case COPPER -> com.timestop.config.TimeStopConfig.COMMON.copperRadius.get();
                case GILDED -> com.timestop.config.TimeStopConfig.COMMON.gildedRadius.get();
                case DIAMOND -> com.timestop.config.TimeStopConfig.COMMON.diamondRadius.get();
                case NETHERITE -> com.timestop.config.TimeStopConfig.COMMON.netheriteRadius.get();
                case CREATIVE -> com.timestop.config.TimeStopConfig.COMMON.creativeRadius.get();
            };
        }
        return bubbleRadius;
    }

    public int getThemeColorHex() {
        return themeColorHex;
    }

    public boolean hasOffhandPassive() {
        return hasOffhandPassive;
    }

    public boolean hasRuneSocket() {
        return hasRuneSocket;
    }

    public Set<TimeMode> getUnlockedModes() {
        return unlockedModes;
    }

    public boolean isModeUnlocked(TimeMode mode) {
        return unlockedModes.contains(mode);
    }

    public static WatchTier getMinimumTierFor(TimeMode mode) {
        for (WatchTier tier : values()) {
            if (tier.isModeUnlocked(mode)) {
                return tier;
            }
        }
        return NETHERITE;
    }
}
