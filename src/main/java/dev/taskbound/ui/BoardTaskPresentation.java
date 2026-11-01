package dev.taskbound.ui;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedTask;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.core.TaskStatus;
import java.util.Collection;
import java.util.List;

/** Presentation-only slices of authoritative board state. */
public final class BoardTaskPresentation {
    private BoardTaskPresentation() { }

    public record ProgressCounts(long completed, long required) { }

    /** One unit per required action, capped per objective so excess cannot fill a different objective. */
    public static ProgressCounts progressCounts(ActiveTask active) {
        long completed = 0, required = 0;
        for (GeneratedObjective objective : active.task().objectives()) {
            int count = requiredCount(objective);
            required += count;
            completed += Math.min(count, active.progress().progressFor(objective.key()));
        }
        return new ProgressCounts(completed, required);
    }

    public static int requiredCount(GeneratedObjective objective) {
        Object value = objective.parameters().get("required_count");
        if (!(value instanceof Number)) value = objective.parameters().get("count");
        return value instanceof Number number ? Math.max(1, number.intValue()) : 1;
    }

    public static List<GeneratedTask> activeTasks(List<ActiveTask> tasks, String guild, boolean allGuilds) {
        return tasks.stream().map(ActiveTask::task)
                .filter(task -> allGuilds || task.giverId().equals(guild)).toList();
    }

    public static boolean readyToClaim(GeneratedTask task, Collection<String> claimableIds, long gameTime) {
        if (!claimableIds.contains(task.taskId())) return false;
        if (task.status() == TaskStatus.COMPLETED) return true;
        return task.status() == TaskStatus.ACTIVE
                && task.objectives().stream().anyMatch(objective -> objective.type().equals("hand_in_item"))
                && (task.expiresAt() == null || gameTime < task.expiresAt());
    }

    /** Opening the chooser does not require matching items to already be in the inventory. */
    public static boolean canOpenHandIn(ActiveTask active, long gameTime) {
        if (active == null || active.task().status() != TaskStatus.ACTIVE && active.task().status() != TaskStatus.COMPLETED) return false;
        if (active.task().status() != TaskStatus.COMPLETED && active.task().expiresAt() != null && gameTime >= active.task().expiresAt()) return false;
        return active.task().objectives().stream().anyMatch(objective -> objective.type().equals("hand_in_item"))
                && active.task().objectives().stream().filter(objective -> !objective.type().equals("hand_in_item"))
                        .allMatch(objective -> active.progress().progressFor(objective.key()) >= requiredCount(objective));
    }

    /** Call only for a displayed Tasks viewport, after search/filter/sort have been applied. */
    public static List<String> visibleUnseen(List<GeneratedTask> filtered, int offset, int rows,
            Collection<String> unseen, Collection<String> pending) {
        return filtered.stream().skip(Math.max(0, offset)).limit(Math.max(0, rows))
                .map(GeneratedTask::taskId).filter(unseen::contains).filter(id -> !pending.contains(id)).toList();
    }
}
