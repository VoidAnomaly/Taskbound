package dev.taskbound.core;

import java.util.Set;

public record RewardPolicy(
        int maximumRewardCount,
        boolean requireTangibleReward,
        int maximumRareRewardCount,
        Set<String> rareGroups
) {
    public static final RewardPolicy DEFAULT = new RewardPolicy(3, true, 1, Set.of("rare"));

    public RewardPolicy {
        if (maximumRewardCount < 1 || maximumRareRewardCount < 0) {
            throw new IllegalArgumentException("Invalid reward-count policy");
        }
        rareGroups = Set.copyOf(rareGroups);
    }

    public boolean isRare(RewardEntryDefinition entry) {
        return rareGroups.contains(entry.rarityGroup());
    }
}
