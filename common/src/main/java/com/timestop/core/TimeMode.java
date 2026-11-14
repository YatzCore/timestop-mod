package com.timestop.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum TimeMode {
    TIME_STOP("Time Stop", ChatFormatting.GOLD, "Freezes all time, entities, and projectiles completely.", ChatFormatting.YELLOW),
    SLOW_MOTION("Slow Motion", ChatFormatting.BLUE, "Slows the entire world to 25% speed.", ChatFormatting.AQUA),
    MATRIX("Matrix", ChatFormatting.GREEN, "World slows to 20% speed while you move at hyper-speed.", ChatFormatting.DARK_GREEN),
    SUPERHOT("SUPERHOT", ChatFormatting.RED, "Time moves only when you move.", ChatFormatting.DARK_RED),
    DECELERATION_FIELD("Deceleration Field", ChatFormatting.AQUA, "Slows only projectiles inside the bubble, or across the server in global mode.", ChatFormatting.DARK_AQUA),
    FAST_FORWARD("Fast Forward", ChatFormatting.LIGHT_PURPLE, "Accelerates time, smelting, and crop growth by 5x.", ChatFormatting.DARK_PURPLE),
    REWIND("Rewind", ChatFormatting.DARK_PURPLE, "Rewinds the world, mobs, and player state backwards in time.", ChatFormatting.LIGHT_PURPLE),
    TIME_PREDICTION("Time Prediction", ChatFormatting.AQUA, "Reveals planned movement, future silhouettes, and complete projectile arcs.", ChatFormatting.GRAY),
    COMBAT_SLOW("Combat Slow Mo", ChatFormatting.BLUE, "Slows mobs and all projectiles; players and the world stay at normal speed.", ChatFormatting.AQUA),
    SELF_ACCELERATION("Self Acceleration", ChatFormatting.LIGHT_PURPLE, "Speeds up only you; everything else stays at normal speed.", ChatFormatting.LIGHT_PURPLE);

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
        return Component.literal(describe()).withStyle(descColor);
    }

    /** Description with the current Speed Calibration values for the speed-based modes. */
    public String describe() {
        var c = com.timestop.config.TimeStopConfig.COMMON;
        return switch (this) {
            case TIME_STOP -> "Freezes the world, mobs and projectiles. You can still move and act.";
            case SLOW_MOTION -> "Slows time to " + slowPercent() + " around you (everywhere in global mode), you included."
                    + " Your attack recharge, item use and cooldowns keep real-time speed.";
            case COMBAT_SLOW -> "Slows mobs and all projectiles (yours too) to " + slowPercent()
                    + "; players, blocks, crops and daylight stay normal.";
            case MATRIX -> "Slows time to " + percent(com.timestop.config.TimeStopConfig.clampMatrix(c.matrixRate.get()))
                    + " while you get 4x movement and attack speed.";
            case SUPERHOT -> "Time crawls at " + percent(com.timestop.config.TimeStopConfig.clampSuperhotIdle(c.superhotIdleRate.get()))
                    + " while you stand still and runs normally while you move or attack.";
            case DECELERATION_FIELD -> "Slows only projectiles to " + percent(com.timestop.config.TimeStopConfig.clampDecelerationDrag(c.decelerationDrag.get()))
                    + " inside the field (everywhere in global mode). Everything else keeps normal speed.";
            case FAST_FORWARD -> "Speeds up time " + fastFactor() + " around you (everywhere in global mode), you included:"
                    + " mobs, crops, furnaces, daylight, and your own eating, healing, breath, effects and cooldowns.";
            case SELF_ACCELERATION -> "Speeds up only you " + fastFactor()
                    + ": movement, falling, mining, eating and item use, attack recharge, healing, hunger, breath, burning, effects and cooldowns."
                    + " Everything else stays normal. Casual mode (watch settings) keeps harmful effects at normal speed.";
            default -> description;
        };
    }

    private static String percent(double rate) {
        double p = rate * 100;
        return (p >= 10 ? String.valueOf(Math.round(p)) : String.valueOf(Math.round(p * 10) / 10.0)) + "%";
    }

    /** Short label of a variant on its card: World / Combat / Self. */
    public String variantLabel() {
        return switch (this) {
            case COMBAT_SLOW -> "Combat";
            case SELF_ACCELERATION -> "Self";
            default -> "World";
        };
    }

    private static String slowPercent() {
        double rate = com.timestop.config.TimeStopConfig.clampSlowMotion(com.timestop.config.TimeStopConfig.COMMON.slowMotionRate.get());
        return Math.round(rate * 100) + "%";
    }

    private static String fastFactor() {
        double rate = com.timestop.config.TimeStopConfig.clampFastForward(com.timestop.config.TimeStopConfig.COMMON.fastForwardRate.get());
        double rounded = Math.round(rate * 10) / 10.0;
        return (rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded)) + "x";
    }

    public String getFormattedName() {
        return titleColor.toString() + ChatFormatting.BOLD + displayName.toUpperCase();
    }

    public String getDescription() {
        return descColor.toString() + describe();
    }

    public TimeMode baseMode() {
        return this == COMBAT_SLOW ? SLOW_MOTION : this == SELF_ACCELERATION ? FAST_FORWARD : this;
    }
    public boolean hasVariant() { return baseMode() == SLOW_MOTION || baseMode() == FAST_FORWARD; }
    public TimeMode alternate() {
        return switch(this) {
            case SLOW_MOTION -> COMBAT_SLOW;
            case COMBAT_SLOW -> SLOW_MOTION;
            case FAST_FORWARD -> SELF_ACCELERATION;
            case SELF_ACCELERATION -> FAST_FORWARD;
            default -> this;
        };
    }
    public TimeMode next() {
        TimeMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
