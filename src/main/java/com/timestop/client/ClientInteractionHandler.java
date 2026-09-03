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
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Optional;

public class ClientInteractionHandler {

    private static boolean canSingleFireWithItem(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof com.timestop.item.AbstractWatchItem;
    }

    @SubscribeEvent
    public void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 0. LEFT-CLICK (Empty hand or Watch): Fire 1 orbiting projectile at crosshair!
        if (event.isAttack() && canSingleFireWithItem(mc.player.getMainHandItem()) && CapturedProjectilesOverlay.getOrbitCount() > 0) {
            if (trySingleFire(mc.player)) {
                event.setCanceled(true);
                event.setSwingHand(true);
                mc.player.swing(InteractionHand.MAIN_HAND);
                ModMessages.sendToServer(new com.timestop.network.SingleFireProjectilePacket());
                return;
            }
        }

        // 0b. EMPTY-HAND RIGHT-CLICK: Trigger Boogie Woogie swap if looking at target!
        if (event.isUseItem() && mc.player.getMainHandItem().isEmpty() && com.timestop.combat.TranspositionManager.hasTranspositionRune(mc.player)) {
            if (com.timestop.combat.TranspositionManager.findSwapTargetClient(mc.player) != null) {
                event.setCanceled(true);
                event.setSwingHand(true);
                mc.player.swing(InteractionHand.MAIN_HAND);
                ModMessages.sendToServer(new com.timestop.network.TranspositionSwapPacket(mc.player.isCrouching()));
                TranspositionRenderer.triggerSwapFlash();
                return;
            }
        }

        boolean fieldActive = com.timestop.combat.DecelerationFieldManager.hasDecelerationField(mc.player);
        boolean timeActive = ClientTimeStopManager.isTimeStopped();

        if (!timeActive && !fieldActive) {
            return;
        }

        TimeMode mode = timeActive ? ClientTimeStopManager.getCurrentMode() : TimeMode.DECELERATION_FIELD;
        if (mode != TimeMode.TIME_STOP && mode != TimeMode.SLOW_MOTION && mode != TimeMode.MATRIX && mode != TimeMode.SUPERHOT && mode != TimeMode.DECELERATION_FIELD) {
            return;
        }

        // 1. LEFT-CLICK (Attack / Deflect / Slap)
        if (event.isAttack()) {
            Projectile targetedProjectile = findTargetedProjectile(mc);
            if (targetedProjectile != null) {
                event.setCanceled(true);
                event.setSwingHand(true);

                ModMessages.sendToServer(new SlapProjectilePacket(targetedProjectile.getId(), mc.player.getLookAngle()));
                mc.player.swing(InteractionHand.MAIN_HAND);

                // Immediate client visual rotation back toward origin/shooter with vanilla formula
                Entity owner = targetedProjectile.getOwner();
                Vec3 returnDir;
                if (owner != null && owner.isAlive()) {
                    returnDir = owner.getEyePosition().subtract(targetedProjectile.position()).normalize();
                } else if (targetedProjectile.getDeltaMovement().lengthSqr() > 1e-5) {
                    returnDir = targetedProjectile.getDeltaMovement().reverse().normalize();
                } else {
                    returnDir = mc.player.getLookAngle().normalize();
                }

                double horiz = Math.sqrt(returnDir.x * returnDir.x + returnDir.z * returnDir.z);
                float yRot = (float) (net.minecraft.util.Mth.atan2(returnDir.x, returnDir.z) * (180.0D / Math.PI));
                float xRot = (float) (net.minecraft.util.Mth.atan2(returnDir.y, horiz) * (180.0D / Math.PI));
                targetedProjectile.setYRot(yRot);
                targetedProjectile.setXRot(xRot);
                targetedProjectile.yRotO = yRot;
                targetedProjectile.xRotO = xRot;

                if (mode == TimeMode.TIME_STOP) {
                    targetedProjectile.setDeltaMovement(Vec3.ZERO);
                    targetedProjectile.setNoGravity(true);
                } else {
                    double speed = Math.max(1.8, targetedProjectile.getDeltaMovement().length() * 1.35);
                    targetedProjectile.setDeltaMovement(returnDir.scale(speed));
                    targetedProjectile.hasImpulse = true;
                }
                return;
            }

            // Check Falling Blocks & Primed TNT
            HitResult hit = mc.hitResult;
            if (hit instanceof EntityHitResult entityHit) {
                Entity target = entityHit.getEntity();
                if (target instanceof FallingBlockEntity || target instanceof PrimedTnt) {
                    event.setCanceled(true);
                    event.setSwingHand(true);

                    ModMessages.sendToServer(new KineticBlockPunchPacket(target.getId(), mc.player.getLookAngle()));
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    return;
                }
            }
        }

        // 2. RIGHT-CLICK (Use / Snatch / Pluck into Inventory)
        if (event.isUseItem()) {
            Projectile targetedProjectile = findTargetedProjectile(mc);
            if (targetedProjectile != null) {
                event.setCanceled(true);
                event.setSwingHand(true);

                ModMessages.sendToServer(new SnatchProjectilePacket(targetedProjectile.getId()));

                // Immediate client-side feedback
                mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.2F, 1.4F);
                mc.level.playSound(mc.player, targetedProjectile.getX(), targetedProjectile.getY(), targetedProjectile.getZ(),
                        SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);

                mc.player.swing(event.getHand());
            }
        }
    }

    /**
     * Highly responsive projectile finder: first checks mc.hitResult, and if missed or obstructed,
     * performs a focused raycast with an expanded bounding box against all active projectiles within 4.5 blocks.
     */
    private Projectile findTargetedProjectile(Minecraft mc) {
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
            // Expand bounding box generously so players easily snatch or slap projectiles
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

    @SubscribeEvent
    public void onLeftClickEmpty(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !canSingleFireWithItem(mc.player.getMainHandItem())) return;
        if (CapturedProjectilesOverlay.getOrbitCount() > 0) {
            if (trySingleFire(mc.player)) {
                mc.player.swing(InteractionHand.MAIN_HAND);
                ModMessages.sendToServer(new com.timestop.network.SingleFireProjectilePacket());
            }
        }
    }
}
