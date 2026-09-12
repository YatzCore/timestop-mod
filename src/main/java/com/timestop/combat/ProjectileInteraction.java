package com.timestop.combat;

import com.timestop.core.TemporalBubbleManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

public final class ProjectileInteraction {
    private ProjectileInteraction() {}

    public static boolean redirect(Player player, Projectile projectile) {
        if (player.level().isClientSide || player.level() != projectile.level()
                || !ProjectileCombatHelper.isActiveInFlight(projectile)
                || player.getEyePosition().distanceToSqr(projectile.position()) > 64
                || TemporalBubbleManager.isEntityInStasis(player)) return false;
        if (player.level().clip(new ClipContext(player.getEyePosition(), projectile.position(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return false;
        if (projectile.getPersistentData().getBoolean("KineticPalmCaptured")) {
            if (!KineticPalmManager.releaseCaptured(projectile, player)) return false;
            TimeStopManager.deflectDynamicProjectile(projectile, player);
            return true;
        }
        if (projectile.getPersistentData().getBoolean("InStasisOrbit")) return false;
        if (TemporalBubbleManager.isEntityInStasis(projectile) || TimeStopManager.isProjectileSuspended(projectile)) {
            TimeStopManager.punchSuspendedProjectile(projectile, player);
            return true;
        }
        var bubble = TemporalBubbleManager.getDominantBubble(projectile.level().dimension(), projectile.position());
        TimeMode mode = bubble != null ? bubble.getMode()
                : TimeStopManager.isGlobalTimeStopActive() ? TimeStopManager.getCurrentMode() : null;
        if (mode == TimeMode.SLOW_MOTION || mode == TimeMode.MATRIX || mode == TimeMode.SUPERHOT
                || mode == TimeMode.DECELERATION_FIELD || DecelerationFieldManager.hasDecelerationField(player)) {
            TimeStopManager.deflectDynamicProjectile(projectile, player);
            return true;
        }
        return false;
    }
}
