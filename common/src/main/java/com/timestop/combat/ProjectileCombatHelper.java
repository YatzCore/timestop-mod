package com.timestop.combat;

import com.timestop.mixin.AbstractArrowAccessor;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;

public class ProjectileCombatHelper {
    public static net.minecraft.world.phys.Vec3 incomingVelocity(Projectile projectile) {
        if (com.timestop.core.TimeStopManager.isProjectileSuspended(projectile))
            return com.timestop.core.TimeStopManager.getSuspendedVelocity(projectile);
        var velocity = projectile.getDeltaMovement();
        if (velocity.lengthSqr() < 1e-8 && projectile instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile fireball)
            return new net.minecraft.world.phys.Vec3(fireball.xPower, fireball.yPower, fireball.zPower).scale(10);
        return velocity;
    }

    public static void markReleased(Projectile projectile, net.minecraft.world.entity.player.Player player) {
        com.timestop.platform.EntityDataHelper.getPersistentData(projectile).putUUID("RuneReleasedBy", player.getUUID());
    }

    public static boolean wasReleasedBy(Projectile projectile, net.minecraft.world.entity.player.Player player) {
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        return data.hasUUID("RuneReleasedBy") && data.getUUID("RuneReleasedBy").equals(player.getUUID());
    }


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
