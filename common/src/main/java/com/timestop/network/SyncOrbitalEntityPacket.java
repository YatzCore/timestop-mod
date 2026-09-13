package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.client.ClientOrbitalHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class SyncOrbitalEntityPacket implements IClientboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "sync_orbital_entity");
    private final int projectileEntityId;
    private final UUID playerUuid;
    private final int orbitIndex;
    private final int orbitTotal;
    private final boolean isOrbiting;

    public SyncOrbitalEntityPacket(int projectileEntityId, UUID playerUuid, int orbitIndex, int orbitTotal, boolean isOrbiting) {
        this.projectileEntityId = projectileEntityId;
        this.playerUuid = playerUuid;
        this.orbitIndex = orbitIndex;
        this.orbitTotal = orbitTotal;
        this.isOrbiting = isOrbiting;
    }

    public SyncOrbitalEntityPacket(FriendlyByteBuf buf) {
        this.projectileEntityId = buf.readVarInt();
        this.playerUuid = buf.readUUID();
        this.orbitIndex = buf.readVarInt();
        this.orbitTotal = buf.readVarInt();
        this.isOrbiting = buf.readBoolean();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.projectileEntityId);
        buf.writeUUID(this.playerUuid);
        buf.writeVarInt(this.orbitIndex);
        buf.writeVarInt(this.orbitTotal);
        buf.writeBoolean(this.isOrbiting);
    }

    @Override
    public void handleClient() {
        if (this.isOrbiting) {
            ClientOrbitalHandler.registerOrbit(this.projectileEntityId, this.playerUuid, this.orbitIndex, this.orbitTotal);
        } else {
            ClientOrbitalHandler.unregisterOrbit(this.projectileEntityId);
        }
    }
}
