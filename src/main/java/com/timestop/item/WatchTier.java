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
            "Copper Chronometer",
            ChatFormatting.GOLD,
            120, // 6 seconds duration
            500, // 25 seconds cooldown
            0.0, // No offhand passive
            false,
            false, // No rune socket
            Set.of(TimeMode.SLOW_MOTION, TimeMode.FAST_FORWARD)
    ),
    GILDED(
            2,
            "Gilded Chronos Watch",
            ChatFormatting.YELLOW,
            200, // 10 seconds duration
            360, // 18 seconds cooldown
            3.5, // 3.5m bullet-dodge
            true,
            true, // 1 Rune Socket
            Set.of(TimeMode.SLOW_MOTION, TimeMode.FAST_FORWARD, TimeMode.DECELERATION_FIELD, TimeMode.SUPERHOT)
    ),
    DIAMOND(
            3,
            "Diamond Chronos Watch",
            ChatFormatting.AQUA,
            280, // 14 seconds duration
            240, // 12 seconds cooldown
            4.5, // 4.5m bullet-dodge
            true,
            true, // 1 Rune Socket
            Set.of(TimeMode.SLOW_MOTION, TimeMode.FAST_FORWARD, TimeMode.DECELERATION_FIELD, TimeMode.SUPERHOT, TimeMode.MATRIX, TimeMode.TIME_STOP)
    ),
    NETHERITE(
            4,
            "Netherite Chronos Sovereign",
            ChatFormatting.DARK_PURPLE,
            400, // 20 seconds duration
            120, // 6 seconds cooldown (Rapid recharge!)
            5.5, // 5.5m bullet-dodge
            true,
            true, // 1 Rune Socket
            Set.of(TimeMode.values()) // All modes unlocked
    ),
    CREATIVE(
            5,
            "Infinite Chronos Watch",
            ChatFormatting.LIGHT_PURPLE,
            0, // Infinite duration
            0, // Zero cooldown
            6.0, // 6.0m bullet-dodge
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
    private final boolean hasOffhandPassive;
    private final boolean hasRuneSocket;
    private final Set<TimeMode> unlockedModes;

    WatchTier(int tierLevel, String displayName, ChatFormatting titleColor, int durationTicks, int cooldownTicks,
              double decelerationRadius, boolean hasOffhandPassive, boolean hasRuneSocket, Set<TimeMode> unlockedModes) {
        this.tierLevel = tierLevel;
        this.displayName = displayName;
        this.titleColor = titleColor;
        this.durationTicks = durationTicks;
        this.cooldownTicks = cooldownTicks;
        this.decelerationRadius = decelerationRadius;
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
        return durationTicks;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public double getDecelerationRadius() {
        return decelerationRadius;
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
