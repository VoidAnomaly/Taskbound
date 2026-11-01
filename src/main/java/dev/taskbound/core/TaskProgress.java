package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TaskProgress {
    private final Map<String, Integer> objectiveProgress;
    private int playerDeaths;
    private final Map<String, Object> additionalData;

    public TaskProgress() {
        this(Map.of(), 0, Map.of());
    }

    public TaskProgress(
            Map<String, Integer> objectiveProgress,
            int playerDeaths,
            Map<String, Object> additionalData
    ) {
        if (playerDeaths < 0 || objectiveProgress.values().stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("Task progress counters cannot be negative");
        }
        this.objectiveProgress = new LinkedHashMap<>(objectiveProgress);
        this.playerDeaths = playerDeaths;
        this.additionalData = new LinkedHashMap<>(DataMaps.copy(additionalData));
    }

    public Map<String, Integer> objectiveProgress() {
        return Map.copyOf(objectiveProgress);
    }

    public int progressFor(String objectiveKey) {
        return objectiveProgress.getOrDefault(objectiveKey, 0);
    }

    public void addProgress(String objectiveKey, int amount) {
        if (objectiveKey.isBlank() || amount < 0) {
            throw new IllegalArgumentException("Invalid objective progress update");
        }
        objectiveProgress.merge(objectiveKey, amount, Math::addExact);
    }

    public int playerDeaths() {
        return playerDeaths;
    }

    public void recordPlayerDeath() {
        playerDeaths = Math.addExact(playerDeaths, 1);
    }

    public void resetProgress() {
        objectiveProgress.clear();
    }

    public Map<String, Object> additionalData() {
        return DataMaps.copy(additionalData);
    }

    public void putAdditionalData(String key, Object value) {
        if (key.isBlank()) {
            throw new IllegalArgumentException("Additional-data key cannot be blank");
        }
        additionalData.put(key, DataMaps.copyValue(value));
    }

    public Map<String, Object> toMap() {
        var progress = new LinkedHashMap<String, Object>();
        objectiveProgress.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> progress.put(entry.getKey(), entry.getValue()));
        var map = new LinkedHashMap<String, Object>();
        map.put("objective_progress", progress);
        map.put("player_deaths", playerDeaths);
        map.put("additional_data", DataMaps.copy(additionalData));
        return map;
    }

    public static TaskProgress fromMap(Map<String, ?> map) {
        var progress = new LinkedHashMap<String, Integer>();
        DataMaps.map(map, "objective_progress").forEach((key, value) -> {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("Objective progress values must be numeric");
            }
            progress.put(key, number.intValue());
        });
        return new TaskProgress(
                progress,
                DataMaps.integer(map, "player_deaths"),
                DataMaps.map(map, "additional_data")
        );
    }
}
