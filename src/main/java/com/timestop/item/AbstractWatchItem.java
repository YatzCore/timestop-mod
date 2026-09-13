package com.timestop.item;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
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

    public static ItemStack findActivationWatch(@Nullable Player player) {
        if (player == null) return ItemStack.EMPTY;
        if (player.getMainHandItem().getItem() instanceof AbstractWatchItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof AbstractWatchItem) return player.getOffhandItem();
        ItemStack best = ItemStack.EMPTY;
        int bestTier = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractWatchItem watch && watch.getTier().getTierLevel() > bestTier) {
                best = stack;
                bestTier = watch.getTier().getTierLevel();
            }
        }
        return best;
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag() : new CompoundTag();
    }

    private static void updateCustomTag(ItemStack stack, java.util.function.Consumer<CompoundTag> consumer) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, consumer);
    }

    public static TimeMode getMode(ItemStack stack) {
        if (stack.getItem() instanceof AbstractWatchItem watch) {
            CompoundTag tag = getCustomTag(stack);
            if (tag.contains("TimeMode")) {
                try {
                    TimeMode mode = TimeMode.valueOf(tag.getString("TimeMode"));
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
        updateCustomTag(stack, tag -> tag.putString("TimeMode", mode.name()));
    }

    public static boolean isGlobalScope(ItemStack stack) {
        if (stack.getItem() instanceof CreativeWatchItem || (stack.getItem() instanceof AbstractWatchItem w && w.getTier() == WatchTier.CREATIVE)) {
            CompoundTag tag = getCustomTag(stack);
            if (tag.contains("GlobalScope")) {
                return tag.getBoolean("GlobalScope");
            }
            return true; // Creative Clock is GLOBAL (Full Server) by default!
        }
        CompoundTag tag = getCustomTag(stack);
        if (tag.contains("GlobalScope")) {
            return tag.getBoolean("GlobalScope");
        }
        return false;
    }

    public static void setGlobalScope(ItemStack stack, boolean global) {
        updateCustomTag(stack, tag -> tag.putBoolean("GlobalScope", global));
    }

    public static ItemStack getSocketedRune(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (tag.contains("SocketedRuneType")) {
            try {
                RuneType type = RuneType.valueOf(tag.getString("SocketedRuneType"));
                Item item = ModItems.getRuneItem(type);
                ItemStack runeStack = new ItemStack(item);
                if (tag.contains("SocketedRuneFilter")) {
                    TemporalRuneItem.setTargetFilter(runeStack, com.timestop.combat.ChainTargetFilter.fromName(tag.getString("SocketedRuneFilter")));
                }
                return runeStack;
            } catch (Exception ignored) {}
        }
        return ItemStack.EMPTY;
    }

    public static void setSocketedRune(ItemStack watchStack, ItemStack runeStack) {
        if (runeStack.isEmpty()) {
            updateCustomTag(watchStack, tag -> {
                tag.remove("SocketedRuneType");
                tag.remove("SocketedRuneFilter");
            });
        } else if (runeStack.getItem() instanceof TemporalRuneItem runeItem) {
            updateCustomTag(watchStack, tag -> {
                tag.putString("SocketedRuneType", runeItem.getType().name());
                if (runeItem.getType() == RuneType.RICOCHET) {
                    tag.putString("SocketedRuneFilter", TemporalRuneItem.getTargetFilter(runeStack).name());
                } else {
                    tag.remove("SocketedRuneFilter");
                }
            });
        }
    }

    @Nullable
    public static RuneType getSocketedRuneType(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (tag.contains("SocketedRuneType")) {
            try {
                return RuneType.valueOf(tag.getString("SocketedRuneType"));
            } catch (Exception ignored) {}
        }
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

            player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 1.2F);
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

            player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 1.2F);
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
            boolean isOmnipotentActive = TimeStopManager.isGlobalTimeStopActive() || com.timestop.core.TemporalBubbleManager.hasCreativeBubble();
            if (isOmnipotentActive) {
                if (!player.isCreative() && !player.hasPermissions(2) && !player.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                    player.displayClientMessage(Component.literal("The temporal continuum is locked by an almighty force (Admin/Creative Clock)!").withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(stack);
                }
            }

            com.timestop.core.TemporalBubble existing = com.timestop.core.TemporalBubbleManager.getPlayerBubble(player.getUUID());
            if (existing != null) {
                com.timestop.core.TemporalBubbleManager.stopBubble(serverLevel, existing);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            } else if (TimeStopManager.isGlobalTimeStopActive()) {
                if (player.isCreative() || player.hasPermissions(2) || (TimeStopManager.getInitiatorUuid() != null && TimeStopManager.getInitiatorUuid().equals(player.getUUID()))) {
                    TimeStopManager.resumeTime(serverLevel);
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
                } else {
                    player.displayClientMessage(Component.literal("The global temporal field is locked by an almighty force (Creative/Command)!").withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(stack);
                }
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        TimeMode mode = getMode(stack);

        tooltipComponents.add(tier.getFormattedName().copy()
                .append(Component.literal(" [Tier " + tier.getTierLevel() + "]").withStyle(ChatFormatting.DARK_GRAY)));

        tooltipComponents.add(Component.literal("Selected Mode: ").withStyle(ChatFormatting.WHITE)
                .append(mode.getFormattedComponent()));
        tooltipComponents.add(Component.literal("Mode Info: ").withStyle(ChatFormatting.GRAY)
                .append(mode.getDescriptionComponent()));

        if (this.tier == WatchTier.CREATIVE || isGlobalScope(stack)) {
            boolean global = isGlobalScope(stack);
            tooltipComponents.add(Component.literal("- Scope: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(global ? "🌐 Full Server (Global)" : "🔮 Local Sphere (Bubble)").withStyle(global ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA))
                    .append(Component.literal(" [Shift+R-Click]").withStyle(ChatFormatting.DARK_GRAY)));
        }

        tooltipComponents.add(Component.empty());

        String durationStr = tier.getDurationTicks() == 0 ? "Unlimited" : (tier.getDurationTicks() / 20) + "s";
        String cooldownStr = tier.getCooldownTicks() == 0 ? "None" : (tier.getCooldownTicks() / 20) + "s";
        tooltipComponents.add(Component.literal("- Domain Radius: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal((int) tier.getBubbleRadius() + "m").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | Duration: ").withStyle(ChatFormatting.GRAY))
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
