package dev.taskbound.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** One full layout at every GUI setting, fitted to the window at whole physical pixels. */
abstract class TaskboundScreen extends Screen {
    private float canvasScale = 1;

    TaskboundScreen(Component title) { super(title); }

    protected final void initCanvas() {
        var window = minecraft.getWindow();
        double fit = Math.min(window.getWidth() / 640.0, window.getHeight() / 360.0);
        double pixels = fit >= 1 ? Math.floor(fit) : fit;
        canvasScale = (float) (pixels / window.getGuiScale());
        width = (int) Math.round(window.getWidth() / pixels);
        height = (int) Math.round(window.getHeight() / pixels);
    }

    @Override public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().scale(canvasScale, canvasScale, 1);
        renderCanvas(graphics, (int) (mouseX / canvasScale), (int) (mouseY / canvasScale), partialTick);
        graphics.pose().popPose();
    }

    protected abstract void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    protected final void scissor(GuiGraphics graphics, int left, int top, int right, int bottom) {
        // GuiGraphics scissors use window coordinates, independent of the pose stack.
        graphics.enableScissor(Math.round(left * canvasScale), Math.round(top * canvasScale),
                Math.round(right * canvasScale), Math.round(bottom * canvasScale));
    }

    protected final void tooltip(Component text) {
        // Screen renders this after our canvas transform is restored. Its tooltip splitter
        // handles newlines and wrapping; the single-Component GuiGraphics overload does not.
        setTooltipForNextRenderPass(TaskboundSkin.text(text));
    }

    /** Wrap before reducing an enlarged title; never shrink below the native font size. */
    protected final TitleLayout fitTitle(Component value, int width, int height, float maximumScale) {
        Component text = TaskboundSkin.text(value);
        List<FormattedCharSequence> lines = List.of();
        for (float scale = maximumScale; scale >= 1; scale -= 0.5F) {
            lines = font.split(text, Math.max(1, (int) (width / scale)));
            if (lines.size() * font.lineHeight * scale <= height) return new TitleLayout(lines, scale, false);
        }
        int count = Math.max(1, height / font.lineHeight);
        var visible = new ArrayList<>(lines.subList(0, Math.min(count, lines.size())));
        if (lines.size() <= count) return new TitleLayout(visible, 1, false);
        // Extremely long custom titles retain their full text in the tooltip.
        StringBuilder tail = new StringBuilder();
        visible.getLast().accept((index, style, codepoint) -> { tail.appendCodePoint(codepoint); return true; });
        Component last = TaskBoardText.elide(font, TaskboundSkin.text(Component.literal(tail + " …")), width);
        visible.set(visible.size() - 1, last.getVisualOrderText());
        return new TitleLayout(visible, 1, true);
    }

    protected final void taskTitle(GuiGraphics graphics, Component value, int x, int y, int width, int height,
                                   float maximumScale, int color) {
        TitleLayout layout = fitTitle(value, width, height, maximumScale);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(layout.scale(), layout.scale(), 1);
        for (int i = 0; i < layout.lines().size(); i++) {
            graphics.drawString(font, layout.lines().get(i), 0, i * font.lineHeight, color, false);
        }
        graphics.pose().popPose();
    }

    protected record TitleLayout(List<FormattedCharSequence> lines, float scale, boolean truncated) { }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Search handles Tab completion before reaching here. Never let a spare Tab
        // transfer selection to action buttons (including after completion is dismissed).
        return key == GLFW.GLFW_KEY_TAB || super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(x / canvasScale, y / canvasScale, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / canvasScale, y / canvasScale, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x / canvasScale, y / canvasScale, button, dx / canvasScale, dy / canvasScale);
    }

    @Override public void mouseMoved(double x, double y) { super.mouseMoved(x / canvasScale, y / canvasScale); }

    @Override public final boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return scrollCanvas(x / canvasScale, y / canvasScale, horizontal, vertical);
    }

    protected boolean scrollCanvas(double x, double y, double horizontal, double vertical) {
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
}
