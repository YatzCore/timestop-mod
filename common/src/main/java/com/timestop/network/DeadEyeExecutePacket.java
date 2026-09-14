package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.combat.DeadEyeManager;
import com.timestop.combat.DeadEyeTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class DeadEyeExecutePacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "dead_eye_execute");
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

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        int toWrite = Math.min(this.tags.size(), DeadEyeManager.MAX_TAGS);
        buf.writeVarInt(toWrite);
        for (int i = 0; i < toWrite; i++) {
            this.tags.get(i).toBytes(buf);
        }
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null && DeadEyeManager.hasDeadEyeRune(player)) {
            if (DeadEyeManager.isRangedWeapon(player.getMainHandItem()) || DeadEyeManager.isRangedWeapon(player.getOffhandItem())) {
                DeadEyeManager.executeVolley(player, this.tags);
            }
        }
    }
}
