package dev.taskbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A keyboard/narrator-accessible button with a replaceable texture and optional card renderer. */
final class TaskboundButton extends AbstractButton {
    interface Renderer { void render(GuiGraphics graphics, TaskboundButton button); }
    private final TaskboundSkin skin;
    private final Runnable action;
    private final Renderer renderer;
    private final boolean selected;
    private String appearance;
    private String selectionSprite = "selection/task_row";
    private boolean selectionOutline = true;
    private String enabledColor = "light", disabledColor = "muted";

    TaskboundButton(TaskboundSkin skin, int x, int y, int width, int height,
                    Component label, boolean selected, Runnable action) {
        this(skin, x, y, width, height, label, selected, action, null);
    }

    TaskboundButton(TaskboundSkin skin, int x, int y, int width, int height,
                    Component label, boolean selected, Runnable action, Renderer renderer) {
        super(x, y, width, height, label);
        this.skin = skin;
        this.action = action;
        this.renderer = renderer;
        this.selected = selected;
        this.appearance = width <= 22 ? "buttons/page" : "buttons/accept";
        setTooltip(Tooltip.create(label));
    }

    @Override public void onPress() { action.run(); }

    TaskboundButton appearance(String spritePrefix) {
        appearance = spritePrefix;
        return this;
    }

    TaskboundButton selection(String sprite) { selectionSprite = sprite; return this; }

    TaskboundButton withoutSelectionOutline() { selectionOutline = false; return this; }

    TaskboundButton colors(String enabled, String disabled) {
        enabledColor = enabled;
        disabledColor = disabled;
        return this;
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (renderer != null) {
            renderer.render(graphics, this);
            if (selectionOutline && (selected || isHoveredOrFocused() && active)) {
                skin.draw(graphics, selectionSprite, getX(), getY(), width, height);
            }
            return;
        }
        boolean section = appearance.startsWith("sections/");
        String state = selected ? section ? "selected" : "pressed"
                : !active && !section ? "disabled" : isHoveredOrFocused() && active ? "hover" : "normal";
        skin.draw(graphics, appearance + "_" + state, getX(), getY(), width, height);
        String symbol = switch (getMessage().getString()) {
            case "↑" -> "up";
            case "↓" -> "down";
            case "‹", "←" -> "left";
            case "›", "→" -> "right";
            case "×" -> "close";
            default -> null;
        };
        if (symbol != null) {
            skin.draw(graphics, "symbols/" + symbol, getX() + (width - 16) / 2, getY() + (height - 16) / 2, 16, 16);
        } else {
            var font = Minecraft.getInstance().font;
            Component label = TaskBoardText.elide(font, TaskboundSkin.text(getMessage()), width - 8);
            graphics.drawString(font, label, getX() + (width - font.width(label)) / 2,
                    getY() + (height - 8) / 2,
                    skin.color(active || selected ? enabledColor : disabledColor), false);
        }
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
