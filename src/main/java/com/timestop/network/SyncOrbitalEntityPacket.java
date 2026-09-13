package com.timestop.network;

import com.timestop.client.ClientOrbitalHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.UUID;

public class SyncOrbitalEntityPacket {
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
        this.projectileEntityId = buf.readInt();
        this.playerUuid = buf.readUUID();
        this.orbitIndex = buf.readInt();
        this.orbitTotal = buf.readInt();
        this.isOrbiting = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.projectileEntityId);
        buf.writeUUID(this.playerUuid);
        buf.writeInt(this.orbitIndex);
        buf.writeInt(this.orbitTotal);
        buf.writeBoolean(this.isOrbiting);
    }

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (this.isOrbiting) {
                ClientOrbitalHandler.registerOrbit(this.projectileEntityId, this.playerUuid, this.orbitIndex, this.orbitTotal);
            } else {
                ClientOrbitalHandler.unregisterOrbit(this.projectileEntityId);
            }
        }));
        context.setPacketHandled(true);
    }
}
