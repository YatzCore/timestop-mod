package com.timestop.combat;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.AbstractWatchItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.timestop.config.TimeStopConfig;

import javax.annotation.Nullable;
import java.util.List;
import java.util.ArrayList;

/** Selected bubble/global projectile time fields, plus independent legacy player defense auras. */
public class DecelerationFieldManager {

    /**
     * Calculates the exact deceleration radius currently projected by the given player.
     * Returns 0.0 if the player has no active field or passive offhand buff.
     */
    public static double getDecelerationRadius(@Nullable Player player) {
        if (player == null || !player.isAlive()) return 0.0;

        double accessoryRadius = 0.0;
        if (player.getOffhandItem().getItem() instanceof AbstractWatchItem watch && watch.getTier().hasOffhandPassive()) {
            accessoryRadius = watch.getTier().getDecelerationRadius();
        }
        for (var stack : com.timestop.platform.Services.PLATFORM.getEquippedWatches(player)) {
            if (stack.getItem() instanceof AbstractWatchItem watch && watch.getTier().hasOffhandPassive()) {
                accessoryRadius = Math.max(accessoryRadius, watch.getTier().getDecelerationRadius());
            }
        }
        if (accessoryRadius > 0.0) return accessoryRadius;

        // Legacy player defense aura in MATRIX / SLOW_MOTION; Deceleration uses its selected scope.
        boolean isModeActive;
        if (player.level().isClientSide) {
            TimeMode mode = ClientTimeStopManager.getCurrentMode();
            isModeActive = ClientTimeStopManager.isTimeStopped()
                    && (mode == TimeMode.MATRIX || mode == TimeMode.SLOW_MOTION)
                    && ClientTimeStopManager.isEntityExempt(player);
        } else {
            TimeMode mode = TimeStopManager.getCurrentMode();
            isModeActive = TimeStopManager.isGlobalTimeStopped()
                    && (mode == TimeMode.MATRIX || mode == TimeMode.SLOW_MOTION)
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

        Level level = projectile.level();
        List<? extends Player> players = level.players();
        if (players.isEmpty()) return null;

        for (Player player : players) {
            if (!player.isAlive() || player.isSpectator()) continue;
            // A passive defense aura excludes its owner's shots; active Deceleration scopes include them.
            if (projectile.getOwner() == player) continue;

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
        return isGlobalFieldActive(projectile.level()) || isPedestalDecelerated(projectile) || getProtectingPlayer(projectile) != null;
    }

    public static boolean isPedestalDecelerated(Projectile projectile) {
        Vec3 position = worldPosition(projectile, projectile.position());
        for (Sphere field : activeFields(projectile)) {
            if (position.distanceToSqr(field.center()) <= field.radius() * field.radius()) return true;
        }
        return false;
    }

    public static boolean isGlobalFieldActive(Level level) {
        return level.isClientSide
                ? ClientTimeStopManager.isGlobalTimeStopActive() && ClientTimeStopManager.getCurrentMode() == TimeMode.DECELERATION_FIELD
                : TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.DECELERATION_FIELD;
    }

    public static double getTimeRate(Projectile projectile) {
        // Budget custom physics using its swept step, including fast entry from outside.
        return getMovementScale(projectile);
    }

    /** Native movement hooks handle these; every other Projectile uses a full-physics tick budget. */
    public static boolean hasMovementHook(Projectile projectile) {
        return projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow
                || projectile instanceof net.minecraft.world.entity.projectile.ThrowableProjectile
                || projectile instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile
                || projectile instanceof net.minecraft.world.entity.projectile.LlamaSpit
                || projectile instanceof ProjectileDecelerationHook;
    }

    /** Advance only the part of a fast native step that fits in this tick's time budget. */
    public static double getMovementScale(Projectile projectile) {
        return getFieldMovementScale(projectile) * com.timestop.prediction.PredictionTime.rate(projectile);
    }

    private static double getFieldMovementScale(Projectile projectile) {
        double rate = TimeStopConfig.COMMON.decelerationDrag.get();
        if (isGlobalFieldActive(projectile.level())) return rate;
        List<Sphere> fields = activeFields(projectile);
        for (Player player : projectile.level().players()) {
            if (player == projectile.getOwner() || !player.isAlive() || player.isSpectator()) continue;
            double radius = getDecelerationRadius(player);
            if (radius > 0) fields.add(new Sphere(player.position().add(0, player.getEyeHeight() * .5, 0), radius));
        }
        if (fields.isEmpty()) return 1.0;
        Vec3 start = worldPosition(projectile, projectile.position());
        Vec3 end = worldPosition(projectile, projectile.position().add(projectile.getDeltaMovement()));
        return movementScale(start, end.subtract(start), fields, rate);
    }

    public record Sphere(Vec3 center, double radius) {}

    public static double movementScale(Vec3 start, Vec3 travel, List<Sphere> fields, double rate) {
        if (travel.lengthSqr() < 1e-12) {
            for (Sphere field : fields) if (start.distanceToSqr(field.center()) <= field.radius() * field.radius()) return rate;
            return 1.0;
        }
        List<double[]> intervals = new ArrayList<>();
        List<Double> boundaries = new ArrayList<>(List.of(0.0, 1.0));
        double a = travel.lengthSqr();
        for (Sphere field : fields) {
            Vec3 offset = start.subtract(field.center());
            double b = offset.dot(travel);
            double discriminant = b * b - a * (offset.lengthSqr() - field.radius() * field.radius());
            if (discriminant <= 0) continue;
            double root = Math.sqrt(discriminant);
            double enter = Math.max(0, (-b - root) / a);
            double exit = Math.min(1, (-b + root) / a);
            if (enter >= exit) continue;
            intervals.add(new double[]{enter, exit});
            boundaries.add(enter);
            boundaries.add(exit);
        }
        boundaries.sort(Double::compare);
        double remainingTime = 1.0;
        for (int i = 1; i < boundaries.size(); i++) {
            double from = boundaries.get(i - 1), to = boundaries.get(i), midpoint = (from + to) * .5;
            boolean inside = false;
            for (double[] interval : intervals) if (midpoint >= interval[0] && midpoint <= interval[1]) { inside = true; break; }
            double localRate = inside ? rate : 1.0;
            double time = (to - from) / localRate;
            if (time >= remainingTime) return Math.min(1.0, from + remainingTime * localRate);
            remainingTime -= time;
        }
        return 1.0;
    }

    private static List<Sphere> activeFields(Projectile projectile) {
        List<Sphere> fields = new ArrayList<>();
        if (projectile.level().isClientSide) {
            String dimension = projectile.level().dimension().location().toString();
            for (var bubble : com.timestop.core.ClientBubbleManager.getActiveBubbles())
                if (bubble.mode == TimeMode.DECELERATION_FIELD && bubble.dimensionId.equals(dimension))
                    fields.add(new Sphere(bubble.getCenter(), bubble.radius));
        } else {
            for (var bubble : com.timestop.core.TemporalBubbleManager.getActiveBubbles().values())
                if (bubble.getMode() == TimeMode.DECELERATION_FIELD && bubble.getDimension().equals(projectile.level().dimension()))
                    fields.add(new Sphere(bubble.getCenter(), bubble.getRadius()));
        }
        return fields;
    }

    private static Vec3 worldPosition(Projectile projectile, Vec3 position) {
        return com.timestop.compat.TemporalWorlds.toWorld(projectile.level(), position);
    }
}
