package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TaskTemplateDefinition(
        String id,
        String categoryId,
        int minimumReputationLevel,
        Integer maximumReputationLevel,
        double baseWeight,
        int baseDifficulty,
        String displayText,
        String title,
        String description,
        int timeLimitTicks,
        Set<String> requiredCapabilities,
        List<TaskVariantDefinition> variants
) {
    public TaskTemplateDefinition {
        if (id.isBlank() || categoryId.isBlank() || displayText.isBlank()) {
            throw new IllegalArgumentException("Template ID, category ID, and display text cannot be blank");
        }
        if (title != null && title.isBlank() || description != null && description.isBlank()) {
            throw new IllegalArgumentException("Optional task title and description cannot be blank");
        }
        if (minimumReputationLevel < 1
                || maximumReputationLevel != null && maximumReputationLevel < minimumReputationLevel) {
            throw new IllegalArgumentException("Invalid template reputation range");
        }
        if (!Double.isFinite(baseWeight) || baseWeight <= 0.0 || baseDifficulty < 0) {
            throw new IllegalArgumentException("Invalid template weight or base difficulty");
        }
        if (timeLimitTicks < 0) throw new IllegalArgumentException("Task time limit cannot be negative");
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("A template must have at least one task variant");
        }
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        variants = List.copyOf(variants);
        if (variants.stream().map(TaskVariantDefinition::id).distinct().count() != variants.size()) {
            throw new IllegalArgumentException("Task variant IDs must be unique within a template");
        }
    }

    /** The 1.0 template constructor retains its language-key fallback. */
    public TaskTemplateDefinition(String id, String categoryId, int minimumReputationLevel,
            Integer maximumReputationLevel, double baseWeight, int baseDifficulty, String displayText,
            Set<String> requiredCapabilities, List<TaskVariantDefinition> variants) {
        this(id, categoryId, minimumReputationLevel, maximumReputationLevel, baseWeight, baseDifficulty,
                displayText, null, null, 0, requiredCapabilities, variants);
    }

    public TaskTemplateDefinition(String id, String categoryId, int minimumReputationLevel,
            Integer maximumReputationLevel, double baseWeight, int baseDifficulty, String displayText,
            String title, String description, Set<String> requiredCapabilities, List<TaskVariantDefinition> variants) {
        this(id, categoryId, minimumReputationLevel, maximumReputationLevel, baseWeight, baseDifficulty,
                displayText, title, description, 0, requiredCapabilities, variants);
    }

    public boolean unlockedAt(int reputationLevel) {
        return reputationLevel >= minimumReputationLevel
                && (maximumReputationLevel == null || reputationLevel <= maximumReputationLevel);
    }

    public List<TaskVariantDefinition> eligibleVariants(int reputationLevel, CapabilitySet capabilities) {
        if (!unlockedAt(reputationLevel) || !capabilities.supportsAll(requiredCapabilities)) {
            return List.of();
        }
        return variants.stream().filter(variant -> variant.supportedBy(capabilities)).toList();
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        map.put("category_id", categoryId);
        map.put("minimum_reputation_level", minimumReputationLevel);
        map.put("maximum_reputation_level", maximumReputationLevel);
        map.put("base_weight", baseWeight);
        map.put("base_difficulty", baseDifficulty);
        map.put("display_text", displayText);
        if (title != null) map.put("title", title);
        if (description != null) map.put("description", description);
        if (timeLimitTicks > 0) map.put("time_limit_ticks", timeLimitTicks);
        map.put("required_capabilities", requiredCapabilities.stream().sorted().toList());
        map.put("variants", variants.stream().map(TaskVariantDefinition::toMap).toList());
        return map;
    }

    private static int optionalTimeLimit(Map<String, ?> map) {
        if (!map.containsKey("time_limit_ticks")) return 0;
        Object value = map.get("time_limit_ticks");
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue() || number.intValue() <= 0) {
            throw new IllegalArgumentException("time_limit_ticks must be a positive integer; omit it for untimed tasks");
        }
        return number.intValue();
    }

    public static TaskTemplateDefinition fromMap(Map<String, ?> map) {
        var variants = DataMaps.list(map, "variants").stream().map(value -> {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Task variants must be objects");
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> variant = (Map<String, ?>) raw;
            return TaskVariantDefinition.fromMap(variant);
        }).toList();
        return new TaskTemplateDefinition(
                DataMaps.string(map, "id"),
                DataMaps.string(map, "category_id"),
                DataMaps.integer(map, "minimum_reputation_level"),
                DataMaps.nullableInteger(map, "maximum_reputation_level"),
                DataMaps.decimal(map, "base_weight"),
                DataMaps.integer(map, "base_difficulty"),
                DataMaps.string(map, "display_text"),
                DataMaps.optionalText(map, "title"),
                DataMaps.optionalText(map, "description"),
                optionalTimeLimit(map),
                DataMaps.stringSet(map, "required_capabilities"),
                variants
        );
    }
}
