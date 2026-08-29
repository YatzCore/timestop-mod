package com.timestop.combat;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.rune.RuneType;
import com.timestop.network.DeadEyeExecutePacket;
import com.timestop.network.DeadEyeStatePacket;
import com.timestop.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.timestop.TimeStopMod;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TimeStopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DeadEyeManager {

    public static final int MAX_TAGS = 6;

    // Client-side tracking
    public static boolean clientAiming = false;
    public static final List<DeadEyeTag> clientTags = new ArrayList<>();
    private static int lastHeartbeatTick = 0;

    // Server-side sequential volley scheduler
    public static class ScheduledVolleyShot {
        public final ServerPlayer player;
        public final DeadEyeTag tag;
        public int delayTicks;

        public ScheduledVolleyShot(ServerPlayer player, DeadEyeTag tag, int delayTicks) {
            this.player = player;
            this.tag = tag;
            this.delayTicks = delayTicks;
        }
    }

    private static final List<ScheduledVolleyShot> activeScheduledShots = new CopyOnWriteArrayList<>();
    private static final List<WeakReference<Arrow>> activeHomingArrows = new CopyOnWriteArrayList<>();

    public static boolean hasDeadEyeRune(Player player) {
        return RuneManager.getSocketedRuneType(player) == RuneType.DEAD_EYE;
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    // ==========================================
    // CLIENT-SIDE AIMING & TARGET PAINTING
    // ==========================================

    public static final net.minecraft.resources.ResourceLocation SEPIA_SHADER = new net.minecraft.resources.ResourceLocation("timestop", "shaders/post/sepia.json");
    private static boolean deadEyeShaderActive = false;

    public static void applyDeadEyeShader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && !deadEyeShaderActive) {
            try {
                mc.gameRenderer.loadEffect(SEPIA_SHADER);
                deadEyeShaderActive = true;
            } catch (Exception ignored) {
            }
        }
    }

    public static void removeDeadEyeShader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && deadEyeShaderActive) {
            try {
                mc.gameRenderer.shutdownEffect();
                deadEyeShaderActive = false;
                if (com.timestop.core.ClientTimeStopManager.isTimeStopped() && com.timestop.core.ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
                    com.timestop.core.ClientTimeStopManager.applyShader();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void clientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            if (clientAiming) stopClientAiming(false);
            return;
        }

        ItemStack useItem = mc.player.getUseItem();
        boolean isDrawing = mc.player.isUsingItem() && isRangedWeapon(useItem) && hasDeadEyeRune(mc.player);

        if (isDrawing) {
            if (!clientAiming) {
                // Enter Dead Eye
                clientAiming = true;
                clientTags.clear();
                applyDeadEyeShader();
                ModMessages.sendToServer(new DeadEyeStatePacket(true));
                mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 1.0F);
                lastHeartbeatTick = mc.player.tickCount;
            }

            // Periodic heartbeat audio
            if (mc.player.tickCount - lastHeartbeatTick >= 22) {
                mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.3F, 1.0F);
                lastHeartbeatTick = mc.player.tickCount;
            }

            // Target painting raycast capped by actual available arrows (up to 6 max)
            int maxAllowed = getAvailableArrowCount(mc.player);
            if (clientTags.size() < maxAllowed) {
                paintTargetUnderCrosshair(mc, maxAllowed);
            }
        } else {
            if (clientAiming) {
                // Weapon released or cancelled
                stopClientAiming(true);
            }
        }
    }

    private static void stopClientAiming(boolean executeIfTagged) {
        clientAiming = false;
        removeDeadEyeShader();
        ModMessages.sendToServer(new DeadEyeStatePacket(false));

        if (executeIfTagged && !clientTags.isEmpty()) {
            ModMessages.sendToServer(new DeadEyeExecutePacket(new ArrayList<>(clientTags)));
        }
        clientTags.clear();
    }

    private static void paintTargetUnderCrosshair(Minecraft mc, int maxAllowed) {
        if (clientTags.size() >= maxAllowed) return;

        Vec3 eyePos = mc.player.getEyePosition(1.0F);
        Vec3 viewVec = mc.player.getViewVector(1.0F);
        double reach = 48.0;
        Vec3 reachVec = eyePos.add(viewVec.scale(reach));
        AABB searchBox = mc.player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(2.0);

        List<LivingEntity> entities = mc.level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != mc.player && e.isAlive() && !e.isSpectator());

        LivingEntity bestEntity = null;
        Vec3 bestHit = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (LivingEntity e : entities) {
            AABB bb = e.getBoundingBox().inflate(0.35);
            Optional<Vec3> clip = bb.clip(eyePos, reachVec);
            if (clip.isPresent()) {
                double dist = eyePos.distanceToSqr(clip.get());
                if (dist < bestDistSqr) {
                    bestDistSqr = dist;
                    bestEntity = e;
                    bestHit = clip.get();
                }
            }
        }

        if (bestEntity != null && bestHit != null) {
            double headThreshold = bestEntity.getY() + bestEntity.getBbHeight() * 0.7;
            boolean isHead = bestHit.y >= headThreshold;
            Vec3 targetPos = isHead ? bestEntity.getEyePosition() : bestEntity.position().add(0, bestEntity.getBbHeight() * 0.65, 0);

            // Check if spot already tagged
            final int entityId = bestEntity.getId();
            final boolean headFlag = isHead;
            boolean alreadyTagged = clientTags.stream().anyMatch(t -> t.entityId == entityId && t.isHead == headFlag);

            if (!alreadyTagged && clientTags.size() < maxAllowed) {
                clientTags.add(new DeadEyeTag(entityId, targetPos, isHead));
                // Metallic revolver cock / click sound
                mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 1.4F, 1.9F);
                mc.level.playSound(mc.player, targetPos.x, targetPos.y, targetPos.z,
                        SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0F, 1.8F);
            }
        }
    }

    // ==========================================
    // SERVER-SIDE EXECUTION & SEQUENTIAL VOLLEY
    // ==========================================

    public static void handleStateChange(ServerPlayer player, boolean active) {
        ServerLevel level = player.serverLevel();
        if (active) {
            if (hasDeadEyeRune(player)) {
                // Engage cinematic Dead Eye slow motion (15% speed)
                TimeStopManager.startTimeStop(level, player, 160, TimeMode.SLOW_MOTION);
            }
        } else {
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

        // Resume normal time speed only if this player initiated slow motion
        if (TimeStopManager.isTimeStopped(level) && player.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
            TimeStopManager.resumeTime(level);
        }

        int delay = 0;
        int count = Math.min(tags.size(), MAX_TAGS);
        for (int i = 0; i < count; i++) {
            activeScheduledShots.add(new ScheduledVolleyShot(player, tags.get(i), delay));
            delay += 6; // 6 ticks = 300ms realistic rapid-fire bow cadence
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 1. Process queued volley shots
        if (!activeScheduledShots.isEmpty()) {
            Iterator<ScheduledVolleyShot> it = activeScheduledShots.iterator();
            while (it.hasNext()) {
                ScheduledVolleyShot shot = it.next();
                if (shot.delayTicks > 0) {
                    shot.delayTicks--;
                    continue;
                }

                fireVolleyArrow(shot.player, shot.tag);
                activeScheduledShots.remove(shot);
            }
        }

        // 2. Process guided homing for active Dead Eye arrows
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

                int targetId = arrow.getPersistentData().getInt("DeadEyeTargetEntity");
                boolean guided = false;
                if (targetId != 0 && arrow.level() instanceof ServerLevel sl) {
                    Entity target = sl.getEntity(targetId);
                    if (target instanceof LivingEntity living && living.isAlive()) {
                        boolean isHead = arrow.getPersistentData().getBoolean("DeadEyeIsHead");
                        Vec3 targetCoord = isHead ? living.getEyePosition() : living.position().add(0, living.getBbHeight() * 0.65, 0);
                        Vec3 toTarget = targetCoord.subtract(arrow.position()).normalize();

                        // Precision trajectory guidance
                        Vec3 currentVel = arrow.getDeltaMovement();
                        Vec3 guidedVel = currentVel.normalize().scale(0.82).add(toTarget.scale(0.18)).normalize().scale(3.8);
                        arrow.setDeltaMovement(guidedVel);
                        arrow.hasImpulse = true;
                        guided = true;
                    }
                }
                if (!guided) {
                    arrow.setNoGravity(false);
                    arrow.getPersistentData().remove("DeadEyeTargetEntity");
                    activeHomingArrows.remove(ref);
                }
            }
        }
    }

    private static void fireVolleyArrow(ServerPlayer player, DeadEyeTag tag) {
        if (!player.isAlive()) return;
        ServerLevel level = player.serverLevel();

        // Check arrow availability in inventory (bypassed if Infinity enchant or Creative)
        boolean hasInfinity = hasInfinityOrCreative(player);
        if (!hasInfinity) {
            if (!consumeArrow(player)) {
                return; // Out of ammo
            }
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 targetPos = tag.targetPos;

        // Dynamic Entity Tracking & Horizontal Velocity Leading
        Entity targetEntity = level.getEntity(tag.entityId);
        if (targetEntity instanceof LivingEntity living && living.isAlive()) {
            targetPos = tag.isHead
                    ? living.getEyePosition()
                    : living.position().add(0, living.getBbHeight() * 0.65, 0);

            // Lead moving target HORIZONTALLY only (avoiding negative ground gravity velocity)
            Vec3 targetVel = living.getDeltaMovement();
            double dist = eyePos.distanceTo(targetPos);
            double travelTime = dist / 3.8;
            targetPos = targetPos.add(targetVel.x * travelTime, 0, targetVel.z * travelTime);
        }

        Vec3 dir = targetPos.subtract(eyePos).normalize();

        Arrow arrow = new Arrow(level, player);
        arrow.setPos(eyePos.x, eyePos.y - 0.05, eyePos.z);
        arrow.shoot(dir.x, dir.y, dir.z, 3.8F, 0.0F); // Sets rotation, pitch/yaw, and exact velocity vector
        arrow.setNoGravity(true); // Zero gravity drop prevents arrows from sinking into dirt!
        arrow.setCritArrow(true);
        arrow.setBaseDamage(arrow.getBaseDamage() + (tag.isHead ? 8.0 : 4.0));
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.getPersistentData().putBoolean("DeadEyeArrow", true);

        if (targetEntity != null) {
            arrow.getPersistentData().putInt("DeadEyeTargetEntity", targetEntity.getId());
            arrow.getPersistentData().putBoolean("DeadEyeIsHead", tag.isHead);
            activeHomingArrows.add(new WeakReference<>(arrow));
        } else {
            arrow.setNoGravity(false);
        }

        level.addFreshEntity(arrow);

        // Visual arm swing feedback on each arrow loose
        player.swing(player.getUsedItemHand(), true);

        // Sonic crack and realistic bow twang audio feedback
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.4F, 1.1F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.2F, 1.3F);

        level.sendParticles(ParticleTypes.CRIT, eyePos.x, eyePos.y, eyePos.z,
                10, dir.x * 0.4, dir.y * 0.4, dir.z * 0.4, 0.15);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, eyePos.x, eyePos.y, eyePos.z,
                6, dir.x * 0.3, dir.y * 0.3, dir.z * 0.3, 0.1);
    }

    public static boolean hasInfinityOrCreative(Player player) {
        if (player.isCreative()) return true;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean hasInfinity = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, main) > 0
                || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, off) > 0;
        return hasInfinity && hasAtLeastOneArrow(player);
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
        boolean hasInfinity = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, main) > 0
                || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, off) > 0;

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
