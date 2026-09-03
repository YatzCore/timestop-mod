package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void onIsPickable(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        boolean timeActive = entity.level().isClientSide ? ClientTimeStopManager.isTimeStopped() : TimeStopManager.isGlobalTimeStopped();
        if (timeActive) {
            TimeMode mode = entity.level().isClientSide ? ClientTimeStopManager.getCurrentMode() : TimeStopManager.getCurrentMode();
            if (mode == TimeMode.TIME_STOP || mode == TimeMode.SLOW_MOTION || mode == TimeMode.MATRIX || mode == TimeMode.SUPERHOT || mode == TimeMode.DECELERATION_FIELD) {
                if (entity instanceof Projectile || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt) {
                    if (entity.onGround()) {
                        return;
                    }
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (entity instanceof Projectile p && !entity.onGround() && com.timestop.combat.DecelerationFieldManager.isDecelerated(p)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getPickRadius", at = @At("HEAD"), cancellable = true)
    private void onGetPickRadius(CallbackInfoReturnable<Float> cir) {
        Entity entity = (Entity) (Object) this;
        boolean timeActive = entity.level().isClientSide ? ClientTimeStopManager.isTimeStopped() : TimeStopManager.isGlobalTimeStopped();
        if (timeActive) {
            TimeMode mode = entity.level().isClientSide ? ClientTimeStopManager.getCurrentMode() : TimeStopManager.getCurrentMode();
            if (mode == TimeMode.TIME_STOP || mode == TimeMode.SLOW_MOTION || mode == TimeMode.MATRIX || mode == TimeMode.SUPERHOT || mode == TimeMode.DECELERATION_FIELD) {
                if (entity instanceof Projectile) {
                    if (entity.onGround()) {
                        return;
                    }
                    // Generously expand projectile raycast pick radius so hitting moving projectiles in bullet-time is fun & responsive
                    cir.setReturnValue(0.45F);
                    return;
                }
            }
        }

        if (entity instanceof Projectile p && !entity.onGround() && com.timestop.combat.DecelerationFieldManager.isDecelerated(p)) {
            cir.setReturnValue(0.45F);
        }
    }

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && com.timestop.combat.TranspositionManager.hasTranspositionRune(mc.player)) {
                if (com.timestop.client.TranspositionRenderer.isTargetOutlined(entity)) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && com.timestop.combat.TranspositionManager.hasTranspositionRune(mc.player)) {
                int color = com.timestop.client.TranspositionRenderer.getTargetOutlineColor(entity);
                if (color != -1) {
                    cir.setReturnValue(color);
                }
            }
        }
    }
}
