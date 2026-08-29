package com.timestop.network;

import com.timestop.combat.TemporalInteractionEvents;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SnatchProjectilePacket {
    private final int entityId;

    public SnatchProjectilePacket(int entityId) {
        this.entityId = entityId;
    }

    public SnatchProjectilePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
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
                    return; // Reject snatch if beyond interaction reach
                }
                TemporalInteractionEvents.snatchProjectile(projectile, player);
            }
        });
        return true;
    }
}
