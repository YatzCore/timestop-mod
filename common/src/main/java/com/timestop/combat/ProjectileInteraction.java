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

    /** Both the use-entity callback and custom packet share the same authoritative catch. */
    public static boolean snatch(Player player, Projectile projectile) {
        if (player.level().isClientSide || !player.isAlive() || player.isSpectator()
                || player.level() != projectile.level() || !ProjectileCombatHelper.isActiveInFlight(projectile)
                || player.distanceToSqr(projectile) > 25 || TemporalBubbleManager.isEntityInStasis(player)) return false;
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        if (data.getBoolean("KineticPalmCaptured") || data.getBoolean("InStasisOrbit")) return false;
        if (player.level().clip(new ClipContext(player.getEyePosition(), projectile.position(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return false;
        var actorBubble = TemporalBubbleManager.getDominantBubble(player.level().dimension(), player.position());
        if (actorBubble != null && !actorBubble.canEntityAct(player)) return false;
        var bubble = TemporalBubbleManager.getDominantBubble(projectile.level().dimension(), projectile.position());
        TimeMode mode = bubble != null ? bubble.getMode() : actorBubble != null ? actorBubble.getMode()
                : TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.isEntityExempt(player)
                    ? TimeStopManager.getCurrentMode() : null;
        if (mode != TimeMode.TIME_STOP && mode != TimeMode.SLOW_MOTION && mode != TimeMode.MATRIX
                && mode != TimeMode.SUPERHOT && mode != TimeMode.TIME_PREDICTION && mode != TimeMode.COMBAT_SLOW
                && mode != TimeMode.DECELERATION_FIELD && !DecelerationFieldManager.hasDecelerationField(player)) return false;
        TemporalInteractionEvents.snatchProjectile(projectile, player);
        return true;
    }

    public static boolean redirect(Player player, Projectile projectile) {
        if (player.level().isClientSide || player.level() != projectile.level()
                || !ProjectileCombatHelper.isActiveInFlight(projectile)
                || player.getEyePosition().distanceToSqr(projectile.position()) > 64
                || TemporalBubbleManager.isEntityInStasis(player)) return false;
        if (player.level().clip(new ClipContext(player.getEyePosition(), projectile.position(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return false;
        if (com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmCaptured")) {
            if (!KineticPalmManager.releaseCaptured(projectile, player)) return false;
            TimeStopManager.deflectDynamicProjectile(projectile, player);
            return true;
        }
        if (com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("InStasisOrbit")) return false;
        if (TemporalBubbleManager.isEntityInStasis(projectile) || TimeStopManager.isProjectileSuspended(projectile)) {
            TimeStopManager.punchSuspendedProjectile(projectile, player);
            return true;
        }
        var bubble = TemporalBubbleManager.getDominantBubble(projectile.level().dimension(), projectile.position());
        TimeMode mode = bubble != null ? bubble.getMode()
                : TimeStopManager.isGlobalTimeStopActive() ? TimeStopManager.getCurrentMode() : null;
        if (mode == TimeMode.SLOW_MOTION || mode == TimeMode.MATRIX || mode == TimeMode.SUPERHOT
                || (mode == TimeMode.TIME_PREDICTION || mode == TimeMode.COMBAT_SLOW)
                || mode == TimeMode.DECELERATION_FIELD || DecelerationFieldManager.hasDecelerationField(player)) {
            TimeStopManager.deflectDynamicProjectile(projectile, player);
            return true;
        }
        return false;
    }
}
