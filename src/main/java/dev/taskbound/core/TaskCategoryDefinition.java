package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TaskCategoryDefinition(
        String id,
        String giverId,
        int minimumReputationLevel,
        Integer maximumReputationLevel,
        double baseWeight,
        int softBoostAfter,
        double recencyBoostPerMiss,
        int guaranteeAfter,
        List<String> templateIds,
        Set<String> requiredCapabilities,
        double newUnlockWeightMultiplier,
        int newUnlockBoostDraws
) {
    public TaskCategoryDefinition {
        if (id.isBlank() || giverId.isBlank()) {
            throw new IllegalArgumentException("Category and giver IDs cannot be blank");
        }
        if (minimumReputationLevel < 1
                || maximumReputationLevel != null && maximumReputationLevel < minimumReputationLevel) {
            throw new IllegalArgumentException("Invalid category reputation range");
        }
        if (!Double.isFinite(baseWeight) || baseWeight <= 0.0) {
            throw new IllegalArgumentException("Category base weight must be finite and positive");
        }
        if (softBoostAfter < 0 || !Double.isFinite(recencyBoostPerMiss) || recencyBoostPerMiss < 0.0) {
            throw new IllegalArgumentException("Invalid category recency configuration");
        }
        if (guaranteeAfter < 1) {
            throw new IllegalArgumentException("Guarantee threshold must be positive");
        }
        if (!Double.isFinite(newUnlockWeightMultiplier) || newUnlockWeightMultiplier <= 0.0
                || newUnlockBoostDraws < 0) {
            throw new IllegalArgumentException("Invalid new-unlock boost configuration");
        }
        if (templateIds.isEmpty()) {
            throw new IllegalArgumentException("A category must reference at least one template");
        }
        templateIds = List.copyOf(templateIds);
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        if (Set.copyOf(templateIds).size() != templateIds.size()) {
            throw new IllegalArgumentException("Category template IDs must be unique");
        }
    }

    public boolean unlockedAt(int reputationLevel) {
        return reputationLevel >= minimumReputationLevel
                && (maximumReputationLevel == null || reputationLevel <= maximumReputationLevel);
    }

    public double effectiveWeight(CategoryHistory history) {
        int boostedMisses = Math.max(0, history.eligibleMisses() - softBoostAfter);
        double result = baseWeight * (1.0 + boostedMisses * recencyBoostPerMiss);
        if (history.newlyUnlockedBoostDrawsRemaining() > 0) {
            result *= newUnlockWeightMultiplier;
        }
        return result;
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        map.put("giver_id", giverId);
        map.put("minimum_reputation_level", minimumReputationLevel);
        map.put("maximum_reputation_level", maximumReputationLevel);
        map.put("base_weight", baseWeight);
        map.put("soft_boost_after", softBoostAfter);
        map.put("recency_boost_per_miss", recencyBoostPerMiss);
        map.put("guarantee_after", guaranteeAfter);
        map.put("template_ids", templateIds);
        map.put("required_capabilities", requiredCapabilities.stream().sorted().toList());
        map.put("new_unlock_weight_multiplier", newUnlockWeightMultiplier);
        map.put("new_unlock_boost_draws", newUnlockBoostDraws);
        return map;
    }

    public static TaskCategoryDefinition fromMap(Map<String, ?> map) {
        return new TaskCategoryDefinition(
                DataMaps.string(map, "id"),
                DataMaps.string(map, "giver_id"),
                DataMaps.integer(map, "minimum_reputation_level"),
                DataMaps.nullableInteger(map, "maximum_reputation_level"),
                DataMaps.decimal(map, "base_weight"),
                DataMaps.integer(map, "soft_boost_after"),
                DataMaps.decimal(map, "recency_boost_per_miss"),
                DataMaps.integer(map, "guarantee_after"),
                DataMaps.strings(map, "template_ids"),
                DataMaps.stringSet(map, "required_capabilities"),
                DataMaps.decimal(map, "new_unlock_weight_multiplier"),
                DataMaps.integer(map, "new_unlock_boost_draws")
        );
    }
}
