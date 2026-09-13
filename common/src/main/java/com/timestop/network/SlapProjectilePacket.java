package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.TimeStopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public class SlapProjectilePacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "slap_projectile");
    private final int entityId;
    private final Vec3 slapDirection;

    public SlapProjectilePacket(int entityId, Vec3 slapDirection) {
        this.entityId = entityId;
        this.slapDirection = slapDirection;
    }

    public SlapProjectilePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            this.slapDirection = Vec3.ZERO;
        } else {
            this.slapDirection = new Vec3(x, y, z);
        }
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeDouble(this.slapDirection.x);
        buf.writeDouble(this.slapDirection.y);
        buf.writeDouble(this.slapDirection.z);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player == null) return;

        Entity entity = player.serverLevel().getEntity(this.entityId);
        if (entity instanceof Projectile projectile && (TimeStopManager.isProjectileSuspended(projectile) || com.timestop.core.TemporalBubbleManager.isEntityInStasis(projectile) || com.timestop.combat.DecelerationFieldManager.isDecelerated(projectile))) {
            if (player.distanceToSqr(projectile) > 25.0) return;

            if (com.timestop.combat.ProjectileInteraction.redirect(player, projectile)) {
                player.swing(InteractionHand.MAIN_HAND, true);
            }
        }
    }
}
