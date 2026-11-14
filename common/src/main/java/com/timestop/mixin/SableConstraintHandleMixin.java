package com.timestop.mixin;

import com.timestop.compat.sable.SableConstraintState;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle", remap = false)
public abstract class SableConstraintHandleMixin implements SableConstraintState.Tracked {
    @Unique private final SableConstraintState timestop$state = new SableConstraintState();
    @Override public SableConstraintState timestop$constraintState() { return timestop$state; }

    @Inject(method = "setMotor", at = @At("RETURN"), remap = false)
    private void timestop$motor(ConstraintJointAxis axis, double target, double stiffness, double damping, boolean limited, double maxForce, CallbackInfo ci) {
        timestop$state.motors.put(axis, new SableConstraintState.Motor(target, stiffness, damping, limited, maxForce));
    }
    @Inject(method = "setContactsEnabled", at = @At("RETURN"), remap = false)
    private void timestop$contacts(boolean enabled, CallbackInfo ci) { timestop$state.contacts = enabled; }
}
