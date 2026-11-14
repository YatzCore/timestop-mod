package com.timestop.mixin;

import com.timestop.prediction.PredictionTeleports;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderMan.class)
public abstract class PredictionEndermanMixin {
    @Unique private boolean timestop$queuedTeleport;
    @Inject(method="teleport(DDD)Z", at=@At("HEAD"), cancellable=true)
    private void timestop$existingReservation(double x,double y,double z,CallbackInfoReturnable<Boolean> ci) {
        timestop$queuedTeleport = false;
        if (PredictionTeleports.pending((EnderMan)(Object)this)) ci.setReturnValue(true);
    }
    // Runs after Forge's native teleport event has validated/modified the requested destination.
    @Redirect(method="teleport(DDD)Z", at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/monster/EnderMan;randomTeleport(DDDZ)Z"))
    private boolean timestop$teleportTell(EnderMan mob,double x,double y,double z,boolean particles) {
        if (!PredictionTeleports.watched(mob)) return mob.randomTeleport(x,y,z,particles);
        timestop$queuedTeleport = PredictionTeleports.reserve(mob,x,y,z);
        return false; // Suppress the immediate jump's sound/event; emit them at the reserved jump.
    }
    @Inject(method="teleport(DDD)Z", at=@At("RETURN"), cancellable=true)
    private void timestop$reservedResult(double x,double y,double z,CallbackInfoReturnable<Boolean> ci) {
        if (timestop$queuedTeleport) ci.setReturnValue(true);
    }
}
