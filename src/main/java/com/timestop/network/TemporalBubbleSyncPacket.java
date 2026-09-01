package com.timestop.network;

import com.timestop.core.ClientBubbleManager;
import com.timestop.core.TimeMode;
import com.timestop.item.WatchTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class TemporalBubbleSyncPacket {
    public enum Action {
        CREATE_OR_UPDATE,
        REMOVE
    }

    private final Action action;
    private final UUID bubbleId;
    @Nullable
    private final UUID ownerUuid;
    private final String dimensionId;
    private final double x, y, z;
    private final double radius;
    private final TimeMode mode;
    private final int remainingTicks;
    private final int totalDuration;
    private final WatchTier tier;
    private final Set<UUID> exemptPlayers;

    public TemporalBubbleSyncPacket(Action action, UUID bubbleId, @Nullable UUID ownerUuid, String dimensionId,
                                    double x, double y, double z, double radius, TimeMode mode,
                                    int remainingTicks, int totalDuration, WatchTier tier, Set<UUID> exemptPlayers) {
        this.action = action;
        this.bubbleId = bubbleId;
        this.ownerUuid = ownerUuid;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.mode = mode;
        this.remainingTicks = remainingTicks;
        this.totalDuration = totalDuration;
        this.tier = tier;
        this.exemptPlayers = exemptPlayers != null ? exemptPlayers : Collections.emptySet();
    }

    public static TemporalBubbleSyncPacket remove(UUID bubbleId) {
        return new TemporalBubbleSyncPacket(Action.REMOVE, bubbleId, null, "", 0, 0, 0, 0,
                TimeMode.TIME_STOP, 0, 0, WatchTier.COPPER, Collections.emptySet());
    }

    public TemporalBubbleSyncPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.bubbleId = buf.readUUID();
        if (this.action == Action.REMOVE) {
            this.ownerUuid = null;
            this.dimensionId = "";
            this.x = 0;
            this.y = 0;
            this.z = 0;
            this.radius = 0;
            this.mode = TimeMode.TIME_STOP;
            this.remainingTicks = 0;
            this.totalDuration = 0;
            this.tier = WatchTier.COPPER;
            this.exemptPlayers = Collections.emptySet();
            return;
        }

        if (buf.readBoolean()) {
            this.ownerUuid = buf.readUUID();
        } else {
            this.ownerUuid = null;
        }
        this.dimensionId = buf.readUtf();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.radius = buf.readDouble();
        this.mode = buf.readEnum(TimeMode.class);
        this.remainingTicks = buf.readVarInt();
        this.totalDuration = buf.readVarInt();
        this.tier = buf.readEnum(WatchTier.class);

        int exemptCount = buf.readVarInt();
        Set<UUID> set = new HashSet<>(exemptCount);
        for (int i = 0; i < exemptCount; i++) {
            set.add(buf.readUUID());
        }
        this.exemptPlayers = set;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUUID(bubbleId);
        if (action == Action.REMOVE) return;

        buf.writeBoolean(ownerUuid != null);
        if (ownerUuid != null) {
            buf.writeUUID(ownerUuid);
        }
        buf.writeUtf(dimensionId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(radius);
        buf.writeEnum(mode);
        buf.writeVarInt(remainingTicks);
        buf.writeVarInt(totalDuration);
        buf.writeEnum(tier);

        buf.writeVarInt(exemptPlayers.size());
        for (UUID uuid : exemptPlayers) {
            buf.writeUUID(uuid);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (action == Action.REMOVE) {
                ClientBubbleManager.handleRemoveBubble(bubbleId);
            } else {
                ClientBubbleManager.handleSyncBubble(bubbleId, ownerUuid, dimensionId, x, y, z, radius,
                        mode, remainingTicks, totalDuration, tier, exemptPlayers);
            }
        });
        return true;
    }
}
