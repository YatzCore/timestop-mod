package com.timestop.network;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SlapProjectilePacket {
    private final int entityId;
    private final Vec3 lookDirection;

    public SlapProjectilePacket(int entityId, Vec3 lookDirection) {
        this.entityId = entityId;
        this.lookDirection = lookDirection;
    }

    public SlapProjectilePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.lookDirection = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeDouble(this.lookDirection.x);
        buf.writeDouble(this.lookDirection.y);
        buf.writeDouble(this.lookDirection.z);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            boolean fieldActive = com.timestop.combat.DecelerationFieldManager.hasDecelerationField(player);
            boolean timeActive = TimeStopManager.isGlobalTimeStopped();

            if (!timeActive && !fieldActive) {
                return;
            }

            TimeMode mode = timeActive ? TimeStopManager.getCurrentMode() : TimeMode.DECELERATION_FIELD;
            if (mode != TimeMode.TIME_STOP && mode != TimeMode.SLOW_MOTION && mode != TimeMode.MATRIX && mode != TimeMode.SUPERHOT && mode != TimeMode.DECELERATION_FIELD) {
                return;
            }

            if (timeActive && !TimeStopManager.isEntityExempt(player)) {
                return;
            }

            Entity entity = level.getEntity(this.entityId);
            if (entity instanceof Projectile projectile && com.timestop.combat.ProjectileCombatHelper.isActiveInFlight(projectile)) {
                if (player.distanceToSqr(projectile) > 64.0) {
                    return; // Reject slap if beyond interaction reach
                }

                if (mode == TimeMode.TIME_STOP) {
                    TimeStopManager.punchSuspendedProjectile(projectile, player);
                    player.swing(InteractionHand.MAIN_HAND, true);
                } else {
                    // Slow Motion, Matrix, SUPERHOT, or Deceleration Field: immediate live deflection!
                    TimeStopManager.deflectDynamicProjectile(projectile, player);
                    player.swing(InteractionHand.MAIN_HAND, true);
                }
            }
        });
        return true;
    }
}
