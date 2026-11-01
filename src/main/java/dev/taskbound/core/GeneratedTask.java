package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GeneratedTask(
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
        DifficultyResult difficulty,
        RewardBundle rewards,
        int reputationReward,
        long acceptedAt,
        Long expiresAt,
        TaskStatus status
) {
    public GeneratedTask {
        if (taskId.isBlank() || playerId.isBlank() || boardId.isBlank() || giverId.isBlank()
                || categoryId.isBlank() || templateId.isBlank() || variantId.isBlank() || displayText.isBlank()) {
            throw new IllegalArgumentException("Generated task identifiers and text cannot be blank");
        }
        if (title != null && title.isBlank() || description != null && description.isBlank()) {
            throw new IllegalArgumentException("Optional task title and description cannot be blank");
        }
        if (objectives.isEmpty() || requiredReputationLevel < 1 || reputationReward < 0
                || expiresAt != null && expiresAt <= acceptedAt) {
            throw new IllegalArgumentException("Invalid generated task");
        }
        objectives = List.copyOf(objectives);
        conditions = DataMaps.copy(conditions);
        ObjectiveSequence.validate(conditions, objectives.size());
        ObjectiveSequence.validateHandIns(conditions, objectives.stream().map(GeneratedObjective::type).toList());
    }

    /** Saved 1.0 tasks and callers without literal text keep their language-key fallback. */
    public GeneratedTask(String taskId, String playerId, String boardId, String giverId, String categoryId,
            String templateId, String variantId, String displayText, List<GeneratedObjective> objectives,
            Map<String, Object> conditions, int requiredReputationLevel, DifficultyResult difficulty,
            RewardBundle rewards, int reputationReward, long acceptedAt, Long expiresAt, TaskStatus status) {
        this(taskId, playerId, boardId, giverId, categoryId, templateId, variantId, displayText, null, null,
                objectives, conditions, requiredReputationLevel, difficulty, rewards, reputationReward,
                acceptedAt, expiresAt, status);
    }

    public GeneratedTask withStatus(TaskStatus newStatus) {
        return new GeneratedTask(
                taskId, playerId, boardId, giverId, categoryId, templateId, variantId, displayText, title, description,
                objectives, conditions, requiredReputationLevel, difficulty, rewards, reputationReward,
                acceptedAt, expiresAt, newStatus
        );
    }

    public GeneratedTask withTiming(long newAcceptedAt, Long newExpiresAt) {
        return new GeneratedTask(
                taskId, playerId, boardId, giverId, categoryId, templateId, variantId, displayText, title, description,
                objectives, conditions, requiredReputationLevel, difficulty, rewards, reputationReward,
                newAcceptedAt, newExpiresAt, status
        );
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("task_id", taskId);
        map.put("player_id", playerId);
        map.put("board_id", boardId);
        map.put("giver_id", giverId);
        map.put("category_id", categoryId);
        map.put("template_id", templateId);
        map.put("variant_id", variantId);
        map.put("display_text", displayText);
        if (title != null) map.put("title", title);
        if (description != null) map.put("description", description);
        map.put("objectives", objectives.stream().map(GeneratedObjective::toMap).toList());
        map.put("conditions", conditions);
        map.put("required_reputation_level", requiredReputationLevel);
        map.put("difficulty", difficulty.toMap());
        map.put("rewards", rewards.toMap());
        map.put("reputation_reward", reputationReward);
        map.put("accepted_at", acceptedAt);
        map.put("expires_at", expiresAt);
        map.put("status", status.name());
        return map;
    }

    public static GeneratedTask fromMap(Map<String, ?> map) {
        var objectives = DataMaps.list(map, "objectives").stream().map(value -> {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Generated objectives must be objects");
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> objective = (Map<String, ?>) raw;
            return GeneratedObjective.fromMap(objective);
        }).toList();
        return new GeneratedTask(
                DataMaps.string(map, "task_id"),
                DataMaps.string(map, "player_id"),
                DataMaps.string(map, "board_id"),
                DataMaps.string(map, "giver_id"),
                DataMaps.string(map, "category_id"),
                DataMaps.string(map, "template_id"),
                DataMaps.string(map, "variant_id"),
                DataMaps.string(map, "display_text"),
                DataMaps.optionalText(map, "title"),
                DataMaps.optionalText(map, "description"),
                objectives,
                DataMaps.map(map, "conditions"),
                DataMaps.integer(map, "required_reputation_level"),
                DifficultyResult.fromMap(DataMaps.map(map, "difficulty")),
                RewardBundle.fromMap(DataMaps.map(map, "rewards")),
                DataMaps.integer(map, "reputation_reward"),
                DataMaps.longValue(map, "accepted_at"),
                DataMaps.nullableLong(map, "expires_at"),
                TaskStatus.valueOf(DataMaps.string(map, "status"))
        );
    }
}
