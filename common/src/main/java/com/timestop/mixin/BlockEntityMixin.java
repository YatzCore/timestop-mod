package com.timestop.mixin;

import com.timestop.core.rewind.TickRecorder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
    @Inject(method = "setChanged()V", at = @At("HEAD"))
    private void timestop$onSetChanged(CallbackInfo ci) {
        TickRecorder.getInstance().blockEntityChanged((BlockEntity) (Object) this);
    }
}
