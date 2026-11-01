package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ObjectiveDefinition(
        String type,
        Map<String, Object> parameters,
        List<DifficultyComponent> difficultyComponents,
        Set<String> requiredCapabilities
) {
    public ObjectiveDefinition {
        if (type.isBlank()) {
            throw new IllegalArgumentException("Objective type cannot be blank");
        }
        parameters = DataMaps.copy(parameters);
        difficultyComponents = List.copyOf(difficultyComponents);
        requiredCapabilities = Set.copyOf(requiredCapabilities);
    }

    public boolean supportedBy(CapabilitySet capabilities) {
        return capabilities.supportsAll(requiredCapabilities);
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", type);
        map.put("parameters", parameters);
        map.put("difficulty_components", difficultyComponents.stream().map(DifficultyComponent::toMap).toList());
        map.put("required_capabilities", requiredCapabilities.stream().sorted().toList());
        return map;
    }

    public static ObjectiveDefinition fromMap(Map<String, ?> map) {
        var components = DataMaps.list(map, "difficulty_components").stream().map(value -> {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Difficulty components must be objects");
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> component = (Map<String, ?>) raw;
            return DifficultyComponent.fromMap(component);
        }).toList();
        return new ObjectiveDefinition(
                DataMaps.string(map, "type"),
                DataMaps.map(map, "parameters"),
                components,
                DataMaps.stringSet(map, "required_capabilities")
        );
    }
}
