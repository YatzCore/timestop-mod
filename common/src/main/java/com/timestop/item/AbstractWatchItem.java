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
    public static final int MIN_CREATIVE_RADIUS = 1;
    public static final int MAX_CREATIVE_RADIUS = 512;
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
        for (ItemStack stack : com.timestop.platform.Services.PLATFORM.getEquippedWatches(player)) {
            if (stack.getItem() instanceof AbstractWatchItem watch && watch.getTier().getTierLevel() > bestTier) {
                best = stack;
                bestTier = watch.getTier().getTierLevel();
            }
        }
        if (!best.isEmpty()) return best;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractWatchItem watch && watch.getTier().getTierLevel() > bestTier) {
                best = stack;
                bestTier = watch.getTier().getTierLevel();
            }
        }
        return best;
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
        if (mode.hasVariant()) stack.getOrCreateTag().putString("Variant_" + mode.baseMode().name(), mode.name());
    }

    /** Last variant chosen for a Slow Motion / Fast Forward card (World variant if never chosen). */
    public static TimeMode getPreferredVariant(ItemStack stack, TimeMode base) {
        if (!base.hasVariant()) return base;
        base = base.baseMode();
        if (stack.hasTag() && stack.getTag().contains("Variant_" + base.name())) {
            try {
                TimeMode mode = TimeMode.valueOf(stack.getTag().getString("Variant_" + base.name()));
                if (mode.baseMode() == base) return mode;
            } catch (IllegalArgumentException ignored) {}
        }
        return base;
    }

    public static boolean isGlobalScope(ItemStack stack) {
        if (stack.getItem() instanceof CreativeWatchItem || (stack.getItem() instanceof AbstractWatchItem w && w.getTier() == WatchTier.CREATIVE)) {
            if (stack.hasTag() && stack.getTag().contains("GlobalScope")) {
                return stack.getTag().getBoolean("GlobalScope");
            }
            return true; // Creative Clock is GLOBAL (Full Server) by default!
        }
        if (stack.hasTag() && stack.getTag().contains("GlobalScope")) {
            return stack.getTag().getBoolean("GlobalScope");
        }
        return false;
    }

    public static void setGlobalScope(ItemStack stack, boolean global) {
        stack.getOrCreateTag().putBoolean("GlobalScope", global);
    }

    public static double getBubbleRadius(ItemStack stack) {
        if (!(stack.getItem() instanceof AbstractWatchItem watch)) return WatchTier.CREATIVE.getBubbleRadius();
        if (watch.getTier() == WatchTier.CREATIVE && stack.hasTag() && stack.getTag().contains("BubbleRadius", Tag.TAG_ANY_NUMERIC)) {
            return Math.max(MIN_CREATIVE_RADIUS, Math.min(MAX_CREATIVE_RADIUS, stack.getTag().getInt("BubbleRadius")));
        }
        return watch.getTier().getBubbleRadius();
    }

    public static void setBubbleRadius(ItemStack stack, int radius) {
        if (stack.getItem() instanceof AbstractWatchItem watch && watch.getTier() == WatchTier.CREATIVE) {
            stack.getOrCreateTag().putInt("BubbleRadius", Math.max(MIN_CREATIVE_RADIUS, Math.min(MAX_CREATIVE_RADIUS, radius)));
        }
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

    public static boolean isMatrixRuneActive(ItemStack stack, RuneType type) {
        if (type == null || type == RuneType.BLANK) return false;
        if (stack.hasTag() && stack.getTag().contains("RuneMatrix")) {
            int mask = stack.getTag().getInt("RuneMatrix");
            return (mask & (1 << type.ordinal())) != 0;
        }
        return false;
    }

    public static void setMatrixRune(ItemStack stack, RuneType type, boolean active) {
        if (type == null || type == RuneType.BLANK) return;
        int mask = stack.hasTag() && stack.getTag().contains("RuneMatrix") ? stack.getTag().getInt("RuneMatrix") : 0;
        if (active) {
            mask |= (1 << type.ordinal());
        } else {
            mask &= ~(1 << type.ordinal());
        }
        stack.getOrCreateTag().putInt("RuneMatrix", mask);
    }

    public static void setAllMatrixRunes(ItemStack stack, boolean active) {
        if (active) {
            int mask = 0;
            for (RuneType t : RuneType.values()) {
                if (t != RuneType.BLANK) {
                    mask |= (1 << t.ordinal());
                }
            }
            stack.getOrCreateTag().putInt("RuneMatrix", mask);
        } else {
            stack.getOrCreateTag().putInt("RuneMatrix", 0);
        }
    }

    public static int getActiveMatrixRuneCount(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains("RuneMatrix")) return 0;
        int mask = stack.getTag().getInt("RuneMatrix");
        int count = 0;
        for (RuneType t : RuneType.values()) {
            if (t != RuneType.BLANK && (mask & (1 << t.ordinal())) != 0) {
                count++;
            }
        }
        return count;
    }

    public static boolean isRewindInitiatorExempt(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof AbstractWatchItem watch)) return false;
        if (watch.getTier() != WatchTier.CREATIVE) return false;
        return stack.hasTag() && stack.getTag().getBoolean("RewindExemptInitiator");
    }

    public static void setRewindInitiatorExempt(ItemStack stack, boolean exempt) {
        if (stack.getItem() instanceof AbstractWatchItem watch && watch.getTier() == WatchTier.CREATIVE) {
            stack.getOrCreateTag().putBoolean("RewindExemptInitiator", exempt);
        }
    }

    public static boolean isPlayerRewindExempt(@Nullable Player player) {
        if (player == null) return false;
        if (!com.timestop.core.TimeStopManager.isRewindExemptAllowed()) return false;
        ItemStack watch = findActivationWatch(player);
        return !watch.isEmpty() && isRewindInitiatorExempt(watch);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack watchStack, ItemStack carriedStack, net.minecraft.world.inventory.Slot slot, net.minecraft.world.inventory.ClickAction action, Player player, net.minecraft.world.entity.SlotAccess access) {
        if (action != net.minecraft.world.inventory.ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }

        if (!this.tier.hasRuneSocket()) {
            return false;
        }

        if (this.tier == WatchTier.CREATIVE) {
            if (carriedStack.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() != RuneType.BLANK) {
                boolean active = isMatrixRuneActive(watchStack, runeItem.getType());
                setMatrixRune(watchStack, runeItem.getType(), !active);
                player.playSound(active ? net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC : net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0F, active ? 0.8F : 1.2F);
                return true;
            }
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

        if (this.tier == WatchTier.CREATIVE) {
            ItemStack slotStack = slot.getItem();
            if (slotStack.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() != RuneType.BLANK) {
                boolean active = isMatrixRuneActive(watchStack, runeItem.getType());
                setMatrixRune(watchStack, runeItem.getType(), !active);
                player.playSound(active ? net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC : net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0F, active ? 0.8F : 1.2F);
                return true;
            }
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
            boolean isOmnipotentActive = TimeStopManager.isGlobalTimeStopActive() || com.timestop.core.TemporalBubbleManager.hasCreativeBubble();
            if (isOmnipotentActive) {
                if (!player.isCreative() && !player.hasPermissions(2) && !player.getUUID().equals(TimeStopManager.getInitiatorUuid())) {
                    player.displayClientMessage(Component.literal("The temporal continuum is locked by an almighty force (Admin/Creative Clock)!").withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (com.timestop.core.rewind.LocalRewind.cancel(player.getUUID())) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
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
                .append(Component.literal((int) getBubbleRadius(stack) + "m").withStyle(ChatFormatting.AQUA))
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
            if (this.tier == WatchTier.CREATIVE) {
                int activeCount = getActiveMatrixRuneCount(stack);
                tooltipComponents.add(Component.literal("Rune Matrix: ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(activeCount + " / 15 Active").withStyle(activeCount > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
                tooltipComponents.add(Component.literal("Rewind Self: ").withStyle(ChatFormatting.GRAY)
                        .append(isRewindInitiatorExempt(stack)
                                ? Component.literal("Exempt (World only)").withStyle(ChatFormatting.GOLD)
                                : Component.literal("Included (Normal)").withStyle(ChatFormatting.GREEN)));
                tooltipComponents.add(Component.literal("  Shift + Right-Click to configure").withStyle(ChatFormatting.DARK_AQUA));
            } else {
                RuneType runeType = getSocketedRuneType(stack);
                if (runeType != null) {
                    tooltipComponents.add(Component.literal("Socketed: ").withStyle(ChatFormatting.GOLD)
                            .append(runeType.getFormattedComponent()));
                    tooltipComponents.add(Component.literal("  " + runeType.getDescription()).withStyle(ChatFormatting.DARK_AQUA));
                } else {
                    tooltipComponents.add(Component.literal("Rune Socket: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("Empty (Shift+Right-Click to socket)").withStyle(ChatFormatting.DARK_GRAY)));
                }
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
