package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.config.TimeStopConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** The server owns fluid collision; clients only request a setting change. */
public class UpdateSwimmingConfigPacket implements IServerboundPacket {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID,"update_stasis_swimming");
    private final boolean swimming;
    public UpdateSwimmingConfigPacket(boolean swimming) { this.swimming=swimming; }
    public UpdateSwimmingConfigPacket(FriendlyByteBuf buf) { this(buf.readBoolean()); }
    @Override public ResourceLocation getId() { return ID; }
    @Override public void toBytes(FriendlyByteBuf buf) { buf.writeBoolean(swimming); }
    @Override public void handle(ServerPlayer player) {
        if(player==null) return;
        if(player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile())) {
            TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(!swimming);
            TimeStopConfig.save();
            ModMessages.sendToClients(SyncSpeedConfigPacket.current());
        } else ModMessages.sendToPlayer(SyncSpeedConfigPacket.current(),player);
    }
}
