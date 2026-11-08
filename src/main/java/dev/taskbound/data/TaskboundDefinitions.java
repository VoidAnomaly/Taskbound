package dev.taskbound.data;

import dev.taskbound.core.TaskCatalog;
import dev.taskbound.core.TaskCategoryDefinition;
import dev.taskbound.core.TaskGiverDefinition;
import dev.taskbound.core.TaskTemplateDefinition;
import dev.taskbound.core.RewardEntryDefinition;
import dev.taskbound.core.LevelRewardDefinition;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable, atomically swapped view of all Taskbound datapack definitions. */
public final class TaskboundDefinitions {
    public static final TaskboundDefinitions EMPTY = new TaskboundDefinitions(Map.of(), List.of(), List.of(), List.of());

    private final Map<String, TaskGiverDefinition> givers;
    private final Map<String, TaskCategoryDefinition> categories;
    private final Map<String, TaskTemplateDefinition> templates;
    private final List<RewardEntryDefinition> rewards;
    private final TaskCatalog catalog;
    private final Map<String, LevelRewardDefinition> levelRewards;

    public TaskboundDefinitions(
            Map<String, TaskGiverDefinition> givers,
            List<TaskCategoryDefinition> categories,
            List<TaskTemplateDefinition> templates,
            List<RewardEntryDefinition> rewards
    ) {
        this(givers, categories, templates, rewards, List.of());
    }

    public TaskboundDefinitions(Map<String, TaskGiverDefinition> givers,
            List<TaskCategoryDefinition> categories, List<TaskTemplateDefinition> templates,
            List<RewardEntryDefinition> rewards, List<LevelRewardDefinition> levelRewards) {
        this.givers = Map.copyOf(givers);
        // Removing a giver retires its dependent definitions without requiring a cascade of file deletions.
        var activeCategories = categories.stream().filter(category -> this.givers.containsKey(category.giverId())).toList();
        this.categories = index(activeCategories, TaskCategoryDefinition::id, "category");
        var activeTemplates = templates.stream().filter(template -> this.categories.containsKey(template.categoryId())).toList();
        this.templates = index(activeTemplates, TaskTemplateDefinition::id, "template");
        this.rewards = rewards.stream().sorted(Comparator.comparing(RewardEntryDefinition::id)).toList();
        this.catalog = new TaskCatalog(activeCategories, activeTemplates, rewards);
        this.levelRewards = index(levelRewards.stream().filter(reward -> this.givers.containsKey(reward.id())).toList(),
                LevelRewardDefinition::id, "level reward");
        validateGivers();
        for (TaskGiverDefinition giver : enabledGivers()) {
            for (String categoryId : giver.categoryIds()) {
                for (String templateId : this.categories.get(categoryId).templateIds()) {
                    var template = this.templates.get(templateId);
                    if (template == null) continue;
                    for (var variant : template.variants()) {
                        if (variant.conditions().containsKey("source_catalog_id")) {
                            variant.objectives().forEach(objective ->
                                    dev.taskbound.event.VanillaEventRules.validate(objective.type(), objective.parameters()));
                            validateCatalogConditions(variant.conditions());
                        }
                    }
                }
            }
        }
    }

    private static void validateCatalogConditions(Map<String, Object> conditions) {
        var known = java.util.Set.of("source_catalog_id", "catalog_difficulty", "objective_order", "objective_stages", "no_death", "reset_on_death", "one_time", "cooldown_ticks", "title",
                "catalog_profile", "completion_group", "currency_reward", "currency_per_1024_blocks");
        for (var entry : conditions.entrySet()) {
            if (!known.contains(entry.getKey())) throw new IllegalArgumentException("Unsupported catalog condition: " + entry.getKey());
            switch (entry.getKey()) {
                case "catalog_profile" -> {
                    if (!dev.taskbound.core.CatalogProfiles.PROFILES.contains(entry.getValue()))
                        throw new IllegalArgumentException("Unknown catalog_profile: " + entry.getValue());
                }
                case "completion_group" -> {
                    if (!(entry.getValue() instanceof String text) || !text.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                        throw new IllegalArgumentException("completion_group must be a resource ID");
                }
                case "currency_reward", "currency_per_1024_blocks" -> {
                    if (!(entry.getValue() instanceof Number n) || n.doubleValue() != n.intValue()
                            || n.intValue() < 1 || n.intValue() > 4096)
                        throw new IllegalArgumentException(entry.getKey() + " must be an integer from 1 to 4096");
                }
                case "objective_stages" -> { /* Validated against objective count by TaskVariantDefinition. */ }
                case "no_death", "reset_on_death", "one_time" -> {
                    if (!(entry.getValue() instanceof Boolean)) throw new IllegalArgumentException(entry.getKey() + " must be boolean");
                }
                case "catalog_difficulty" -> {
                    if (!(entry.getValue() instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < 1 || n.intValue() > 5)
                        throw new IllegalArgumentException("catalog_difficulty must be 1 to 5");
                }
                case "cooldown_ticks" -> {
                    if (!(entry.getValue() instanceof Number n) || n.doubleValue() != n.longValue() || n.longValue() < 0)
                        throw new IllegalArgumentException("cooldown_ticks must be a nonnegative integer");
                }
                case "objective_order" -> {
                    if (!"strict".equals(entry.getValue())) throw new IllegalArgumentException("Only strict objective ordering is supported");
                }
                default -> {
                    if (!(entry.getValue() instanceof String value) || value.isBlank()) throw new IllegalArgumentException(entry.getKey() + " must be text");
                }
            }
        }
    }

    public List<TaskGiverDefinition> enabledGivers() {
        return givers.values().stream().filter(TaskGiverDefinition::enabled)
                .sorted(Comparator.comparing(TaskGiverDefinition::id)).toList();
    }

    public List<LevelRewardDefinition.Gift> levelRewards(String giverId) {
        LevelRewardDefinition definition = levelRewards.get(giverId);
        return definition == null ? List.of() : definition.rewards();
    }

    public Map<String, TaskGiverDefinition> givers() {
        return givers;
    }

    public TaskGiverDefinition giver(String id) {
        TaskGiverDefinition giver = givers.get(id);
        if (giver == null) {
            throw new IllegalArgumentException("Unknown task giver: " + id);
        }
        return giver;
    }

    public Map<String, TaskCategoryDefinition> categories() {
        return categories;
    }

    public Map<String, TaskTemplateDefinition> templates() {
        return templates;
    }

    public List<RewardEntryDefinition> rewards() {
        return rewards;
    }

    public TaskCatalog catalog() {
        return catalog;
    }

    private void validateGivers() {
        for (TaskGiverDefinition giver : givers.values()) {
            for (String categoryId : giver.categoryIds()) {
                TaskCategoryDefinition category = categories.get(categoryId);
                if (category == null) {
                    throw new IllegalArgumentException("Giver " + giver.id() + " references missing category " + categoryId);
                }
                if (!giver.id().equals(category.giverId())) {
                    throw new IllegalArgumentException("Category " + categoryId + " belongs to a different giver");
                }
            }
        }
    }

    private static <T> Map<String, T> index(
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
