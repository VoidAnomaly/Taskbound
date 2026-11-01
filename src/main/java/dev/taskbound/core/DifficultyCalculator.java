package dev.taskbound.core;

import java.util.ArrayList;

public final class DifficultyCalculator {
    public DifficultyResult calculate(GeneratedTaskDraft task) {
        var breakdown = new ArrayList<DifficultyComponent>();
        breakdown.add(new DifficultyComponent("Base template", task.baseDifficulty()));
        breakdown.addAll(task.difficultyComponents());
        int score = 0;
        for (DifficultyComponent component : breakdown) {
            score = Math.addExact(score, component.points());
        }
        score = Math.max(0, score);
        if (breakdown.stream().mapToInt(DifficultyComponent::points).sum() != score) {
            throw new IllegalArgumentException("Difficulty modifiers cannot reduce the total below zero");
        }
        return new DifficultyResult(score, DifficultyTier.fromScore(score), breakdown);
    }
}
