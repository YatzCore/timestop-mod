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

public class SocketRunePacket {
    private final InteractionHand hand;

    public SocketRunePacket(InteractionHand hand) {
        this.hand = hand;
    }

    public SocketRunePacket(FriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack watchStack = player.getItemInHand(this.hand);
                if (watchStack.getItem() instanceof AbstractWatchItem watch) {
                    if (!watch.getTier().hasRuneSocket()) {
                        player.displayClientMessage(Component.literal("This watch tier cannot hold runes! (Requires Tier 2+)").withStyle(ChatFormatting.RED), true);
                        return;
                    }

                    ItemStack currentRune = AbstractWatchItem.getSocketedRune(watchStack);
                    if (!currentRune.isEmpty()) {
                        // Extract rune back into inventory
                        AbstractWatchItem.setSocketedRune(watchStack, ItemStack.EMPTY);
                        if (!player.getInventory().add(currentRune)) {
                            player.drop(currentRune, false);
                        }
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 0.8F);
                        player.displayClientMessage(Component.literal("Extracted " + currentRune.getHoverName().getString()).withStyle(ChatFormatting.YELLOW), true);
                    } else {
                        // Find first tactical rune in inventory
                        int runeSlot = -1;
                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                            ItemStack invStack = player.getInventory().getItem(i);
                            if (invStack.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() != RuneType.BLANK) {
                                runeSlot = i;
                                break;
                            }
                        }

                        if (runeSlot != -1) {
                            ItemStack invRune = player.getInventory().getItem(runeSlot);
                            ItemStack socketCopy = invRune.copy();
                            socketCopy.setCount(1);
                            AbstractWatchItem.setSocketedRune(watchStack, socketCopy);
                            invRune.shrink(1);

                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.0F, 1.2F);
                            player.displayClientMessage(Component.literal("Socketed " + socketCopy.getHoverName().getString()).withStyle(ChatFormatting.GREEN), true);
                        } else {
                            player.displayClientMessage(Component.literal("No tactical runes found in inventory!").withStyle(ChatFormatting.RED), true);
                        }
                    }
                }
            }
        });
        return true;
    }
}
