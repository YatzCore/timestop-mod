package com.timestop.mixin;

import com.timestop.compat.sable.SableConstraintState;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import org.joml.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.generic.RapierGenericConstraintHandle", remap = false)
public abstract class SableGenericConstraintHandleMixin {
    @Unique private SableConstraintState timestop$state() { return ((SableConstraintState.Tracked) this).timestop$constraintState(); }
    @Inject(method = "setFrame1", at = @At("RETURN"), remap = false)
    private void timestop$first(Vector3dc position, Quaterniondc orientation, CallbackInfo ci) {
        timestop$state().first = new SableConstraintState.Frame(new Vector3d(position), new Quaterniond(orientation));
    }
    @Inject(method = "setFrame2", at = @At("RETURN"), remap = false)
    private void timestop$second(Vector3dc position, Quaterniondc orientation, CallbackInfo ci) {
        timestop$state().second = new SableConstraintState.Frame(new Vector3d(position), new Quaterniond(orientation));
    }
    @Inject(method = "setLimit", at = @At("RETURN"), remap = false)
    private void timestop$limit(ConstraintJointAxis axis, double min, double max, CallbackInfo ci) {
        timestop$state().limits.put(axis, new SableConstraintState.Limit(min, max));
    }
    @Inject(method = "lockAxes", at = @At("RETURN"), remap = false)
    private void timestop$locks(ConstraintJointAxis[] axes, CallbackInfo ci) { timestop$state().locks = java.util.Set.of(axes); }
}
