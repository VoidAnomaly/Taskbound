package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A concrete, achievable combination of variables for a task template. */
public record TaskVariantDefinition(
        String id,
        double weight,
        List<ObjectiveDefinition> objectives,
        Map<String, Object> conditions,
        List<DifficultyComponent> difficultyComponents,
        Set<String> requiredCapabilities
) {
    public TaskVariantDefinition {
        if (id.isBlank()) {
            throw new IllegalArgumentException("Variant ID cannot be blank");
        }
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw new IllegalArgumentException("Variant weight must be finite and positive");
        }
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("A task variant must contain at least one objective");
        }
        objectives = List.copyOf(objectives);
        conditions = DataMaps.copy(conditions);
        ObjectiveSequence.validate(conditions, objectives.size());
        ObjectiveSequence.validateHandIns(conditions, objectives.stream().map(ObjectiveDefinition::type).toList());
        difficultyComponents = List.copyOf(difficultyComponents);
        requiredCapabilities = Set.copyOf(requiredCapabilities);
    }

    public boolean supportedBy(CapabilitySet capabilities) {
        return capabilities.supportsAll(requiredCapabilities)
                && objectives.stream().allMatch(objective -> objective.supportedBy(capabilities));
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        map.put("weight", weight);
        map.put("objectives", objectives.stream().map(ObjectiveDefinition::toMap).toList());
        map.put("conditions", conditions);
        map.put("difficulty_components", difficultyComponents.stream().map(DifficultyComponent::toMap).toList());
        map.put("required_capabilities", requiredCapabilities.stream().sorted().toList());
        return map;
    }

    public static TaskVariantDefinition fromMap(Map<String, ?> map) {
        var objectives = DataMaps.list(map, "objectives").stream().map(value -> {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Objectives must be objects");
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> objective = (Map<String, ?>) raw;
            return ObjectiveDefinition.fromMap(objective);
        }).toList();
        var difficulty = DataMaps.list(map, "difficulty_components").stream().map(value -> {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Difficulty components must be objects");
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> component = (Map<String, ?>) raw;
            return DifficultyComponent.fromMap(component);
        }).toList();
        return new TaskVariantDefinition(
                DataMaps.string(map, "id"),
                DataMaps.decimal(map, "weight"),
                objectives,
                DataMaps.map(map, "conditions"),
                difficulty,
                DataMaps.stringSet(map, "required_capabilities")
        );
    }
}
