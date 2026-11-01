package dev.taskbound.client;

import dev.taskbound.ui.ScrollMetrics;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

/** The same native scrollbar artwork and interaction for rows and guild-strip pixels. */
final class TaskboundScrollbar extends AbstractWidget {
    private final TaskboundSkin skin;
    private final boolean horizontal;
    private final IntConsumer changed;
    private int total, visibleUnits, offset;
    private double grab;

    TaskboundScrollbar(TaskboundSkin skin, int x, int y, int width, int height, boolean horizontal,
            Component label, IntConsumer changed) {
        super(x, y, width, height, label);
        this.skin = skin;
        this.horizontal = horizontal;
        this.changed = changed;
    }

    void range(int total, int visible, int offset) {
        this.total = total;
        this.visibleUnits = visible;
        this.offset = Math.clamp(offset, 0, Math.max(0, total - visible));
        active = total > visible;
    }

    private ScrollMetrics metrics() { return new ScrollMetrics(horizontal ? width : height, total, visibleUnits, offset); }
    private double position(double x, double y) { return horizontal ? x - getX() : y - getY(); }

    @Override public void onClick(double x, double y) {
        ScrollMetrics m = metrics();
        double p = position(x, y);
        grab = p >= m.thumbStart() && p < m.thumbStart() + m.thumbLength() ? p - m.thumbStart() : m.thumbLength() / 2.0;
        move(p - grab);
    }

    @Override protected void onDrag(double x, double y, double dx, double dy) { move(position(x, y) - grab); }

    private void move(double position) {
        int next = metrics().offsetAt(position);
        if (next != offset) { offset = next; changed.accept(next); }
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        int step = horizontal ? 32 : 1;
        int next = switch (key) {
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> metrics().maximum();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> offset - step;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> offset + step;
            default -> offset;
        };
        if (next == offset) return false;
        offset = Math.clamp(next, 0, metrics().maximum());
        changed.accept(offset);
        return true;
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(graphics, "board/track", 0, horizontal ? width : height);
        ScrollMetrics m = metrics();
        draw(graphics, "board/thumb", m.thumbStart(), m.thumbLength());
        if (isFocused() && active) TaskboundSkin.outline(graphics, getX(), getY(), width, height, skin.color("highlight"));
    }

    private void draw(GuiGraphics graphics, String sprite, int start, int length) {
        if (horizontal) {
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + start, getY() + height, 0);
            graphics.pose().mulPose(new Quaternionf().rotateZ((float) (-Math.PI / 2)));
            skin.draw(graphics, sprite, 0, 0, height, length);
            graphics.pose().popPose();
        } else skin.draw(graphics, sprite, getX(), getY() + start, width, length);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.POSITION, Component.literal(offset + " / " + metrics().maximum()));
    }
}
