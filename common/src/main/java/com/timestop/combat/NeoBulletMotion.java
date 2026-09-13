package com.timestop.combat;

import net.minecraft.world.phys.Vec3;

/** Invisible resistance: preserve the incoming trajectory, then settle in world space. */
public final class NeoBulletMotion {
    public static final double DRAG = 0.68;
    private NeoBulletMotion() {}

    public static Vec3 entryPoint(Vec3 position, Vec3 velocity, Vec3 eye, Vec3 look) {
        if (velocity.lengthSqr() < 1e-6 || velocity.normalize().dot(look) > 0.30) return null;
        Vec3 relative = position.subtract(eye);
        double radius = 4.5;
        double t = 0;
        if (relative.lengthSqr() > radius * radius) {
            double a = velocity.lengthSqr();
            double b = relative.dot(velocity);
            double c = relative.lengthSqr() - radius * radius;
            double discriminant = b * b - a * c;
            if (discriminant < 0) return null;
            t = (-b - Math.sqrt(discriminant)) / a;
            if (t < 0 || t > 1.5) return null;
        }
        Vec3 entry = position.add(velocity.scale(Math.min(1.0, Math.max(0.0, t))));
        Vec3 toEntry = entry.subtract(eye);
        if (toEntry.lengthSqr() < 1.0) {
            entry = eye.add(look.scale(1.5));
            toEntry = entry.subtract(eye);
        }
        return toEntry.normalize().dot(look) >= 0.15 ? entry : null;
    }

    /** Own outgoing shots are caught in front of the player, rather than rejected as allies. */
    public static Vec3 ownShotEntryPoint(Vec3 position, Vec3 velocity, Vec3 eye, Vec3 look) {
        double forward = position.subtract(eye).dot(look);
        double advance = velocity.dot(look);
        double t = advance > 1e-8 ? Math.max(0, Math.min(1, (1.5 - forward) / advance)) : 0;
        Vec3 entry = position.add(velocity.scale(t));
        Vec3 relative = entry.subtract(eye);
        return relative.lengthSqr() <= 4.5 * 4.5 && relative.dot(look) > 0.05
                && relative.normalize().dot(look) >= 0.25 ? entry : null;
    }

    public static Vec3 initialDrift(Vec3 incoming, double distanceToPlayer) {
        // The geometric series cannot cross the defender, even for high-speed bullets.
        double travel = Math.min(1.65, Math.max(0, distanceToPlayer - 1.15));
        return incoming.normalize().scale(Math.min(incoming.length(), travel * (1 - DRAG)));
    }
}
