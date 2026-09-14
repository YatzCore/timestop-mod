package com.timestop.combat;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class TemporalInteractionEvents {

    public static boolean onAttackEntity(Player player, Entity target) {
        if (target instanceof Projectile projectile) {
            if (player.level().isClientSide) return false;
            if (ProjectileInteraction.redirect(player, projectile)) {
                player.swing(InteractionHand.MAIN_HAND, true);
                return true;
            }
            return false;
        }

        TimeMode mode;
        if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            com.timestop.core.TemporalBubble b = com.timestop.core.TemporalBubbleManager.getDominantBubble(player.level().dimension(), player.position());
            if (b == null || !b.canEntityAct(player)) return false;
            mode = b.getMode();
        } else {
            if (!TimeStopManager.isGlobalTimeStopped() || !TimeStopManager.isEntityExempt(player)) {
                return false;
            }
            mode = TimeStopManager.getCurrentMode();
        }

        if (mode != TimeMode.TIME_STOP && mode != TimeMode.SLOW_MOTION && mode != TimeMode.MATRIX && mode != TimeMode.SUPERHOT) {
            return false;
        }

        if (target instanceof FallingBlockEntity || target instanceof PrimedTnt) {
            if (!player.level().isClientSide) {
                Vec3 look = player.getLookAngle();
                double power = player.getMainHandItem().isEmpty() ? 0.22 : 0.35;
                Vec3 impulse = look.scale(power);

                TemporalKineticBlockManager.recordHit(target, impulse, player);
                player.swing(InteractionHand.MAIN_HAND, true);
            }
            return true;
        }
        return false;
    }

    public static InteractionResult onEntityInteract(Player player, Entity target, InteractionHand hand) {
        TimeMode mode;
        if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            com.timestop.core.TemporalBubble b = com.timestop.core.TemporalBubbleManager.getDominantBubble(player.level().dimension(), player.position());
            if (b == null || !b.canEntityAct(player)) return InteractionResult.PASS;
            mode = b.getMode();
        } else {
            if (!TimeStopManager.isGlobalTimeStopped() || !TimeStopManager.isEntityExempt(player)) {
                return InteractionResult.PASS;
            }
            mode = TimeStopManager.getCurrentMode();
        }

        if (mode != TimeMode.TIME_STOP && mode != TimeMode.SLOW_MOTION && mode != TimeMode.MATRIX && mode != TimeMode.SUPERHOT) {
            return InteractionResult.PASS;
        }

        // BULLET SNATCHING: Plucking suspended or slow-motion projectiles directly into inventory!
        if (target instanceof Projectile projectile) {
            snatchProjectile(projectile, player);
            return InteractionResult.sidedSuccess(player.level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    public static void snatchProjectile(Projectile projectile, Player player) {
        ItemStack stackToGive = getDroppedItemForProjectile(projectile);

        if (!player.level().isClientSide) {
            if (!stackToGive.isEmpty()) {
                if (!player.getInventory().add(stackToGive)) {
                    player.drop(stackToGive, false);
                }
            }
            TimeStopManager.removeSuspendedProjectile(projectile);
            projectile.discard();

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF,
                        projectile.getX(), projectile.getY(), projectile.getZ(),
                        8, 0.1, 0.1, 0.1, 0.05);
            }
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.2F, 1.4F);
        player.level().playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static ItemStack getDroppedItemForProjectile(Projectile projectile) {
        if (TaczProjectileCompat.isBullet(projectile)) return TaczProjectileCompat.ammunition(projectile);
        if (projectile instanceof AbstractArrow arrow) {
            ItemStack stack = arrow.getPickupItemStackOrigin();
            if (!stack.isEmpty()) return stack.copy();
            return new ItemStack(Items.ARROW);
        } else if (projectile instanceof Snowball) {
            return new ItemStack(Items.SNOWBALL);
        } else if (projectile instanceof ThrownEgg) {
            return new ItemStack(Items.EGG);
        } else if (projectile instanceof ThrownEnderpearl) {
            return new ItemStack(Items.ENDER_PEARL);
        } else if (projectile instanceof ThrownExperienceBottle) {
            return new ItemStack(Items.EXPERIENCE_BOTTLE);
        } else if (projectile instanceof ThrownPotion potion) {
            return potion.getItem().copy();
        } else if (projectile instanceof LargeFireball || projectile instanceof SmallFireball || projectile instanceof Fireball) {
            return new ItemStack(Items.FIRE_CHARGE);
        } else if (projectile instanceof FireworkRocketEntity) {
            return new ItemStack(Items.FIREWORK_ROCKET);
        } else {
            // Wither skulls, shulker bullets, dragon breath, and llama spit dissipate safely without duping end-game items
            return ItemStack.EMPTY;
        }
    }
}