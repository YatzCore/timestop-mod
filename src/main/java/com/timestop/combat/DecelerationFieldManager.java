package com.timestop.combat;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.AbstractWatchItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Pure physics engine manager for the Deceleration Field (Bullet-Dodge).
 * Scales dynamically according to the player's watch tier:
 * - Copper: No passive field (primitive mechanism)
 * - Gilded: 3.5m radius
 * - Diamond: 4.5m radius
 * - Netherite: 5.5m radius
 * - Creative: 6.0m radius
 */
public class DecelerationFieldManager {

    /**
     * Calculates the exact deceleration radius currently projected by the given player.
     * Returns 0.0 if the player has no active field or passive offhand buff.
     */
    public static double getDecelerationRadius(@Nullable Player player) {
        if (player == null || !player.isAlive()) return 0.0;

        // 1. Off-Hand Passive Check
        if (player.getOffhandItem().getItem() instanceof AbstractWatchItem offWatch) {
            if (offWatch.getTier().hasOffhandPassive()) {
                return offWatch.getTier().getDecelerationRadius();
            }
        }

        // 2. Active DECELERATION_FIELD mode in engine
        boolean isModeActive;
        if (player.level().isClientSide) {
            isModeActive = ClientTimeStopManager.isTimeStopped()
                    && ClientTimeStopManager.getCurrentMode() == TimeMode.DECELERATION_FIELD
                    && ClientTimeStopManager.isEntityExempt(player);
        } else {
            isModeActive = TimeStopManager.isGlobalTimeStopped()
                    && TimeStopManager.getCurrentMode() == TimeMode.DECELERATION_FIELD
                    && TimeStopManager.isEntityExempt(player);
        }

        if (isModeActive) {
            if (player.getMainHandItem().getItem() instanceof AbstractWatchItem mainWatch) {
                return Math.max(4.0, mainWatch.getTier().getDecelerationRadius());
            }
            return 4.5;
        }

        return 0.0;
    }

    /**
     * Checks if the given player is currently projecting a Deceleration Field.
     */
    public static boolean hasDecelerationField(@Nullable Player player) {
        return getDecelerationRadius(player) > 0.0;
    }

    /**
     * Finds any nearby player projecting a deceleration field over this projectile.
     * Returns null if no protecting player is in range or if the projectile was fired by that player.
     */
    @Nullable
    public static Player getProtectingPlayer(Projectile projectile) {
        if (!ProjectileCombatHelper.isActiveInFlight(projectile)) return null;

        AABB searchBox = projectile.getBoundingBox().inflate(7.0);
        List<Player> nearbyPlayers = projectile.level().getEntitiesOfClass(Player.class, searchBox);
        if (nearbyPlayers.isEmpty()) return null;

        for (Player player : nearbyPlayers) {
            if (!player.isAlive() || player.isSpectator()) continue;
            // Player's own shots are NEVER slowed!
            if (projectile.getOwner() == player) continue;
            if (!hasDecelerationField(player)) continue;

            double radius = getDecelerationRadius(player);
            if (radius <= 0.0) continue;

            double radiusSqr = radius * radius;

            // Calculate 3D distance to player torso / center of mass
            double dx = projectile.getX() - player.getX();
            double dy = projectile.getY() - (player.getY() + player.getEyeHeight() * 0.5);
            double dz = projectile.getZ() - player.getZ();
            double distSqr = dx * dx + dy * dy + dz * dz;

            if (distSqr <= radiusSqr) {
                return player;
            }
        }
        return null;
    }

    /**
     * Checks if this projectile is currently inside any active deceleration field.
     */
    public static boolean isDecelerated(Projectile projectile) {
        return getProtectingPlayer(projectile) != null;
    }
}
