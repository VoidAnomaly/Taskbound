package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TaskGiverDefinition(
        String id,
        String displayName,
        ReputationThresholds reputationThresholds,
        Map<Integer, String> rankNames,
        List<String> categoryIds,
        Set<String> rewardPoolIds,
        double rewardMultiplier,
        boolean enabled,
        String iconItem
) {
    public TaskGiverDefinition(String id, String displayName, ReputationThresholds thresholds,
            Map<Integer, String> ranks, List<String> categories, Set<String> pools, double multiplier) {
        this(id, displayName, thresholds, ranks, categories, pools, multiplier, true, defaultIcon(id));
    }

    public TaskGiverDefinition {
        if (id.isBlank() || displayName.isBlank()) {
            throw new IllegalArgumentException("Giver ID and display name cannot be blank");
        }
        if (!Double.isFinite(rewardMultiplier) || rewardMultiplier < 0.0) {
            throw new IllegalArgumentException("Reward multiplier must be finite and non-negative");
        }
        rankNames = Map.copyOf(rankNames);
        categoryIds = List.copyOf(categoryIds);
        rewardPoolIds = Set.copyOf(rewardPoolIds);
        if (Set.copyOf(categoryIds).size() != categoryIds.size()) {
            throw new IllegalArgumentException("Giver category IDs must be unique");
        }
    }

    public Map<String, Object> toMap() {
        var ranks = new LinkedHashMap<String, Object>();
        rankNames.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ranks.put(Integer.toString(entry.getKey()), entry.getValue()));
        var map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        map.put("display_name", displayName);
        map.put("reputation", reputationThresholds.toMap());
        map.put("rank_names", ranks);
        map.put("category_ids", categoryIds);
        map.put("reward_pool_ids", rewardPoolIds.stream().sorted().toList());
        map.put("reward_multiplier", rewardMultiplier);
        map.put("enabled", enabled);
        map.put("icon_item", iconItem);
        return map;
    }

    public static TaskGiverDefinition fromMap(Map<String, ?> map) {
        var ranks = new LinkedHashMap<Integer, String>();
        DataMaps.map(map, "rank_names").forEach((level, name) -> {
            if (!(name instanceof String rankName)) {
                throw new IllegalArgumentException("Rank names must be strings");
            }
            ranks.put(Integer.parseInt(level), rankName);
        });
        return new TaskGiverDefinition(
                DataMaps.string(map, "id"),
                DataMaps.string(map, "display_name"),
                ReputationThresholds.fromMap(DataMaps.map(map, "reputation")),
                ranks,
                DataMaps.strings(map, "category_ids"),
                DataMaps.stringSet(map, "reward_pool_ids"),
                DataMaps.decimal(map, "reward_multiplier"),
                !map.containsKey("enabled") || DataMaps.bool(map, "enabled"),
                map.containsKey("icon_item") ? DataMaps.string(map, "icon_item") : defaultIcon(DataMaps.string(map, "id"))
        );
    }

    private static String defaultIcon(String id) {
        return switch (id) {
            case "taskbound:hunter" -> "minecraft:bow";
            case "taskbound:angler" -> "minecraft:fishing_rod";
            case "taskbound:wizard" -> "minecraft:brewing_stand";
            case "taskbound:porter" -> "minecraft:chest";
            case "taskbound:gathering" -> "minecraft:iron_hoe";
            case "taskbound:crafting" -> "minecraft:crafting_table";
            case "taskbound:exploration" -> "minecraft:compass";
            case "taskbound:challenges" -> "minecraft:shield";
            default -> "minecraft:book";
        };
    }
}
