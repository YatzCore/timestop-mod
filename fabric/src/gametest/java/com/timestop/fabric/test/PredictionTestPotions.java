package com.timestop.fabric.test;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.projectile.Arrow;
public final class PredictionTestPotions {
    public static ItemStack setPotion(ItemStack stack,Holder<Potion> potion) {stack.set(DataComponents.POTION_CONTENTS,new PotionContents(potion));return stack;}
    public static Holder<Potion> getPotion(ItemStack stack) {return stack.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).potion().orElse(null);}
    public static void setCustomEffects(ItemStack stack,java.util.List<net.minecraft.world.effect.MobEffectInstance> effects) {
        stack.set(DataComponents.POTION_CONTENTS,new PotionContents(java.util.Optional.empty(),java.util.Optional.empty(),effects));
    }
    public static Arrow arrow(Arrow old,ItemStack item) {
        var arrow=new Arrow(old.level(),old.getX(),old.getY(),old.getZ(),item,null);
        arrow.setOwner(old.getOwner());arrow.setDeltaMovement(old.getDeltaMovement());arrow.setNoGravity(old.isNoGravity());return arrow;
    }
}
