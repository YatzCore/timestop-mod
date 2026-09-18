package com.timestop.mixin;

import com.timestop.core.rewind.RewindEntitySync;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
    @Shadow @Final private Entity entity;
    @Shadow private int teleportDelay;

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void timestop$resetRelativeMovement(CallbackInfo ci) {
        if (RewindEntitySync.consume(entity)) {
            teleportDelay = 400;
            entity.hasImpulse = true;
        }
    }
}
