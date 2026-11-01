package dev.taskbound.core;

/**
 * Enriches completely selected task content before difficulty and rewards are calculated.
 * Implementations may capture world-specific generation inputs while keeping the core pipeline testable.
 */
@FunctionalInterface
public interface TaskDraftCustomizer {
    TaskDraftCustomizer IDENTITY = draft -> draft;

    GeneratedTaskDraft customize(GeneratedTaskDraft draft);
}
