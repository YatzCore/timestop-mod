package com.timestop.combat;

import com.timestop.core.TimeStopManager;
import com.timestop.entity.ChronoCoinEntity;
import com.timestop.item.rune.RuneType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Optional native projectile integration; no TacZ classes load when the mod is absent. */
public final class TaczProjectileCompat {
    private TaczProjectileCompat() {}
    public static boolean isBullet(Projectile p) {
        return p.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet");
    }

    public static boolean beforeTick(Projectile bullet) {
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(bullet);
        if (!bullet.isAlive() || data.getBoolean("KineticPalmCaptured") || data.getBoolean("InStasisOrbit")) return true;
        if (!(bullet.level() instanceof ServerLevel level)) return false;
        KineticPalmManager.interceptIncoming(bullet);
        OrbitalProjectileManager.interceptIncoming(bullet);
        if (data.getBoolean("KineticPalmCaptured") || data.getBoolean("InStasisOrbit")) return true;
        if (TimeStopManager.isProjectileSuspended(bullet)) return true;
        Vec3 start = bullet.position();
        Vec3 end = start.add(bullet.getDeltaMovement());
        var wall = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bullet));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        // Sweep defenses along the whole native step: bullets can cross a field in one tick.
        net.minecraft.world.entity.player.Player defender = null;
        Vec3 entry = null;
        double closest = Double.MAX_VALUE;
        for (var player : level.players()) {
            if (player == bullet.getOwner() || !player.isAlive() || player.isSpectator()) continue;
            RuneType rune = RuneManager.getSocketedRuneType(player);
            double radius = switch (rune == null ? RuneType.BLANK : rune) {
                case DEFLECTION -> 3.4;
                case SNATCHING -> 2.9;
                case PHASING -> 2.4;
                default -> 0;
            };
            radius = Math.min(radius, DecelerationFieldManager.getDecelerationRadius(player));
            if (radius <= 0) continue;
            Vec3 center = player.position().add(0, player.getEyeHeight() * 0.5, 0);
            Vec3 travel = end.subtract(start);
            double t = travel.lengthSqr() < 1e-12 ? 0 : Math.max(0, Math.min(1, center.subtract(start).dot(travel) / travel.lengthSqr()));
            Vec3 near = start.add(travel.scale(t));
            if (near.distanceToSqr(center) > radius * radius) continue;
            // First entry into the defense sphere, not its far side or the player's body.
            double length = travel.length();
            double offset = Math.sqrt(Math.max(0, radius * radius - near.distanceToSqr(center)));
            Vec3 point = length < 1e-12 ? start : start.add(travel.scale(Math.max(0, t - offset / length)));
            if (start.distanceToSqr(point) < closest) {
                closest = start.distanceToSqr(point); defender = player; entry = point;
            }
        }
        if (defender != null) {
            bullet.setPos(entry);
            RuneManager.evaluateRuneDefense(bullet, defender);
            if (!bullet.isAlive() || bullet.getOwner() == defender || data.getBoolean("InStasisOrbit")) return true;
            bullet.setPos(start);
        }
        // Stop at entry for one tick when a fast round would otherwise cross an entire field.
        for (var player : level.players()) {
            if (player == bullet.getOwner() || !player.isAlive() || player.isSpectator()) continue;
            double radius = DecelerationFieldManager.getDecelerationRadius(player);
            if (radius <= 0) continue;
            Vec3 center = player.position().add(0, player.getEyeHeight() * 0.5, 0);
            if (start.distanceToSqr(center) <= radius * radius) continue;
            Vec3 travel = end.subtract(start);
            double length = travel.length();
            if (length < 1e-8) continue;
            double t = Math.max(0, Math.min(1, center.subtract(start).dot(travel) / travel.lengthSqr()));
            Vec3 near = start.add(travel.scale(t));
            if (near.distanceToSqr(center) >= radius * radius) continue;
            double entryT = Math.max(0, t - Math.sqrt(radius * radius - near.distanceToSqr(center)) / length);
            bullet.setPos(start.add(travel.scale(Math.min(1, entryT + 0.00001))));
            bullet.hasImpulse = true;
            return true;
        }
        // Coins use a swept hit box so native high-speed bullets cannot tunnel through them.
        ChronoCoinEntity coinHit = null;
        double coinDistance = Double.MAX_VALUE;
        for (var coin : level.getEntitiesOfClass(ChronoCoinEntity.class, new AABB(start, end).inflate(0.5), c -> c.isAlive() && !c.onGround())) {
            var box = coin.getBoundingBox().inflate(0.4);
            var hit = box.contains(start) ? java.util.Optional.of(start) : box.clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < coinDistance) {
                coinDistance = start.distanceToSqr(hit.get()); coinHit = coin;
            }
        }
        if (coinHit != null) {
            coinHit.triggerRicoshot(bullet.getOwner() == null ? bullet : bullet.getOwner());
            bullet.discard();
            return true;
        }
        return false;
    }

    public static ItemStack ammunition(Projectile bullet) {
        if (!isBullet(bullet)) return ItemStack.EMPTY;
        try {
            Object id = bullet.getClass().getMethod("getAmmoId").invoke(bullet);
            Class<?> type = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder");
            Object builder = type.getMethod("create").invoke(null);
            type.getMethod("setId", net.minecraft.resources.ResourceLocation.class).invoke(builder, id);
            type.getMethod("setCount", int.class).invoke(builder, 1);
            return (ItemStack) type.getMethod("build").invoke(builder);
        } catch (ReflectiveOperationException e) {
            return ItemStack.EMPTY;
        }
    }
}
