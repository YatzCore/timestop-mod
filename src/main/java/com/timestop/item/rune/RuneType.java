package com.timestop.item.rune;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum RuneType {
    BLANK(
            "blank_rune",
            "Blank Temporal Rune",
            "An uncarved temporal slate infused with chronal resonance.",
            ChatFormatting.GRAY
    ),
    DEFLECTION(
            "rune_deflection",
            "Rune of Redirection",
            "Automatically parries incoming projectiles back at their shooters with supersonic velocity.",
            ChatFormatting.AQUA
    ),
    SNATCHING(
            "rune_snatching",
            "Rune of Snatching",
            "Automatically captures incoming projectiles directly into your inventory.",
            ChatFormatting.GOLD
    ),
    PHASING(
            "rune_phasing",
            "Rune of Phasing",
            "Automatically teleports you away from incoming projectiles upon imminent impact.",
            ChatFormatting.LIGHT_PURPLE
    ),
    KINETIC(
            "rune_kinetic",
            "Rune of Kinetic Amplification",
            "Supercharges strikes on frozen entities and objects with 2.5x launch force.",
            ChatFormatting.RED
    ),
    VAMPIRISM(
            "rune_vampirism",
            "Rune of Chrono-Vampirism",
            "Siphons temporal duration from foes, up to double the watch's base duration.",
            ChatFormatting.DARK_RED
    ),
    VOLATILE(
            "rune_volatile",
            "Rune of Volatile Stasis",
            "Infuses struck projectiles and falling blocks with delayed kinetic bomb blasts.",
            ChatFormatting.GOLD
    ),
    TACHYON(
            "rune_tachyon",
            "Rune of the Tachyon",
            "Accelerates mining speed (3x) and attack recharge in Slow-Mo & Matrix modes.",
            ChatFormatting.AQUA
    ),
    DEAD_EYE(
            "rune_deadeye",
            "Rune of the Dead Eye",
            "Dilates time when aiming ranged weapons. Sweeping crosshair paints up to 6 targets for a supersonic volley.",
            ChatFormatting.DARK_RED
    ),
    RICOCHET(
            "rune_ricochet",
            "Rune of Voltaic Ricochet",
            "Arrows ricochet between nearby targets in stasis like chain lightning.",
            ChatFormatting.YELLOW
    ),
    ORBITAL(
            "rune_orbital",
            "Rune of Orbital Redirection",
            "Intercepts projectiles into a spinning orbital halo, launching them at nearest enemies when full or triggered.",
            ChatFormatting.AQUA
    ),
    TRANSPOSITION(
            "rune_transposition",
            "Rune of Spatial Transposition",
            "Instantly swaps positions with any entity or projectile in sight with a percussive clap.",
            ChatFormatting.LIGHT_PURPLE
    );

    private final String id;
    private final String displayName;
    private final String description;
    private final ChatFormatting format;

    RuneType(String id, String displayName, String description, ChatFormatting format) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.format = format;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public ChatFormatting getFormat() {
        return format;
    }

    public Component getFormattedComponent() {
        return Component.literal(displayName).withStyle(format, ChatFormatting.BOLD);
    }
}
