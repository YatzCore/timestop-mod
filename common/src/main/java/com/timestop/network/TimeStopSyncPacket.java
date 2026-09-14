package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TimeStopSyncPacket implements IClientboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "time_stop_sync");
    private final boolean active;
    private final int duration;
    @Nullable
    private final UUID initiator;
    private final TimeMode mode;
    private final Set<UUID> exemptPlayers;
    private final com.timestop.core.TimeStopManager.ProjectileStasisMode projectileMode;
    private final boolean allowPlayerProjectiles;

    public TimeStopSyncPacket(boolean active, int duration, @Nullable UUID initiator, TimeMode mode, Set<UUID> exemptPlayers) {
        this.active = active;
        this.duration = duration;
        this.initiator = initiator;
        this.mode = mode;
        this.exemptPlayers = exemptPlayers != null ? new HashSet<>(exemptPlayers) : Collections.emptySet();
        this.projectileMode = com.timestop.core.TimeStopManager.getProjectileStasisMode();
        this.allowPlayerProjectiles = com.timestop.config.TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.get();
    }

    public TimeStopSyncPacket(boolean active, int duration, @Nullable UUID initiator, TimeMode mode) {
        this(active, duration, initiator, mode, Collections.emptySet());
    }

    public TimeStopSyncPacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
        this.duration = buf.readVarInt();
        if (buf.readBoolean()) {
            this.initiator = buf.readUUID();
        } else {
            this.initiator = null;
        }
        this.mode = buf.readEnum(TimeMode.class);
        int exemptCount = buf.readVarInt();
        Set<UUID> exempt = new HashSet<>(exemptCount);
        for (int i = 0; i < exemptCount; i++) {
            exempt.add(buf.readUUID());
        }
        this.exemptPlayers = exempt;
        this.projectileMode = buf.readEnum(com.timestop.core.TimeStopManager.ProjectileStasisMode.class);
        this.allowPlayerProjectiles = buf.readBoolean();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeVarInt(duration);
        buf.writeBoolean(initiator != null);
        if (initiator != null) {
            buf.writeUUID(initiator);
        }
        buf.writeEnum(mode);
        buf.writeVarInt(exemptPlayers.size());
        for (UUID uuid : exemptPlayers) {
            buf.writeUUID(uuid);
        }
        buf.writeEnum(projectileMode);
        buf.writeBoolean(allowPlayerProjectiles);
    }

    @Override
    public void handleClient() {
        ClientTimeStopManager.handleSync(active, duration, initiator, mode, exemptPlayers);
        ClientTimeStopManager.setProjectileFlow(projectileMode, allowPlayerProjectiles);
    }
}
