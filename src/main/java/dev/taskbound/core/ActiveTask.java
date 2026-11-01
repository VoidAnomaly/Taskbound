package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.Map;

public record ActiveTask(GeneratedTask task, TaskProgress progress) {
    public ActiveTask {
        if (task.status() != TaskStatus.ACTIVE && task.status() != TaskStatus.COMPLETED) {
            throw new IllegalArgumentException("Persisted active tasks must be active or completed");
        }
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("task", task.toMap());
        map.put("progress", progress.toMap());
        return map;
    }

    /** Lifecycle eligibility shared by the server's claim action and its UI snapshot. */
    public boolean readyToClaim(long gameTime) {
        return task.status() == TaskStatus.COMPLETED
                && (!Boolean.TRUE.equals(task.conditions().get("no_death")) || progress.playerDeaths() == 0)
                && task.objectives().stream().allMatch(objective -> {
                    Object count = objective.parameters().getOrDefault("required_count", 1);
                    int required = count instanceof Number number ? Math.max(1, number.intValue()) : 1;
                    return progress.progressFor(objective.key()) >= required;
                });
    }

    public static ActiveTask fromMap(Map<String, ?> map) {
        return new ActiveTask(
                GeneratedTask.fromMap(DataMaps.map(map, "task")),
                TaskProgress.fromMap(DataMaps.map(map, "progress"))
        );
    }
}
