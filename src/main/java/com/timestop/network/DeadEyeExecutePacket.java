package com.timestop.network;

import com.timestop.combat.DeadEyeManager;
import com.timestop.combat.DeadEyeTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class DeadEyeExecutePacket {
    private final List<DeadEyeTag> tags;

    public DeadEyeExecutePacket(List<DeadEyeTag> tags) {
        this.tags = tags;
    }

    public DeadEyeExecutePacket(FriendlyByteBuf buf) {
        int count = Math.min(DeadEyeManager.MAX_TAGS, Math.max(0, buf.readVarInt()));
        this.tags = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.tags.add(new DeadEyeTag(buf));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        int toWrite = Math.min(this.tags.size(), DeadEyeManager.MAX_TAGS);
        buf.writeVarInt(toWrite);
        for (int i = 0; i < toWrite; i++) {
            this.tags.get(i).toBytes(buf);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && DeadEyeManager.hasDeadEyeRune(player)) {
                if (DeadEyeManager.isRangedWeapon(player.getMainHandItem()) || DeadEyeManager.isRangedWeapon(player.getOffhandItem())) {
                    DeadEyeManager.executeVolley(player, this.tags);
                }
            }
        });
        return true;
    }
}
