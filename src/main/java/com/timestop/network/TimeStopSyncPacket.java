package com.timestop.network;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class TimeStopSyncPacket {
    private final boolean active;
    private final int duration;
    @Nullable
    private final UUID initiator;
    private final TimeMode mode;
    private final Set<UUID> exemptPlayers;

    public TimeStopSyncPacket(boolean active, int duration, @Nullable UUID initiator, TimeMode mode, Set<UUID> exemptPlayers) {
        this.active = active;
        this.duration = duration;
        this.initiator = initiator;
        this.mode = mode;
        this.exemptPlayers = exemptPlayers != null ? exemptPlayers : Collections.emptySet();
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
    }

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
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ClientTimeStopManager.handleSync(active, duration, initiator, mode, exemptPlayers);
        });
        return true;
    }
}
