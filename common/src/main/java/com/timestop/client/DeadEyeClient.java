package com.timestop.client;

import com.timestop.combat.DeadEyeManager;
import com.timestop.combat.DeadEyeTag;
import com.timestop.core.TimeMode;
import com.timestop.network.DeadEyeExecutePacket;
import com.timestop.network.DeadEyeStatePacket;
import com.timestop.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DeadEyeClient {
    public static boolean clientAiming = false;
    public static final List<DeadEyeTag> clientTags = new ArrayList<>();
    private static int lastHeartbeatTick = 0;

    public static final ResourceLocation SEPIA_SHADER = ResourceLocation.fromNamespaceAndPath("timestop", "shaders/post/sepia.json");
    private static boolean deadEyeShaderActive = false;

    public static void applyDeadEyeShader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) {
            try {
                if (mc.gameRenderer.currentEffect() == null || !deadEyeShaderActive) {
                    ((com.timestop.mixin.GameRendererAccessor) mc.gameRenderer).timestop$loadEffect(SEPIA_SHADER);
                    deadEyeShaderActive = true;
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void removeDeadEyeShader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) {
            try {
                if (mc.gameRenderer.currentEffect() != null && deadEyeShaderActive) {
                    mc.gameRenderer.shutdownEffect();
                }
                deadEyeShaderActive = false;
                if (com.timestop.core.ClientTimeStopManager.isTimeStopped() && com.timestop.core.ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
                    com.timestop.core.ClientTimeStopManager.applyShader();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void clientTick(Minecraft mc) {
        if (deadEyeShaderActive && mc.gameRenderer != null && mc.gameRenderer.currentEffect() == null && mc.level != null) {
            applyDeadEyeShader();
        }
        if (mc.player == null || mc.level == null) {
            if (clientAiming) stopClientAiming(false);
            return;
        }

        boolean isDrawing = isAimingRanged(mc);

        if (isDrawing) {
            if (clientAiming && mc.options.keyAttack.isDown()) {
                stopClientAiming(true);
                return;
            }

            if (!clientAiming) {
                clientAiming = true;
                clientTags.clear();
                applyDeadEyeShader();
                ModMessages.sendToServer(new DeadEyeStatePacket(true));
                mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 1.0F);
                lastHeartbeatTick = mc.player.tickCount;
            }

            if (mc.player.tickCount - lastHeartbeatTick >= 22) {
                mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.3F, 1.0F);
                lastHeartbeatTick = mc.player.tickCount;
            }

            int maxAllowed = DeadEyeManager.getAvailableArrowCount(mc.player);
            if (clientTags.size() < maxAllowed) {
                paintTargetUnderCrosshair(mc, maxAllowed);
            }
        } else {
            if (clientAiming) {
                stopClientAiming(true);
            }
        }
    }

    private static boolean isAimingRanged(Minecraft mc) {
        if (mc.player == null) return false;
        if (!DeadEyeManager.hasDeadEyeRune(mc.player)) return false;

        if (mc.player.isUsingItem()) {
            ItemStack useItem = mc.player.getUseItem();
            if (useItem.getItem() instanceof BowItem || useItem.getItem() instanceof CrossbowItem) {
                return true;
            }
        }

        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        if (DeadEyeManager.isGun(main) || DeadEyeManager.isGun(off)) {
            return mc.options.keyUse.isDown();
        }

        return false;
    }

    private static void stopClientAiming(boolean executeIfTagged) {
        clientAiming = false;
        removeDeadEyeShader();
        boolean connected = Minecraft.getInstance().getConnection() != null;
        if (connected) ModMessages.sendToServer(new DeadEyeStatePacket(false));

        if (connected && executeIfTagged && !clientTags.isEmpty()) {
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

            final int entityId = bestEntity.getId();
            final boolean headFlag = isHead;
            boolean alreadyTagged = clientTags.stream().anyMatch(t -> t.entityId == entityId && t.isHead == headFlag);

            if (!alreadyTagged && addOrUpgradeTag(clientTags, new DeadEyeTag(entityId, targetPos, isHead), maxAllowed)) {
                mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 1.4F, 1.9F);
                mc.level.playSound(mc.player, targetPos.x, targetPos.y, targetPos.z,
                        SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0F, 1.8F);
            }
        }
    }

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

    public static AABB headBounds(LivingEntity target) {
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

    public static boolean isHeadAim(LivingEntity target, Vec3 from, Vec3 to) {
        AABB head = headBounds(target);
        return head.contains(from) || head.clip(from, to).isPresent();
    }
}
