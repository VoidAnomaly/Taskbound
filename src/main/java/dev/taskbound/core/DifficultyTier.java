package dev.taskbound.core;

public enum DifficultyTier {
    TRIVIAL(0, 1),
    EASY(15, 2),
    MODERATE(30, 4),
    HARD(50, 7),
    EXCEPTIONAL(75, 12);

    private final int minimumScore;
    private final int reputationReward;

    DifficultyTier(int minimumScore, int reputationReward) {
        this.minimumScore = minimumScore;
        this.reputationReward = reputationReward;
    }

    public int minimumScore() {
        return minimumScore;
    }

    public int reputationReward() {
        return reputationReward;
    }

    public static DifficultyTier fromScore(int score) {
        if (score < 0) {
            throw new IllegalArgumentException("Difficulty score cannot be negative");
        }
        DifficultyTier result = TRIVIAL;
        for (DifficultyTier tier : values()) {
            if (score >= tier.minimumScore) {
                result = tier;
            }
        }
        return result;
    }
}
