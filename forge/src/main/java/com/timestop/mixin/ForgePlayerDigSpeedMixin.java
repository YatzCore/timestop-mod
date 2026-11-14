package com.timestop.mixin;

import com.timestop.core.SelfAcceleration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge 52: block breaking reads the Forge overload Player.getDestroySpeed(BlockState, BlockPos);
 * the vanilla one-argument method delegates to it.
 */
@Mixin(Player.class)
public abstract class ForgePlayerDigSpeedMixin {
    @Inject(method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void timestop$personalBreakSpeed(BlockState state, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        // Setting a value cancels and returns, so only do it when the speed actually changes.
        float speed = SelfAcceleration.modifyBreakSpeed((Player) (Object) this, cir.getReturnValueF());
        speed = com.timestop.combat.TachyonRuneHandler.modifyBreakSpeed((Player) (Object) this, speed);
        if (speed != cir.getReturnValueF()) cir.setReturnValue(speed);
    }
}
