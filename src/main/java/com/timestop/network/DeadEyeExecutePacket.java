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
        int count = buf.readVarInt();
        this.tags = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.tags.add(new DeadEyeTag(buf));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.tags.size());
        for (DeadEyeTag tag : this.tags) {
            tag.toBytes(buf);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                DeadEyeManager.executeVolley(player, this.tags);
            }
        });
        return true;
    }
}
