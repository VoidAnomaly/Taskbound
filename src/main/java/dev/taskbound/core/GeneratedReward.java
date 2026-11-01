package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.Map;

public record GeneratedReward(
        String entryId,
        RewardType type,
        int count,
        int totalCost,
        Map<String, Object> payload
) {
    public GeneratedReward {
        if (entryId.isBlank() || count < 1 || totalCost < 1) {
            throw new IllegalArgumentException("Invalid generated reward");
        }
        payload = DataMaps.copy(payload);
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("entry_id", entryId);
        map.put("type", type.name());
        map.put("count", count);
        map.put("total_cost", totalCost);
        map.put("payload", payload);
        return map;
    }

    public static GeneratedReward fromMap(Map<String, ?> map) {
        return new GeneratedReward(
                DataMaps.string(map, "entry_id"),
                RewardType.valueOf(DataMaps.string(map, "type")),
                DataMaps.integer(map, "count"),
                DataMaps.integer(map, "total_cost"),
                DataMaps.map(map, "payload")
        );
    }
}
