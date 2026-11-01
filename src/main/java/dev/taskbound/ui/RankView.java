package dev.taskbound.ui;

import java.util.List;
import java.util.Map;

public record RankView(int level, int points, String name, List<String> unlockDescriptions) {
    public RankView {
        unlockDescriptions = List.copyOf(unlockDescriptions);
    }
    public Map<String, Object> toMap() {
        return Map.of("level", level, "points", points, "name", name, "unlocks", unlockDescriptions);
    }
    public static RankView fromMap(Map<String, ?> map) {
        return new RankView(UiJson.integer(map, "level"), UiJson.integer(map, "points"), UiJson.string(map, "name"),
                UiJson.list(map, "unlocks").stream().map(value -> (String) value).toList());
    }
}
