package com.timestop.combat;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.timestop.TimeStopMod;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TimeStopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TemporalInteractionEvents {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();

        TimeMode mode;
        if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            com.timestop.core.TemporalBubble b = com.timestop.core.TemporalBubbleManager.getDominantBubble(player.level().dimension(), player.position());
            if (b == null || !b.canEntityAct(player)) return;
            mode = b.getMode();
        } else {
            if (!TimeStopManager.isGlobalTimeStopped() || !TimeStopManager.isEntityExempt(player)) {
                return;
            }
            mode = TimeStopManager.getCurrentMode();
        }

        if (mode != TimeMode.TIME_STOP && mode != TimeMode.SLOW_MOTION && mode != TimeMode.MATRIX && mode != TimeMode.SUPERHOT) {
            return;
        }

        // Cancel vanilla attack to prevent damage & server-side invalid entity kick
        if (target instanceof Projectile projectile) {
            event.setCanceled(true);

            if (!player.level().isClientSide && player.level() instanceof ServerLevel serverLevel) {
                if (mode == TimeMode.TIME_STOP) {
                    TimeStopManager.punchSuspendedProjectile(projectile, player);
                } else {
                    TimeStopManager.deflectDynamicProjectile(projectile, player);
                }

                player.swing(InteractionHand.MAIN_HAND, true);
            }
            return;
        }

        if (target instanceof FallingBlockEntity || target instanceof PrimedTnt) {
            event.setCanceled(true);

            if (!player.level().isClientSide) {
                Vec3 look = player.getLookAngle();
                double power = player.getMainHandItem().isEmpty() ? 0.22 : 0.35;
                Vec3 impulse = look.scale(power);

                TemporalKineticBlockManager.recordHit(target, impulse, player);
                player.swing(InteractionHand.MAIN_HAND, true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();

        TimeMode mode;
        if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            com.timestop.core.TemporalBubble b = com.timestop.core.TemporalBubbleManager.getDominantBubble(player.level().dimension(), player.position());
            if (b == null || !b.canEntityAct(player)) return;
            mode = b.getMode();
        } else {
            if (!TimeStopManager.isGlobalTimeStopped() || !TimeStopManager.isEntityExempt(player)) {
                return;
            }
            mode = TimeStopManager.getCurrentMode();
        }

        if (mode != TimeMode.TIME_STOP && mode != TimeMode.SLOW_MOTION && mode != TimeMode.MATRIX && mode != TimeMode.SUPERHOT) {
            return;
        }

        // BULLET SNATCHING: Plucking suspended or slow-motion projectiles directly into inventory!
        if (target instanceof Projectile projectile) {
            snatchProjectile(projectile, player);
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide()));
            event.setCanceled(true);
        }
    }

    public static void snatchProjectile(Projectile projectile, Player player) {
        ItemStack stackToGive = getDroppedItemForProjectile(projectile);

        if (!player.level().isClientSide) {
            if (!player.getInventory().add(stackToGive)) {
                player.drop(stackToGive, false);
            }
            TimeStopManager.removeSuspendedProjectile(projectile);
            projectile.discard();

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF,
                        projectile.getX(), projectile.getY(), projectile.getZ(),
                        8, 0.1, 0.1, 0.1, 0.05);
            }
        } else {
            projectile.discard();
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.2F, 1.4F);
        player.level().playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);

        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static ItemStack getDroppedItemForProjectile(Projectile projectile) {
        if (projectile instanceof Arrow arrow) {
            if (arrow.getColor() > 0) {
                return new ItemStack(Items.TIPPED_ARROW);
            }
            return new ItemStack(Items.ARROW);
        } else if (projectile instanceof SpectralArrow) {
            return new ItemStack(Items.SPECTRAL_ARROW);
        } else if (projectile instanceof ThrownTrident) {
            return new ItemStack(Items.TRIDENT);
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
        } else if (projectile instanceof DragonFireball) {
            return new ItemStack(Items.DRAGON_BREATH);
        } else if (projectile instanceof WitherSkull) {
            return new ItemStack(Items.WITHER_SKELETON_SKULL);
        } else if (projectile instanceof LargeFireball || projectile instanceof SmallFireball || projectile instanceof Fireball) {
            return new ItemStack(Items.FIRE_CHARGE);
        } else if (projectile instanceof ShulkerBullet) {
            return new ItemStack(Items.SHULKER_SHELL);
        } else if (projectile instanceof LlamaSpit) {
            return new ItemStack(Items.SLIME_BALL);
        } else if (projectile instanceof FireworkRocketEntity) {
            return new ItemStack(Items.FIREWORK_ROCKET);
        } else {
            return new ItemStack(Items.ARROW);
        }
    }
}
