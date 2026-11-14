package com.timestop.combat;

import com.timestop.core.TimeStopSavedData;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ProjectileRedirection {
    private ProjectileRedirection() {}

    public static boolean usesLook(Player player) {
        if (TimeStopSavedData.get().isRedirectToLook()) return true;
        if (RuneManager.hasRune(player, RuneType.VECTOR)) return true;
        // Vector Control is a modifier and can accompany the barrier rune in another watch or hand.
        if (player.getOffhandItem().getItem() instanceof TemporalRuneItem r && r.getType() == RuneType.VECTOR) return true;
        if (player.getMainHandItem().getItem() instanceof TemporalRuneItem r && r.getType() == RuneType.VECTOR) return true;
        for (var stack : com.timestop.platform.Services.PLATFORM.getEquippedWatches(player)) {
            if (stack.getItem() instanceof AbstractWatchItem watch && watch.getTier() != com.timestop.item.WatchTier.CREATIVE
                    && AbstractWatchItem.getSocketedRuneType(stack) == RuneType.VECTOR) return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractWatchItem watch && watch.getTier() != com.timestop.item.WatchTier.CREATIVE
                    && AbstractWatchItem.getSocketedRuneType(stack) == RuneType.VECTOR) return true;
            if (stack.getItem() instanceof TemporalRuneItem r && r.getType() == RuneType.VECTOR) return true;
        }
        return false;
    }

    public static Vec3 direction(Projectile projectile, Player player, Entity shooter, Vec3 originalVelocity) {
        if (usesLook(player)) return lookDirection(projectile, player);
        if (shooter != null && shooter != player && shooter.isAlive()) {
            Vec3 toward = shooter.getEyePosition().subtract(projectile.position());
            if (toward.lengthSqr() > 1e-8) return toward.normalize();
        }
        return originalVelocity.lengthSqr() > 1e-8 ? originalVelocity.reverse().normalize() : player.getLookAngle().normalize();
    }

    /** Resolve the eye's aim ray, then converge from each projectile's own position. */
    public static Vec3 lookDirection(Projectile projectile, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().normalize().scale(256));
        Vec3 target = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(player, eye, target, new AABB(eye, target).inflate(1),
                entity -> isAimTarget(entity, projectile, player), eye.distanceToSqr(target));
        if (hit != null) target = hit.getLocation();
        Vec3 toward = target.subtract(projectile.position());
        return toward.lengthSqr() > 1e-8 ? toward.normalize() : player.getLookAngle().normalize();
    }

    private static boolean isAimTarget(Entity entity, Projectile redirected, Player player) {
        if (entity == redirected || entity.isSpectator() || !entity.isPickable()) return false;
        if (entity instanceof Projectile other) {
            // 1.21 arrows have a large pick radius. Our held volley must not obscure its target.
            if (other.getOwner() == player) return false;
            var data = com.timestop.platform.EntityDataHelper.getPersistentData(other);
            if (data.hasUUID("KineticPalmOwner") && data.getUUID("KineticPalmOwner").equals(player.getUUID())) return false;
        }
        return true;
    }

    /** Gravity would pull a crosshair-directed arrow below its resolved target. */
    public static void setStraightFlight(Projectile projectile, boolean enabled) {
        if (!(projectile instanceof AbstractArrow)) return;
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        if (enabled) {
            if (!data.contains("VectorOriginalNoGravity")) data.putBoolean("VectorOriginalNoGravity", projectile.isNoGravity());
            projectile.setNoGravity(true);
        } else if (data.contains("VectorOriginalNoGravity")) {
            projectile.setNoGravity(data.getBoolean("VectorOriginalNoGravity"));
            data.remove("VectorOriginalNoGravity");
        }
    }

    public static void clearGuidance(Projectile projectile) {
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        data.remove("DeadEyeTaczPrecision");
        data.remove("DeadEyeAimEntity");
        data.remove("DeadEyeTargetEntity");
    }
}
