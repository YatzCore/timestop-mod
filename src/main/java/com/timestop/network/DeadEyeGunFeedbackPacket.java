package com.timestop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** One confirmed native round; cosmetic only and never asks TACZ to fire again. */
public record DeadEyeGunFeedbackPacket(ItemStack gun, boolean firstRound) {
    public DeadEyeGunFeedbackPacket { gun = gun.copy(); }
    public DeadEyeGunFeedbackPacket(FriendlyByteBuf buf) {
        this(buf instanceof RegistryFriendlyByteBuf rfb ? ItemStack.OPTIONAL_STREAM_CODEC.decode(rfb) : ItemStack.EMPTY, buf.readBoolean());
    }
    public void toBytes(FriendlyByteBuf buf) {
        if (buf instanceof RegistryFriendlyByteBuf rfb) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(rfb, gun);
        }
        buf.writeBoolean(firstRound);
    }
    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> com.timestop.client.TaczDeadEyeFeedback.play(gun, firstRound));
        context.setPacketHandled(true);
    }
}
