package com.timestop.client.gui;

import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import com.timestop.network.ModMessages;
import com.timestop.network.SetWatchRadiusPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import com.timestop.item.WatchTarget;
import org.lwjgl.glfw.GLFW;

public class CreativeWatchRadiusScreen extends Screen {
    private final Screen parent;
    private final WatchTarget target;
    private EditBox input;
    private Button apply;
    private AbstractSliderButton slider;

    public CreativeWatchRadiusScreen(Screen parent, InteractionHand hand) {
        this(parent, WatchTarget.inHand(hand));
    }

    public CreativeWatchRadiusScreen(Screen parent, WatchTarget target) {
        super(Component.literal("Creative Watch Radius"));
        this.parent = parent;
        this.target = target;
    }

    @Override protected void init() {
        int x = (width - 280) / 2;
        int y = (height - 174) / 2;
        String previous = input == null ? Integer.toString((int) AbstractWatchItem.getBubbleRadius(target.resolve(minecraft.player))) : input.getValue();
        input = new EditBox(font, x + 95, y + 42, 90, 20, Component.literal("Radius in blocks"));
        input.setMaxLength(3);
        input.setFilter(value -> value.matches("[0-9]*"));
        input.setValue(previous);
        addRenderableWidget(input);
        slider = addRenderableWidget(new AbstractSliderButton(x + 16, y + 72, 248, 20, Component.empty(), fraction(validRadius() > 0 ? validRadius() : defaultRadius())) {
            @Override protected void updateMessage() {
                setMessage(Component.literal("Radius: " + radius() + " blocks"));
            }
            private int radius() {
                return AbstractWatchItem.MIN_CREATIVE_RADIUS + (int) Math.round(value * (AbstractWatchItem.MAX_CREATIVE_RADIUS - AbstractWatchItem.MIN_CREATIVE_RADIUS));
            }
            @Override protected void applyValue() { input.setValue(Integer.toString(radius())); }
            @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (validRadius() > 0) value = fraction(validRadius());
                updateMessage();
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
            }
        });
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> input.setValue(Integer.toString(defaultRadius())))
                .bounds(x + 16, y + 140, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(x + 104, y + 140, 72, 20).build());
        apply = addRenderableWidget(Button.builder(Component.literal("Apply"), button -> save()).bounds(x + 192, y + 140, 72, 20).build());
        input.setResponder(value -> apply.active = validRadius() > 0);
        apply.active = validRadius() > 0;
        setInitialFocus(input);
    }

    private static double fraction(int radius) {
        return (radius - AbstractWatchItem.MIN_CREATIVE_RADIUS) / (double) (AbstractWatchItem.MAX_CREATIVE_RADIUS - AbstractWatchItem.MIN_CREATIVE_RADIUS);
    }

    private static int defaultRadius() {
        return Math.max(AbstractWatchItem.MIN_CREATIVE_RADIUS, Math.min(AbstractWatchItem.MAX_CREATIVE_RADIUS, (int) WatchTier.CREATIVE.getBubbleRadius()));
    }

    private int validRadius() {
        try {
            int radius = Integer.parseInt(input.getValue());
            return radius >= AbstractWatchItem.MIN_CREATIVE_RADIUS && radius <= AbstractWatchItem.MAX_CREATIVE_RADIUS ? radius : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }

    private void save() {
        if (validRadius() < 0 || minecraft.player == null) return;
        var stack = target.resolve(minecraft.player);
        if (stack.getItem() instanceof AbstractWatchItem watch && watch.getTier() == WatchTier.CREATIVE) {
            int radius = validRadius();
            ModMessages.sendToServer(new SetWatchRadiusPacket(target, radius));
            AbstractWatchItem.setBubbleRadius(stack, radius);
        }
        onClose();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int x = (width - 280) / 2;
        int y = (height - 174) / 2;
        graphics.fill(x, y, x + 280, y + 174, 0xF2190E18);
        graphics.renderOutline(x, y, 280, 174, 0xFFBE185D);
        graphics.drawCenteredString(font, title, width / 2, y + 14, 0xFFF472B6);
        graphics.drawCenteredString(font, "1 - 512 blocks (default: " + defaultRadius() + ")", width / 2, y + 30, 0xFF94A3B8);
        graphics.drawCenteredString(font, "Used when you next activate a local field.", width / 2, y + 105, 0xFFE2E8F0);
        graphics.drawCenteredString(font, "Global scope affects the full server.", width / 2, y + 118, 0xFF94A3B8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
