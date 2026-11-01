package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.Map;

public record DifficultyComponent(String description, int points) {
    public DifficultyComponent {
        if (description.isBlank()) {
            throw new IllegalArgumentException("Difficulty descriptions cannot be blank");
        }
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("description", description);
        map.put("points", points);
        return map;
    }

    public static DifficultyComponent fromMap(Map<String, ?> map) {
        return new DifficultyComponent(DataMaps.string(map, "description"), DataMaps.integer(map, "points"));
    }
}
