package com.timestop.network;

import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SocketSpecificRunePacket {
    private final InteractionHand hand;
    private final int slotIndex; // -1 to eject

    public SocketSpecificRunePacket(InteractionHand hand, int slotIndex) {
        this.hand = hand;
        this.slotIndex = slotIndex;
    }

    public SocketSpecificRunePacket(FriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
        this.slotIndex = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeVarInt(this.slotIndex);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack watchStack = player.getItemInHand(this.hand);
                if (watchStack.getItem() instanceof AbstractWatchItem watch) {
                    if (!watch.getTier().hasRuneSocket()) {
                        player.displayClientMessage(Component.literal("This watch tier cannot hold runes!").withStyle(ChatFormatting.RED), true);
                        return;
                    }

                    ItemStack currentRune = AbstractWatchItem.getSocketedRune(watchStack);

                    if (this.slotIndex == -1) {
                        // Eject
                        if (!currentRune.isEmpty()) {
                            AbstractWatchItem.setSocketedRune(watchStack, ItemStack.EMPTY);
                            if (!player.getInventory().add(currentRune)) {
                                player.drop(currentRune, false);
                            }
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 0.8F);
                            player.displayClientMessage(Component.literal("Extracted " + currentRune.getHoverName().getString()).withStyle(ChatFormatting.YELLOW), true);
                        }
                    } else if (this.slotIndex >= 0 && this.slotIndex < player.getInventory().getContainerSize()) {
                        ItemStack invRune = player.getInventory().getItem(this.slotIndex);
                        if (invRune.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() != RuneType.BLANK) {
                            ItemStack toSocket = invRune.copy();
                            toSocket.setCount(1);
                            AbstractWatchItem.setSocketedRune(watchStack, toSocket);
                            invRune.shrink(1);

                            // If watch previously held a rune, return it to inventory
                            if (!currentRune.isEmpty()) {
                                if (!player.getInventory().add(currentRune)) {
                                    player.drop(currentRune, false);
                                }
                            }

                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.0F, 1.2F);
                            player.displayClientMessage(Component.literal("Socketed " + toSocket.getHoverName().getString()).withStyle(ChatFormatting.GREEN), true);
                        }
                    }
                }
            }
        });
        return true;
    }
}
