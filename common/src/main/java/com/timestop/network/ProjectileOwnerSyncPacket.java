package com.timestop.network;

import com.timestop.TimeStopMod;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.Projectile;

/** Vanilla synchronizes ownership at spawn, but not when an existing arrow is reflected. */
public record ProjectileOwnerSyncPacket(int entityId, UUID uuid, int ownerId) implements IClientboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "projectile_owner");
    public ProjectileOwnerSyncPacket(Projectile projectile) {
        this(projectile.getId(), projectile.getUUID(), projectile.getOwner() == null ? -1 : projectile.getOwner().getId());
    }
    public ProjectileOwnerSyncPacket(FriendlyByteBuf buf) { this(buf.readVarInt(),buf.readUUID(),buf.readVarInt()); }
    @Override public ResourceLocation getId() { return ID; }
    @Override public void toBytes(FriendlyByteBuf buf) { buf.writeVarInt(entityId);buf.writeUUID(uuid);buf.writeVarInt(ownerId); }
    @Override public void handleClient() {
        com.timestop.client.ClientPacketHandlers.syncProjectileOwner(entityId, uuid, ownerId);
    }
}
