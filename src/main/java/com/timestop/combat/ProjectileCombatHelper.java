package com.timestop.combat;

import com.timestop.mixin.AbstractArrowAccessor;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;

public class ProjectileCombatHelper {

    /**
     * Checks if a projectile is dead, lying on the ground, or lodged into a block/wall.
     * Grounded/embedded arrows are completely filtered out from combat interactions.
     */
    public static boolean isStuckOrDead(Projectile projectile) {
        if (projectile == null || !projectile.isAlive()) return true;
        if (projectile.onGround()) return true;

        if (projectile instanceof AbstractArrow arrow) {
            try {
                if (((AbstractArrowAccessor) arrow).timestop$isInGround()) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    /**
     * Checks if a projectile is actively in-flight and interactive.
     */
    public static boolean isActiveInFlight(Projectile projectile) {
        return !isStuckOrDead(projectile);
    }
}
