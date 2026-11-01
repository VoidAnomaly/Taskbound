package dev.taskbound.core;

/** Runtime eligibility check applied to concrete variants before any weighted task selection. */
@FunctionalInterface
public interface TaskVariantEligibility {
    TaskVariantEligibility ACCEPT_ALL = (category, template, variant) -> true;

    boolean isEligible(
            TaskCategoryDefinition category,
            TaskTemplateDefinition template,
            TaskVariantDefinition variant
    );
}
