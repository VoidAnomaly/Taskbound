package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DifficultyResult(int score, DifficultyTier tier, List<DifficultyComponent> breakdown) {
    public DifficultyResult {
        if (score < 0 || tier != DifficultyTier.fromScore(score)) {
            throw new IllegalArgumentException("Difficulty tier does not match its score");
        }
        breakdown = List.copyOf(breakdown);
        int sum = breakdown.stream().mapToInt(DifficultyComponent::points).sum();
        if (sum != score) {
            throw new IllegalArgumentException("Difficulty breakdown does not add up to score");
        }
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("score", score);
        map.put("tier", tier.name());
        map.put("breakdown", breakdown.stream().map(DifficultyComponent::toMap).toList());
        return map;
    }

    public static DifficultyResult fromMap(Map<String, ?> map) {
        var breakdown = DataMaps.list(map, "breakdown").stream().map(value -> {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Difficulty breakdown entries must be objects");
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> component = (Map<String, ?>) raw;
            return DifficultyComponent.fromMap(component);
        }).toList();
        return new DifficultyResult(
                DataMaps.integer(map, "score"),
                DifficultyTier.valueOf(DataMaps.string(map, "tier")),
                breakdown
        );
    }
}
