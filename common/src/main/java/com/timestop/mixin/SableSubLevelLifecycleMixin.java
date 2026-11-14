package com.timestop.mixin;

import com.timestop.core.rewind.TickRecorder;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.ServerSubLevel", remap = false)
public abstract class SableSubLevelLifecycleMixin {
    @Inject(method = "setSplitFrom", at = @At("RETURN"), remap = false)
    private void timestop$recordSplit(ServerSubLevel parent, Pose3d originalPose, CallbackInfo ci) {
        var child = (ServerSubLevel) (Object) this;
        TickRecorder.getInstance().recordSubLevelDependency(child.getLevel(), parent.getUniqueId(), child.getUniqueId());
    }
}
