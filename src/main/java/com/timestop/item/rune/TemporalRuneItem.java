package com.timestop.item.rune;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class TemporalRuneItem extends Item {
    private final RuneType type;

    public TemporalRuneItem(Properties properties, RuneType type) {
        super(properties);
        this.type = type;
    }

    public RuneType getType() {
        return type;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return type != RuneType.BLANK;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        tooltipComponents.add(type.getFormattedComponent());
        tooltipComponents.add(Component.literal(type.getDescription()).withStyle(ChatFormatting.GRAY));

        tooltipComponents.add(Component.empty());
        if (type == RuneType.BLANK) {
            tooltipComponents.add(Component.literal("Crafting Component: ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Combine with catalysts to carve specialized runes.").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            if (type == RuneType.RICOCHET) {
                com.timestop.combat.ChainTargetFilter filter = getTargetFilter(stack);
                tooltipComponents.add(Component.literal("Target Mode: ").withStyle(ChatFormatting.GOLD)
                        .append(filter.getFormattedComponent()));
                tooltipComponents.add(Component.literal("[Sneak + Right-Click to Cycle Mode]").withStyle(ChatFormatting.DARK_GRAY));
                tooltipComponents.add(Component.empty());
            }

            tooltipComponents.add(Component.literal("Socketing: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Insert into Chronos Watch (Tier 2+) via Shift+Right-Click menu.").withStyle(ChatFormatting.WHITE)));
            tooltipComponents.add(Component.literal("Trigger: ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("Active in Off-Hand or during Time Control.").withStyle(ChatFormatting.WHITE)));
        }
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (type == RuneType.RICOCHET && player.isShiftKeyDown()) {
            com.timestop.combat.ChainTargetFilter current = getTargetFilter(stack);
            com.timestop.combat.ChainTargetFilter next = current.next();
            setTargetFilter(stack, next);

            if (level.isClientSide) {
                player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.8F);
                player.displayClientMessage(Component.literal("Voltaic Target Mode: ").withStyle(ChatFormatting.YELLOW)
                        .append(next.getFormattedComponent()), true);
            }
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return super.use(level, player, hand);
    }

    public static com.timestop.combat.ChainTargetFilter getTargetFilter(ItemStack stack) {
        if (stack != null && stack.hasTag() && stack.getTag().contains("ChainFilter")) {
            return com.timestop.combat.ChainTargetFilter.fromName(stack.getTag().getString("ChainFilter"));
        }
        return com.timestop.combat.ChainTargetFilter.HOSTILE;
    }

    public static void setTargetFilter(ItemStack stack, com.timestop.combat.ChainTargetFilter filter) {
        if (stack != null) {
            stack.getOrCreateTag().putString("ChainFilter", filter.name());
        }
    }
}
