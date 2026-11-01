package dev.taskbound.core;

import java.util.Map;
import java.util.Set;

public record TaskSelection(
        TaskCategoryDefinition category,
        TaskTemplateDefinition template,
        TaskVariantDefinition variant,
        Set<String> eligibleCategoryIds,
        Map<String, Double> effectiveCategoryWeights,
        boolean guaranteed
) {
    public TaskSelection {
        eligibleCategoryIds = Set.copyOf(eligibleCategoryIds);
        effectiveCategoryWeights = Map.copyOf(effectiveCategoryWeights);
    }
}
