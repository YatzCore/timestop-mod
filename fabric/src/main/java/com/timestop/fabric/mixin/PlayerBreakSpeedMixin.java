package com.timestop.fabric.mixin;

import com.timestop.core.SelfAcceleration;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric: block breaking reads Player.getDestroySpeed. */
@Mixin(Player.class)
public abstract class PlayerBreakSpeedMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void timestop$personalBreakSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        // Setting a value cancels and returns, so only do it when the speed actually changes.
        float speed = SelfAcceleration.modifyBreakSpeed((Player) (Object) this, cir.getReturnValueF());
        speed = com.timestop.combat.TachyonRuneHandler.modifyBreakSpeed((Player) (Object) this, speed);
        if (speed != cir.getReturnValueF()) cir.setReturnValue(speed);
    }
}
