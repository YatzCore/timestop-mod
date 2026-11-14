package com.timestop.mixin;

import com.timestop.compat.sable.SableBodyTimeManager;
import com.timestop.core.TemporalBubbleManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional target: no Sable classes are loaded when the target mod is absent. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem", remap = false)
public abstract class SablePhysicsSystemMixin {

    @Unique
    private static boolean timestop$isPureGlobalFreeze() {
        return TimeStopManager.isGlobalTimeStopActive()
                && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP
                && !TemporalBubbleManager.hasActiveBubbles();
    }

    // Suppress stepping entirely when all physics bodies are globally frozen without local exceptions.
    @Inject(method = "tickPipelinePhysics", at = @At("HEAD"), cancellable = true, remap = false)
    private void timestop$onTickPipelinePhysicsHead(ServerSubLevelContainer container, CallbackInfo ci) {
        if (timestop$isPureGlobalFreeze()) {
            ci.cancel();
            return;
        }
        SableBodyTimeManager.onPhysicsTickBegin((SubLevelPhysicsSystem) (Object) this, container);
    }

    @Redirect(method = "tickPipelinePhysics", at = @At(value = "FIELD",
            target = "Ldev/ryanhcode/sable/physics/config/PhysicsConfigData;substepsPerTick:I",
            ordinal = 0), remap = false)
    private int timestop$redirectLoopSubsteps(dev.ryanhcode.sable.physics.config.PhysicsConfigData config) {
        return SableBodyTimeManager.getTotalLoopSubsteps(config.substepsPerTick);
    }

    @Inject(method = "tickPipelinePhysics", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;getAllSubLevels()Ljava/util/List;",
            ordinal = 0), remap = false)
    private void timestop$onSubstepBegin(ServerSubLevelContainer container, CallbackInfo ci) {
        SableBodyTimeManager.onSubstepBegin((SubLevelPhysicsSystem) (Object) this, container);
    }

    @Inject(method = "tickPipelinePhysics", at = @At("TAIL"), remap = false)
    private void timestop$onTickPipelinePhysicsTail(ServerSubLevelContainer container, CallbackInfo ci) {
        SableBodyTimeManager.onPhysicsTickEnd((SubLevelPhysicsSystem) (Object) this, container);
    }

    // Gate actors when a body is in stasis.
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/block/BlockEntitySubLevelActor;sable$tick(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V"), remap = false)
    private void timestop$gateSubLevelActor(BlockEntitySubLevelActor actor, ServerSubLevel body) {
        if (!SableBodyTimeManager.shouldGateActor(body)) {
            actor.sable$tick(body);
        }
    }

    // Gate force and lift provider contributions when body is frozen or not scheduled for this substep.
    @Redirect(method = "tickPipelinePhysics", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;prePhysicsTick(Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V"), remap = false)
    private void timestop$gatePrePhysicsTick(ServerSubLevel subLevel, SubLevelPhysicsSystem system, RigidBodyHandle handle, double timeStep) {
        if (!SableBodyTimeManager.shouldGateForces(subLevel)) {
            subLevel.prePhysicsTick(system, handle, timeStep);
        }
    }

    // Gate queued force applications (reaction wheels, contraptions, drag) for frozen bodies.
    @Redirect(method = "tickPipelinePhysics", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;applyQueuedForces(Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V"), remap = false)
    private void timestop$gateApplyQueuedForces(ServerSubLevel subLevel, SubLevelPhysicsSystem system, RigidBodyHandle handle, double timeStep) {
        if (!SableBodyTimeManager.shouldGateForces(subLevel)) {
            subLevel.applyQueuedForces(system, handle, timeStep);
        }
    }

    // Prevent floating point drift or solver push from displacing a frozen body.
    @Inject(method = "updatePose", at = @At("TAIL"), remap = false)
    private void timestop$enforceHeldPose(ServerSubLevel serverSubLevel, CallbackInfo ci) {
        SableBodyTimeManager.enforceStasisPose((SubLevelPhysicsSystem) (Object) this, serverSubLevel);
    }

    // Clean up temporary constraints when a body is removed.
    @Inject(method = "onSubLevelRemoved", at = @At("HEAD"), remap = false)
    private void timestop$onBodyRemoved(SubLevel subLevel, SubLevelRemovalReason reason, CallbackInfo ci) {
        if (subLevel instanceof ServerSubLevel serverSubLevel) {
            com.timestop.compat.sable.SableWorldBridge.recordBodyRemoved(serverSubLevel, reason);
            SableBodyTimeManager.onBodyRemoved(serverSubLevel);
        }
    }
}
