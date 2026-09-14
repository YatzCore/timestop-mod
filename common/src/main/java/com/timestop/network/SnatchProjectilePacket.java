package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.TimeStopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

public class SnatchProjectilePacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "snatch_projectile");
    private final int entityId;

    public SnatchProjectilePacket(int entityId) {
        this.entityId = entityId;
    }

    public SnatchProjectilePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player == null) return;

        ServerLevel level = player.serverLevel();
        boolean fieldActive = com.timestop.combat.DecelerationFieldManager.hasDecelerationField(player);
        boolean timeActive = TimeStopManager.isGlobalTimeStopped() && TimeStopManager.isEntityExempt(player);
        boolean bubbleActive = com.timestop.core.TemporalBubbleManager.hasActiveBubbles();

        if (!timeActive && !fieldActive && !bubbleActive) return;

        Entity entity = level.getEntity(this.entityId);
        if (entity instanceof Projectile projectile && com.timestop.combat.ProjectileCombatHelper.isActiveInFlight(projectile)) {
            if (player.distanceToSqr(projectile) > 25.0) return;
            com.timestop.combat.TemporalInteractionEvents.snatchProjectile(projectile, player);
        }
    }
}
