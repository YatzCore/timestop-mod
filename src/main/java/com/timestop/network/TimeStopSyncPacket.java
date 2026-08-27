package com.timestop.network;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public class TimeStopSyncPacket {
    private final boolean active;
    private final int duration;
    @Nullable
    private final UUID initiator;
    private final TimeMode mode;

    public TimeStopSyncPacket(boolean active, int duration, @Nullable UUID initiator, TimeMode mode) {
        this.active = active;
        this.duration = duration;
        this.initiator = initiator;
        this.mode = mode;
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
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeVarInt(duration);
        buf.writeBoolean(initiator != null);
        if (initiator != null) {
            buf.writeUUID(initiator);
        }
        buf.writeEnum(mode);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ClientTimeStopManager.handleSync(active, duration, initiator, mode);
        });
        return true;
    }
}
