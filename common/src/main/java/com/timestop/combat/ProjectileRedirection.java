package com.timestop.combat;

import com.timestop.core.TimeStopSavedData;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public final class ProjectileRedirection {
    private ProjectileRedirection() {}

    public static boolean usesLook(Player player) {
        if (TimeStopSavedData.get().isRedirectToLook()) return true;
        // Vector Control is a modifier and can accompany the barrier rune in another watch or hand.
        if (player.getOffhandItem().getItem() instanceof TemporalRuneItem r && r.getType() == RuneType.VECTOR) return true;
        if (player.getMainHandItem().getItem() instanceof TemporalRuneItem r && r.getType() == RuneType.VECTOR) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractWatchItem
                    && AbstractWatchItem.getSocketedRuneType(stack) == RuneType.VECTOR) return true;
            if (stack.getItem() instanceof TemporalRuneItem r && r.getType() == RuneType.VECTOR) return true;
        }
        return false;
    }

    public static Vec3 direction(Projectile projectile, Player player, Entity shooter, Vec3 originalVelocity) {
        if (usesLook(player)) return player.getLookAngle().normalize();
        if (shooter != null && shooter != player && shooter.isAlive()) {
            Vec3 toward = shooter.getEyePosition().subtract(projectile.position());
            if (toward.lengthSqr() > 1e-8) return toward.normalize();
        }
        return originalVelocity.lengthSqr() > 1e-8 ? originalVelocity.reverse().normalize() : player.getLookAngle().normalize();
    }

    public static void clearGuidance(Projectile projectile) {
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        data.remove("DeadEyeTaczPrecision");
        data.remove("DeadEyeAimEntity");
        data.remove("DeadEyeTargetEntity");
    }
}
