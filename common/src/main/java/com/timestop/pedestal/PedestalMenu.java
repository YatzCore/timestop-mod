package com.timestop.pedestal;

import com.timestop.item.AbstractWatchItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public class PedestalMenu extends AbstractContainerMenu {
    private final PedestalBlockEntity pedestal;
    private final ContainerData data;
    public PedestalMenu(int id, Inventory playerInventory) { this(id, playerInventory, null); }
    public PedestalMenu(int id, Inventory playerInventory, PedestalBlockEntity pedestal) {
        super(ModPedestals.MENU.get(), id); this.pedestal = pedestal;
        Container inventory = pedestal == null ? new SimpleContainer(1) : pedestal.inventory;
        data = pedestal == null ? new SimpleContainerData(6) : new ContainerData() {
            public int getCount() { return 6; }
            public void set(int index, int value) {}
            public int get(int index) { return switch(index) {
                case 0 -> java.util.Arrays.asList(PedestalBlockEntity.MODES).indexOf(pedestal.getMode());
                case 1 -> pedestal.getRadius();
                case 2 -> pedestal.maxRadius();
                case 3 -> pedestal.isDisarmed() ? 3 : pedestal.isActive() ? 2 : pedestal.isPowered() ? 1 : 0;
                case 4 -> pedestal.pedestalTier().getTierLevel();
                case 5 -> { int mask = 0; for (int i = 0; i < 4; i++) if (pedestal.supports(PedestalBlockEntity.MODES[i])) mask |= 1 << i; yield mask; }
                default -> 0;
            }; }
        };
        addDataSlots(data);
        addSlot(new Slot(inventory, 0, 17, 28) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof AbstractWatchItem w && w.getTier().getTierLevel() <= data.get(4); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 142 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 200));
    }
    public int modeIndex() { return data.get(0); }
    public int radius() { return data.get(1); }
    public int maxRadius() { return Math.max(1, data.get(2)); }
    public int status() { return data.get(3); }
    public boolean supports(int index) { return (data.get(5) & (1 << index)) != 0; }
    @Override public boolean stillValid(Player player) {
        return pedestal == null || (!pedestal.isRemoved() && pedestal.getLevel() == player.level()
                && player.distanceToSqr(pedestal.getBlockPos().getX()+0.5, pedestal.getBlockPos().getY()+0.5, pedestal.getBlockPos().getZ()+0.5) <= 64);
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (pedestal == null || !stillValid(player)) return false;
        if (button >= 0 && button < 4 && supports(button)) pedestal.configure(PedestalBlockEntity.MODES[button], pedestal.getRadius());
        else return false;
        broadcastChanges(); return true;
    }
    public void setRadius(Player player, int radius) {
        if (pedestal == null || !stillValid(player) || radius < 1 || radius > pedestal.maxRadius()) return;
        pedestal.configure(pedestal.getMode(), radius);
        broadcastChanges();
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index == 0 ? !moveItemStackTo(stack, 1, slots.size(), true) : !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return original;
    }
}
