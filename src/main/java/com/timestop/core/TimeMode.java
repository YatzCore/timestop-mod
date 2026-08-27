package com.timestop.core;

public enum TimeMode {
    TIME_STOP("Time Stop", "§6§lTIME STOP", "§eFreezes all time, entities, and projectiles completely."),
    SLOW_MOTION("Slow Motion", "§9§lSLOW MOTION", "§bSlows the entire world to 25% speed."),
    MATRIX("Matrix", "§a§lMATRIX", "§2World slows to 20% speed while you move at hyper-speed."),
    FAST_FORWARD("Fast Forward", "§c§lFAST FORWARD", "§4Accelerates time, smelting, and crop growth by 5x.");

    private final String displayName;
    private final String formattedName;
    private final String description;

    TimeMode(String displayName, String formattedName, String description) {
        this.displayName = displayName;
        this.formattedName = formattedName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFormattedName() {
        return formattedName;
    }

    public String getDescription() {
        return description;
    }

    public TimeMode next() {
        TimeMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
