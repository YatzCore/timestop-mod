package com.timestop.network;

import com.timestop.TimeStopMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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

        Entity entity = player.serverLevel().getEntity(this.entityId);
        if (entity instanceof Projectile projectile) {
            com.timestop.combat.ProjectileInteraction.snatch(player, projectile);
        }
    }
}
