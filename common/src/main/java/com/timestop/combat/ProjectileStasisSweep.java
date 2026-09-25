package com.timestop.combat;

import com.timestop.core.*;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;

/** Sweeps every boundary before native gun raycasts can hit a target inside stasis. */
public final class ProjectileStasisSweep {
    private ProjectileStasisSweep() {}

    public static boolean intercept(Projectile projectile) {
        return intercept(projectile, projectile.position().add(projectile.getDeltaMovement()), true);
    }

    /** Native impacts have already resolved earlier collisions along this path. */
    public static boolean beforeImpact(Projectile projectile, Vec3 impact) {
        return intercept(projectile, impact, false);
    }

    private static boolean intercept(Projectile projectile, Vec3 end, boolean checkEarlierCollisions) {
        if (!(projectile.level() instanceof ServerLevel level) || TimeStopManager.isGlobalTimeStopActive()
                || TimeStopManager.isProjectileSuspended(projectile)) return false;
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 travel = end.subtract(projectile.position());
        double lengthSquared = travel.lengthSqr();
        if (lengthSquared < 1e-12) return false;
        Vec3 origin = projectile.position().add(0, projectile.getBbHeight() * .5, 0);
        var cuts = new ArrayList<Double>();
        cuts.add(0.0); cuts.add(1.0);
        for (var bubble : TemporalBubbleManager.getActiveBubbles().values()) {
            if (!bubble.getDimension().equals(level.dimension())) continue;
            Vec3 offset = origin.subtract(bubble.getCenter());
            double along = offset.dot(travel);
            double discriminant = along * along - lengthSquared * (offset.lengthSqr() - bubble.getRadius() * bubble.getRadius());
            if (discriminant <= 0) continue;
            double root = Math.sqrt(discriminant);
            double entry = (-along - root) / lengthSquared, exit = (-along + root) / lengthSquared;
            if (entry > 0 && entry < 1) cuts.add(entry);
            if (exit > 0 && exit < 1) cuts.add(exit);
        }
        Collections.sort(cuts);
        for (int i = 0; i + 1 < cuts.size(); i++) {
            double entry = cuts.get(i), exit = cuts.get(i + 1);
            if (exit - entry < 1e-12) continue;
            // Check intervals, including where a stronger overlapping field ends.
            var winner = TemporalBubbleManager.getDominantBubble(level.dimension(), origin.add(travel.scale((entry + exit) * .5)));
            if (winner == null || winner.getMode() != TimeMode.TIME_STOP || winner.canEntityAct(projectile)) continue;
            double t = entry + Math.min(1e-5 / Math.sqrt(lengthSquared), (exit - entry) * .25);
            Vec3 point = projectile.position().add(travel.scale(t));
            // Let native collision handling resolve walls/entities before the sphere.
            if (checkEarlierCollisions) {
                var wall = level.clip(new ClipContext(projectile.position(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
                if (wall.getType() != HitResult.Type.MISS) return false;
                var hit = ProjectileUtil.getEntityHitResult(level, projectile, projectile.position(), point,
                        projectile.getBoundingBox().expandTowards(travel.scale(t)).inflate(1),
                        target -> target.isAlive() && !target.isSpectator() && target.isPickable() && target != projectile.getOwner());
                if (hit != null) return false;
            }
            projectile.setPos(point);
            TimeStopManager.registerSuspendedProjectile(projectile, velocity);
            projectile.setOldPosAndRot();
            projectile.hasImpulse = true;
            level.getChunkSource().broadcast(projectile, new ClientboundTeleportEntityPacket(projectile));
            level.getChunkSource().broadcast(projectile, new ClientboundSetEntityMotionPacket(projectile));
            return true;
        }
        return false;
    }
}
