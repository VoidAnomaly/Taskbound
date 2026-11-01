package dev.taskbound.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class RewardGenerator {
    private final RewardPolicy policy;

    public RewardGenerator() {
        this(RewardPolicy.DEFAULT);
    }

    public RewardGenerator(RewardPolicy policy) {
        this.policy = policy;
    }

    public int budgetFor(int difficultyScore, double giverMultiplier) {
        if (difficultyScore < 0 || !Double.isFinite(giverMultiplier) || giverMultiplier < 0.0) {
            throw new IllegalArgumentException("Invalid reward-budget inputs");
        }
        long rounded = Math.round(difficultyScore * giverMultiplier);
        if (rounded > Integer.MAX_VALUE) {
            throw new ArithmeticException("Reward budget exceeds integer range");
        }
        return (int) rounded;
    }

    public RewardBundle generate(
            DifficultyResult difficulty,
            TaskGiverDefinition giver,
            int reputationLevel,
            List<RewardEntryDefinition> definitions,
            RandomGenerator random
    ) {
        int budget = budgetFor(difficulty.score(), giver.rewardMultiplier());
        int remaining = budget;
        int rareCount = 0;
        int selectionCount = 0;
        boolean hasTangible = false;
        var counts = new LinkedHashMap<String, Integer>();
        var selectedDefinitions = new LinkedHashMap<String, RewardEntryDefinition>();

        while (selectionCount < policy.maximumRewardCount()) {
            final int availableBudget = remaining;
            final int currentRareCount = rareCount;
            final boolean tangibleStillRequired = policy.requireTangibleReward() && !hasTangible;
            var eligible = definitions.stream()
                    .filter(entry -> entry.eligibleFor(
                            giver.id(), giver.rewardPoolIds(), difficulty.score(), reputationLevel))
                    .filter(entry -> entry.cost() <= availableBudget)
                    .filter(entry -> counts.getOrDefault(entry.id(), 0) < entry.maximumCount())
                    .filter(entry -> !policy.isRare(entry)
                            || currentRareCount < policy.maximumRareRewardCount())
                    .filter(entry -> !tangibleStillRequired || entry.tangible())
                    .sorted(Comparator.comparing(RewardEntryDefinition::id))
                    .toList();
            if (eligible.isEmpty()) {
                break;
            }
            RewardEntryDefinition selected = WeightedPicker.pick(eligible, RewardEntryDefinition::weight, random);
            counts.merge(selected.id(), 1, Integer::sum);
            selectedDefinitions.put(selected.id(), selected);
            remaining -= selected.cost();
            selectionCount++;
            hasTangible |= selected.tangible();
            if (policy.isRare(selected)) {
                rareCount++;
            }
        }

        var rewards = new ArrayList<GeneratedReward>();
        selectedDefinitions.forEach((entryId, definition) -> {
            int count = counts.get(entryId);
            rewards.add(new GeneratedReward(
                    entryId,
                    definition.type(),
                    count,
                    Math.multiplyExact(count, definition.cost()),
                    definition.payload()
            ));
        });
        rewards.sort(Comparator.comparing(GeneratedReward::entryId));
        return new RewardBundle(budget, budget - remaining, rewards);
    }
}
