package dev.taskbound.core;

public record TaskGenerationContext(
        String playerId,
        String boardId,
        String giverId,
        int reputationLevel,
        CapabilitySet capabilities,
        long acceptedAt,
        Long expiresAt
) {
    public TaskGenerationContext {
        if (playerId.isBlank() || boardId.isBlank() || giverId.isBlank() || reputationLevel < 1
                || expiresAt != null && expiresAt <= acceptedAt) {
            throw new IllegalArgumentException("Invalid task generation context");
        }
    }
}
