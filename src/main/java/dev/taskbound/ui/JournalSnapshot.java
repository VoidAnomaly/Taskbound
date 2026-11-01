package dev.taskbound.ui;

import dev.taskbound.core.ActiveTask;
import java.util.List;
import java.util.Map;

/** Player-owned read view; contains no board authority or reward-claim intent. */
public record JournalSnapshot(long gameTime, List<ActiveTask> activeTasks, List<GuildView> guilds, String notice) {
    public JournalSnapshot {
        activeTasks = List.copyOf(activeTasks); guilds = List.copyOf(guilds);
        notice = notice == null ? "" : notice;
    }
    public String toJson() {
        return UiJson.write(Map.of("game_time", gameTime, "active_tasks", activeTasks.stream().map(ActiveTask::toMap).toList(),
                "guilds", guilds.stream().map(GuildView::toMap).toList(), "notice", notice));
    }
    public static JournalSnapshot fromJson(String json) {
        Map<String, Object> map = UiJson.read(json);
        return new JournalSnapshot(UiJson.longValue(map, "game_time"), UiJson.objects(map, "active_tasks", ActiveTask::fromMap),
                UiJson.objects(map, "guilds", GuildView::fromMap), UiJson.string(map, "notice"));
    }
}
