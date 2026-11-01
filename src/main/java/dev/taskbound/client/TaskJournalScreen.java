package dev.taskbound.client;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedTask;
import dev.taskbound.core.TaskStatus;
import dev.taskbound.network.TaskboundNetworking;
import dev.taskbound.ui.GuildView;
import dev.taskbound.ui.JournalSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Portable, read-only journal. The supplied pixel book uses live server-owned task data. */
public final class TaskJournalScreen extends TaskboundScreen {
    private static final int LINE_HEIGHT = 11;
    private static final int ROW_GAP = 6;
    private JournalSnapshot snapshot;
    private TaskboundSkin skin;
    private int taskPage, guildPage, detailScroll, guildDetailScroll, snapshotAge, pollTicks;
    @Nullable private String selectedTaskId;
    @Nullable private String selectedGuildId;
    private int panelX, panelY, panelWidth, panelHeight, pageWidth, leftX, rightX;
    private int headingY, bodyY, bodyHeight, navY, detailY, detailHeight;
    private int taskRows, taskRowHeight, guildRows, guildRowHeight, detailLineCount, guildDetailLineCount;
    @Nullable private TaskboundButton detailUp, detailDown, guildDetailUp, guildDetailDown;

    private TaskJournalScreen(JournalSnapshot snapshot) {
        super(Component.literal("Taskbound - Task Journal"));
        this.snapshot = snapshot;
    }

    public static void openOrUpdate(JournalSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TaskJournalScreen screen) screen.apply(snapshot);
        else minecraft.setScreen(new TaskJournalScreen(snapshot));
    }

    public static void updateIfOpen(JournalSnapshot snapshot) {
        if (Minecraft.getInstance().screen instanceof TaskJournalScreen screen) screen.apply(snapshot);
    }

    private void apply(JournalSnapshot updated) {
        if (updated.gameTime() < snapshot.gameTime()) return;
        boolean changed = !snapshot.activeTasks().stream().map(value -> value.task().taskId()).toList()
                .equals(updated.activeTasks().stream().map(value -> value.task().taskId()).toList())
                || !snapshot.guilds().stream().map(GuildView::id).toList().equals(updated.guilds().stream().map(GuildView::id).toList());
        snapshot = updated;
        snapshotAge = 0;
        if (selectedTaskId != null && selectedTask() == null) { selectedTaskId = null; changed = true; }
        if (selectedGuildId != null && selectedGuild() == null) { selectedGuildId = null; guildDetailScroll = 0; changed = true; }
        if (changed) rebuildWidgets();
    }

    @Override protected void init() {
        initCanvas();
        skin = TaskboundSkin.load();
        // Wider facing pages match the journal reference without shrinking native pixel art.
        panelWidth = Math.min(640, Math.max(1, width - 8));
        panelHeight = Math.min(360, Math.max(1, height - 8));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int padding = 32;
        int gutter = 24;
        pageWidth = Math.max(1, (panelWidth - padding * 2 - gutter) / 2);
        leftX = panelX + padding;
        rightX = leftX + pageWidth + gutter;
        headingY = panelY + 42;
        bodyY = panelY + 78;
        navY = panelY + panelHeight - 57;
        bodyHeight = Math.max(24, navY - bodyY - 8);
        taskRowHeight = 56;
        guildRowHeight = 46;
        taskRows = Math.max(1, Math.min(3, (bodyHeight + ROW_GAP) / (taskRowHeight + ROW_GAP)));
        guildRows = Math.max(1, Math.min(4, (bodyHeight + ROW_GAP) / (guildRowHeight + ROW_GAP)));
        taskRowHeight = Math.min(taskRowHeight, bodyHeight);
        guildRowHeight = Math.min(guildRowHeight, bodyHeight);
        detailY = bodyY + 48;
        detailHeight = Math.max(LINE_HEIGHT, bodyHeight - 48);
        taskPage = Mth.clamp(taskPage, 0, Math.max(0, taskPageCount() - 1));
        guildPage = Mth.clamp(guildPage, 0, Math.max(0, guildPageCount() - 1));
        detailUp = detailDown = guildDetailUp = guildDetailDown = null;
        button(panelX + panelWidth - 29, panelY + 29, 20, 20, "×", this::onClose);
        if (selectedGuildId == null) buildTasks();
        else {
            guildDetailUp = button(leftX, navY, 18, 18, "↑", () -> scrollGuildDetails(-visibleDetailLines()));
            button(leftX + 22, navY, Math.max(1, pageWidth - 44), 18,
                    Component.translatable("screen.taskbound.journal.active_tasks"), () -> {
                        selectedGuildId = null; guildDetailScroll = 0; rebuildWidgets();
                    });
            guildDetailDown = button(leftX + pageWidth - 18, navY, 18, 18, "↓", () -> scrollGuildDetails(visibleDetailLines()));
        }
        if (selectedTaskId == null) buildGuilds();
        else {
            detailUp = button(rightX, navY, 18, 18, "↑", () -> scrollDetails(-visibleDetailLines()));
            button(rightX + 22, navY, Math.max(1, pageWidth - 44), 18, pageWidth < 160 ? "Guilds" : "Reputation", () -> {
                selectedTaskId = null; detailScroll = 0; rebuildWidgets();
            });
            detailDown = button(rightX + pageWidth - 18, navY, 18, 18, "↓", () -> scrollDetails(visibleDetailLines()));
        }
    }

    private void buildTasks() {
        for (int row = 0; row < taskRows && taskPage * taskRows + row < snapshot.activeTasks().size(); row++) {
            ActiveTask active = snapshot.activeTasks().get(taskPage * taskRows + row);
            String id = active.task().taskId();
            addRenderableWidget(new TaskboundButton(skin, leftX, bodyY + row * (taskRowHeight + ROW_GAP),
                    pageWidth, taskRowHeight, TaskBoardText.taskTitle(active.task()),
                    id.equals(selectedTaskId), () -> {
                        selectedTaskId = id; selectedGuildId = null; detailScroll = 0; rebuildWidgets();
                    }, (graphics, widget) -> renderTask(graphics, widget, id)).selection("board/selection"));
        }
        TaskboundButton previous = button(leftX, navY, 18, 18, "‹", () -> changeTaskPage(-1));
        previous.active = taskPage > 0;
        TaskboundButton next = button(leftX + pageWidth - 18, navY, 18, 18, "›", () -> changeTaskPage(1));
        next.active = taskPage + 1 < taskPageCount();
    }

    private void buildGuilds() {
        for (int row = 0; row < guildRows && guildPage * guildRows + row < snapshot.guilds().size(); row++) {
            GuildView guild = snapshot.guilds().get(guildPage * guildRows + row);
            String id = guild.id();
            addRenderableWidget(new TaskboundButton(skin, rightX, bodyY + row * (guildRowHeight + ROW_GAP),
                    pageWidth, guildRowHeight, Component.translatable(guild.displayName()),
                    id.equals(selectedGuildId), () -> {
                        selectedGuildId = id; selectedTaskId = null; guildDetailScroll = 0; rebuildWidgets();
                    }, (graphics, widget) -> renderGuild(graphics, widget, id)).selection("board/selection"));
        }
        TaskboundButton previous = button(rightX, navY, 18, 18, "‹", () -> changeGuildPage(-1));
        previous.active = guildPage > 0;
        TaskboundButton next = button(rightX + pageWidth - 18, navY, 18, 18, "›", () -> changeGuildPage(1));
        next.active = guildPage + 1 < guildPageCount();
    }

    @Override protected void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        skin.journal(graphics, panelX, panelY, panelWidth, panelHeight);
        heading(graphics, Component.translatable(selectedGuildId == null ? "screen.taskbound.journal.active_tasks"
                : "screen.taskbound.journal.guild_milestones"), leftX, headingY, pageWidth, true);
        heading(graphics, Component.literal(selectedTaskId == null ? "Guild Reputation" : "Task Details"), rightX, headingY, pageWidth, true);
        if (selectedGuildId == null && snapshot.activeTasks().isEmpty()) {
            wrapped(graphics, Component.literal("Your journal is empty. Visit a task board to accept a task."),
                    leftX + 6, bodyY + 8, pageWidth - 12, Math.max(1, bodyHeight / LINE_HEIGHT), skin.color("muted"));
        }
        if (selectedGuildId != null) renderGuildDetails(graphics);
        if (selectedTaskId != null) renderDetails(graphics);
        else if (snapshot.guilds().isEmpty()) wrapped(graphics, Component.literal("No guild information available."),
                rightX + 6, bodyY + 8, pageWidth - 12, Math.max(1, bodyHeight / LINE_HEIGHT), skin.color("muted"));
        for (Renderable renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);
        if (selectedGuildId == null) centered(graphics, Component.literal((taskPage + 1) + " / " + taskPageCount()), leftX + pageWidth / 2,
                navY + 6, pageWidth - 44, skin.color("ink"));
        if (selectedTaskId == null) centered(graphics, Component.literal((guildPage + 1) + " / " + guildPageCount()),
                rightX + pageWidth / 2, navY + 6, pageWidth - 44, skin.color("ink"));
        if (!snapshot.notice().isBlank()) {
            centered(graphics, Component.literal(snapshot.notice()), panelX + panelWidth / 2,
                    panelY + panelHeight - 20, panelWidth - 32, skin.color("error"));
        }
    }

    private void renderTask(GuiGraphics graphics, TaskboundButton widget, String id) {
        ActiveTask active = findTask(id);
        if (active == null) return;
        int x = widget.getX(), y = widget.getY(), w = widget.getWidth(), h = widget.getHeight();
        rule(graphics, x, y + h - 1, w);
        GeneratedTask task = active.task();
        boolean completed = task.status() == TaskStatus.COMPLETED;
        taskTitle(graphics, TaskBoardText.taskTitle(task), x + 8, y + 4, w - (completed ? 34 : 14), 18, 1.5F, skin.color("ink"));
        if (completed) skin.draw(graphics, "symbols/check", x + w - 22, y + 7, 16, 16);
        int split = Math.max(90, w * 56 / 100);
        text(graphics, guildName(task.giverId()), x + 8, y + 24, split - 12, skin.color("muted"));
        var counts = dev.taskbound.ui.BoardTaskPresentation.progressCounts(active);
        text(graphics, Component.literal(counts.completed() + " / " + counts.required()),
                x + 8, y + h - 22, split - 14, skin.color(completed ? "success" : "ink"));
        skin.countedProgress(graphics, x + 8, y + h - 11, split - 14, 7, counts.completed(), counts.required());
        skin.locationIcon(graphics, task, x + split + 4, y + 22);
        text(graphics, TaskBoardText.location(task), x + split + 23, y + 27, w - split - 29, skin.color("ink"));
        skin.timingIcon(graphics, task, x + split + 4, y + h - 18);
        text(graphics, TaskBoardText.time(task, snapshot.gameTime() + snapshotAge),
                x + split + 29, y + h - 13, w - split - 35, skin.color("ink"));
    }

    private void renderGuild(GuiGraphics graphics, TaskboundButton widget, String id) {
        GuildView guild = findGuild(id);
        if (guild == null) return;
        int x = widget.getX(), y = widget.getY();
        rule(graphics, x, y + guildRowHeight - 1, pageWidth);
        int inset = 52, textWidth = pageWidth - inset - 8;
        skin.guildPlaque(graphics, guild.id(), guild.iconItem(), x + 4, y + 2);
        title(graphics, Component.translatable(guild.displayName()), x + inset, y + 3, textWidth, true);
        Component level = Component.literal("Lv " + guild.reputationLevel() + " ").append(Component.translatable(guild.rankName()));
        Component reputation = reputationCount(guild);
        int reputationWidth = Math.min(textWidth, font.width(TaskboundSkin.text(reputation)));
        text(graphics, level, x + inset, y + 22, textWidth - reputationWidth - 8, skin.color("muted"));
        text(graphics, reputation, x + inset + textWidth - reputationWidth, y + 22, reputationWidth, skin.color("board_ink"));
        skin.countedProgress(graphics, x + inset, y + 35, Math.max(1, textWidth), 7,
                guild.levelProgressPoints(), guild.levelProgressRequired());
        Component tooltip = Component.translatable(guild.displayName()).copy()
                .append("\n").append(Component.translatable("screen.taskbound.journal.level_rank",
                        guild.reputationLevel(), Component.translatable(guild.rankName())))
                .append("\n").append(reputationCount(guild)).append(" reputation")
                .append("\n").append(Component.translatable("screen.taskbound.journal.guild_hint"));
        widget.setTooltip(Tooltip.create(tooltip));
    }

    private void renderGuildDetails(GuiGraphics graphics) {
        GuildView guild = selectedGuild();
        if (guild == null) return;
        taskTitle(graphics, Component.translatable(guild.displayName()), leftX + 2, bodyY + 2, pageWidth - 8, 18, 1.5F, skin.color("ink"));
        Component points = reputationCount(guild);
        int pointsWidth = font.width(TaskboundSkin.text(points));
        text(graphics, Component.translatable("screen.taskbound.journal.level_rank", guild.reputationLevel(),
                Component.translatable(guild.rankName())), leftX + 2, bodyY + 25, pageWidth - pointsWidth - 16, skin.color("muted"));
        text(graphics, points, leftX + pageWidth - pointsWidth - 8, bodyY + 25, pointsWidth, skin.color("ink"));
        skin.countedProgress(graphics, leftX + 2, bodyY + 37, pageWidth - 10, 7,
                guild.levelProgressPoints(), guild.levelProgressRequired());
        var lines = new ArrayList<FormattedCharSequence>();
        var dividers = new HashSet<Integer>();
        for (Component line : guildMilestoneLines(guild)) {
            if (line.getString().isEmpty()) {
                dividers.add(lines.size());
                lines.add(Component.empty().getVisualOrderText());
            } else lines.addAll(font.split(TaskboundSkin.text(line), Math.max(1, pageWidth - 14)));
        }
        guildDetailLineCount = lines.size();
        guildDetailScroll = Mth.clamp(guildDetailScroll, 0, Math.max(0, guildDetailLineCount - visibleDetailLines()));
        renderDetailLines(graphics, lines, leftX, guildDetailScroll, dividers);
        if (guildDetailUp != null) guildDetailUp.active = guildDetailScroll > 0;
        if (guildDetailDown != null) guildDetailDown.active = guildDetailScroll < guildDetailLineCount - visibleDetailLines();
    }

    private List<Component> guildMilestoneLines(GuildView guild) {
        var milestones = new TreeMap<Integer, List<Component>>();
        for (var rank : guild.ranks()) {
            var lines = milestones.computeIfAbsent(rank.level(), ignored -> new ArrayList<>());
            lines.add(Component.translatable("screen.taskbound.journal.rank_heading", rank.level(),
                    Component.translatable(rank.name())).withStyle(style -> style.withBold(true)));
            String status = guild.reputationLevel() >= rank.level() ? "unlocked" : "locked";
            lines.add(Component.translatable("screen.taskbound.journal.rank_requirement", rank.points(),
                    Component.translatable("screen.taskbound.journal." + status)));
            if (rank.unlockDescriptions().isEmpty()) lines.add(Component.translatable("screen.taskbound.journal.no_unlocks"));
            for (String unlock : rank.unlockDescriptions()) lines.add(Component.translatable(
                    "screen.taskbound.journal.unlock", Component.translatable(unlock)));
        }
        for (var reward : guild.levelRewards()) {
            var lines = milestones.computeIfAbsent(reward.level(), ignored -> new ArrayList<>());
            lines.add(Component.translatable("screen.taskbound.journal.offer_heading", reward.level()).withStyle(style -> style.withBold(true)));
            for (var item : reward.items()) lines.add(Component.translatable("screen.taskbound.task_board.reward.item",
                    item.count(), TaskBoardText.namedItem(item.item())));
            String status = reward.claimed() ? "claimed" : reward.eligible() ? "claim_at_board" : "locked";
            lines.add(Component.translatable("screen.taskbound.journal." + status)
                    .withStyle(style -> style.withColor(skin.color(reward.eligible() ? "success" : "muted"))));
        }
        if (milestones.isEmpty()) return List.of(Component.translatable("screen.taskbound.journal.no_milestones"));
        var result = new ArrayList<Component>();
        for (var entry : milestones.entrySet()) {
            var lines = entry.getValue();
            if (guild.reputationLevel() < entry.getKey()) {
                lines.set(0, Component.translatable("screen.taskbound.journal.locked").append(" · ")
                        .append(lines.getFirst()).withStyle(style -> style.withBold(true)));
                for (Component line : lines) result.add(line.copy().withStyle(style -> style.withColor(skin.color("muted"))));
            } else result.addAll(lines);
            result.add(Component.empty());
        }
        return result;
    }

    private void renderDetails(GuiGraphics graphics) {
        ActiveTask active = selectedTask();
        if (active == null) return;
        taskTitle(graphics, TaskBoardText.taskTitle(active.task()), rightX + 2, bodyY + 2, pageWidth - 8, 30, 1.5F, skin.color("ink"));
        skin.rating(graphics, active.task(), rightX + 2, bodyY + 35);
        var lines = new ArrayList<FormattedCharSequence>();
        lines.addAll(font.split(TaskboundSkin.text(Component.literal(active.task().status() == TaskStatus.COMPLETED
                ? "Completed - ready to claim" : "Active task")), Math.max(1, pageWidth - 14)));
        lines.add(Component.empty().getVisualOrderText());
        for (Component line : TaskBoardText.details(active.task(), active.progress(), snapshot.gameTime() + snapshotAge)) {
            if (line.getString().isEmpty()) lines.add(Component.empty().getVisualOrderText());
            else lines.addAll(font.split(TaskboundSkin.text(line), Math.max(1, pageWidth - 14)));
        }
        lines.add(Component.empty().getVisualOrderText());
        lines.addAll(font.split(TaskboundSkin.text(Component.literal("Claim completed tasks at a task board.")), Math.max(1, pageWidth - 14)));
        detailLineCount = lines.size();
        int visible = visibleDetailLines();
        detailScroll = Mth.clamp(detailScroll, 0, Math.max(0, detailLineCount - visible));
        renderDetailLines(graphics, lines, rightX, detailScroll);
        if (detailUp != null) detailUp.active = detailScroll > 0;
        if (detailDown != null) detailDown.active = detailScroll < detailLineCount - visible;
    }

    private void renderDetailLines(GuiGraphics graphics, List<FormattedCharSequence> lines, int x, int scroll) {
        renderDetailLines(graphics, lines, x, scroll, Set.of());
    }

    private void renderDetailLines(GuiGraphics graphics, List<FormattedCharSequence> lines, int x, int scroll, Set<Integer> dividers) {
        int visible = visibleDetailLines();
        scissor(graphics, x, detailY, x + pageWidth - 7, detailY + detailHeight);
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            int y = detailY + i * LINE_HEIGHT;
            if (dividers.contains(scroll + i)) rule(graphics, x + 2, y + LINE_HEIGHT / 2, pageWidth - 14);
            else graphics.drawString(font, lines.get(scroll + i), x + 2, y, skin.color("ink"), false);
        }
        graphics.disableScissor();
        skin.scrollbar(graphics, x + pageWidth - 5, detailY, detailHeight, scroll, visible, lines.size());
    }

    @Override public void tick() {
        snapshotAge++;
        if (++pollTicks >= 20) { pollTicks = 0; TaskboundNetworking.refreshJournal(); }
    }

    @Override protected boolean scrollCanvas(double x, double y, double horizontal, double vertical) {
        if (x >= rightX && x < rightX + pageWidth && y >= bodyY && y < bodyY + bodyHeight) {
            if (selectedTaskId != null) scrollDetails(-(int) Math.signum(vertical) * 3);
            else changeGuildPage(-(int) Math.signum(vertical));
            return true;
        }
        if (x >= leftX && x < leftX + pageWidth && y >= bodyY && y < bodyY + bodyHeight) {
            if (selectedGuildId != null) scrollGuildDetails(-(int) Math.signum(vertical) * 3);
            else changeTaskPage(-(int) Math.signum(vertical));
            return true;
        }
        return super.scrollCanvas(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            int direction = key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
            if (selectedTaskId != null) scrollDetails(direction * visibleDetailLines());
            else if (selectedGuildId != null) scrollGuildDetails(direction * visibleDetailLines());
            else changeTaskPage(direction);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void scrollDetails(int amount) { detailScroll = Mth.clamp(detailScroll + amount, 0, Math.max(0, detailLineCount - visibleDetailLines())); }
    private void scrollGuildDetails(int amount) { guildDetailScroll = Mth.clamp(guildDetailScroll + amount, 0, Math.max(0, guildDetailLineCount - visibleDetailLines())); }
    private int visibleDetailLines() { return Math.max(1, detailHeight / LINE_HEIGHT); }
    private int taskPageCount() { return Math.max(1, (snapshot.activeTasks().size() + taskRows - 1) / taskRows); }
    private int guildPageCount() { return Math.max(1, (snapshot.guilds().size() + guildRows - 1) / guildRows); }
    private void changeTaskPage(int step) { taskPage = Math.max(0, taskPage + step); rebuildWidgets(); }
    private void changeGuildPage(int step) { guildPage = Math.max(0, guildPage + step); rebuildWidgets(); }
    @Nullable private ActiveTask selectedTask() { return selectedTaskId == null ? null : findTask(selectedTaskId); }
    @Nullable private ActiveTask findTask(String id) { return snapshot.activeTasks().stream().filter(value -> value.task().taskId().equals(id)).findFirst().orElse(null); }
    @Nullable private GuildView selectedGuild() { return selectedGuildId == null ? null : findGuild(selectedGuildId); }
    @Nullable private GuildView findGuild(String id) { return snapshot.guilds().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null); }
    private Component guildName(String id) { return snapshot.guilds().stream().filter(value -> value.id().equals(id)).findFirst().map(value -> Component.translatable(value.displayName())).orElseGet(() -> Component.literal(TaskBoardText.humanize(id))); }
    private Component reputationCount(GuildView guild) {
        return Component.literal(guild.maximumLevel() ? "MAX" : guild.levelProgressPoints() + " / " + guild.levelProgressRequired());
    }
    private TaskboundButton button(int x, int y, int width, int height, String text, Runnable action) {
        return button(x, y, width, height, Component.literal(text), action);
    }
    private TaskboundButton button(int x, int y, int width, int height, Component label, Runnable action) {
        return addRenderableWidget(new TaskboundButton(skin, x, y, width, height, label, false, action)
                .appearance(width <= 22 ? "buttons/page" : "board/utility").colors("board_text", "board_muted"));
    }
    private void rule(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, skin.color("board_rule"));
    }
    private void text(GuiGraphics graphics, Component value, int x, int y, int width, int color) {
        graphics.drawString(font, TaskBoardText.elide(font, TaskboundSkin.text(value), Math.max(0, width)), x, y, color, false);
    }
    private void centered(GuiGraphics graphics, Component value, int center, int y, int width, int color) {
        Component label = TaskBoardText.elide(font, TaskboundSkin.text(value), Math.max(0, width));
        graphics.drawString(font, label, center - font.width(label) / 2, y, color, false);
    }
    private void heading(GuiGraphics graphics, Component value, int x, int y, int width, boolean large) {
        int height = large ? 32 : 20;
        int maxRibbonWidth = Math.max(1, width - 32);
        int labelSpace = Math.max(0, maxRibbonWidth - 40);
        Component label = TaskboundSkin.text(value);
        int scale = large && font.width(label) * 2 <= labelSpace ? 2 : 1;
        label = TaskBoardText.elide(font, label, labelSpace / scale);
        int labelWidth = font.width(label) * scale;
        int ribbonWidth = Math.min(maxRibbonWidth, labelWidth + 40);
        skin.draw(graphics, x == rightX ? "board/journal_header_reputation" : "board/journal_header",
                x + (width - ribbonWidth) / 2, y, ribbonWidth, height);
        int start = x + (width - labelWidth) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(start, y + (height - 8 * scale) / 2, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, label, 0, 0, skin.color("board_text"), false);
        graphics.pose().popPose();
    }
    private void title(GuiGraphics graphics, Component value, int x, int y, int width, boolean large) {
        Component label = TaskboundSkin.text(value);
        int scale = large && font.width(label) * 2 <= width ? 2 : 1;
        label = TaskBoardText.elide(font, label, Math.max(0, width / scale));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, label, 0, 0, skin.color("ink"), false);
        graphics.pose().popPose();
    }
    private void wrapped(GuiGraphics graphics, Component value, int x, int y, int width, int maximum, int color) {
        List<FormattedCharSequence> lines = font.split(TaskboundSkin.text(value), Math.max(1, width));
        for (int i = 0; i < Math.min(maximum, lines.size()); i++) graphics.drawString(font, lines.get(i), x, y + i * LINE_HEIGHT, color, false);
    }
    @Override public boolean isPauseScreen() { return false; }
}
