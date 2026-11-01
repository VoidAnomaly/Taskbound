package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.Map;

public record GeneratedObjective(String key, String type, Map<String, Object> parameters) {
    public GeneratedObjective {
        if (key.isBlank() || type.isBlank()) {
            throw new IllegalArgumentException("Objective key and type cannot be blank");
        }
        parameters = DataMaps.copy(parameters);
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("key", key);
        map.put("type", type);
        map.put("parameters", parameters);
        return map;
    }

    public static GeneratedObjective fromMap(Map<String, ?> map) {
        return new GeneratedObjective(
                DataMaps.string(map, "key"),
                DataMaps.string(map, "type"),
                DataMaps.map(map, "parameters")
        );
    }
}
