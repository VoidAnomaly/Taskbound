package dev.taskbound.api;

import java.util.Set;
import java.util.UUID;

/** Stable, loader-facing context exposed to optional task providers. */
public record TaskGenerationContext(
        UUID boardId,
        String giverId,
        int reputationLevel,
        Set<String> availableCapabilities
) {
    public TaskGenerationContext {
        availableCapabilities = Set.copyOf(availableCapabilities);
    }
}
