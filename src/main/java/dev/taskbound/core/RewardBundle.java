package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RewardBundle(int budget, int spent, List<GeneratedReward> rewards) {
    public RewardBundle {
        if (budget < 0 || spent < 0 || spent > budget) {
            throw new IllegalArgumentException("Invalid reward budget accounting");
        }
        rewards = List.copyOf(rewards);
        int actualSpent = rewards.stream().mapToInt(GeneratedReward::totalCost).sum();
        if (actualSpent != spent) {
            throw new IllegalArgumentException("Reward costs do not match spent budget");
        }
    }

    public int remaining() {
        return budget - spent;
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("budget", budget);
        map.put("spent", spent);
        map.put("rewards", rewards.stream().map(GeneratedReward::toMap).toList());
        return map;
    }

    public static RewardBundle fromMap(Map<String, ?> map) {
        var rewards = DataMaps.list(map, "rewards").stream().map(value -> {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Generated rewards must be objects");
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> reward = (Map<String, ?>) raw;
            return GeneratedReward.fromMap(reward);
        }).toList();
        return new RewardBundle(DataMaps.integer(map, "budget"), DataMaps.integer(map, "spent"), rewards);
    }
}
