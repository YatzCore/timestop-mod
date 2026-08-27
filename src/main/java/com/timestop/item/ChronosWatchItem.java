package com.timestop.item;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class ChronosWatchItem extends Item {
    public static final int DEFAULT_SURVIVAL_TICKS = 200; // 10 seconds

    public ChronosWatchItem(Properties properties) {
        super(properties);
    }

    public static TimeMode getMode(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("TimeMode")) {
            try {
                return TimeMode.valueOf(stack.getTag().getString("TimeMode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return TimeMode.TIME_STOP;
    }

    public static void setMode(ItemStack stack, TimeMode mode) {
        stack.getOrCreateTag().putString("TimeMode", mode.name());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // SHIFT + RIGHT CLICK: ONLY cycle mode, DO NOT activate!
        if (player.isShiftKeyDown()) {
            TimeMode current = getMode(stack);
            TimeMode next = current.next();
            setMode(stack, next);

            if (level.isClientSide) {
                player.displayClientMessage(Component.literal("§6[Mode Switched] " + next.getFormattedName() + " §7- " + next.getDescription()), true);
            } else {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // NORMAL RIGHT CLICK: Activate or Stop!
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            if (TimeStopManager.isTimeStopped(serverLevel)) {
                // Manually stopping ends the mode, which starts the cooldown in resumeTime()
                TimeStopManager.resumeTime(serverLevel);
            } else {
                // Check if on cooldown
                if (player.getCooldowns().isOnCooldown(this)) {
                    player.displayClientMessage(Component.literal("§cYour Chronos Watch is recharging!"), true);
                    return InteractionResultHolder.fail(stack);
                }

                TimeMode mode = getMode(stack);
                int duration = player.isCreative() ? 0 : DEFAULT_SURVIVAL_TICKS;
                // Note: Cooldown will begin when time stop ENDS in resumeTime()
                TimeStopManager.startTimeStop(serverLevel, player, duration, mode);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        TimeMode mode = getMode(stack);
        tooltipComponents.add(Component.literal("§6§lChronos Pocket Watch §7(Survival)").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal("Selected Mode: " + mode.getFormattedName()));
        tooltipComponents.add(Component.literal("Mode Info: " + mode.getDescription()).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal(""));
        tooltipComponents.add(Component.literal("§eShift + Right-click: §fCycle Temporal Mode").withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(Component.literal("§aRight-click: §fActivate / Stop Selected Mode").withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(Component.literal("• Duration: 10s | Cooldown: 15s (Starts on completion)").withStyle(ChatFormatting.DARK_AQUA));
    }
}
