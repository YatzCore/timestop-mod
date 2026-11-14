package com.timestop.prediction;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The centre of a native archer's current aim, read without firing or advancing its random source. */
public final class PredictionPreparedShots {
    private PredictionPreparedShots() {}

    public static boolean prepared(PredictionMotion motion) { return motion.entityId() < -1; }

    public static PredictionMotion capture(Mob shooter) {
        var target = shooter.getTarget();
        if (target == null || !target.isAlive() || !shooter.hasLineOfSight(target)) return null;
        var weapon = shooter.isUsingItem() ? shooter.getUseItem() : shooter.getMainHandItem();
        boolean bow = shooter instanceof AbstractSkeleton && shooter.isUsingItem() && weapon.getItem() instanceof BowItem;
        boolean crossbow = shooter instanceof Pillager && weapon.getItem() instanceof CrossbowItem
                && (shooter.isUsingItem() || CrossbowItem.isCharged(weapon))
                && !CrossbowItem.containsChargedProjectile(weapon, Items.FIREWORK_ROCKET);
        if (!bow && !crossbow) return null;
        Vec3 origin = shooter.position().add(0, shooter.getEyeHeight() - .1, 0);
        double dx = target.getX() - shooter.getX(), dz = target.getZ() - shooter.getZ();
        double dy = target.getY(1.0 / 3.0) - origin.y + Math.sqrt(dx * dx + dz * dz) * (double).2F;
        Vec3 direction = new Vec3(dx, dy, dz);
        if (direction.lengthSqr() < 1e-8) return null;
        return new PredictionMotion(-2 - shooter.getId(), shooter.getId(), PredictionMotion.Kind.ARROW,
                origin, direction.normalize().scale((double)1.6F), Vec3.ZERO, .05, .99, .6,
                .5F, .5F, false, false, false, 0, PredictionServer.timeRate(shooter));
    }
}
