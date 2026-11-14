package com.timestop.compat.sable;

import com.timestop.core.TemporalBubble;
import com.timestop.core.TemporalBubbleManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-body temporal state, stasis constraints, velocity preservation,
 * fractional substep scheduling, and buffered collision/attack impulses.
 */
public final class SableBodyTimeManager {

    public static final class BodyStasisState {
        public final UUID bodyId;
        public FixedConstraintHandle constraintHandle;
        public final Vector3d storedLinearVelocity = new Vector3d();
        public final Vector3d storedAngularVelocity = new Vector3d();
        public final Vector3d heldPosition = new Vector3d();
        public final Quaterniond heldOrientation = new Quaterniond();
        public final Vector3d bufferedLinearImpulse = new Vector3d();
        public final Vector3d bufferedAngularImpulse = new Vector3d();
        public final PhysicsStepCredits credits = new PhysicsStepCredits();
        public boolean isHeld = false;
        public boolean shouldStepCurrentSubstep = true;
        public int remainingStepsThisTick = 0;
        public final Set<Long> processedCollisionPairsThisTick = new HashSet<>();

        public BodyStasisState(UUID bodyId) {
            this.bodyId = bodyId;
        }

        public void resetCollisionPairs() {
            processedCollisionPairsThisTick.clear();
        }
    }

    private static final Map<UUID, BodyStasisState> states = new ConcurrentHashMap<>();

    public static BodyStasisState getState(UUID bodyId) {
        return states.computeIfAbsent(bodyId, BodyStasisState::new);
    }

    public static Vector3d getBodyCenterOfMassWorld(ServerSubLevel body) {
        Vector3dc comPlot = body.getSelfMassTracker().getCenterOfMass();
        Vector3d world = new Vector3d();
        if (comPlot != null) {
            body.logicalPose().transformPosition(comPlot, world);
        } else {
            world.set(body.logicalPose().position());
        }
        return world;
    }

    public static double getBodyTimeRate(Level level, ServerSubLevel body) {
        if (isRewindControlled(body)) return 0.0;
        Vector3d com = getBodyCenterOfMassWorld(body);

        // 1. Local bubbles take precedence based on dominant bubble at body's COM
        if (TemporalBubbleManager.hasActiveBubbles()) {
            TemporalBubble dominant = TemporalBubbleManager.getDominantBubble(level.dimension(), com.x, com.y, com.z);
            if (dominant != null) {
                return getRateForBubble(dominant);
            }
        }

        // 2. Global time stop / modes
        if (TimeStopManager.isGlobalTimeStopActive()) {
            return getRateForGlobalMode();
        }

        return 1.0;
    }

    public static double getRateForBubble(TemporalBubble bubble) {
        if (bubble == null) return 1.0;
        TimeMode mode = bubble.getMode();
        if (mode == TimeMode.SUPERHOT) {
            double idleRate = com.timestop.config.TimeStopConfig.COMMON.superhotIdleRate.get();
            float activity = bubble.getSuperhotActivity();
            return idleRate + activity * (1.0 - idleRate);
        }
        return getRateForMode(mode);
    }

    public static boolean isRewindControlled(ServerSubLevel body) {
        return (TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.REWIND)
                || com.timestop.core.rewind.LocalRewind.containsSubLevel(body.getUniqueId());
    }

    public static double getRateForGlobalMode() {
        TimeMode mode = TimeStopManager.getCurrentMode();
        if (mode == TimeMode.SUPERHOT) {
            double idleRate = com.timestop.config.TimeStopConfig.COMMON.superhotIdleRate.get();
            long maxMs = (long) Math.max(50.0F, Math.round(50.0F / idleRate));
            long tickMs = TimeStopManager.getServerTickMs();
            float activity = (float) Math.max(0.0, Math.min(1.0, (double) (maxMs - tickMs) / (maxMs - 50L)));
            return idleRate + activity * (1.0 - idleRate);
        }
        return getRateForMode(mode);
    }

    public static double getRateForMode(TimeMode mode) {
        if (mode == null) return 1.0;
        return switch (mode) {
            case TIME_STOP -> 0.0;
            case TIME_PREDICTION -> com.timestop.config.TimeStopConfig.COMMON.predictionRate.get();
            case SLOW_MOTION -> com.timestop.config.TimeStopConfig.COMMON.slowMotionRate.get();
            case MATRIX -> com.timestop.config.TimeStopConfig.COMMON.matrixRate.get();
            case FAST_FORWARD -> com.timestop.config.TimeStopConfig.COMMON.fastForwardRate.get();
            case SUPERHOT -> com.timestop.config.TimeStopConfig.COMMON.superhotIdleRate.get();
            default -> 1.0;
        };
    }

    public static boolean isBodyStasisActive(Level level, ServerSubLevel body) {
        return getBodyTimeRate(level, body) == 0.0;
    }

    public static boolean shouldGateActor(ServerSubLevel body) {
        BodyStasisState state = states.get(body.getUniqueId());
        if (state != null && state.isHeld) return true;
        return isBodyStasisActive(body.getLevel(), body);
    }

    public static boolean shouldGateForces(ServerSubLevel body) {
        BodyStasisState state = states.get(body.getUniqueId());
        if (state != null) {
            if (state.isHeld || !state.shouldStepCurrentSubstep) return true;
        }
        return isBodyStasisActive(body.getLevel(), body);
    }

    public static boolean isBodyHeld(ServerSubLevel body) {
        BodyStasisState state = states.get(body.getUniqueId());
        return state != null && state.isHeld;
    }

    private static volatile int totalLoopSubsteps = 1;
    private static int currentSubstepIndex = 0;

    public static int getTotalLoopSubsteps(int baseSubsteps) {
        return Math.max(baseSubsteps, totalLoopSubsteps);
    }

    public static void onPhysicsTickBegin(SubLevelPhysicsSystem system, ServerSubLevelContainer container) {
        int baseSubsteps = Math.max(1, system.getConfig().substepsPerTick);
        double serverIntervalSeconds = TimeStopManager.getServerTickMs() / 1000.0;
        double quantumSeconds = 1.0 / 20.0 / baseSubsteps;
        currentSubstepIndex = 0;
        int maxNeededSteps = baseSubsteps;

        for (ServerSubLevel body : container.getAllSubLevels()) {
            if (body.isRemoved()) continue;
            BodyStasisState state = getState(body.getUniqueId());
            state.resetCollisionPairs();
            double rate = getBodyTimeRate(body.getLevel(), body);

            if (rate <= 0.0) {
                state.remainingStepsThisTick = 0;
                holdBody(system, body, state);
            } else {
                // Pure global stasis skips native stepping without touching velocity; explicit
                // impulses still need a single release when the global hold ends.
                if (!state.isHeld) releaseBufferedImpulses(system.getPhysicsHandle(body), state);
                int maxBudget = (int) Math.ceil(Math.max(rate, 1.0) * (serverIntervalSeconds / quantumSeconds) + 4);
                int steps = state.credits.advance(rate, serverIntervalSeconds, quantumSeconds, maxBudget);
                state.remainingStepsThisTick = steps;
                if (steps > maxNeededSteps) {
                    maxNeededSteps = steps;
                }
                if (steps <= 0) {
                    holdBody(system, body, state);
                }
            }
        }
        totalLoopSubsteps = maxNeededSteps;
    }

    public static void onSubstepBegin(SubLevelPhysicsSystem system, ServerSubLevelContainer container) {
        currentSubstepIndex++;
        for (ServerSubLevel body : container.getAllSubLevels()) {
            if (body.isRemoved()) continue;
            BodyStasisState state = getState(body.getUniqueId());
            double rate = getBodyTimeRate(body.getLevel(), body);

            if (rate <= 0.0) {
                state.shouldStepCurrentSubstep = false;
                holdBody(system, body, state);
            } else if (state.remainingStepsThisTick > 0) {
                state.remainingStepsThisTick--;
                state.shouldStepCurrentSubstep = true;
                releaseBody(system, body, state);
            } else {
                state.shouldStepCurrentSubstep = false;
                holdBody(system, body, state);
            }
        }
    }

    public static void onPhysicsTickEnd(SubLevelPhysicsSystem system, ServerSubLevelContainer container) {
        for (ServerSubLevel body : container.getAllSubLevels()) {
            if (body.isRemoved()) continue;
            double rate = getBodyTimeRate(body.getLevel(), body);
            BodyStasisState state = states.get(body.getUniqueId());
            if (state == null) continue;

            if (rate > 0.0) {
                if (state.isHeld) {
                    releaseBody(system, body, state);
                }
                // Sable publishes displacement per server tick, not the body's unscaled
                // native momentum. Recompute after temporary holds in a faster scene.
                body.logicalPose().position().sub(body.lastPose().position(), body.latestLinearVelocity).mul(20);
                Quaterniond difference = body.logicalPose().orientation().difference(body.lastPose().orientation(), new Quaterniond()).conjugate();
                Vector3d angular = body.latestAngularVelocity.set(difference.x, difference.y, difference.z);
                if (angular.lengthSquared() <= 1E-15) angular.mul(2.0 / difference.w);
                else angular.normalize().mul(2.0 * org.joml.Math.safeAcos(difference.w));
                angular.mul(20);
            }
        }
    }

    private static void holdBody(SubLevelPhysicsSystem system, ServerSubLevel body, BodyStasisState state) {
        if (state.isHeld) return;
        RigidBodyHandle handle = system.getPhysicsHandle(body);
        if (handle == null || !handle.isValid()) return;

        // Snapshot pre-stasis velocities and current pose
        handle.getLinearVelocity(state.storedLinearVelocity);
        handle.getAngularVelocity(state.storedAngularVelocity);
        state.heldPosition.set(body.logicalPose().position());
        state.heldOrientation.set(body.logicalPose().orientation());

        try {
            state.constraintHandle = createHoldConstraint(system, body);
        } catch (Exception ignored) {
            state.constraintHandle = null;
        }

        // Reset velocity inside Rapier so solver does not drift or stress
        system.getPipeline().resetVelocity(body);
        state.isHeld = true;
    }

    public static FixedConstraintHandle createHoldConstraint(SubLevelPhysicsSystem system, ServerSubLevel body) {
        // Use the merged center of mass to avoid a long lever arm from the plot's y=0 anchor.
        Vector3d pos1 = new Vector3d(body.getMassTracker().getCenterOfMass());
        Vector3d pos2 = body.logicalPose().transformPosition(pos1, new Vector3d());
        // The second endpoint is the world. Its frame relative to the body is R^-1,
        // so Rapier's body rotation times the local frame stays the world identity.
        Quaterniond orientation = new Quaterniond(body.logicalPose().orientation()).conjugate();
        var handle = system.getPipeline().addConstraint(body, null, new FixedConstraintConfiguration(pos1, pos2, orientation));
        SableWorldBridge.forgetConstraint(handle);
        return handle;
    }

    private static void releaseBody(SubLevelPhysicsSystem system, ServerSubLevel body, BodyStasisState state) {
        if (!state.isHeld) return;

        // Remove temporary world constraint
        if (state.constraintHandle != null) {
            try {
                if (state.constraintHandle.isValid()) {
                    state.constraintHandle.remove();
                }
            } catch (Exception ignored) {}
            state.constraintHandle = null;
        }

        state.isHeld = false;

        // Restore pre-stasis velocity
        RigidBodyHandle handle = system.getPhysicsHandle(body);
        if (handle != null && handle.isValid()) {
            // Replace constraint/contact solver residue with the saved momentum before
            // applying buffered impacts, so repeated holds do not amplify that residue.
            system.getPipeline().resetVelocity(body);
            handle.addLinearAndAngularVelocity(state.storedLinearVelocity, state.storedAngularVelocity);

            releaseBufferedImpulses(handle, state);
        }
    }

    private static void releaseBufferedImpulses(RigidBodyHandle handle, BodyStasisState state) {
        if (handle != null && handle.isValid()) {
            if (state.bufferedLinearImpulse.lengthSquared() > 1e-12 || state.bufferedAngularImpulse.lengthSquared() > 1e-12) {
                Vector3d lin = new Vector3d(state.bufferedLinearImpulse);
                Vector3d ang = new Vector3d(state.bufferedAngularImpulse);
                state.bufferedLinearImpulse.zero();
                state.bufferedAngularImpulse.zero();
                handle.applyLinearAndAngularImpulse(lin, ang, true);
            }
        }
    }

    public static void enforceStasisPose(SubLevelPhysicsSystem system, ServerSubLevel body) {
        BodyStasisState state = states.get(body.getUniqueId());
        if (state != null && state.isHeld) {
            // Constraint/contact corrections still move the native body during a skipped
            // step. Restore it as well as the published pose, or the next active step jumps
            // to that hidden displacement/rotation and visibly shakes a resting structure.
            system.getPipeline().teleport(body, state.heldPosition, state.heldOrientation);
            system.getPipeline().resetVelocity(body);
            body.logicalPose().position().set(state.heldPosition);
            body.logicalPose().orientation().set(state.heldOrientation);
            body.latestLinearVelocity.zero();
            body.latestAngularVelocity.zero();
        }
    }

    public static void bufferImpulse(ServerSubLevel body, Vector3dc linearImpulse, Vector3dc angularImpulse) {
        // Playback owns exact recorded momentum. Discard new forces instead of replaying them later.
        if (isRewindControlled(body)) return;
        BodyStasisState state = getState(body.getUniqueId());
        if (state.isHeld || isBodyStasisActive(body.getLevel(), body)) {
            state.bufferedLinearImpulse.add(linearImpulse);
            state.bufferedAngularImpulse.add(angularImpulse);
        } else {
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            if (handle != null && handle.isValid()) {
                handle.applyLinearAndAngularImpulse(linearImpulse, angularImpulse, true);
            }
        }
    }

    public static void bufferCollisionImpulse(ServerSubLevel body, long pairKey, Vector3dc pairImpulse) {
        bufferCollisionImpulse(body, pairKey, pairImpulse, new Vector3d());
    }

    public static void bufferCollisionImpulse(ServerSubLevel body, long pairKey, Vector3dc pairImpulse, Vector3dc angularImpulse) {
        if (isRewindControlled(body)) return;
        BodyStasisState state = getState(body.getUniqueId());
        if (state.processedCollisionPairsThisTick.add(pairKey)) {
            bufferImpulse(body, pairImpulse, angularImpulse != null ? angularImpulse : new Vector3d());
        }
    }

    public static void onBodyRemoved(ServerSubLevel body) {
        BodyStasisState state = states.remove(body.getUniqueId());
        if (state != null && state.constraintHandle != null) {
            try {
                if (state.constraintHandle.isValid()) {
                    state.constraintHandle.remove();
                }
            } catch (Exception ignored) {}
            state.constraintHandle = null;
        }
    }

    public static void clearAll() {
        for (BodyStasisState state : states.values()) {
            if (state.constraintHandle != null) {
                try {
                    if (state.constraintHandle.isValid()) {
                        state.constraintHandle.remove();
                    }
                } catch (Exception ignored) {}
                state.constraintHandle = null;
            }
        }
        states.clear();
    }
}
