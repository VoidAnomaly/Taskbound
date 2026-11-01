package dev.taskbound.core;

import java.util.List;
import java.util.Map;

/** The completely generated task content passed to difficulty scoring before rewards exist. */
public record GeneratedTaskDraft(
        String taskId,
        String playerId,
        String boardId,
        String giverId,
        String categoryId,
        String templateId,
        String variantId,
        String displayText,
        String title,
        String description,
        List<GeneratedObjective> objectives,
        Map<String, Object> conditions,
        int requiredReputationLevel,
        int baseDifficulty,
        List<DifficultyComponent> difficultyComponents,
        long acceptedAt,
        Long expiresAt
) {
    public GeneratedTaskDraft {
        if (taskId.isBlank() || playerId.isBlank() || boardId.isBlank() || giverId.isBlank()
                || categoryId.isBlank() || templateId.isBlank() || variantId.isBlank() || displayText.isBlank()) {
            throw new IllegalArgumentException("Generated task identifiers and text cannot be blank");
        }
        if (title != null && title.isBlank() || description != null && description.isBlank()) {
            throw new IllegalArgumentException("Optional task title and description cannot be blank");
        }
        if (objectives.isEmpty() || requiredReputationLevel < 1 || baseDifficulty < 0
                || expiresAt != null && expiresAt <= acceptedAt) {
            throw new IllegalArgumentException("Invalid generated task draft");
        }
        objectives = List.copyOf(objectives);
        conditions = DataMaps.copy(conditions);
        difficultyComponents = List.copyOf(difficultyComponents);
    }

    /** Retains source compatibility for draft customizers written against 1.0. */
    public GeneratedTaskDraft(String taskId, String playerId, String boardId, String giverId, String categoryId,
            String templateId, String variantId, String displayText, List<GeneratedObjective> objectives,
            Map<String, Object> conditions, int requiredReputationLevel, int baseDifficulty,
            List<DifficultyComponent> difficultyComponents, long acceptedAt, Long expiresAt) {
        this(taskId, playerId, boardId, giverId, categoryId, templateId, variantId, displayText, null, null,
                objectives, conditions, requiredReputationLevel, baseDifficulty, difficultyComponents,
                acceptedAt, expiresAt);
    }
}
