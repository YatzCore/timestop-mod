package com.timestop.prediction;

import com.timestop.core.TimeStopManager;
import com.timestop.mixin.HurtingProjectilePhysicsAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.Vec3;

public final class PredictionPhysics {
    private PredictionPhysics() {}
    public static PredictionMotion capture(Entity entity, Vec3 livingStep, boolean free, float phase, double rate) {
        var kind = PredictionMotion.Kind.UNKNOWN;
        double gravity = 0, drag = 1, waterDrag = 1;
        Vec3 acceleration = Vec3.ZERO;
        Vec3 velocity = entity.getDeltaMovement();
        int owner = entity instanceof Projectile p && p.getOwner() != null ? p.getOwner().getId() : -1;
        if (entity instanceof net.minecraft.world.entity.LivingEntity) {
            kind = entity instanceof net.minecraft.world.entity.monster.EnderMan ? PredictionMotion.Kind.ENDERMAN : PredictionMotion.Kind.LIVING; gravity = entity.getGravity(); drag = .98; waterDrag = .8;
            if (livingStep != null) {
                double scale = PredictionTime.rate(entity);
                velocity = new Vec3(livingStep.x / Math.max(.001, scale), velocity.y, livingStep.z / Math.max(.001, scale));
            }
            if (entity instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi()) { velocity = Vec3.ZERO; gravity = 0; }
        } else if (entity instanceof net.minecraft.world.entity.item.PrimedTnt) {
            kind = PredictionMotion.Kind.TNT; gravity = entity.getGravity(); drag = .98;
        } else if (entity instanceof NativePredictionPhysics nativePhysics) {
            kind = PredictionMotion.Kind.NATIVE;
            gravity = nativePhysics.timestop$predictionGravity();
            drag = Math.max(0, 1 - nativePhysics.timestop$predictionDrag()); waterDrag = .6;
        } else if (entity instanceof AbstractArrow) {
            kind = PredictionMotion.Kind.ARROW; gravity = entity.getGravity(); drag = .99;
            waterDrag = entity instanceof ThrownTrident ? .99 : .6;
        } else if (entity instanceof ThrowableProjectile projectile) {
            kind = PredictionMotion.Kind.THROWABLE;
            gravity = projectile.getGravity();
            drag = .99; waterDrag = .8;
        } else if (entity instanceof AbstractHurtingProjectile projectile) {
            kind = PredictionMotion.Kind.FIREBALL;
            acceleration = projectile.getDeltaMovement().normalize().scale(projectile.accelerationPower);
            drag = ((HurtingProjectilePhysicsAccessor) projectile).timestop$inertia(); waterDrag = .8;
        }
        if (entity instanceof Projectile p && TimeStopManager.isProjectileSuspended(p)) velocity = Vec3.ZERO;
        var motion = new PredictionMotion(entity.getId(), owner, kind, entity.position(), velocity, acceleration,
                gravity, drag, waterDrag, entity.getBbWidth(), entity.getBbHeight(), entity.onGround(),
                entity.isNoGravity(), free, phase, rate);
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) motion = motion.withStepHeight(com.timestop.platform.Services.PLATFORM.getPredictionStepHeight(entity)).withJumpBoost(living.getJumpBoostPower());
        if (entity instanceof net.minecraft.world.entity.Mob mob && !mob.isNoAi()) {
            var path = mob.getNavigation().getPath();
            if (path != null && !path.isDone()) {
                var route = new java.util.ArrayList<Vec3>();
                for (int i=path.getNextNodeIndex(); i<path.getNodeCount() && route.size()<24; i++) {
                    Vec3 node = path.getEntityPosAtNode(mob, i);
                    if (node.distanceToSqr(entity.position()) > PredictionEngine.RANGE * PredictionEngine.RANGE) break;
                    route.add(node);
                }
                double speed = velocity.horizontalDistance();
                if (speed < .005) speed = 0;
                motion = motion.withRoute(route, Math.min(2, speed));
            }
        }
        return motion.withEffects(PredictionEffects.capture(entity));
    }
}
