package com.timestop.item;

import com.timestop.combat.RuneManager;
import com.timestop.entity.ChronoCoinEntity;
import com.timestop.item.rune.RuneType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ChronoCoinItem extends Item {

    public ChronoCoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!RuneManager.hasRune(player, RuneType.RICOSHOT)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.literal("Rune of the Marksman (+RICOSHOT) must be socketed in a Chronos Watch!").withStyle(ChatFormatting.RED),
                        true
                );
            }
            return InteractionResultHolder.fail(itemstack);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.2F, 1.8F);

        if (!level.isClientSide) {
            ChronoCoinEntity coin = new ChronoCoinEntity(level, player);
            Vec3 look = player.getLookAngle();
            coin.shoot(look.x * 0.35, 0.72 + look.y * 0.15, look.z * 0.35, 0.75F, 1.0F);
            level.addFreshEntity(coin);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            itemstack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    public static void flipCoinFromInventory(Player player) {
        Level level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.2F, 1.8F);

        if (!level.isClientSide) {
            ChronoCoinEntity coin = new ChronoCoinEntity(level, player);
            Vec3 look = player.getLookAngle();
            coin.shoot(look.x * 0.35, 0.72 + look.y * 0.15, look.z * 0.35, 0.75F, 1.0F);
            level.addFreshEntity(coin);
        }
    }
}