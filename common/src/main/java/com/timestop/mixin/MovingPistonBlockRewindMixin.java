package com.timestop.mixin;
import com.timestop.core.rewind.RewindExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MovingPistonBlock.class)
public abstract class MovingPistonBlockRewindMixin {
 @Inject(method="onRemove", at=@At("HEAD"), cancellable=true)
 private void rewindRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving, CallbackInfo ci) {
  if (!level.isClientSide && RewindExecutor.isApplyingPlan()) {
   level.removeBlockEntity(pos);
   ci.cancel();
  }
 }
}
