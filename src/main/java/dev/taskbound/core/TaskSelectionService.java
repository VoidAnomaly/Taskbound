package dev.taskbound.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class TaskSelectionService {
    private final TemplateRecencyPolicy templateRecencyPolicy;
    private final TaskVariantEligibility variantEligibility;

    public TaskSelectionService() {
        this(TemplateRecencyPolicy.DEFAULT, TaskVariantEligibility.ACCEPT_ALL);
    }

    public TaskSelectionService(TemplateRecencyPolicy templateRecencyPolicy) {
        this(templateRecencyPolicy, TaskVariantEligibility.ACCEPT_ALL);
    }

    public TaskSelectionService(TaskVariantEligibility variantEligibility) {
        this(TemplateRecencyPolicy.DEFAULT, variantEligibility);
    }

    public TaskSelectionService(
            TemplateRecencyPolicy templateRecencyPolicy,
            TaskVariantEligibility variantEligibility
    ) {
        this.templateRecencyPolicy = Objects.requireNonNull(templateRecencyPolicy, "templateRecencyPolicy");
        this.variantEligibility = Objects.requireNonNull(variantEligibility, "variantEligibility");
    }

    public TaskSelection choose(
            String giverId,
            int reputationLevel,
            CapabilitySet capabilities,
            List<TaskCategoryDefinition> categories,
            Map<String, TaskTemplateDefinition> templates,
            SelectionHistory history,
            RandomGenerator random
    ) {
        if (!giverId.equals(history.giverId())) {
            throw new IllegalArgumentException("Selection history belongs to a different giver");
        }
        history.activateUnlockedCategories(categories, reputationLevel);

        var templatesByCategory = new LinkedHashMap<String, List<TaskTemplateDefinition>>();
        var variantsByTemplate = new LinkedHashMap<String, List<TaskVariantDefinition>>();
        var eligibleCategories = categories.stream()
                .filter(category -> category.giverId().equals(giverId))
                .filter(category -> category.unlockedAt(reputationLevel))
                .filter(category -> capabilities.supportsAll(category.requiredCapabilities()))
                .filter(category -> {
                    var eligibleTemplates = eligibleTemplates(
                            category, reputationLevel, capabilities, templates, variantsByTemplate);
                    templatesByCategory.put(category.id(), eligibleTemplates);
                    return !eligibleTemplates.isEmpty();
                })
                .sorted(Comparator.comparing(TaskCategoryDefinition::id))
                .toList();
        if (eligibleCategories.isEmpty()) {
            throw new NoEligibleTaskException("No eligible task category for giver " + giverId);
        }

        var weights = new LinkedHashMap<String, Double>();
        eligibleCategories.forEach(category -> weights.put(
                category.id(), category.effectiveWeight(requireHistory(history, category.id()))));

        var overdue = eligibleCategories.stream()
                .filter(category -> requireHistory(history, category.id()).eligibleMisses() >= category.guaranteeAfter())
                .toList();
        boolean guaranteed = !overdue.isEmpty();
        TaskCategoryDefinition category = guaranteed
                ? overdue.stream().min(overdueComparator(history)).orElseThrow()
                : WeightedPicker.pick(eligibleCategories, candidate -> weights.get(candidate.id()), random);

        List<TaskTemplateDefinition> eligibleTemplates = templatesByCategory.get(category.id());
        TaskTemplateDefinition template = WeightedPicker.pick(
                eligibleTemplates,
                candidate -> candidate.baseWeight() * templateRecencyPolicy.multiplier(
                        candidate.id(), history.recentTemplateIds()),
                random
        );
        List<TaskVariantDefinition> variants = variantsByTemplate.get(template.id());
        TaskVariantDefinition variant = WeightedPicker.pick(variants, TaskVariantDefinition::weight, random);

        return new TaskSelection(
                category,
                template,
                variant,
                eligibleCategories.stream().map(TaskCategoryDefinition::id)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                weights,
                guaranteed
        );
    }

    public double effectiveTemplateWeight(TaskTemplateDefinition template, SelectionHistory history) {
        return template.baseWeight() * templateRecencyPolicy.multiplier(template.id(), history.recentTemplateIds());
    }

    private List<TaskTemplateDefinition> eligibleTemplates(
            TaskCategoryDefinition category,
            int reputationLevel,
            CapabilitySet capabilities,
            Map<String, TaskTemplateDefinition> templates,
            Map<String, List<TaskVariantDefinition>> variantsByTemplate
    ) {
        var result = new ArrayList<TaskTemplateDefinition>();
        for (String templateId : category.templateIds()) {
            TaskTemplateDefinition template = templates.get(templateId);
            if (template == null) {
                throw new IllegalArgumentException("Unknown template " + templateId + " in category " + category.id());
            }
            if (!template.categoryId().equals(category.id())) {
                throw new IllegalArgumentException("Template " + templateId + " belongs to another category");
            }
            List<TaskVariantDefinition> eligibleVariants = template
                    .eligibleVariants(reputationLevel, capabilities)
                    .stream()
                    .filter(variant -> variantEligibility.isEligible(category, template, variant))
                    .sorted(Comparator.comparing(TaskVariantDefinition::id))
                    .toList();
            if (!eligibleVariants.isEmpty()) {
                result.add(template);
                variantsByTemplate.put(template.id(), eligibleVariants);
            }
        }
        result.sort(Comparator.comparing(TaskTemplateDefinition::id));
        return List.copyOf(result);
    }

    private static CategoryHistory requireHistory(SelectionHistory history, String categoryId) {
        CategoryHistory result = history.category(categoryId);
        if (result == null) {
            throw new IllegalStateException("Unlocked category has no history: " + categoryId);
        }
        return result;
    }

    /** Comparator ordered best-first, hence it is used with {@code min}. */
    private static Comparator<TaskCategoryDefinition> overdueComparator(SelectionHistory history) {
        return Comparator
                .<TaskCategoryDefinition>comparingDouble(category ->
                        -(double) requireHistory(history, category.id()).eligibleMisses() / category.guaranteeAfter())
                .thenComparingInt(category -> -requireHistory(history, category.id()).eligibleMisses())
                .thenComparingLong(category -> requireHistory(history, category.id()).lastSelectedGeneration())
                .thenComparing(TaskCategoryDefinition::id);
    }
}
