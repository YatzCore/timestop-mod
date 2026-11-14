package com.timestop.mixin;

import com.timestop.compat.sable.SableBodyTimeManager;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts external impulses and collisions applied to rigid bodies in Rapier,
 * buffering them during stasis so they release with directional momentum once on resume.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class SableRapierPipelineMixin {

    @Inject(method = "applyLinearAndAngularImpulse", at = @At("HEAD"), cancellable = true, remap = false)
    private void timestop$bufferImpulseIfHeld(PhysicsPipelineBody body, Vector3dc force, Vector3dc torque, boolean wakeUp, CallbackInfo ci) {
        if (body instanceof ServerSubLevel serverSubLevel) {
            if (SableBodyTimeManager.isRewindControlled(serverSubLevel)) { ci.cancel(); return; }
            if (SableBodyTimeManager.isBodyHeld(serverSubLevel) || SableBodyTimeManager.isBodyStasisActive(serverSubLevel.getLevel(), serverSubLevel)) {
                SableBodyTimeManager.bufferImpulse(serverSubLevel, force, torque);
                ci.cancel();
            }
        }
    }

    @Inject(method = "applyImpulse", at = @At("HEAD"), cancellable = true, remap = false)
    private void timestop$bufferPointImpulseIfHeld(PhysicsPipelineBody body, Vector3dc position, Vector3dc force, CallbackInfo ci) {
        if (body instanceof ServerSubLevel serverSubLevel) {
            if (SableBodyTimeManager.isRewindControlled(serverSubLevel)) { ci.cancel(); return; }
            if (SableBodyTimeManager.isBodyHeld(serverSubLevel) || SableBodyTimeManager.isBodyStasisActive(serverSubLevel.getLevel(), serverSubLevel)) {
                Vector3dc com = body.getMassTracker().getCenterOfMass();
                Vector3d r = new Vector3d(position.x() - com.x(), position.y() - com.y(), position.z() - com.z());
                Vector3d torque = new Vector3d();
                r.cross(force, torque);
                SableBodyTimeManager.bufferImpulse(serverSubLevel, force, torque);
                ci.cancel();
            }
        }
    }

    @org.spongepowered.asm.mixin.Shadow(remap = false)
    private it.unimi.dsi.fastutil.ints.Int2ObjectMap<ServerSubLevel> activeSubLevels;

    @Shadow(remap = false)
    protected abstract long getSceneHandle();

    @Unique private double timestop$collisionStepSeconds = 0.05;
    @Unique private final java.util.List<double[]> timestop$pendingCollisionEffects = new java.util.ArrayList<>();

    @Inject(method = "physicsTick", at = @At("HEAD"), remap = false)
    private void timestop$beginContactStep(double timeStep, CallbackInfo ci) {
        timestop$collisionStepSeconds = timeStep;
        for (var body : activeSubLevels.values()) SableBodyTimeManager.getState(body.getUniqueId()).resetCollisionPairs();
    }

    @Inject(method = "physicsTick", at = @At("RETURN"), remap = false)
    private void timestop$collectContactStep(double timeStep, CallbackInfo ci) {
        // Native records repeat a pair's force total at every contact point. Drain per substep
        // so we can integrate F * dt once per pair without losing later substeps' impacts.
        double[] contacts = SableRapier3DAccessor.timestop$clearCollisions(getSceneHandle());
        if (contacts.length != 0) {
            timestop$captureCollisions(contacts);
            timestop$pendingCollisionEffects.add(contacts);
        }
    }

    @Redirect(method = "processCollisionEffects", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;clearCollisions(J)[D"), remap = false)
    private double[] timestop$replayCollisionEffects(long scene) {
        double[] tail = SableRapier3DAccessor.timestop$clearCollisions(scene);
        int length = tail.length;
        for (double[] part : timestop$pendingCollisionEffects) length += part.length;
        double[] result = java.util.Arrays.copyOf(tail, length);
        int offset = tail.length;
        for (double[] part : timestop$pendingCollisionEffects) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        timestop$pendingCollisionEffects.clear();
        return result;
    }

    @Inject(method = "addConstraint", at = @At("RETURN"), remap = false)
    private void timestop$trackConstraint(PhysicsPipelineBody bodyA, PhysicsPipelineBody bodyB,
                                          dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration<?> config,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle> cir) {
        var handle = cir.getReturnValue();
        if (handle != null && (bodyA instanceof ServerSubLevel || bodyB instanceof ServerSubLevel)
                && (bodyA == null || bodyA instanceof ServerSubLevel) && (bodyB == null || bodyB instanceof ServerSubLevel)) {
            com.timestop.compat.sable.SableWorldBridge.registerConstraint(
                    bodyA instanceof ServerSubLevel a ? a.getUniqueId() : null,
                    bodyB instanceof ServerSubLevel b ? b.getUniqueId() : null, config, handle);
        }
    }

    @Unique
    private double[] timestop$captureCollisions(double[] collisions) {
        if (collisions != null && collisions.length >= 15 && this.activeSubLevels != null) {
            for (int i = 0; i < collisions.length / 15; i++) {
                int idx = i * 15;
                int idA = (int) collisions[idx];
                int idB = (int) collisions[idx + 1];
                // Rapier's local normals point out of each body; its contact reaction is opposite.
                double impulse = -collisions[idx + 2] * timestop$collisionStepSeconds;
                Vector3d normalA = new Vector3d(collisions[idx + 3], collisions[idx + 4], collisions[idx + 5]);
                Vector3d normalB = new Vector3d(collisions[idx + 6], collisions[idx + 7], collisions[idx + 8]);
                Vector3d contactPointA = new Vector3d(collisions[idx + 9], collisions[idx + 10], collisions[idx + 11]);
                Vector3d contactPointB = new Vector3d(collisions[idx + 12], collisions[idx + 13], collisions[idx + 14]);

                ServerSubLevel bodyA = this.activeSubLevels.get(idA);
                ServerSubLevel bodyB = this.activeSubLevels.get(idB);
                // Static terrain is a support reaction, not momentum arriving from a moving
                // structure. Replaying its constraint/contact forces injects energy each time
                // a slowed body rests on the ground, eventually launching it out of the world.
                if (bodyA == null || bodyB == null) continue;
                long pairKey = ((long) Math.min(idA, idB) << 32) | ((long) Math.max(idA, idB) & 0xFFFFFFFFL);

                if (bodyA != null && SableBodyTimeManager.isBodyHeld(bodyA)) {
                    Vector3d linImpulse = new Vector3d(normalA).mul(impulse);
                    Vector3d torque = new Vector3d();
                    var tracker = bodyA.getMassTracker();
                    if (tracker != null) {
                        Vector3dc com = tracker.getCenterOfMass();
                        if (com != null) {
                            // Native contacts are local offsets from the pose rotation point.
                            // The release API accepts local impulses and rotates them in Rapier.
                            Vector3d r = new Vector3d(contactPointA).add(bodyA.logicalPose().rotationPoint()).sub(com);
                            r.cross(linImpulse, torque);
                        }
                    }
                    SableBodyTimeManager.bufferCollisionImpulse(bodyA, pairKey, linImpulse, torque);
                }
                if (bodyB != null && SableBodyTimeManager.isBodyHeld(bodyB)) {
                    Vector3d linImpulse = new Vector3d(normalB).mul(impulse);
                    Vector3d torque = new Vector3d();
                    var tracker = bodyB.getMassTracker();
                    if (tracker != null) {
                        Vector3dc com = tracker.getCenterOfMass();
                        if (com != null) {
                            Vector3d r = new Vector3d(contactPointB).add(bodyB.logicalPose().rotationPoint()).sub(com);
                            r.cross(linImpulse, torque);
                        }
                    }
                    SableBodyTimeManager.bufferCollisionImpulse(bodyB, pairKey, linImpulse, torque);
                }
            }
        }
        return collisions;
    }
}
