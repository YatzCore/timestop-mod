package com.timestop.core.rewind;
import com.timestop.item.AbstractWatchItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
public final class WatchPreferences {
 private static final String ID="TimeStopWatchId";
 public static ItemStack snapshot(ItemStack stack) {
  com.timestop.combat.RewindRuneManager.identifyRunes(stack);
  if (stack.getItem() instanceof AbstractWatchItem && !stack.getOrCreateTag().hasUUID(ID)) stack.getOrCreateTag().putUUID(ID,UUID.randomUUID());
  return stack.copy();
 }
 public static Map<UUID, ItemStack> capture(ServerPlayer player) {
  Map<UUID,ItemStack> result=new HashMap<>();
  for(int i=0;i<player.getInventory().getContainerSize();i++) add(result,player.getInventory().getItem(i));
  add(result,player.containerMenu.getCarried());
  return result;
 }
 private static void add(Map<UUID,ItemStack> result,ItemStack stack) {
  if(stack.getItem() instanceof AbstractWatchItem) { snapshot(stack); result.put(stack.getTag().getUUID(ID),stack.copy()); }
 }
 public static ItemStack restore(ItemStack historical,Map<UUID,ItemStack> current) {
  ItemStack result=historical.copy();
  if(result.getItem() instanceof AbstractWatchItem && result.hasTag() && result.getTag().hasUUID(ID)) {
   var latest=current.get(result.getTag().getUUID(ID));
   if(latest!=null && latest.is(result.getItem())) {
    AbstractWatchItem.setMode(result,AbstractWatchItem.getMode(latest));
    AbstractWatchItem.setGlobalScope(result,AbstractWatchItem.isGlobalScope(latest));
   }
  }
  return result;
 }
}
