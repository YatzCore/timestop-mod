package com.timestop.combat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public class DeadEyeTag {
    public final int entityId;
    public final Vec3 targetPos;
    public final boolean isHead;

    public DeadEyeTag(int entityId, Vec3 targetPos, boolean isHead) {
        this.entityId = entityId;
        this.targetPos = targetPos;
        this.isHead = isHead;
    }

    public DeadEyeTag(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.targetPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.isHead = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeDouble(this.targetPos.x);
        buf.writeDouble(this.targetPos.y);
        buf.writeDouble(this.targetPos.z);
        buf.writeBoolean(this.isHead);
    }
}
