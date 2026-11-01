package dev.taskbound.client;

import dev.taskbound.ui.BoardTaskSearch;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

/** Native text editing with a small, keyboard-accessible search-completion popup. */
final class TaskboundSearchBox extends EditBox {
    private static final int ROW_HEIGHT = 12, MAX_ROWS = 5;
    private final Font font;
    private final Supplier<List<BoardTaskSearch.Document>> documents;
    private final Supplier<TaskboundSkin> skin;
    private BoardTaskSearch.Completion completion;
    private List<BoardTaskSearch.Document> previousDocuments = List.of();
    private String previousInput;
    private int previousCursor = -1, selected, offset;
    private boolean dismissed;

    TaskboundSearchBox(Font font, int x, int y, int width, int height,
            Supplier<List<BoardTaskSearch.Document>> documents, Supplier<TaskboundSkin> skin) {
        super(font, x, y, width, height, Component.literal("Search tasks and details"));
        this.font = font;
        this.documents = documents;
        this.skin = skin;
    }

    private void refreshSuggestions() {
        String input = getValue();
        int cursor = getCursorPosition();
        List<BoardTaskSearch.Document> currentDocuments = documents.get();
        boolean edited = !input.equals(previousInput) || cursor != previousCursor;
        if (edited || !currentDocuments.equals(previousDocuments)) {
            completion = BoardTaskSearch.complete(input, cursor, currentDocuments);
            previousInput = input;
            previousCursor = cursor;
            previousDocuments = currentDocuments;
            selected = offset = 0;
            if (edited) dismissed = false;
        }
    }

    boolean suggestionsVisible() {
        refreshSuggestions();
        return isFocused() && !dismissed && completion != null && !completion.values().isEmpty();
    }

    private int popupX() { return getX() - 18; }
    private int popupY() { return getY() + getHeight() + 4; }
    private int popupWidth() { return getWidth() + 22; }
    private int visibleRows() { return Math.min(MAX_ROWS, completion.values().size()); }
    private boolean overPopup(double x, double y) {
        return suggestionsVisible() && x >= popupX() && x < popupX() + popupWidth()
                && y >= popupY() && y < popupY() + visibleRows() * ROW_HEIGHT + 18;
    }

    void renderSuggestions(GuiGraphics graphics) {
        if (!suggestionsVisible()) return;
        TaskboundSkin theme = skin.get();
        int x = popupX(), y = popupY(), width = popupWidth();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        theme.draw(graphics, "board/search", x, y, width, visibleRows() * ROW_HEIGHT + 18);
        for (int row = 0; row < visibleRows(); row++) {
            int index = offset + row;
            int top = y + 2 + row * ROW_HEIGHT;
            if (index == selected) TaskboundSkin.outline(graphics, x + 2, top, width - 4, ROW_HEIGHT, theme.color("board_highlight"));
            var label = TaskBoardText.elide(font, TaskboundSkin.text(Component.literal(completion.values().get(index))), width - 12);
            graphics.drawString(font, label, x + 6, top + 2, theme.color(index == selected ? "board_selected_text" : "board_text"), false);
        }
        graphics.drawString(font, TaskboundSkin.text(Component.literal("Tab/Enter: use | Esc: close")),
                x + 6, y + visibleRows() * ROW_HEIGHT + 6, theme.color("board_muted"), false);
        graphics.pose().popPose();
    }

    private void choose(int step) {
        selected = Math.floorMod(selected + step, completion.values().size());
        offset = Math.clamp(offset, Math.max(0, selected - MAX_ROWS + 1), Math.min(selected, Math.max(0, completion.values().size() - MAX_ROWS)));
    }

    private void completeSelected() {
        String value = completion.values().get(selected);
        int cursor = completion.start() + value.length();
        String updated = completion.apply(getValue(), value);
        if (updated.length() > 512) return;
        setValue(updated);
        setCursorPosition(cursor);
        setHighlightPos(cursor);
        refreshSuggestions();
        dismissed = !value.endsWith(":");
    }

    @Override public void setFocused(boolean focused) {
        if (focused && !isFocused()) previousInput = null;
        super.setFocused(focused);
        if (!focused) dismissed = true;
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (suggestionsVisible()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { dismissed = true; return true; }
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) { choose(key == GLFW.GLFW_KEY_DOWN ? 1 : -1); return true; }
            if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                completeSelected(); return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean isMouseOver(double x, double y) { return overPopup(x, y) || super.isMouseOver(x, y); }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && overPopup(x, y)) {
            int row = (int) (y - popupY() - 2) / ROW_HEIGHT;
            if (y >= popupY() + 2 && row < visibleRows()) { selected = offset + row; completeSelected(); }
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0 || !overPopup(x, y)) return false;
        choose(-(int) Math.signum(vertical));
        return true;
    }

    @Override protected MutableComponent createNarrationMessage() {
        MutableComponent message = super.createNarrationMessage();
        if (suggestionsVisible()) message.append(". Suggestion: " + completion.values().get(selected)
                + ". Up and down choose; Tab or Enter completes; Escape dismisses.");
        return message;
    }
}
