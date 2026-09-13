package com.timestop.client;

import com.timestop.core.TimeMode;
import com.timestop.network.DeadEyeExecutePacket;
import com.timestop.network.DeadEyeStatePacket;
import com.timestop.network.ModMessages;
import net.minecraft.client.Minecraft;
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

import com.timestop.combat.DeadEyeTag;
import static com.timestop.combat.DeadEyeManager.*;

/** Client input and rendering are kept out of the dedicated-server volley manager. */
public final class DeadEyeClient {
    // Client-side tracking
    public static boolean clientAiming = false;
    public static final List<DeadEyeTag> clientTags = new ArrayList<>();
    private static int lastHeartbeatTick = 0;

    // ==========================================
    // CLIENT-SIDE AIMING & TARGET PAINTING
    // ==========================================

    public static final net.minecraft.resources.ResourceLocation SEPIA_SHADER = new net.minecraft.resources.ResourceLocation("timestop", "shaders/post/sepia.json");
    private static boolean deadEyeShaderActive = false;

    public static void applyDeadEyeShader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && !deadEyeShaderActive) {
            try {
                ((com.timestop.mixin.GameRendererAccessor) mc.gameRenderer).timestop$loadEffect(SEPIA_SHADER);
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
        AABB searchBox = mc.player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(2.0);

        List<LivingEntity> entities = mc.level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != mc.player && e.isAlive() && !e.isSpectator());

        LivingEntity bestEntity = null;
        Vec3 bestHit = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (LivingEntity e : entities) {
            AABB bb = e.getBoundingBox().inflate(0.35);
            Optional<Vec3> clip = bb.clip(eyePos, reachVec);
            Optional<Vec3> headClip = DeadEyeHeadGeometry.getBounds(e).inflate(0.04).clip(eyePos, reachVec);
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
            AABB head = com.timestop.client.DeadEyeHeadGeometry.getBounds(bestEntity).inflate(0.04);
            boolean isHead = head.contains(eyePos) || head.clip(eyePos, reachVec).isPresent();
            Vec3 targetPos = bestHit;

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

}
