package com.timestop.network;

import com.timestop.client.ClientOrbitalHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

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

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (this.isOrbiting) {
                ClientOrbitalHandler.registerOrbit(this.projectileEntityId, this.playerUuid, this.orbitIndex, this.orbitTotal);
            } else {
                ClientOrbitalHandler.unregisterOrbit(this.projectileEntityId);
            }
        }));
        return true;
    }
}
