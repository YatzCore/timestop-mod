package com.timestop.combat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

public enum ChainTargetFilter {
    HOSTILE("Hostile Mobs", ChatFormatting.RED),
    ALL("All Living", ChatFormatting.GOLD),
    PASSIVE("Passive Animals", ChatFormatting.GREEN);

    private final String displayName;
    private final ChatFormatting color;

    ChainTargetFilter(String displayName, ChatFormatting color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public Component getFormattedComponent() {
        return Component.literal(displayName).withStyle(color, ChatFormatting.BOLD);
    }

    public boolean matches(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isSpectator()) return false;

        return switch (this) {
            case HOSTILE -> entity instanceof Enemy;
            case ALL -> !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
            case PASSIVE -> (entity instanceof Animal) && !(entity instanceof Enemy) && !(entity instanceof Player);
        };
    }

    public ChainTargetFilter next() {
        ChainTargetFilter[] vals = values();
        return vals[(this.ordinal() + 1) % vals.length];
    }

    public static ChainTargetFilter fromName(String name) {
        if (name == null) return HOSTILE;
        try {
            return valueOf(name);
        } catch (Exception e) {
            return HOSTILE;
        }
    }
}
