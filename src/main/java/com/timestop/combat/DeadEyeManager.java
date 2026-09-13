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
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key != null) {
            String id = key.toString().toLowerCase();
            return id.contains("gun") || id.contains("rifle") || id.contains("pistol")
                    || id.contains("shotgun") || id.contains("smg") || id.contains("revolver") || id.contains("sniper");
        }
        return false;
    }

    // ==========================================
    // CLIENT-SIDE AIMING & TARGET PAINTING
    // ==========================================

    public static final net.minecraft.resources.ResourceLocation SEPIA_SHADER = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("timestop", "shaders/post/sepia.json");
    private static boolean deadEyeShaderActive = false;

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
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

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
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

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static void clientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            if (clientAiming) stopClientAiming(false);
            return;
        }

        boolean isDrawing = isAimingRanged(mc);

        if (isDrawing) {
            // If player pulls the trigger to shoot faster than slow-mo ends, execute tags immediately and exit!
            if (clientAiming && mc.options.keyAttack.isDown()) {
                stopClientAiming(true);
                return;
            }

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
            if (maxAllowed > 0) {
                paintTargetUnderCrosshair(mc, maxAllowed);
            }
        } else {
            if (clientAiming) {
                // Weapon released or cancelled
                stopClientAiming(true);
            }
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static boolean isAimingRanged(Minecraft mc) {
        if (mc.player == null) return false;
        if (!hasDeadEyeRune(mc.player)) return false;

        // 1. Vanilla bow / crossbow item usage:
        if (mc.player.isUsingItem()) {
            ItemStack useItem = mc.player.getUseItem();
            if (useItem.getItem() instanceof BowItem || useItem.getItem() instanceof CrossbowItem) {
                return true;
            }
        }

        // 2. Modern firearm / gun aiming (Right-Click held while holding gun in main or off hand):
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        if (isGun(main) || isGun(off)) {
            return mc.options.keyUse.isDown();
        }

        return false;
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void stopClientAiming(boolean executeIfTagged) {
        clientAiming = false;
        removeDeadEyeShader();
        ModMessages.sendToServer(new DeadEyeStatePacket(false));

        if (executeIfTagged && !clientTags.isEmpty()) {
            ModMessages.sendToServer(new DeadEyeExecutePacket(new ArrayList<>(clientTags)));
        }
        clientTags.clear();
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void paintTargetUnderCrosshair(Minecraft mc, int maxAllowed) {

        Vec3 eyePos = mc.player.getEyePosition(1.0F);
        Vec3 viewVec = mc.player.getViewVector(1.0F);
        double reach = 48.0;
        Vec3 reachVec = eyePos.add(viewVec.scale(reach));
        var blockHit = mc.level.clip(new net.minecraft.world.level.ClipContext(eyePos, reachVec,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) reachVec = blockHit.getLocation();
        AABB searchBox = mc.player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(2.0);

        List<LivingEntity> entities = mc.level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != mc.player && e.isAlive() && !e.isSpectator());

        LivingEntity bestEntity = null;
        Vec3 bestHit = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (LivingEntity e : entities) {
            AABB bb = e.getBoundingBox().inflate(0.12);
            Optional<Vec3> clip = bb.clip(eyePos, reachVec);
            // Pig heads protrude beyond the vanilla collision box.
            Optional<Vec3> headClip = headBounds(e).clip(eyePos, reachVec);
            if (headClip.isPresent() && (clip.isEmpty()
                    || eyePos.distanceToSqr(headClip.get()) < eyePos.distanceToSqr(clip.get()))) clip = headClip;
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
            boolean isHead = isHeadAim(bestEntity, eyePos, reachVec);
            Vec3 targetPos = isHead ? headPosition(bestEntity, 1.0F) : bestEntity.position().add(0, bestEntity.getBbHeight() * 0.65, 0);

            // Check if spot already tagged
            final int entityId = bestEntity.getId();
            final boolean headFlag = isHead;
            boolean alreadyTagged = clientTags.stream().anyMatch(t -> t.entityId == entityId && t.isHead == headFlag);

            if (!alreadyTagged && addOrUpgradeTag(clientTags, new DeadEyeTag(entityId, targetPos, isHead), maxAllowed)) {
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

    public static boolean addOrUpgradeTag(List<DeadEyeTag> tags, DeadEyeTag tag, int maxAllowed) {
        if (tags.size() < maxAllowed) return tags.add(tag);
        if (tag.isHead) {
            for (int i = 0; i < tags.size(); i++) {
                if (tags.get(i).entityId == tag.entityId && !tags.get(i).isHead) {
                    tags.set(i, tag);
                    return true;
                }
            }
        }
        return false;
    }

    public static Vec3 headPosition(LivingEntity target, float partialTick) {
        if (target instanceof net.minecraft.world.entity.animal.Pig pig) {
            // Vanilla PigModel: head pivot (0,12,-6), skull centre (0,0,-4).
            // Piglets retain the full head, translated down/back by four model pixels.
            double bodyYaw = Math.toRadians(net.minecraft.util.Mth.rotLerp(partialTick, pig.yBodyRotO, pig.yBodyRot));
            double headYaw = Math.toRadians(net.minecraft.util.Mth.rotLerp(partialTick, pig.yHeadRotO, pig.yHeadRot));
            double pitch = Math.toRadians(pig.getViewXRot(partialTick));
            double pivotForward = pig.isBaby() ? 0.125 : 0.375;
            double height = pig.isBaby() ? 0.501 : 0.751;
            double scale = pig.getScale();
            return pig.getPosition(partialTick).add(
                    (-Math.sin(bodyYaw) * pivotForward - Math.sin(headYaw) * Math.cos(pitch) * 0.25) * scale,
                    (height - Math.sin(pitch) * 0.25) * scale,
                    (Math.cos(bodyYaw) * pivotForward + Math.cos(headYaw) * Math.cos(pitch) * 0.25) * scale);
        }
        return target.getEyePosition(partialTick);
    }

    private static AABB headBounds(LivingEntity target) {
        Vec3 centre = headPosition(target, 1.0F);
        if (target instanceof net.minecraft.world.entity.animal.Pig) {
            double radius = 0.29 * target.getScale();
            return new AABB(centre, centre).inflate(radius);
        }
        double radius = Math.max(0.10, Math.min(0.30, target.getBbWidth() * 0.42));
        double halfHeight = Math.max(0.09, Math.min(0.24, target.getBbHeight() * 0.13));
        return new AABB(centre.x - radius, centre.y - halfHeight, centre.z - radius,
                centre.x + radius, Math.min(target.getBoundingBox().maxY, centre.y + halfHeight), centre.z + radius);
    }

    /** Uses the visible head, including anatomy that extends outside the collision box. */
    public static boolean isHeadAim(LivingEntity target, Vec3 from, Vec3 to) {
        AABB head = headBounds(target);
        return head.contains(from) || head.clip(from, to).isPresent();
    }

    public static void handleStateChange(ServerPlayer player, boolean active) {
        ServerLevel level = player.serverLevel();
        if (active) {
            if (hasDeadEyeRune(player)) {
                // Engage cinematic Dead Eye slow motion (15% speed)
                TimeStopManager.startTimeStop(level, player, 160, TimeMode.SLOW_MOTION);
            }
        } else {
            // STOP SLOW MOTION: collapse localized bubble and resume global stasis immediately!
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

        // Immediately collapse the slow-mo bubble and resume time as soon as you shoot!
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
            delay += isGunWeapon ? 3 : 6; // 3 ticks (150ms) rapid fanning for guns; 6 ticks for bows
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 1. Process queued volley shots
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

        // 2. Process guided homing for active Dead Eye arrows
        if (!activeHomingArrows.isEmpty()) {
            Iterator<WeakReference<Arrow>> arrowIt = activeHomingArrows.iterator();
            while (arrowIt.hasNext()) {
                WeakReference<Arrow> ref = arrowIt.next();
                Arrow arrow = ref.get();
                if (ProjectileCombatHelper.isStuckOrDead(arrow)) {
                    if (arrow != null) {
                        arrow.setNoGravity(false);
                        arrow.getPersistentData().remove("DeadEyeTargetEntity");
                    }
                    activeHomingArrows.remove(ref);
                    continue;
                }

                if (com.timestop.core.TemporalBubbleManager.isEntityInStasis(arrow)
                        || arrow.getPersistentData().getBoolean("InStasisOrbit")
                        || arrow.getPersistentData().getBoolean("KineticPalmCaptured")) continue;
                int targetId = arrow.getPersistentData().getInt("DeadEyeTargetEntity");
                boolean guided = false;
                if (targetId != 0 && arrow.level() instanceof ServerLevel sl) {
                    Entity target = sl.getEntity(targetId);
                    if (target instanceof LivingEntity living && living.isAlive()) {
                        boolean isHead = arrow.getPersistentData().getBoolean("DeadEyeIsHead");
                        // Vanilla projectile collisions use the body hitbox, even for protruding model heads.
                        Vec3 targetCoord = isHead ? living.getEyePosition() : living.position().add(0, living.getBbHeight() * 0.65, 0);
                        Vec3 toTarget = targetCoord.subtract(arrow.position()).normalize();

                        // Precision trajectory guidance
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
                    arrow.getPersistentData().remove("DeadEyeTargetEntity");
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
            // Never substitute arrows for a gun, including when TACZ rejects firing.
            return TaczDeadEyeCompat.fire(player, living, tag.isHead) == TaczDeadEyeCompat.Result.RETRY;
        }
        if (!hasInfinityOrCreative(player) && !consumeArrow(player)) return false;

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

        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), player.getMainHandItem());
        arrow.setPos(eyePos.x, eyePos.y - 0.05, eyePos.z);
        float projectileSpeed = 3.8F;
        arrow.shoot(dir.x, dir.y, dir.z, projectileSpeed, 0.0F); // Sets rotation, pitch/yaw, and exact velocity vector
        arrow.setNoGravity(true); // Zero gravity drop prevents arrows from sinking into dirt!
        arrow.setCritArrow(true);
        double extraDmg = tag.isHead ? 8.0 : 4.0;
        arrow.setBaseDamage(arrow.getBaseDamage() + extraDmg);
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
