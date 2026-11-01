package dev.taskbound.client;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedTask;
import dev.taskbound.config.TaskboundClientConfig;
import dev.taskbound.network.TaskboundNetworking;
import dev.taskbound.ui.BoardAction;
import dev.taskbound.ui.BoardSnapshot;
import dev.taskbound.ui.BoardTaskQuery;
import dev.taskbound.ui.BoardTaskSearch;
import dev.taskbound.ui.BoardTaskPresentation;
import dev.taskbound.ui.GuildView;
import dev.taskbound.ui.LevelRewardView;
import dev.taskbound.ui.RankView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Board actions remain server-owned; all artwork is supplied through TaskboundSkin. */
public final class GuildBoardScreen extends TaskboundScreen {
    private static final SystemToast.SystemToastId ACTION_ERROR = new SystemToast.SystemToastId();
    private static final float REPUTATION_TITLE_SCALE = 1.5F;
    private enum Section { TASKS, REPUTATION }
    private BoardSnapshot snapshot;
    private TaskboundSkin skin;
    private Section section = Section.TASKS;
    private boolean activeTasks;
    private boolean compactList;
    private boolean allActiveGuilds;
    private final Set<String> pendingSeenOffers = new HashSet<>();
    private final Set<String> displayedUnseenOffers = new HashSet<>();
    @Nullable private String selectedId;
    private int guildOffset;
    private int guildX, guildY, guildWidth, guildHeight, guildStep;
    private final List<TaskboundButton> guildButtons = new ArrayList<>();
    private final List<TaskboundButton> taskButtons = new ArrayList<>();
    private TaskboundScrollbar guildScrollbar, taskScrollbar;
    private TaskboundSearchBox searchField;
    private List<BoardTaskSearch.Document> searchDocuments = List.of();
    private BoardTaskQuery.Options query = BoardTaskQuery.Options.defaults();
    private List<GeneratedTask> filteredTasks = List.of();
    private final Consumer<BoardAction> actionSender;
    private boolean revealGuild = true;
    private int listScroll;
    private int detailScroll;
    private int reputationScroll;
    private int snapshotAge;
    private int pollTicks;
    private int waitingTicks;
    private boolean awaitingResponse;
    @Nullable private BoardAction pendingAction;
    private int panelX, panelY, panelWidth, panelHeight;
    private int contentX, contentY, contentWidth, contentHeight;
    private int searchY, searchWidth, navigationX;
    private int listWidth, listHeight, rowY, rowHeight, visibleRows;
    private int detailX, detailWidth, detailY, detailHeight;
    private int detailLineCount;
    private int detailTop, detailPanelHeight, sectionY, reputationTop, reputationRowHeight, reputationVisible;
    private int reputationSummaryWidth, milestonesX, milestonesWidth, reputationPanelHeight;
    private List<ReputationEntry> reputationEntries = List.of();
    @Nullable private TaskboundButton primaryButton;
    @Nullable private TaskboundButton abandonButton;

    private GuildBoardScreen(BoardSnapshot snapshot, Consumer<BoardAction> actionSender) {
        super(Component.literal("Taskbound • Task Board"));
        this.snapshot = snapshot;
        this.actionSender = actionSender;
        refreshQuery();
    }

    public static void openOrUpdate(BoardSnapshot snapshot) {
        openOrUpdate(snapshot, TaskboundNetworking::sendAction);
    }

    /** The transport is injectable so the real screen can also run against dev-only fixtures. */
    public static void openOrUpdate(BoardSnapshot snapshot, Consumer<BoardAction> actionSender) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof GuildBoardScreen screen && screen.matches(snapshot.boardId())) screen.applySnapshot(snapshot);
        else minecraft.setScreen(new GuildBoardScreen(snapshot, actionSender));
    }

    public static void updateIfOpen(BoardSnapshot snapshot) {
        if (Minecraft.getInstance().screen instanceof GuildBoardScreen screen && screen.matches(snapshot.boardId())) {
            if (!screen.snapshot.giverId().equals(snapshot.giverId())
                    && snapshot.guilds().stream().anyMatch(guild -> guild.id().equals(screen.snapshot.giverId()))
                    && (screen.pendingAction == null || screen.pendingAction.type() != BoardAction.Type.SELECT_GIVER
                        || !screen.pendingAction.giverId().equals(snapshot.giverId()))) return;
            screen.applySnapshot(snapshot);
        }
    }

    public static void rejectIfOpen(String boardId, String message, boolean closeScreen) {
        if (Minecraft.getInstance().screen instanceof GuildBoardScreen screen && screen.matches(boardId)) {
            if (closeScreen) {
                screen.onClose();
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player
                        .displayClientMessage(Component.literal(message), false);
                return;
            }
            screen.awaitingResponse = false;
            screen.pendingAction = null;
            screen.snapshot = screen.snapshot.withNotice(message);
            screen.showActionError(message);
            screen.rebuildWidgets();
        }
    }

    private boolean matches(String boardId) { return snapshot.boardId().equals(boardId); }

    private void applySnapshot(BoardSnapshot updated) {
        if (updated.gameTime() < snapshot.gameTime()) return;
        // A poll already in flight must not acknowledge a later mutation or guild selection.
        if (awaitingResponse && pendingAction != null) {
            if (!pendingAction.giverId().equals(updated.giverId())
                    && updated.guilds().stream().anyMatch(guild -> guild.id().equals(pendingAction.giverId()))) return;
            if (pendingAction.type() != BoardAction.Type.SELECT_GIVER && updated.notice().isBlank()) return;
        }
        boolean guildChanged = !snapshot.giverId().equals(updated.giverId());
        String oldSelected = selectedId;
        boolean rebuild = awaitingResponse || guildChanged || snapshot.reputationLevel() != updated.reputationLevel()
                || !snapshot.unseenOfferIdsByGuild().equals(updated.unseenOfferIdsByGuild())
                || !snapshot.guilds().equals(updated.guilds())
                || !snapshot.offers().stream().map(GeneratedTask::taskId).toList()
                        .equals(updated.offers().stream().map(GeneratedTask::taskId).toList())
                || !snapshot.activeTasks().stream().map(value -> value.task().taskId()).toList()
                        .equals(updated.activeTasks().stream().map(value -> value.task().taskId()).toList());
        snapshot = updated;
        pendingSeenOffers.retainAll(updated.unseenOfferIdsByGuild().values().stream().flatMap(List::stream).toList());
        snapshotAge = 0;
        awaitingResponse = false;
        pendingAction = null;
        if (guildChanged) {
            selectedId = null;
            listScroll = detailScroll = reputationScroll = 0;
            revealGuild = true;
        } else if (!activeTasks && oldSelected != null && findOffer(oldSelected) == null && findActive(oldSelected) != null) {
            activeTasks = true;
            selectedId = oldSelected;
            listScroll = detailScroll = 0;
            rebuild = true;
        }
        refreshQuery();
        if (rebuild) rebuildWidgets();
        else updateActions();
    }

    @Override protected void init() {
        initCanvas();
        skin = TaskboundSkin.load();
        if (snapshot.guilds().isEmpty()) {
            section = Section.TASKS;
            activeTasks = allActiveGuilds = true;
        }
        compactList = TaskboundClientConfig.COMPACT_TASK_LIST.getAsBoolean();
        panelWidth = 624;
        panelHeight = 336;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int margin = 18;
        contentX = panelX + margin;
        contentWidth = panelWidth - margin * 2;
        sectionY = panelY - 4;
        guildX = contentX;
        guildY = panelY + 28;
        guildWidth = contentWidth;
        guildHeight = 30;
        guildStep = Math.max(96, (guildWidth + 4) / 6);
        contentY = guildY + guildHeight + 10;
        contentHeight = panelY + panelHeight - 18 - contentY;
        detailWidth = (contentWidth - 8) * 32 / 100;
        listWidth = contentWidth - detailWidth - 8;
        detailX = contentX + listWidth + 8;
        detailTop = contentY;
        detailPanelHeight = contentHeight;
        searchY = contentY + (isActiveView() ? 19 : 5);
        rowY = searchY + 24;
        listHeight = contentY + contentHeight - rowY;
        // Distribute complete rows across the reclaimed footer space, keeping four-pixel gaps.
        visibleRows = Math.max(1, (listHeight + 4) / (compactList ? 32 : 46));
        rowHeight = (listHeight + 4) / visibleRows;
        detailY = detailTop + 70;
        detailHeight = Math.max(11, detailPanelHeight - (detailY - detailTop) - 33);
        refreshQuery();
        guildButtons.clear();
        taskButtons.clear();
        taskScrollbar = null;
        buildGuilds();
        navigationX = contentX + contentWidth - 256;
        TaskboundButton tasksTab = button(navigationX, sectionY, 68, 28, "Tasks", section == Section.TASKS && !activeTasks, () -> navigate(false, Section.TASKS)).appearance("sections/board");
        tasksTab.setTooltip(Tooltip.create(Component.literal(unseenOfferCount() > 0 ? "Tasks • " + unseenOfferCount() + " new offers" : "Tasks")));
        button(navigationX + 72, sectionY, 104, 28, "Reputation", section == Section.REPUTATION, () -> navigate(activeTasks, Section.REPUTATION)).appearance("sections/board");
        button(navigationX + 180, sectionY, 52, 28, "Active", section == Section.TASKS && activeTasks, () -> navigate(true, Section.TASKS)).appearance("sections/board");
        button(contentX + contentWidth - 20, sectionY, 20, 20, "×", false, this::onClose);
        primaryButton = abandonButton = null;
        if (section == Section.TASKS) buildTasks(); else buildReputation();
    }

    private void buildGuilds() {
        List<GuildView> guilds = guilds();
        int totalWidth = Math.max(0, guilds.size() * guildStep - 4);
        guildOffset = Mth.clamp(guildOffset, 0, Math.max(0, totalWidth - guildWidth));
        if (revealGuild) {
            for (int i = 0; i < guilds.size(); i++) if (guilds.get(i).id().equals(snapshot.giverId())) {
                int start = i * guildStep, end = start + guildStep - 4;
                if (start < guildOffset) guildOffset = start;
                if (end > guildOffset + guildWidth) guildOffset = end - guildWidth;
            }
            revealGuild = false;
        }
        for (int slot = 0; slot < guilds.size(); slot++) {
            GuildView guild = guilds.get(slot);
            boolean selected = guild.id().equals(snapshot.giverId()) && !(isActiveView() && allActiveGuilds);
            boolean hasNew = !snapshot.unseenOfferIdsByGuild().getOrDefault(guild.id(), List.of()).isEmpty();
            TaskboundButton tab = addRenderableWidget(new TaskboundButton(skin, guildX + slot * guildStep - guildOffset, guildY, guildStep - 4, guildHeight,
                    Component.translatable(guild.displayName()), selected, () -> selectGuild(guild.id()),
                    (graphics, widget) -> {
                        skin.draw(graphics, "board/" + (selected ? "tab_selected" : widget.isHoveredOrFocused() && widget.active ? "brown_hover" : "brown_normal"),
                                widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
                        int iconSize = 24;
                        int iconX = widget.getX() + 5, iconY = widget.getY() + (widget.getHeight() - iconSize) / 2;
                        skin.guildIconLarge(graphics, guild.id(), guild.iconItem(), iconX, iconY);
                        int labelX = iconX + iconSize + 4, labelWidth = widget.getWidth() - iconSize - 14;
                        text(graphics, Component.translatable(guild.displayName()), labelX, widget.getY() + 4,
                                labelWidth - (hasNew ? 13 : 0), skin.color(selected ? "board_text" : "board_muted"));
                        Component level = Component.literal("Lv " + guild.reputationLevel());
                        int levelWidth = font.width(TaskboundSkin.text(level));
                        text(graphics, level, labelX, widget.getY() + 15, labelWidth, skin.color("board_text"));
                        skin.countedProgress(graphics, labelX + levelWidth + 3, widget.getY() + 16,
                                Math.max(1, labelWidth - levelWidth - 3), 5,
                                guild.levelProgressPoints(), guild.levelProgressRequired());
                        if (hasNew) skin.draw(graphics, "board/new_badge", widget.getX() + widget.getWidth() - 14, widget.getY() + 3, 12, 12);
                    }).withoutSelectionOutline());
            tab.setTooltip(Tooltip.create(Component.translatable(guild.displayName()).copy()
                    .append("\nLevel " + guild.reputationLevel() + " • ")
                    .append(guild.maximumLevel() ? "Maximum level" : guild.levelProgressPoints() + " / " + guild.levelProgressRequired() + " to next level")
                    .append(hasNew ? "\nNew offers available" : "")
                    .append(isActiveView() ? selected ? "\nClick again to show all active tasks" : "\nFilter active tasks by this guild" : "")));
            guildButtons.add(tab);
        }
        guildScrollbar = addRenderableWidget(new TaskboundScrollbar(skin, guildX, guildY + guildHeight + 2, guildWidth, 6, true,
                Component.literal("Guilds"), this::setGuildOffset));
        setGuildOffset(guildOffset);
    }

    private void setGuildOffset(int offset) {
        int total = Math.max(0, guilds().size() * guildStep - 4);
        guildOffset = Mth.clamp(offset, 0, Math.max(0, total - guildWidth));
        for (int i = 0; i < guildButtons.size(); i++) {
            TaskboundButton tab = guildButtons.get(i);
            tab.setX(guildX + i * guildStep - guildOffset);
            tab.visible = tab.getX() < guildX + guildWidth && tab.getX() + tab.getWidth() > guildX;
            // Partially clipped tabs cannot take mouse or keyboard actions.
            tab.active = !awaitingResponse && tab.getX() >= guildX && tab.getX() + tab.getWidth() <= guildX + guildWidth
                    && (isActiveView() || !guilds().get(i).id().equals(snapshot.giverId()));
            if (!tab.active && tab.isFocused()) setFocused(null);
        }
        guildScrollbar.range(total, guildWidth, guildOffset);
    }

    private void buildTasks() {
        searchWidth = listWidth - 158;
        if (searchField == null) searchField = new TaskboundSearchBox(font, contentX + 23, searchY + 6, searchWidth - 22, 12,
                () -> searchDocuments, () -> skin);
        searchField.setPosition(contentX + 23, searchY + 6);
        searchField.setWidth(searchWidth - 22);
        searchField.setBordered(false);
        searchField.setTextColor(skin.color("board_text"));
        searchField.setTextShadow(false);
        searchField.setMaxLength(512);
        searchField.setHint(TaskboundSkin.text(Component.literal("Search tasks... (Tab)").withStyle(style -> style.withColor(skin.color("board_muted")))));
        searchField.setFormatter((value, cursor) -> TaskboundSkin.text(Component.literal(value)).getVisualOrderText());
        if (!searchField.getValue().equals(query.search())) searchField.setValue(query.search());
        searchField.setResponder(value -> {
            query = new BoardTaskQuery.Options(value, query.tierOrdinal(), query.timing(), query.sort(), query.paymentCurrency());
            listScroll = 0;
            refreshQuery();
            rebuildTaskRows();
            updateActions();
        });
        addRenderableWidget(searchField);
        TaskboundButton density = button(contentX + listWidth - 148, searchY, 64, 20,
                compactList ? "Compact" : "Relaxed", compactList, this::toggleListDensity).appearance("board/utility");
        density.setTooltip(Tooltip.create(Component.literal("List density: " + (compactList ? "Compact. Click for Relaxed." : "Relaxed. Click for Compact."))));
        TaskboundButton filter = button(contentX + listWidth - 80, searchY, 74, 20, "Filter/Sort", false, () -> minecraft.setScreen(new BoardFilterScreen(this, query,
                BoardTaskQuery.currencies(rawTasks()), options -> { query = options; listScroll = 0; refreshQuery(); }))).appearance("board/utility");
        filter.active = !awaitingResponse;
        taskScrollbar = addRenderableWidget(new TaskboundScrollbar(skin, contentX + listWidth - 8, rowY,
                6, listHeight, false, Component.literal("Tasks"), offset -> { listScroll = offset; rebuildTaskRows(); }));
        rebuildTaskRows();
        int footerY = panelY + panelHeight - 45;
        int split = activeTasks ? Math.max(38, (detailWidth - 20) / 2) : 0;
        abandonButton = button(detailX + 8, footerY, split, 20, "Abandon", false, this::abandon);
        primaryButton = button(detailX + 8 + (activeTasks ? split + 4 : 0), footerY,
                detailWidth - 16 - (activeTasks ? split + 4 : 0), 20, activeTasks ? "Claim" : "Accept task", false, this::primaryAction);
        button(detailX + detailWidth - 42, detailTop + 48, 16, 18, "↑", false, () -> scrollDetails(-3));
        button(detailX + detailWidth - 24, detailTop + 48, 16, 18, "↓", false, () -> scrollDetails(3));
        updateActions();
    }

    private void toggleListDensity() {
        TaskboundClientConfig.COMPACT_TASK_LIST.set(!compactList);
        TaskboundClientConfig.SPEC.save();
        rebuildWidgets();
    }

    private void rebuildTaskRows() {
        for (TaskboundButton row : taskButtons) removeWidget(row);
        taskButtons.clear();
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, tasks().size() - visibleRows));
        for (int row = 0; row < visibleRows && row + listScroll < tasks().size(); row++) {
            GeneratedTask task = tasks().get(row + listScroll);
            String taskId = task.taskId();
            int height = row == visibleRows - 1 ? listHeight - row * rowHeight : rowHeight - 4;
            TaskboundButton widget = addRenderableWidget(new TaskboundButton(skin, contentX + 4, rowY + row * rowHeight, listWidth - 15, height,
                    TaskBoardText.taskTitle(task), taskId.equals(selectedId), () -> selectTask(taskId),
                    (graphics, button) -> renderTaskRow(graphics, button, taskId)).selection("board/selection"));
            taskButtons.add(widget);
        }
        if (taskScrollbar != null) taskScrollbar.range(tasks().size(), visibleRows, listScroll);
    }

    @Override protected void rebuildWidgets() {
        boolean restoreSearchFocus = searchField != null && searchField.isFocused();
        super.rebuildWidgets();
        if (restoreSearchFocus && section == Section.TASKS) setFocused(searchField);
    }

    private void buildReputation() {
        GuildView guild = currentGuild();
        var entries = new ArrayList<ReputationEntry>();
        for (RankView rank : guild.ranks()) entries.add(new ReputationEntry(rank.level(), rank, null));
        for (LevelRewardView reward : guild.levelRewards()) entries.add(new ReputationEntry(reward.level(), null, reward));
        entries.sort(Comparator.comparingInt(ReputationEntry::level));
        reputationEntries = List.copyOf(entries);
        reputationSummaryWidth = Math.max(92, contentWidth * 26 / 100);
        milestonesX = contentX + reputationSummaryWidth + 6;
        milestonesWidth = contentWidth - reputationSummaryWidth - 6;
        reputationPanelHeight = contentHeight;
        reputationTop = contentY + 38;
        reputationRowHeight = 34;
        int available = contentY + reputationPanelHeight - 24 - reputationTop;
        reputationVisible = Math.max(1, available / reputationRowHeight);
        reputationScroll = Mth.clamp(reputationScroll, 0, Math.max(0, entries.size() - reputationVisible));
        for (int row = 0; row < reputationVisible && reputationScroll + row < entries.size(); row++) {
            ReputationEntry entry = entries.get(reputationScroll + row);
            int y = reputationTop + row * reputationRowHeight;
            boolean hasClaim = entry.reward != null && entry.reward.eligible() && !entry.reward.claimed();
            int statusWidth = 68;
            if (hasClaim) {
                TaskboundButton claim = button(milestonesX + milestonesWidth - statusWidth - 14, y + 6, statusWidth, 20,
                        "Claim gift", false,
                        () -> send(BoardAction.claimLevelReward(snapshot, guild.id(), entry.level)));
                claim.active = !awaitingResponse;
            }
            addRenderableWidget(new TaskboundButton(skin, milestonesX + 10, y, milestonesWidth - statusWidth - 28, reputationRowHeight - 2,
                    entry.label().copy().append("\n").append(entry.description()), false,
                    () -> {}, (graphics, widget) -> renderReputationRow(graphics, widget, entry, guild)).selection("board/selection"));
        }
        int navY = contentY + reputationPanelHeight - 22;
        button(milestonesX + milestonesWidth - 52, navY, 18, 18, "↑", false, () -> scrollReputation(-reputationVisible)).active = reputationScroll > 0;
        button(milestonesX + milestonesWidth - 30, navY, 18, 18, "↓", false, () -> scrollReputation(reputationVisible)).active = reputationScroll + reputationVisible < entries.size();
    }

    @Override protected void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        skin.board(graphics, panelX, panelY, panelWidth, panelHeight);
        skin.draw(graphics, "backgrounds/parchment", panelX + 12, guildY - 4, panelWidth - 24, guildHeight + 8);
        skin.draw(graphics, isActiveView() ? "board/active_body" : "board/body", panelX + 12, contentY - 2, panelWidth - 24, panelHeight - (contentY - panelY) - 10);
        skin.draw(graphics, "board/rail", panelX + 12, guildY + guildHeight + 2, panelWidth - 24, 8);
        if (section == Section.TASKS) renderTasks(graphics); else renderReputation(graphics);
        for (Renderable widget : renderables) {
            boolean guild = guildButtons.contains(widget);
            if (guild) scissor(graphics, guildX, guildY, guildX + guildWidth, guildY + guildHeight);
            widget.render(graphics, mouseX, mouseY, partialTick);
            if (guild) graphics.disableScissor();
        }
        if (unseenOfferCount() > 0) skin.draw(graphics, "board/new_badge", navigationX + 54, sectionY + 4, 12, 12);
        GeneratedTask selected = selectedTask();
        if (section == Section.TASKS && selected != null && inside(mouseX, mouseY, detailX + 8, detailTop + 8, detailWidth - 16, 38)) {
            tooltip(TaskBoardText.taskTitle(selected));
        }
        // A modal may render the board behind itself. Only the actual foreground Tasks viewport is seen.
        if (minecraft.screen == this && minecraft.isWindowActive() && section == Section.TASKS && !activeTasks && !awaitingResponse
                && !searchField.suggestionsVisible()) {
            displayedUnseenOffers.addAll(BoardTaskPresentation.visibleUnseen(tasks(), listScroll, visibleRows,
                    snapshot.unseenOfferIdsByGuild().getOrDefault(snapshot.giverId(), List.of()), pendingSeenOffers));
        }
        if (section == Section.TASKS && minecraft.screen == this) searchField.renderSuggestions(graphics);
    }

    private void renderTasks(GuiGraphics graphics) {
        if (activeTasks) {
            text(graphics, Component.literal(allActiveGuilds ? "All active tasks" : "Active tasks"), contentX + 10, contentY + 5,
                    listWidth - 24, skin.color("board_text"));
        }
        skin.draw(graphics, "board/paper", detailX, detailTop, detailWidth, detailPanelHeight);
        skin.draw(graphics, "board/search", contentX + 5, searchY, searchWidth, 20);
        skin.draw(graphics, "board/search_icon", contentX + 9, searchY + 4, 12, 12);
        if (tasks().isEmpty()) wrapped(graphics, Component.literal(rawTasks().isEmpty()
                ? activeTasks ? allActiveGuilds ? "No active tasks." : "No active tasks for this guild." : "No eligible offers."
                : "No tasks match your search or filters."), contentX + 10, rowY + 6, listWidth - 24, Math.max(1, visibleRows * 3), skin.color("board_muted"));
        GeneratedTask selected = selectedTask();
        if (selected == null) {
            heading(graphics, Component.literal("Task details"), detailX + 10, detailTop + 12, detailWidth - 20, 1);
            wrapped(graphics, Component.literal("Select a task to read its objectives and rewards."), detailX + 10, detailY, detailWidth - 20,
                    Math.max(1, detailHeight / 11), skin.color("muted"));
            return;
        }
        taskTitle(graphics, TaskBoardText.taskTitle(selected), detailX + 10, detailTop + 10, detailWidth - 20, 36, 1.5F, skin.color("ink"));
        skin.taskIcon(graphics, selected, detailX + 10, detailTop + 49);
        skin.rating(graphics, selected, detailX + 32, detailTop + 53);
        ActiveTask active = findActive(selected.taskId());
        List<Component> details = TaskBoardText.details(selected, active == null ? null : active.progress(), currentGameTime());
        var lines = new ArrayList<DetailLine>();
        boolean previousBlank = false;
        for (Component line : details) {
            String label = line.getString();
            String icon = label.equals("Objectives") ? "rewards/objective" : label.equals("Rewards") ? "rewards/currency" : null;
            if (icon != null) {
                // Use the existing section gap for its rule, rather than adding extra empty space.
                if (previousBlank) lines.removeLast();
                lines.add(new DetailLine(Component.empty().getVisualOrderText(), "rule"));
                lines.add(new DetailLine(TaskboundSkin.text(line.copy().withStyle(style -> style.withBold(true))).getVisualOrderText(), icon));
                lines.add(new DetailLine(Component.empty().getVisualOrderText(), null));
            } else if (label.isEmpty()) lines.add(new DetailLine(Component.empty().getVisualOrderText(), null));
            else for (FormattedCharSequence wrapped : font.split(TaskboundSkin.text(line), Math.max(1, detailWidth - 24))) lines.add(new DetailLine(wrapped, null));
            previousBlank = label.isEmpty();
        }
        detailLineCount = lines.size();
        int visible = Math.max(1, detailHeight / 11);
        detailScroll = Mth.clamp(detailScroll, 0, Math.max(0, lines.size() - visible));
        scissor(graphics, detailX + 4, detailY, detailX + detailWidth - 4, detailY + detailHeight);
        for (int i = 0; i < visible && i + detailScroll < lines.size(); i++) {
            DetailLine line = lines.get(i + detailScroll);
            int y = detailY + i * 11;
            if ("rule".equals(line.icon)) {
                graphics.fill(detailX + 10, y + 4, detailX + detailWidth - 10, y + 5, skin.color("board_rule"));
            } else if (line.icon != null) {
                if (line.icon.equals("rewards/currency")) graphics.renderItem(TaskBoardText.rewardIcon(), detailX + 8, y);
                else graphics.renderItem(TaskBoardText.item("minecraft:target"), detailX + 8, y);
                graphics.drawString(font, line.text, detailX + 28, y + 4, skin.color("board_ink"), false);
            } else graphics.drawString(font, line.text, detailX + 9, y, skin.color("board_ink"), false);
        }
        graphics.disableScissor();
        skin.scrollbar(graphics, detailX + detailWidth - 7, detailY, detailHeight, detailScroll, visible, lines.size());
    }

    private void renderTaskRow(GuiGraphics graphics, TaskboundButton widget, String id) {
        GeneratedTask task = activeTasks ? findActiveTask(id) : findOffer(id);
        if (task == null) return;
        String state = (activeTasks ? "active_" : "") + (id.equals(selectedId) ? "row_selected" : widget.isHoveredOrFocused() ? "row_hover" : "row");
        skin.draw(graphics, "board/" + state, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
        int y = widget.getY() + Math.max(0, (widget.getHeight() - (compactList ? 24 : 42)) / 2);
        int paymentWidth = compactList ? 50 : 76;
        int paymentX = widget.getX() + widget.getWidth() - paymentWidth - 4;
        int timingWidth = compactList ? 64 : 76;
        int timingX = compactList ? paymentX - timingWidth - 6 : paymentX;
        int x = widget.getX() + (activeTasks ? 44 : 26);
        int locationX = compactList ? widget.getX() + widget.getWidth() * 39 / 100 : x + 45;
        int mainWidth = (compactList ? locationX : timingX) - x - 7;
        int locationWidth = timingX - locationX - 7;
        int iconY = y + (compactList ? 4 : 9);
        skin.taskIcon(graphics, task, widget.getX() + 5, iconY);
        boolean ready = activeTasks && readyToClaim(task);
        if (ready) skin.draw(graphics, "board/ready_badge", widget.getX() + 25, iconY, 16, 16);
        int titleColor = skin.color(id.equals(selectedId) ? "board_selected_text" : "board_text");
        if (compactList) text(graphics, TaskBoardText.taskTitle(task), x, y + 4, mainWidth, titleColor);
        else taskTitle(graphics, TaskBoardText.taskTitle(task), x, y + 2, mainWidth, 18, 1, titleColor);
        skin.rating(graphics, task, x, y + (compactList ? 15 : 30));
        int locationY = y + (compactList ? 0 : 25);
        skin.locationIcon(graphics, task, locationX, locationY);
        text(graphics, TaskBoardText.location(task), locationX + 19, locationY + 4, locationWidth - 19, skin.color("board_muted"));
        int timingY = y + (compactList ? 0 : 1);
        skin.timingIcon(graphics, task, timingX, timingY);
        text(graphics, shortTime(task), timingX + 26, timingY + 4, timingWidth - 26, skin.color("board_text"));
        int paymentY = y + (compactList ? 0 : 19);
        Map<String, Long> payments = BoardTaskQuery.payments(task);
        if (payments.isEmpty()) {
            graphics.renderItem(TaskBoardText.rewardIcon(), paymentX + 4, paymentY);
            text(graphics, Component.literal("None"), paymentX + 26, paymentY + 4, paymentWidth - 26, skin.color("board_muted"));
        } else {
            var first = payments.entrySet().iterator().next();
            graphics.renderItem(TaskBoardText.item(first.getKey()), paymentX + 4, paymentY);
            String amount = first.getValue() + (payments.size() > 1 ? " …" : "");
            text(graphics, Component.literal(amount), paymentX + 26, paymentY + 4, paymentWidth - 26, skin.color("board_text"));
        }
        var tooltip = Component.literal(ready ? "Ready to claim\n" : "").append(TaskBoardText.taskTitle(task)).append("\n").append(TaskBoardText.location(task))
                .append("\n").append(TaskBoardText.time(task, currentGameTime(), activeTasks))
                .append("\nDifficulty: " + TaskBoardText.humanize(task.difficulty().tier().name()));
        ActiveTask active = findActive(id);
        if (activeTasks && active != null) tooltip.append("\n").append(TaskBoardText.progressLabel(active));
        if (activeTasks && allActiveGuilds) tooltip.append("\nGuild: ").append(guildName(task.giverId()));
        if (payments.isEmpty()) tooltip.append("\nNo monetary payment");
        for (var entry : payments.entrySet()) tooltip.append("\nPayment: " + entry.getValue() + " x ").append(TaskBoardText.namedItem(entry.getKey()));
        if (payments.size() > 1) tooltip.append("\n+" + (payments.size() - 1) + " additional currency types; totals kept separate");
        widget.setTooltip(Tooltip.create(tooltip));
    }

    private Component shortTime(GeneratedTask task) {
        return Component.literal(switch (BoardTaskQuery.timing(task)) {
            case UNLIMITED -> "Any";
            case DAY -> "Day";
            case NIGHT -> "Night";
            case TIMED -> TaskBoardText.formatTicks(BoardTaskQuery.durationOrRemainingTicks(task, activeTasks, currentGameTime()));
        });
    }

    private void renderReputation(GuiGraphics graphics) {
        GuildView guild = currentGuild();
        skin.draw(graphics, "board/list", contentX, contentY, reputationSummaryWidth, reputationPanelHeight);
        skin.draw(graphics, "board/paper", milestonesX, contentY, milestonesWidth, reputationPanelHeight);
        int iconScale = 3;
        graphics.pose().pushPose();
        graphics.pose().translate(contentX + (reputationSummaryWidth - 24 * iconScale) / 2, contentY + 22, 0);
        graphics.pose().scale(iconScale, iconScale, 1);
        skin.guildIconLarge(graphics, guild.id(), guild.iconItem(), 0, 0);
        graphics.pose().popPose();
        Component guildTitle = Component.translatable(guild.displayName()).copy().append(" Guild");
        reputationTitle(graphics, guildTitle, contentX + 8, contentY + 104, reputationSummaryWidth - 16);
        centered(graphics, Component.literal("Lv " + guild.reputationLevel() + " - ").append(Component.translatable(guild.rankName())),
                contentX + 8, contentY + 149, reputationSummaryWidth - 16, 1, skin.color("board_muted"));
        Component points = Component.literal(guild.maximumLevel() ? "MAX" : guild.levelProgressPoints() + " / " + guild.levelProgressRequired());
        int meterY = contentY + Math.min(164, reputationPanelHeight - 86);
        int meterWidth = reputationSummaryWidth - 22;
        int pointsWidth = Math.min(meterWidth / 2, font.width(TaskboundSkin.text(points)));
        text(graphics, Component.literal("Reputation"), contentX + 11, meterY, meterWidth - pointsWidth - 4, skin.color("board_text"));
        text(graphics, points, contentX + reputationSummaryWidth - 11 - pointsWidth,
                meterY, pointsWidth, skin.color("board_text"));
        skin.countedProgress(graphics, contentX + 11, meterY + 18, meterWidth, 10,
                guild.levelProgressPoints(), guild.levelProgressRequired());
        wrapped(graphics, Component.literal(guild.maximumLevel() ? "Maximum guild level reached." : "Complete guild tasks to earn reputation."),
                contentX + 11, meterY + 43, meterWidth,
                Math.max(0, (contentY + reputationPanelHeight - 20 - (meterY + 43)) / 11), skin.color("board_muted"));
        text(graphics, Component.literal("Up to " + dev.taskbound.core.OfferProgression.offerCount(guild.reputationLevel()) + " offers"),
                contentX + 11, contentY + reputationPanelHeight - 18, meterWidth, skin.color("board_text"));
        heading(graphics, Component.literal("Guild Milestones"), milestonesX + 14, contentY + 12, milestonesWidth - 28, 2);
        for (int row = 0; row < reputationVisible && reputationScroll + row < reputationEntries.size(); row++) {
            if (guild.reputationLevel() >= reputationEntries.get(reputationScroll + row).level()) {
                int y = reputationTop + row * reputationRowHeight;
                graphics.fill(milestonesX + 12, y, milestonesX + milestonesWidth - 15, y + reputationRowHeight - 1,
                        (skin.color("board_ink") & 0x00FFFFFF) | 0x18000000);
            }
        }
        for (int row = 0; row <= reputationVisible && reputationScroll + row <= reputationEntries.size(); row++) {
            int y = reputationTop + row * reputationRowHeight - 1;
            graphics.fill(milestonesX + 12, y, milestonesX + milestonesWidth - 15, y + 1, skin.color("board_rule"));
        }
        if (reputationEntries.isEmpty()) wrapped(graphics, Component.literal("No milestones configured for this guild."), milestonesX + 14,
                reputationTop + 8, milestonesWidth - 30, 3, skin.color("muted"));
        skin.scrollbar(graphics, milestonesX + milestonesWidth - 9, reputationTop,
                Math.max(12, reputationVisible * reputationRowHeight - 2), reputationScroll, reputationVisible, reputationEntries.size());
    }

    private void renderReputationRow(GuiGraphics graphics, TaskboundButton widget, ReputationEntry entry, GuildView guild) {
        boolean unlocked = guild.reputationLevel() >= entry.level;
        boolean claimed = entry.reward != null && entry.reward.claimed();
        int x = widget.getX(), y = widget.getY(), statusWidth = 68;
        int textX = x + 6, textWidth = widget.getWidth() - (textX - x) - 3;
        text(graphics, entry.label().copy().withStyle(style -> style.withBold(true)), textX, y + 5, textWidth,
                skin.color(unlocked ? "board_ink" : "muted"));
        int descriptionX = textX;
        if (entry.reward != null && !entry.reward.items().isEmpty()) {
            graphics.renderItem(TaskBoardText.item(entry.reward.items().getFirst().item()), textX, y + 15);
            descriptionX += 19;
        }
        Component description = entry.rank != null && !entry.rank.unlockDescriptions().isEmpty()
                ? Component.translatable(entry.rank.unlockDescriptions().getFirst()) : entry.description();
        text(graphics, description, descriptionX, y + 20, textWidth - (descriptionX - textX), skin.color("muted"));
        boolean claimable = entry.reward != null && entry.reward.eligible() && !claimed;
        if (!claimable) {
            String status = claimed ? "Claimed" : unlocked ? "Unlocked" : "Locked";
            text(graphics, Component.literal(status), milestonesX + milestonesWidth - statusWidth - 14,
                    y + (reputationRowHeight - 8) / 2, statusWidth, skin.color(unlocked ? "success" : "muted"));
        }
    }

    private record DetailLine(FormattedCharSequence text, @Nullable String icon) { }

    private void scrollDetails(int amount) {
        detailScroll = Mth.clamp(detailScroll + amount, 0, Math.max(0, detailLineCount - Math.max(1, detailHeight / 11)));
    }

    private void updateActions() {
        if (primaryButton == null) return;
        GeneratedTask selected = selectedTask();
        boolean handIn = activeTasks && selected != null && selected.objectives().stream().anyMatch(o -> o.type().equals("hand_in_item"));
        primaryButton.setMessage(handIn ? Component.translatable("taskbound.action.hand_in") : Component.literal(activeTasks ? "Claim" : "Accept task"));
        primaryButton.active = !awaitingResponse && selected != null && (activeTasks
                ? (handIn ? BoardTaskPresentation.canOpenHandIn(findActive(selected.taskId()), currentGameTime()) : readyToClaim(selected)) : selected.requiredReputationLevel() <= snapshot.reputationLevel());
        if (abandonButton != null) {
            abandonButton.visible = activeTasks;
            abandonButton.active = !awaitingResponse && activeTasks && selected != null;
        }
    }

    private void primaryAction() {
        GeneratedTask selected = selectedTask();
        if (selected != null) send(activeTasks ? BoardAction.claim(snapshot, selected.taskId()) : BoardAction.accept(snapshot, selected.taskId()));
    }

    private void abandon() {
        GeneratedTask selected = selectedTask();
        if (selected == null) return;
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) send(BoardAction.abandon(snapshot, selected.taskId()));
        }, Component.literal("Abandon this task?"), TaskBoardText.taskTitle(selected)));
    }

    private void send(BoardAction action) {
        if (awaitingResponse) return;
        awaitingResponse = true;
        pendingAction = action;
        waitingTicks = 0;
        actionSender.accept(action);
        rebuildWidgets();
    }

    private void showActionError(String message) {
        minecraft.getToasts().addToast(SystemToast.multiline(minecraft, ACTION_ERROR,
                Component.literal("Taskbound"), Component.literal(message)));
    }

    @Override public void tick() {
        snapshotAge++;
        updateActions();
        if (!awaitingResponse && section == Section.TASKS && !activeTasks && !displayedUnseenOffers.isEmpty()) {
            List<String> unseen = snapshot.unseenOfferIdsByGuild().getOrDefault(snapshot.giverId(), List.of());
            displayedUnseenOffers.retainAll(unseen);
            displayedUnseenOffers.removeAll(pendingSeenOffers);
            List<String> displayed = displayedUnseenOffers.stream().limit(16).toList();
            displayedUnseenOffers.removeAll(displayed);
            if (!displayed.isEmpty()) {
                pendingSeenOffers.addAll(displayed);
                actionSender.accept(BoardAction.markOffersSeen(snapshot, displayed));
            }
        }
        if (awaitingResponse) {
            if (++waitingTicks > 200) {
                awaitingResponse = false;
                pendingAction = null;
                snapshot = snapshot.withNotice("No server response. Please try again.");
                showActionError(snapshot.notice());
                rebuildWidgets();
            }
        } else if (++pollTicks >= 20) {
            pollTicks = 0;
            actionSender.accept(BoardAction.forBoard(snapshot, BoardAction.Type.REFRESH_VIEW));
        }
    }

    @Override protected boolean scrollCanvas(double x, double y, double horizontal, double vertical) {
        if (section == Section.TASKS && searchField != null && searchField.mouseScrolled(x, y, horizontal, vertical)) return true;
        if (inside(x, y, guildX, guildY, guildWidth, guildHeight + 8)) {
            setGuildOffset(guildOffset - (int) Math.signum(vertical != 0 ? vertical : horizontal) * guildStep);
            return true;
        }
        if (section == Section.REPUTATION && inside(x, y, contentX, contentY, contentWidth, contentHeight)) {
            scrollReputation(-(int) Math.signum(vertical)); return true;
        }
        if (section == Section.TASKS && inside(x, y, detailX, detailY, detailWidth, detailHeight)) {
            detailScroll = Mth.clamp(detailScroll - (int) Math.signum(vertical) * 3, 0, Math.max(0, detailLineCount - detailHeight / 11)); return true;
        }
        if (section == Section.TASKS && inside(x, y, contentX, contentY, listWidth, contentHeight)) {
            scrollList(-(int) Math.signum(vertical)); return true;
        }
        return super.scrollCanvas(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (section == Section.TASKS && searchField != null && searchField.isFocused()) {
            return searchField.keyPressed(key, scanCode, modifiers) || super.keyPressed(key, scanCode, modifiers);
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            int direction = key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
            if (section == Section.REPUTATION) scrollReputation(direction * 3);
            else detailScroll = Mth.clamp(detailScroll + direction * Math.max(1, detailHeight / 11), 0, Math.max(0, detailLineCount - detailHeight / 11));
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void scrollList(int amount) {
        listScroll = Mth.clamp(listScroll + amount, 0, Math.max(0, tasks().size() - visibleRows));
        rebuildTaskRows();
    }
    private void scrollReputation(int amount) { reputationScroll = Math.max(0, reputationScroll + amount); rebuildWidgets(); }
    private void selectTask(String id) { selectedId = id; detailScroll = 0; rebuildTaskRows(); updateActions(); }
    private void selectGuild(String guildId) {
        if (isActiveView()) {
            if (guildId.equals(snapshot.giverId())) {
                allActiveGuilds = !allActiveGuilds;
                listScroll = detailScroll = 0;
                refreshQuery();
                rebuildWidgets();
                return;
            }
            allActiveGuilds = false;
        }
        displayedUnseenOffers.clear();
        send(BoardAction.selectGiver(snapshot, guildId));
    }
    private void navigate(boolean active, Section next) {
        boolean viewChanged = activeTasks != active;
        activeTasks = active;
        section = next;
        if (viewChanged) { selectedId = null; listScroll = detailScroll = 0; }
        refreshQuery();
        rebuildWidgets();
    }
    private long currentGameTime() { return snapshot.gameTime() + snapshotAge; }
    private void refreshQuery() {
        var raw = rawTasks();
        var documents = new java.util.LinkedHashMap<String, BoardTaskSearch.Document>();
        for (var task : raw) documents.put(task.taskId(), TaskBoardText.searchDocument(task, guildName(task.giverId())));
        searchDocuments = List.copyOf(documents.values());
        filteredTasks = BoardTaskQuery.applyIndexed(raw, query, value -> documents.get(value.taskId()));
        if (filteredTasks.stream().noneMatch(value -> value.taskId().equals(selectedId))) {
            selectedId = filteredTasks.isEmpty() ? null : filteredTasks.getFirst().taskId();
            detailScroll = 0;
        }
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, filteredTasks.size() - visibleRows));
    }
    private List<GeneratedTask> rawTasks() { return activeTasks ? BoardTaskPresentation.activeTasks(snapshot.activeTasks(), snapshot.giverId(), allActiveGuilds) : snapshot.offers(); }
    private boolean isActiveView() { return section == Section.TASKS && activeTasks; }
    private boolean readyToClaim(GeneratedTask task) { return BoardTaskPresentation.readyToClaim(task, snapshot.claimableTaskIds(), currentGameTime()); }
    private int unseenOfferCount() { return snapshot.unseenOfferIdsByGuild().values().stream().mapToInt(List::size).sum(); }
    private Component guildName(String id) { return guilds().stream().filter(guild -> guild.id().equals(id)).findFirst().map(guild -> Component.translatable(guild.displayName())).orElseGet(() -> Component.literal(id)); }
    private List<GeneratedTask> tasks() { return filteredTasks; }
    @Nullable private GeneratedTask selectedTask() { return selectedId == null ? null : activeTasks ? findActiveTask(selectedId) : findOffer(selectedId); }
    @Nullable private GeneratedTask findOffer(String id) { return snapshot.offers().stream().filter(value -> value.taskId().equals(id)).findFirst().orElse(null); }
    @Nullable private ActiveTask findActive(String id) { return snapshot.activeTasks().stream().filter(value -> value.task().taskId().equals(id)).findFirst().orElse(null); }
    @Nullable private GeneratedTask findActiveTask(String id) { ActiveTask active = findActive(id); return active == null ? null : active.task(); }
    private List<GuildView> guilds() { return snapshot.guilds(); }
    private GuildView currentGuild() { return guilds().stream().filter(value -> value.id().equals(snapshot.giverId())).findFirst().orElseGet(this::fallbackGuild); }
    private GuildView fallbackGuild() { return new GuildView(snapshot.giverId(), snapshot.giverDisplayName(), "minecraft:paper", snapshot.reputationPoints(), snapshot.reputationLevel(), snapshot.reputationLevelFloor(), snapshot.nextReputationLevelThreshold(), snapshot.rankName(), List.of(), List.of()); }
    private TaskboundButton button(int x, int y, int width, int height, String label, boolean selected, Runnable action) {
        return addRenderableWidget(new TaskboundButton(skin, x, y, Math.max(1, width), height, Component.literal(label), selected, action)
                .appearance(width <= 22 ? "buttons/page" : "board/action").colors("board_text", "board_muted"));
    }
    private void text(GuiGraphics graphics, Component text, int x, int y, int width, int color) { graphics.drawString(font, TaskBoardText.elide(font, TaskboundSkin.text(text), Math.max(0, width)), x, y, color, false); }
    private void wrapped(GuiGraphics graphics, Component text, int x, int y, int width, int maxLines, int color) { List<FormattedCharSequence> lines = font.split(TaskboundSkin.text(text), Math.max(1, width)); for (int i = 0; i < Math.min(maxLines, lines.size()); i++) graphics.drawString(font, lines.get(i), x, y + i * 11, color, false); }
    private void heading(GuiGraphics graphics, Component label, int x, int y, int width, int scale) {
        Component text = TaskBoardText.elide(font, TaskboundSkin.text(label), Math.max(0, width / scale));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, skin.color("ink"), false);
        graphics.pose().popPose();
    }
    private void centered(GuiGraphics graphics, Component label, int x, int y, int width, int scale, int color) {
        Component text = TaskBoardText.elide(font, TaskboundSkin.text(label), Math.max(0, width / scale));
        graphics.pose().pushPose();
        graphics.pose().translate(x + (width - font.width(text) * scale) / 2, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
    private void reputationTitle(GuiGraphics graphics, Component label, int x, int y, int width) {
        List<FormattedCharSequence> lines = font.split(TaskboundSkin.text(label), Math.max(1, (int) (width / REPUTATION_TITLE_SCALE)));
        int count = Math.min(3, lines.size());
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (40 - count * font.lineHeight * REPUTATION_TITLE_SCALE) / 2, 0);
        graphics.pose().scale(REPUTATION_TITLE_SCALE, REPUTATION_TITLE_SCALE, 1);
        for (int i = 0; i < count; i++) {
            graphics.drawString(font, lines.get(i), (int) ((width / REPUTATION_TITLE_SCALE - font.width(lines.get(i))) / 2),
                    i * font.lineHeight, skin.color("board_text"), false);
        }
        graphics.pose().popPose();
    }
    private static boolean inside(double x, double y, int left, int top, int width, int height) { return x >= left && x < left + width && y >= top && y < top + height; }
    @Override public boolean isPauseScreen() { return false; }

    private record ReputationEntry(int level, @Nullable RankView rank, @Nullable LevelRewardView reward) {
        Component label() { return Component.literal("Level " + level + " • ").append(rank != null ? Component.translatable(rank.name()) : Component.literal("+1 offer")); }
        Component description() {
            if (reward != null) return Component.translatable(reward.description());
            if (rank == null) return Component.empty();
            var text = Component.literal(rank.points() + " reputation");
            if (rank.unlockDescriptions().isEmpty()) text.append(" • No additional tasks in this release.");
            for (String description : rank.unlockDescriptions()) text.append(" • ").append(Component.translatable(description));
            return text;
        }
    }
}
