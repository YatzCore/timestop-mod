package com.timestop.fabric.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class FabricProjectileImpactMixin {
    @org.spongepowered.asm.mixin.Unique private boolean timestop$cancelledImpact;

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void timestop$skipLastRicochetVictim(net.minecraft.world.entity.Entity target,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        var data = com.timestop.platform.EntityDataHelper.getPersistentData((Projectile) (Object) this);
        var hits = data.getList("RicochetHitList", net.minecraft.nbt.Tag.TAG_INT);
        if (!hits.isEmpty() && hits.getInt(hits.size() - 1) == target.getId()) cir.setReturnValue(false);
    }

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void timestop$onHit(HitResult hitResult, CallbackInfo ci) {
        timestop$cancelledImpact = false;
        Projectile projectile = (Projectile) (Object) this;
        if (com.timestop.combat.OrbitalProjectileManager.onProjectileImpact(projectile, hitResult)) {
            timestop$cancelledImpact = true;
            ci.cancel();
            return;
        }
        com.timestop.combat.VolatileStasisHandler.onProjectileImpact(projectile);
        if (com.timestop.combat.KineticPalmManager.onDroppedProjectileImpact(projectile)) {
            timestop$cancelledImpact = true;
            ci.cancel();
            return;
        }
    }

    @Inject(method = "onHit", at = @At("RETURN"))
    private void timestop$ricochetAfterDamage(HitResult hit, CallbackInfo ci) {
        if (timestop$cancelledImpact) return;
        Projectile original = (Projectile) (Object) this;
        if (!(original instanceof net.minecraft.world.entity.projectile.AbstractArrow)
                || !(original.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !(hit instanceof net.minecraft.world.phys.EntityHitResult)
                || !(original.getOwner() instanceof net.minecraft.world.entity.player.Player player)
                || !com.timestop.combat.RuneManager.hasRune(player, com.timestop.item.rune.RuneType.RICOCHET)) return;

        var copy = original.getType().create(level);
        if (!(copy instanceof net.minecraft.world.entity.projectile.AbstractArrow next)) return;
        var data = original.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        data.remove("UUID");
        next.load(data);
        next.setOwner(player);
        if (com.timestop.combat.VoltaicRicochetHandler.onProjectileImpact(next, hit)) {
            original.discard();
            if (next.isAlive()) level.addFreshEntity(next);
        } else {
            next.discard();
        }
    }
}
