package dev.taskbound.client;

import dev.taskbound.core.DifficultyTier;
import dev.taskbound.ui.BoardTaskQuery.Options;
import dev.taskbound.ui.BoardTaskQuery.Sort;
import dev.taskbound.ui.BoardTaskQuery.Timing;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compact modal for task difficulty, timing and ordering. */
final class BoardFilterScreen extends TaskboundScreen {
    private final GuildBoardScreen parent;
    private final List<String> currencies;
    private final Consumer<Options> apply;
    private Options options;
    private TaskboundSkin skin;
    private int left, top, menuWidth;

    BoardFilterScreen(GuildBoardScreen parent, Options options, List<String> currencies, Consumer<Options> apply) {
        super(Component.literal("Filter / Sort"));
        this.parent = parent;
        this.options = options;
        this.currencies = currencies;
        this.apply = apply;
    }

    @Override protected void init() {
        initCanvas();
        parent.resize(minecraft, width, height);
        skin = TaskboundSkin.load();
        menuWidth = Math.min(244, width - 24);
        left = (width - menuWidth) / 2;
        top = (height - 130) / 2;
        control(28, "Difficulty: " + (options.tierOrdinal() < 0 ? "All" : TaskBoardText.humanize(DifficultyTier.values()[options.tierOrdinal()].name())), () ->
                change(new Options(options.search(), (options.tierOrdinal() + 2) % 6 - 1, options.timing(), options.sort(), options.paymentCurrency())));
        control(50, "Timing: " + (options.timing() == null ? "All" : TaskBoardText.humanize(options.timing().name())), () -> {
            int i = options.timing() == null ? 0 : options.timing().ordinal() + 1;
            change(new Options(options.search(), options.tierOrdinal(), i == Timing.values().length ? null : Timing.values()[i], options.sort(), options.paymentCurrency()));
        });
        control(72, "Sort: " + switch (options.sort()) {
            case DEFAULT -> "Original order";
            case TITLE -> "Title A-Z";
            case DIFFICULTY -> "Difficulty: easiest first";
            case PAYMENT -> TaskBoardText.namedItem(options.paymentCurrency()).getString() + ": most first";
        }, () -> {
            Sort next = Sort.values()[(options.sort().ordinal() + 1) % Sort.values().length];
            if (next == Sort.PAYMENT && currencies.isEmpty()) next = Sort.DEFAULT;
            String currency = currencies.contains(options.paymentCurrency()) ? options.paymentCurrency() : currencies.isEmpty() ? "" : currencies.getFirst();
            change(new Options(options.search(), options.tierOrdinal(), options.timing(), next, currency));
        });
        addRenderableWidget(new TaskboundButton(skin, left + 12, top + 100, (menuWidth - 28) / 2, 20,
                Component.literal("Reset all"), false, () -> change(Options.defaults())).appearance("board/utility").colors("board_text", "board_muted"));
        addRenderableWidget(new TaskboundButton(skin, left + menuWidth / 2 + 2, top + 100, (menuWidth - 28) / 2, 20,
                Component.literal("Done"), false, this::onClose).appearance("board/action").colors("board_text", "board_muted"));
    }

    private TaskboundButton control(int y, String text, Runnable action) {
        return addRenderableWidget(new TaskboundButton(skin, left + 12, top + y, menuWidth - 24, 20, Component.literal(text), false, action)
                .appearance("board/utility").colors("board_text", "board_muted"));
    }
    private void change(Options options) { this.options = options; rebuildWidgets(); }

    @Override protected void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        parent.renderCanvas(graphics, -1, -1, partialTick);
        graphics.pose().pushPose();
        // Item icons in the parent render at an elevated depth. Keep the whole modal above them.
        graphics.pose().translate(0, 0, 300);
        graphics.fill(0, 0, width, height, 0x88000000);
        skin.draw(graphics, "board/paper", left, top, menuWidth, 130);
        graphics.drawString(font, TaskboundSkin.text(title), left + 12, top + 12, skin.color("ink"), false);
        for (var widget : renderables) widget.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }
    @Override public void onClose() { apply.accept(options); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
