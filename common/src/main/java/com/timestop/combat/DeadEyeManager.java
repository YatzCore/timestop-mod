package com.timestop.combat;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.rune.RuneType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DeadEyeManager {

    public static final int MAX_TAGS = 6;

    public static class ScheduledVolleyShot {
        public final ServerPlayer player;
        public final DeadEyeTag tag;
        public int delayTicks;
        public int retries;
        public final ItemStack weapon;
        public final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;

        public ScheduledVolleyShot(ServerPlayer player, DeadEyeTag tag, int delayTicks) {
            this.player = player;
            this.tag = tag;
            this.delayTicks = delayTicks;
            this.weapon = isRangedWeapon(player.getMainHandItem()) ? player.getMainHandItem() : player.getOffhandItem();
            this.dimension = player.level().dimension();
        }
    }

    private static final List<ScheduledVolleyShot> activeScheduledShots = new CopyOnWriteArrayList<>();
    private static final List<WeakReference<Arrow>> activeHomingArrows = new CopyOnWriteArrayList<>();

    public static boolean hasDeadEyeRune(Player player) {
        return RuneManager.getSocketedRuneType(player) == RuneType.DEAD_EYE;
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) {
            return true;
        }
        return isGun(stack);
    }

    public static boolean isGun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().getClass().getName().toLowerCase();
        if (name.startsWith("com.tacz.")) return TaczDeadEyeCompat.isGun(stack);
        if (name.contains("gun") || name.contains("tacz") || name.contains("firearm")) {
            return true;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key != null) {
            String id = key.toString().toLowerCase();
            return id.contains("gun") || id.contains("rifle") || id.contains("pistol")
                    || id.contains("shotgun") || id.contains("smg") || id.contains("revolver") || id.contains("sniper");
        }
        return false;
    }

    public static void handleStateChange(ServerPlayer player, boolean active) {
        ServerLevel level = player.serverLevel();
        if (active) {
            if (hasDeadEyeRune(player)) {
                TimeStopManager.startTimeStop(level, player, 160, TimeMode.SLOW_MOTION);
            }
        } else {
            com.timestop.core.TemporalBubbleManager.stopPlayerBubble(level, player.getUUID());
            if (TimeStopManager.isTimeStopped(level) && TimeStopManager.getCurrentMode() == TimeMode.SLOW_MOTION) {
                if (player.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                    TimeStopManager.resumeTime(level);
                }
            }
        }
    }

    public static void executeVolley(ServerPlayer player, List<DeadEyeTag> tags) {
        if (player == null || tags == null || tags.isEmpty()) return;
        if (!hasDeadEyeRune(player)) return;
        ServerLevel level = player.serverLevel();

        com.timestop.core.TemporalBubbleManager.stopPlayerBubble(level, player.getUUID());
        if (TimeStopManager.isTimeStopped(level) && player.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
            TimeStopManager.resumeTime(level);
        }

        activeScheduledShots.removeIf(shot -> shot.player.getUUID().equals(player.getUUID()));
        boolean isGunWeapon = isGun(player.getMainHandItem()) || isGun(player.getOffhandItem());
        int delay = 0;
        int count = Math.min(tags.size(), MAX_TAGS);
        for (int i = 0; i < count; i++) {
            activeScheduledShots.add(new ScheduledVolleyShot(player, tags.get(i), delay));
            delay += isGunWeapon ? 3 : 6;
        }
    }

    public static void serverTick() {
        if (!activeScheduledShots.isEmpty()) {
            java.util.Set<java.util.UUID> waiting = new java.util.HashSet<>();
            Iterator<ScheduledVolleyShot> it = activeScheduledShots.iterator();
            while (it.hasNext()) {
                ScheduledVolleyShot shot = it.next();
                if (!shot.player.isAlive() || shot.player.hasDisconnected() || !hasDeadEyeRune(shot.player)
                        || !shot.player.level().dimension().equals(shot.dimension)
                        || (shot.weapon != shot.player.getMainHandItem() && shot.weapon != shot.player.getOffhandItem())) {
                    activeScheduledShots.remove(shot);
                    continue;
                }
                if (waiting.contains(shot.player.getUUID())) continue;
                if (shot.delayTicks > 0) {
                    shot.delayTicks--;
                    continue;
                }

                if (fireVolleyShot(shot.player, shot.tag) && ++shot.retries < 100) {
                    waiting.add(shot.player.getUUID());
                } else {
                    activeScheduledShots.remove(shot);
                }
            }
        }

        if (!activeHomingArrows.isEmpty()) {
            Iterator<WeakReference<Arrow>> arrowIt = activeHomingArrows.iterator();
            while (arrowIt.hasNext()) {
                WeakReference<Arrow> ref = arrowIt.next();
                Arrow arrow = ref.get();
                if (arrow == null || !arrow.isAlive() || arrow.onGround()) {
                    if (arrow != null) {
                        arrow.setNoGravity(false);
                    }
                    activeHomingArrows.remove(ref);
                    continue;
                }

                int targetId = com.timestop.platform.EntityDataHelper.getPersistentData(arrow).getInt("DeadEyeTargetEntity");
                boolean guided = false;
                if (targetId != 0 && arrow.level() instanceof ServerLevel sl) {
                    Entity target = sl.getEntity(targetId);
                    if (target instanceof LivingEntity living && living.isAlive()) {
                        boolean isHead = com.timestop.platform.EntityDataHelper.getPersistentData(arrow).getBoolean("DeadEyeIsHead");
                        Vec3 targetCoord = isHead ? living.getEyePosition() : living.position().add(0, living.getBbHeight() * 0.65, 0);
                        Vec3 toTarget = targetCoord.subtract(arrow.position()).normalize();

                        double bulletSpeed = 3.8;
                        Vec3 currentVel = arrow.getDeltaMovement();
                        Vec3 guidedVel = currentVel.normalize().scale(0.78).add(toTarget.scale(0.22)).normalize().scale(bulletSpeed);
                        arrow.setDeltaMovement(guidedVel);
                        arrow.hasImpulse = true;
                        guided = true;
                    }
                }
                if (!guided) {
                    arrow.setNoGravity(false);
                    com.timestop.platform.EntityDataHelper.getPersistentData(arrow).remove("DeadEyeTargetEntity");
                    activeHomingArrows.remove(ref);
                }
            }
        }
    }

    private static boolean fireVolleyShot(ServerPlayer player, DeadEyeTag tag) {
        if (!player.isAlive()) return false;
        ServerLevel level = player.serverLevel();

        boolean isGunWeapon = isGun(player.getMainHandItem()) || isGun(player.getOffhandItem());

        if (isGunWeapon) {
            Entity target = level.getEntity(tag.entityId);
            if (!(target instanceof LivingEntity living) || !living.isAlive()
                    || player.distanceToSqr(living) > 48 * 48) return false;
            return TaczDeadEyeCompat.fire(player, living, tag.isHead) == TaczDeadEyeCompat.Result.RETRY;
        }
        if (!hasInfinityOrCreative(player) && !consumeArrow(player)) return false;

        Vec3 eyePos = player.getEyePosition();
        Vec3 targetPos = tag.targetPos;

        Entity targetEntity = level.getEntity(tag.entityId);
        if (targetEntity instanceof LivingEntity living && living.isAlive()) {
            targetPos = tag.isHead
                    ? living.getEyePosition()
                    : living.position().add(0, living.getBbHeight() * 0.65, 0);

            Vec3 targetVel = living.getDeltaMovement();
            double dist = eyePos.distanceTo(targetPos);
            double travelTime = dist / 3.8;
            targetPos = targetPos.add(targetVel.x * travelTime, 0, targetVel.z * travelTime);
        }

        Vec3 dir = targetPos.subtract(eyePos).normalize();

        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), player.getMainHandItem());
        arrow.setPos(eyePos.x, eyePos.y - 0.05, eyePos.z);
        float projectileSpeed = 3.8F;
        arrow.shoot(dir.x, dir.y, dir.z, projectileSpeed, 0.0F);
        arrow.setNoGravity(true);
        arrow.setCritArrow(true);
        double extraDmg = tag.isHead ? 8.0 : 4.0;
        arrow.setBaseDamage(arrow.getBaseDamage() + extraDmg);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        com.timestop.platform.EntityDataHelper.getPersistentData(arrow).putBoolean("DeadEyeArrow", true);

        if (targetEntity != null) {
            com.timestop.platform.EntityDataHelper.getPersistentData(arrow).putInt("DeadEyeTargetEntity", targetEntity.getId());
            com.timestop.platform.EntityDataHelper.getPersistentData(arrow).putBoolean("DeadEyeIsHead", tag.isHead);
            activeHomingArrows.add(new WeakReference<>(arrow));
        } else {
            arrow.setNoGravity(false);
        }

        level.addFreshEntity(arrow);
        player.swing(player.getUsedItemHand(), true);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.4F, 1.1F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.2F, 1.3F);

        level.sendParticles(ParticleTypes.CRIT, eyePos.x, eyePos.y, eyePos.z,
                10, dir.x * 0.4, dir.y * 0.4, dir.z * 0.4, 0.15);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, eyePos.x, eyePos.y, eyePos.z,
                6, dir.x * 0.3, dir.y * 0.3, dir.z * 0.3, 0.1);
        return false;
    }

    public static boolean hasInfinityOrCreative(Player player) {
        if (player.isCreative()) return true;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean hasInfinity = hasInfinityEnchantment(player, main) || hasInfinityEnchantment(player, off);
        return hasInfinity && hasAtLeastOneArrow(player);
    }

    private static boolean hasInfinityEnchantment(Player player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        var lookup = player.level().registryAccess().lookup(net.minecraft.core.registries.Registries.ENCHANTMENT);
        if (lookup.isEmpty()) return false;
        var holder = lookup.get().get(net.minecraft.world.item.enchantment.Enchantments.INFINITY);
        return holder.map(h -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(h, stack) > 0).orElse(false);
    }

    private static boolean hasAtLeastOneArrow(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.ARROW || stack.getItem() == Items.SPECTRAL_ARROW || stack.getItem() == Items.TIPPED_ARROW) {
                return true;
            }
        }
        return false;
    }

    public static int getAvailableArrowCount(Player player) {
        if (player == null) return 0;
        if (isGun(player.getMainHandItem()) || isGun(player.getOffhandItem())) {
            return MAX_TAGS;
        }
        if (hasInfinityOrCreative(player)) return MAX_TAGS;

        int totalArrows = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.ARROW || stack.getItem() == Items.SPECTRAL_ARROW || stack.getItem() == Items.TIPPED_ARROW) {
                totalArrows += stack.getCount();
            }
        }
        return Math.min(MAX_TAGS, totalArrows);
    }

    private static boolean consumeArrow(ServerPlayer player) {
        if (player.isCreative()) return true;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean hasInfinity = hasInfinityEnchantment(player, main) || hasInfinityEnchantment(player, off);

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.ARROW) {
                if (!hasInfinity) {
                    stack.shrink(1);
                }
                return true;
            } else if (stack.getItem() == Items.SPECTRAL_ARROW || stack.getItem() == Items.TIPPED_ARROW) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
