package dev.taskbound.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record CapabilitySet(Set<String> values) {
    public static final CapabilitySet NONE = new CapabilitySet(Set.of());

    public CapabilitySet {
        values = Set.copyOf(values);
    }

    public static CapabilitySet of(String... capabilities) {
        return new CapabilitySet(Set.of(capabilities));
    }

    public boolean supports(String capability) {
        return values.contains(capability);
    }

    public boolean supportsAll(Collection<String> required) {
        return values.containsAll(required);
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("values", values.stream().sorted().toList());
        return map;
    }

    public static CapabilitySet fromMap(Map<String, ?> map) {
        return new CapabilitySet(DataMaps.stringSet(map, "values"));
    }
}
