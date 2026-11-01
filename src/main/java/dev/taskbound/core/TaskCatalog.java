package dev.taskbound.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TaskCatalog {
    private final Map<String, TaskCategoryDefinition> categories;
    private final Map<String, TaskTemplateDefinition> templates;
    private final List<RewardEntryDefinition> rewards;

    public TaskCatalog(
            List<TaskCategoryDefinition> categories,
            List<TaskTemplateDefinition> templates,
            List<RewardEntryDefinition> rewards
    ) {
        this.categories = uniqueById(categories, TaskCategoryDefinition::id, "category");
        this.templates = uniqueById(templates, TaskTemplateDefinition::id, "template");
        var rewardIds = new java.util.HashSet<String>();
        for (RewardEntryDefinition reward : rewards) {
            if (!rewardIds.add(reward.id())) {
                throw new IllegalArgumentException("Duplicate reward ID: " + reward.id());
            }
        }
        this.rewards = rewards.stream().sorted(Comparator.comparing(RewardEntryDefinition::id)).toList();
        validateReferences();
    }

    public List<TaskCategoryDefinition> categoriesForGiver(String giverId) {
        return categories.values().stream()
                .filter(category -> category.giverId().equals(giverId))
                .sorted(Comparator.comparing(TaskCategoryDefinition::id))
                .toList();
    }

    public Map<String, TaskTemplateDefinition> templates() {
        return templates;
    }

    public List<RewardEntryDefinition> rewards() {
        return rewards;
    }

    private void validateReferences() {
        categories.values().forEach(category -> category.templateIds().forEach(templateId -> {
            TaskTemplateDefinition template = templates.get(templateId);
            if (template == null) {
                throw new IllegalArgumentException("Category " + category.id() + " references missing template " + templateId);
            }
            if (!template.categoryId().equals(category.id())) {
                throw new IllegalArgumentException("Template " + templateId + " does not belong to " + category.id());
            }
        }));
    }

    private static <T> Map<String, T> uniqueById(
            List<T> values,
            java.util.function.Function<T, String> id,
            String kind
    ) {
        var result = new LinkedHashMap<String, T>();
        values.stream().sorted(Comparator.comparing(id)).forEach(value -> {
            String valueId = id.apply(value);
            if (result.put(valueId, value) != null) {
                throw new IllegalArgumentException("Duplicate " + kind + " ID: " + valueId);
            }
        });
        return Map.copyOf(result);
    }
}
