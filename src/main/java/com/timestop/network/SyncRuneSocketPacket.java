package com.timestop.network;

import com.timestop.item.ModItems;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server-to-client packet to synchronize the socketed rune in a watch and update open GUIs.
 */
public class SyncRuneSocketPacket {
    private final InteractionHand hand;
    private final boolean hasRune;
    private final RuneType runeType;
    private final com.timestop.combat.ChainTargetFilter filter;

    public SyncRuneSocketPacket(InteractionHand hand, ItemStack socketedRune) {
        this.hand = hand;
        if (!socketedRune.isEmpty() && socketedRune.getItem() instanceof TemporalRuneItem runeItem) {
            this.hasRune = true;
            this.runeType = runeItem.getType();
            this.filter = (this.runeType == RuneType.RICOCHET) ? TemporalRuneItem.getTargetFilter(socketedRune) : null;
        } else {
            this.hasRune = false;
            this.runeType = null;
            this.filter = null;
        }
    }

    public SyncRuneSocketPacket(FriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
        this.hasRune = buf.readBoolean();
        if (this.hasRune) {
            this.runeType = buf.readEnum(RuneType.class);
            if (this.runeType == RuneType.RICOCHET) {
                this.filter = com.timestop.combat.ChainTargetFilter.fromName(buf.readUtf());
            } else {
                this.filter = null;
            }
        } else {
            this.runeType = null;
            this.filter = null;
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeBoolean(this.hasRune);
        if (this.hasRune) {
            buf.writeEnum(this.runeType);
            if (this.runeType == RuneType.RICOCHET) {
                buf.writeUtf(this.filter != null ? this.filter.name() : "");
            }
        }
    }

    public void handle(CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            ItemStack runeStack = ItemStack.EMPTY;
            if (hasRune && runeType != null) {
                runeStack = new ItemStack(ModItems.getRuneItem(runeType));
                if (runeType == RuneType.RICOCHET && filter != null) {
                    TemporalRuneItem.setTargetFilter(runeStack, filter);
                }
            }
            com.timestop.client.ClientPacketHandlers.syncRune(hand, runeStack);
        });
        context.setPacketHandled(true);
    }
}
