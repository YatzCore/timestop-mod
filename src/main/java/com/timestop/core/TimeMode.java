package com.timestop.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum TimeMode {
    TIME_STOP("Time Stop", ChatFormatting.GOLD, "Freezes all time, entities, and projectiles completely.", ChatFormatting.YELLOW),
    SLOW_MOTION("Slow Motion", ChatFormatting.BLUE, "Slows the entire world to 25% speed.", ChatFormatting.AQUA),
    MATRIX("Matrix", ChatFormatting.GREEN, "World slows to 20% speed while you move at hyper-speed.", ChatFormatting.DARK_GREEN),
    SUPERHOT("SUPERHOT", ChatFormatting.RED, "Time moves only when you move.", ChatFormatting.DARK_RED),
    DECELERATION_FIELD("Deceleration Field", ChatFormatting.AQUA, "Normal world speed. Projectiles in 4m radius slow by 80%.", ChatFormatting.DARK_AQUA),
    FAST_FORWARD("Fast Forward", ChatFormatting.LIGHT_PURPLE, "Accelerates time, smelting, and crop growth by 5x.", ChatFormatting.DARK_PURPLE);

    private final String displayName;
    private final ChatFormatting titleColor;
    private final String description;
    private final ChatFormatting descColor;

    TimeMode(String displayName, ChatFormatting titleColor, String description, ChatFormatting descColor) {
        this.displayName = displayName;
        this.titleColor = titleColor;
        this.description = description;
        this.descColor = descColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatFormatting getTitleColor() {
        return titleColor;
    }

    public Component getFormattedComponent() {
        return Component.literal(displayName.toUpperCase()).withStyle(titleColor, ChatFormatting.BOLD);
    }

    public Component getDescriptionComponent() {
        return Component.literal(description).withStyle(descColor);
    }

    public String getFormattedName() {
        return titleColor.toString() + ChatFormatting.BOLD + displayName.toUpperCase();
    }

    public String getDescription() {
        return descColor.toString() + description;
    }

    public TimeMode next() {
        TimeMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
