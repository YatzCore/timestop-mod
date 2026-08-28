package com.timestop.item;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractWatchItem extends Item {
    protected final WatchTier tier;

    public AbstractWatchItem(Properties properties, WatchTier tier) {
        super(properties);
        this.tier = tier;
    }

    public WatchTier getTier() {
        return tier;
    }

    public static TimeMode getMode(ItemStack stack) {
        if (stack.getItem() instanceof AbstractWatchItem watch) {
            if (stack.hasTag() && stack.getTag().contains("TimeMode")) {
                try {
                    TimeMode mode = TimeMode.valueOf(stack.getTag().getString("TimeMode"));
                    if (watch.getTier().isModeUnlocked(mode)) {
                        return mode;
                    }
                } catch (IllegalArgumentException ignored) {}
            }
            // Default to the first unlocked mode for this tier
            return watch.getTier().getUnlockedModes().iterator().next();
        }
        return TimeMode.SLOW_MOTION;
    }

    public static void setMode(ItemStack stack, TimeMode mode) {
        stack.getOrCreateTag().putString("TimeMode", mode.name());
    }

    public static ItemStack getSocketedRune(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("SocketedRune", Tag.TAG_COMPOUND)) {
            return ItemStack.of(stack.getTag().getCompound("SocketedRune"));
        }
        return ItemStack.EMPTY;
    }

    public static void setSocketedRune(ItemStack watchStack, ItemStack runeStack) {
        if (runeStack.isEmpty()) {
            if (watchStack.hasTag()) {
                watchStack.getTag().remove("SocketedRune");
            }
        } else {
            watchStack.getOrCreateTag().put("SocketedRune", runeStack.save(new CompoundTag()));
        }
    }

    @Nullable
    public static RuneType getSocketedRuneType(ItemStack stack) {
        ItemStack rune = getSocketedRune(stack);
        if (!rune.isEmpty() && rune.getItem() instanceof TemporalRuneItem runeItem) {
            return runeItem.getType();
        }
        return null;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack watchStack, ItemStack carriedStack, net.minecraft.world.inventory.Slot slot, net.minecraft.world.inventory.ClickAction action, Player player, net.minecraft.world.entity.SlotAccess access) {
        if (action != net.minecraft.world.inventory.ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }

        if (!this.tier.hasRuneSocket()) {
            return false;
        }

        ItemStack socketed = getSocketedRune(watchStack);

        if (carriedStack.isEmpty()) {
            // Right-clicking socketed watch with empty cursor: EXTRACT to cursor!
            if (!socketed.isEmpty()) {
                setSocketedRune(watchStack, ItemStack.EMPTY);
                access.set(socketed.copy());
                player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.0F, 0.8F);
                return true;
            }
        } else if (carriedStack.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() != RuneType.BLANK) {
            // Right-clicking watch with a Rune on cursor: SOCKET OR SWAP!
            ItemStack newSocket = carriedStack.copy();
            newSocket.setCount(1);
            setSocketedRune(watchStack, newSocket);
            carriedStack.shrink(1);

            if (!socketed.isEmpty()) {
                if (carriedStack.isEmpty()) {
                    access.set(socketed.copy());
                } else if (!player.getInventory().add(socketed)) {
                    player.drop(socketed, false);
                }
            }

            player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0F, 1.2F);
            return true;
        }

        return false;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack watchStack, net.minecraft.world.inventory.Slot slot, net.minecraft.world.inventory.ClickAction action, Player player) {
        if (action != net.minecraft.world.inventory.ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }

        if (!this.tier.hasRuneSocket()) {
            return false;
        }

        ItemStack slotStack = slot.getItem();
        if (slotStack.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() != RuneType.BLANK) {
            ItemStack socketed = getSocketedRune(watchStack);

            ItemStack newSocket = slotStack.copy();
            newSocket.setCount(1);
            setSocketedRune(watchStack, newSocket);
            slotStack.shrink(1);

            if (!socketed.isEmpty()) {
                if (!player.getInventory().add(socketed)) {
                    player.drop(socketed, false);
                }
            }

            player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0F, 1.2F);
            return true;
        }

        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // SHIFT + RIGHT CLICK: Opens interactive mode selection GUI!
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                com.timestop.client.gui.ClientGuiOpener.openModeSelection(hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // NORMAL RIGHT CLICK: Activate or Stop!
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            if (TimeStopManager.isTimeStopped(serverLevel)) {
                TimeStopManager.resumeTime(serverLevel);
            } else {
                // Check if on cooldown
                if (player.getCooldowns().isOnCooldown(this)) {
                    player.displayClientMessage(Component.literal("Your Chronos Watch is recharging!").withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(stack);
                }

                TimeMode mode = getMode(stack);

                // Verify mode is unlocked for this watch tier
                if (!tier.isModeUnlocked(mode)) {
                    WatchTier required = WatchTier.getMinimumTierFor(mode);
                    player.displayClientMessage(Component.literal("This mode is locked! Requires " + required.getDisplayName()).withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(stack);
                }

                int duration = (player.isCreative() || tier.getDurationTicks() == 0) ? 0 : tier.getDurationTicks();
                TimeStopManager.startTimeStop(serverLevel, player, duration, mode);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        TimeMode mode = getMode(stack);

        tooltipComponents.add(tier.getFormattedName().copy()
                .append(Component.literal(" [Tier " + tier.getTierLevel() + "]").withStyle(ChatFormatting.DARK_GRAY)));

        tooltipComponents.add(Component.literal("Selected Mode: ").withStyle(ChatFormatting.WHITE)
                .append(mode.getFormattedComponent()));
        tooltipComponents.add(Component.literal("Mode Info: ").withStyle(ChatFormatting.GRAY)
                .append(mode.getDescriptionComponent()));

        tooltipComponents.add(Component.empty());

        String durationStr = tier.getDurationTicks() == 0 ? "Unlimited" : (tier.getDurationTicks() / 20) + "s";
        String cooldownStr = tier.getCooldownTicks() == 0 ? "None" : (tier.getCooldownTicks() / 20) + "s";
        tooltipComponents.add(Component.literal("- Duration: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(durationStr).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" | Cooldown: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(cooldownStr).withStyle(ChatFormatting.YELLOW)));

        if (tier.hasOffhandPassive()) {
            tooltipComponents.add(Component.literal("Off-Hand: ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("Passive Bullet-Dodge (" + tier.getDecelerationRadius() + "m Radius)").withStyle(ChatFormatting.WHITE)));
        } else {
            tooltipComponents.add(Component.literal("Off-Hand: ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("No Field Passive (Requires Tier 2+)").withStyle(ChatFormatting.DARK_GRAY)));
        }

        // Rune Socket Info
        if (tier.hasRuneSocket()) {
            RuneType runeType = getSocketedRuneType(stack);
            if (runeType != null) {
                tooltipComponents.add(Component.literal("Socketed: ").withStyle(ChatFormatting.GOLD)
                        .append(runeType.getFormattedComponent()));
                tooltipComponents.add(Component.literal("  " + runeType.getDescription()).withStyle(ChatFormatting.DARK_AQUA));
            } else {
                tooltipComponents.add(Component.literal("Rune Socket: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Empty (Shift+Right-Click to socket)").withStyle(ChatFormatting.DARK_GRAY)));
            }
        } else {
            tooltipComponents.add(Component.literal("Rune Socket: ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("None (Requires Tier 2+)").withStyle(ChatFormatting.DARK_GRAY)));
        }

        tooltipComponents.add(Component.empty());
        tooltipComponents.add(Component.literal("Shift + Right-click: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Open Mode & Rune Menu").withStyle(ChatFormatting.WHITE)));
        tooltipComponents.add(Component.literal("Right-click: ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Activate / Stop Selected Mode").withStyle(ChatFormatting.WHITE)));
    }
}
