package com.timestop.network;

import com.timestop.TimeStopMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record DeadEyeGunFeedbackPacket(ItemStack gun, boolean firstRound) implements IClientboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "dead_eye_gun_feedback");

    public DeadEyeGunFeedbackPacket { gun = gun.copy(); }
    public DeadEyeGunFeedbackPacket(FriendlyByteBuf buf) { this(buf.readItem(), buf.readBoolean()); }

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    public void toBytes(FriendlyByteBuf buf) { buf.writeItem(gun); buf.writeBoolean(firstRound); }

    @Override
    public void handleClient() {
        com.timestop.client.TaczDeadEyeFeedback.play(gun, firstRound);
    }
}
