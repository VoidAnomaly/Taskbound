package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record RewardEntryDefinition(
        String id,
        RewardType type,
        String poolId,
        double weight,
        int cost,
        int minimumDifficulty,
        Integer maximumDifficulty,
        Integer minimumReputationLevel,
        int maximumCount,
        Set<String> giverRestrictions,
        String rarityGroup,
        boolean tangible,
        Map<String, Object> payload
) {
    public RewardEntryDefinition {
        if (id.isBlank() || poolId.isBlank() || rarityGroup.isBlank()) {
            throw new IllegalArgumentException("Reward ID, pool ID, and rarity group cannot be blank");
        }
        if (!Double.isFinite(weight) || weight <= 0.0 || cost <= 0 || minimumDifficulty < 0
                || maximumDifficulty != null && maximumDifficulty < minimumDifficulty
                || minimumReputationLevel != null && minimumReputationLevel < 1
                || maximumCount < 1) {
            throw new IllegalArgumentException("Invalid reward configuration");
        }
        giverRestrictions = Set.copyOf(giverRestrictions);
        payload = DataMaps.copy(payload);
    }

    public boolean eligibleFor(
            String giverId,
            Set<String> giverRewardPools,
            int difficulty,
            int reputationLevel
    ) {
        return (giverRewardPools.isEmpty() || giverRewardPools.contains(poolId))
                && (giverRestrictions.isEmpty() || giverRestrictions.contains(giverId))
                && difficulty >= minimumDifficulty
                && (maximumDifficulty == null || difficulty <= maximumDifficulty)
                && (minimumReputationLevel == null || reputationLevel >= minimumReputationLevel);
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        map.put("type", type.name());
        map.put("pool_id", poolId);
        map.put("weight", weight);
        map.put("cost", cost);
        map.put("minimum_difficulty", minimumDifficulty);
        map.put("maximum_difficulty", maximumDifficulty);
        map.put("minimum_reputation_level", minimumReputationLevel);
        map.put("maximum_count", maximumCount);
        map.put("giver_restrictions", giverRestrictions.stream().sorted().toList());
        map.put("rarity_group", rarityGroup);
        map.put("tangible", tangible);
        map.put("payload", payload);
        return map;
    }

    public static RewardEntryDefinition fromMap(Map<String, ?> map) {
        return new RewardEntryDefinition(
                DataMaps.string(map, "id"),
                RewardType.valueOf(DataMaps.string(map, "type")),
                DataMaps.string(map, "pool_id"),
                DataMaps.decimal(map, "weight"),
                DataMaps.integer(map, "cost"),
                DataMaps.integer(map, "minimum_difficulty"),
                DataMaps.nullableInteger(map, "maximum_difficulty"),
                DataMaps.nullableInteger(map, "minimum_reputation_level"),
                DataMaps.integer(map, "maximum_count"),
                DataMaps.stringSet(map, "giver_restrictions"),
                DataMaps.string(map, "rarity_group"),
                DataMaps.bool(map, "tangible"),
                DataMaps.map(map, "payload")
        );
    }
}
