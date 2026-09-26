package com.timestop.client.gui;

import com.timestop.pedestal.PedestalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PedestalScreen extends AbstractContainerScreen<PedestalMenu> {
    private final Button[] modes = new Button[4];
    private RadiusSlider slider;
    public PedestalScreen(PedestalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageHeight = 224; inventoryLabelY = 132;
    }
    @Override protected void init() {
        super.init();
        for (int i = 0; i < 4; i++) {
            final int mode = i;
            modes[i] = addRenderableWidget(Button.builder(Component.translatable("pedestal.timestop.mode." + i), b -> send(mode))
                    .bounds(leftPos + 46, topPos + 20 + i * 18, 122, 17).build());
        }
        slider = addRenderableWidget(new RadiusSlider(leftPos + 8, topPos + 108));
    }
    private void send(int button) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && slider != null && slider.dragging) return slider.mouseDragged(x, y, button, dx, dy);
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && slider != null && slider.dragging) {
            slider.mouseReleased(x, y, button);
            setDragging(false);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override protected void containerTick() {
        super.containerTick();
        for (int i = 0; i < 4; i++) {
            modes[i].active = menu.supports(i);
            modes[i].setMessage(Component.literal(menu.modeIndex() == i ? "> " : "").append(Component.translatable("pedestal.timestop.mode." + i)));
        }
        if (!slider.dragging) slider.sync();
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff292c30);
        g.fill(leftPos+2, topPos+2, leftPos+imageWidth-2, topPos+imageHeight-2, 0xffc7c2b5);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            g.fill(x-1,y-1,x+17,y+17,0xff5a5752); g.fill(x,y,x+16,y+16,0xff888579);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xff302b25, false);
        g.drawString(font, Component.translatable("pedestal.timestop.status." + menu.status()), 8, 96, 0xff302b25, false);
        g.drawString(font, playerInventoryTitle, 8, 132, 0xff302b25, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial); super.render(g, mouseX, mouseY, partial); renderTooltip(g, mouseX, mouseY);
    }
    private class RadiusSlider extends AbstractSliderButton {
        boolean dragging;
        RadiusSlider(int x, int y) { super(x,y,160,20,Component.empty(),0); sync(); }
        void sync() { value = menu.maxRadius() <= 1 ? 0 : (double)(menu.radius()-1)/(menu.maxRadius()-1); updateMessage(); }
        int selected() { return 1 + (int)Math.round(value * (menu.maxRadius()-1)); }
        @Override protected void updateMessage() { setMessage(Component.translatable("pedestal.timestop.radius", selected())); }
        @Override protected void applyValue() {
            com.timestop.network.ModMessages.sendToServer(new com.timestop.network.SetPedestalRadiusPacket(menu.containerId, selected()));
        }
        @Override public void onClick(double x, double y) { dragging = true; super.onClick(x,y); }
        @Override public void onRelease(double x, double y) { super.onRelease(x,y); dragging = false; }
    }
}
