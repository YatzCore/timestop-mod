package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.ClientBubbleManager;
import com.timestop.core.TimeMode;
import com.timestop.item.WatchTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TemporalBubbleSyncPacket implements IClientboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "temporal_bubble_sync");

    public enum Action {
        CREATE_OR_UPDATE,
        REMOVE
    }

    private final Action action;
    private final UUID bubbleId;
    private final UUID ownerUuid;
    private final String dimensionId;
    private final double x, y, z;
    private final double radius;
    private final TimeMode mode;
    private final int remainingTicks;
    private final int totalDuration;
    private final WatchTier tier;
    private final Set<UUID> exemptPlayers;
    private boolean stationary;
    private boolean affectsPlayers = true;

    public static TemporalBubbleSyncPacket remove(UUID bubbleId) {
        return new TemporalBubbleSyncPacket(Action.REMOVE, bubbleId, null, "", 0, 0, 0, 0, TimeMode.TIME_STOP, 0, 0, WatchTier.COPPER, Collections.emptySet());
    }

    public static TemporalBubbleSyncPacket createRemove(UUID bubbleId) {
        return remove(bubbleId);
    }

    public TemporalBubbleSyncPacket(Action action, UUID bubbleId, UUID ownerUuid, String dimensionId,
                                    double x, double y, double z, double radius,
                                    TimeMode mode, int remainingTicks, int totalDuration,
                                    WatchTier tier, Set<UUID> exemptPlayers) {
        this.action = action;
        this.bubbleId = bubbleId;
        this.ownerUuid = ownerUuid;
        this.dimensionId = dimensionId != null ? dimensionId : "";
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.mode = mode;
        this.remainingTicks = remainingTicks;
        this.totalDuration = totalDuration;
        this.tier = tier;
        this.exemptPlayers = exemptPlayers != null ? new HashSet<>(exemptPlayers) : Collections.emptySet();
    }

    public TemporalBubbleSyncPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.bubbleId = buf.readUUID();
        if (this.action == Action.REMOVE) {
            this.ownerUuid = null;
            this.dimensionId = "";
            this.x = this.y = this.z = 0;
            this.radius = 0;
            this.mode = TimeMode.TIME_STOP;
            this.remainingTicks = 0;
            this.totalDuration = 0;
            this.tier = WatchTier.COPPER;
            this.exemptPlayers = Collections.emptySet();
        } else {
            this.ownerUuid = buf.readBoolean() ? buf.readUUID() : null;
            this.dimensionId = buf.readUtf();
            this.x = buf.readDouble();
            this.y = buf.readDouble();
            this.z = buf.readDouble();
            this.radius = buf.readDouble();
            this.mode = buf.readEnum(TimeMode.class);
            this.remainingTicks = buf.readVarInt();
            this.totalDuration = buf.readVarInt();
            this.tier = buf.readEnum(WatchTier.class);
            int count = buf.readVarInt();
            this.exemptPlayers = new HashSet<>(count);
            for (int i = 0; i < count; i++) {
                this.exemptPlayers.add(buf.readUUID());
            }
            this.stationary = buf.readBoolean();
            this.affectsPlayers = buf.readBoolean();
        }
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    public TemporalBubbleSyncPacket(Action action, UUID bubbleId, UUID ownerUuid, String dimensionId,
                                   double x, double y, double z, double radius, TimeMode mode,
                                   int remainingTicks, int totalDuration, WatchTier tier, Set<UUID> exemptPlayers,
                                   boolean stationary, boolean affectsPlayers) {
        this(action, bubbleId, ownerUuid, dimensionId, x, y, z, radius, mode, remainingTicks, totalDuration, tier, exemptPlayers);
        this.stationary = stationary;
        this.affectsPlayers = affectsPlayers;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        buf.writeUUID(this.bubbleId);
        if (this.action != Action.REMOVE) {
            buf.writeBoolean(this.ownerUuid != null);
            if (this.ownerUuid != null) {
                buf.writeUUID(this.ownerUuid);
            }
            buf.writeUtf(this.dimensionId);
            buf.writeDouble(this.x);
            buf.writeDouble(this.y);
            buf.writeDouble(this.z);
            buf.writeDouble(this.radius);
            buf.writeEnum(this.mode);
            buf.writeVarInt(this.remainingTicks);
            buf.writeVarInt(this.totalDuration);
            buf.writeEnum(this.tier);
            buf.writeVarInt(this.exemptPlayers.size());
            for (UUID uuid : this.exemptPlayers) {
                buf.writeUUID(uuid);
            }
            buf.writeBoolean(stationary);
            buf.writeBoolean(affectsPlayers);
        }
    }

    @Override
    public void handleClient() {
        if (action == Action.REMOVE) {
            ClientBubbleManager.handleRemoveBubble(bubbleId);
        } else {
            ClientBubbleManager.handleSyncBubble(bubbleId, ownerUuid, dimensionId, x, y, z, radius,
                    mode, remainingTicks, totalDuration, tier, exemptPlayers, stationary, affectsPlayers);
        }
    }
}
