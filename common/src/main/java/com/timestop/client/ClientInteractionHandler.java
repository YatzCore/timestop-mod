package com.timestop.client;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import com.timestop.network.KineticBlockPunchPacket;
import com.timestop.network.ModMessages;
import com.timestop.network.SlapProjectilePacket;
import com.timestop.network.SnatchProjectilePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class ClientInteractionHandler {

    public static boolean canSingleFireWithItem(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof com.timestop.item.AbstractWatchItem;
    }

    public static boolean onAttackKey(Minecraft mc) {
        if (mc.player == null || mc.level == null) return false;

        if (com.timestop.combat.KineticPalmManager.isGuarding(mc.player)) {
            return true;
        }

        // 0. LEFT-CLICK (Empty hand or Watch): Fire 1 orbiting projectile at crosshair!
        if (canSingleFireWithItem(mc.player.getMainHandItem()) && CapturedProjectilesOverlay.getOrbitCount() > 0) {
            if (trySingleFire(mc.player)) {
                mc.player.swing(InteractionHand.MAIN_HAND);
                ModMessages.sendToServer(new com.timestop.network.SingleFireProjectilePacket());
                return true;
            }
        }

        boolean fieldActive = com.timestop.combat.DecelerationFieldManager.hasDecelerationField(mc.player);
        boolean timeActive = ClientTimeStopManager.isTimeStopped();

        if (!timeActive && !fieldActive) {
            return false;
        }

        TimeMode mode = timeActive ? ClientTimeStopManager.getCurrentMode() : TimeMode.DECELERATION_FIELD;
        if (mode != TimeMode.TIME_STOP && mode != TimeMode.SLOW_MOTION && mode != TimeMode.MATRIX && mode != TimeMode.SUPERHOT && mode != TimeMode.DECELERATION_FIELD) {
            return false;
        }

        // 1. LEFT-CLICK (Attack / Deflect / Slap)
        Projectile targetedProjectile = findTargetedProjectile(mc);
        if (targetedProjectile != null) {
            ModMessages.sendToServer(new SlapProjectilePacket(targetedProjectile.getId(), mc.player.getLookAngle()));
            mc.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }

        // Check Falling Blocks & Primed TNT
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (target instanceof FallingBlockEntity || target instanceof PrimedTnt) {
                ModMessages.sendToServer(new KineticBlockPunchPacket(target.getId(), mc.player.getLookAngle()));
                mc.player.swing(InteractionHand.MAIN_HAND);
                return true;
            }
        }

        return false;
    }

    public static boolean onUseItemKey(Minecraft mc, InteractionHand hand) {
        if (mc.player == null || mc.level == null) return false;

        // 0b. EMPTY-HAND RIGHT-CLICK: Trigger Boogie Woogie swap if looking at target!
        if (mc.player.getMainHandItem().isEmpty() && com.timestop.combat.TranspositionManager.hasTranspositionRune(mc.player)) {
            if (com.timestop.combat.TranspositionManager.findSwapTargetClient(mc.player) != null) {
                mc.player.swing(InteractionHand.MAIN_HAND);
                ModMessages.sendToServer(new com.timestop.network.TranspositionSwapPacket(mc.player.isCrouching()));
                TranspositionRenderer.triggerSwapFlash();
                return true;
            }
        }

        boolean fieldActive = com.timestop.combat.DecelerationFieldManager.hasDecelerationField(mc.player);
        boolean timeActive = ClientTimeStopManager.isTimeStopped();

        if (!timeActive && !fieldActive) {
            return false;
        }

        TimeMode mode = timeActive ? ClientTimeStopManager.getCurrentMode() : TimeMode.DECELERATION_FIELD;
        if (mode != TimeMode.TIME_STOP && mode != TimeMode.SLOW_MOTION && mode != TimeMode.MATRIX && mode != TimeMode.SUPERHOT && mode != TimeMode.DECELERATION_FIELD) {
            return false;
        }

        // 2. RIGHT-CLICK (Use / Snatch / Pluck into Inventory)
        Projectile targetedProjectile = findTargetedProjectile(mc);
        if (targetedProjectile != null) {
            ModMessages.sendToServer(new SnatchProjectilePacket(targetedProjectile.getId()));

            // Immediate client-side feedback
            mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.2F, 1.4F);
            mc.level.playSound(mc.player, targetedProjectile.getX(), targetedProjectile.getY(), targetedProjectile.getZ(),
                    SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);

            if (hand != null) {
                mc.player.swing(hand);
            }
            return true;
        }

        return false;
    }

    public static void onLeftClickEmpty() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !canSingleFireWithItem(mc.player.getMainHandItem())) return;
        if (CapturedProjectilesOverlay.getOrbitCount() > 0) {
            if (trySingleFire(mc.player)) {
                mc.player.swing(InteractionHand.MAIN_HAND);
                ModMessages.sendToServer(new com.timestop.network.SingleFireProjectilePacket());
            }
        }
    }

    private static Projectile findTargetedProjectile(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;

        // 1. Direct hit result
        if (mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Projectile p && com.timestop.combat.ProjectileCombatHelper.isActiveInFlight(p)) {
            return p;
        }

        // 2. Generous raycast fallback for high-speed / small projectiles (Blaze fireballs, Wither skulls, etc.)
        Vec3 eyePos = mc.player.getEyePosition(1.0F);
        Vec3 viewVec = mc.player.getViewVector(1.0F);
        double reach = 4.5;
        Vec3 reachVec = eyePos.add(viewVec.scale(reach));
        AABB searchBox = mc.player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(2.0);

        List<Projectile> nearby = mc.level.getEntitiesOfClass(Projectile.class, searchBox, e -> !e.isSpectator() && com.timestop.combat.ProjectileCombatHelper.isActiveInFlight(e));
        Projectile best = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (Projectile p : nearby) {
            AABB aabb = p.getBoundingBox().inflate(0.65);
            Optional<Vec3> clip = aabb.clip(eyePos, reachVec);
            if (clip.isPresent()) {
                double dist = eyePos.distanceToSqr(clip.get());
                if (dist < bestDistSqr) {
                    bestDistSqr = dist;
                    best = p;
                }
            }
        }

        return best;
    }

    private static long lastSingleFireTick = -1;

    public static boolean trySingleFire(net.minecraft.world.entity.player.Player player) {
        long current = player.tickCount;
        if (current == lastSingleFireTick) return false;
        lastSingleFireTick = current;
        return true;
    }
}