package dev.taskbound.porter;

import java.util.Map;
import java.util.Set;

/** Configuration schema for concrete, server-generated courier routes. */
public final class PorterObjectiveRules {
    public static final String TYPE = "porter_route";
    public static final String CAPABILITY = "minecraft:porter_route";
    public static final Set<String> KINDS = Set.of("delivery", "deliveries", "collect_return", "receipt_return",
            "delivery_circuit", "collections", "exchange", "delivery_returns", "nether", "three_realms");
    private static final Set<String> NUMBERS = Set.of("required_count", "stops", "minimum_leg_distance",
            "maximum_leg_distance", "minimum_route_distance", "maximum_route_distance", "maximum_damage");
    private static final Set<String> FLAGS = Set.of("different_biomes", "on_foot", "no_death",
            "teleportation_allowed", "dimensional_travel_allowed");

    private PorterObjectiveRules() { }

    public static boolean supports(String type) { return TYPE.equals(type); }
    public static Set<String> supportedCapabilities() { return Set.of(CAPABILITY); }

    public static void validate(String type, Map<String, ?> parameters) {
        if (!supports(type)) throw new IllegalArgumentException("Unsupported Porter objective: " + type);
        if (!(parameters.get("route_kind") instanceof String kind) || !KINDS.contains(kind)) throw new IllegalArgumentException("Unknown route_kind");
        for (var entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (NUMBERS.contains(key)) {
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0) {
                    throw new IllegalArgumentException(key + " must be a nonnegative finite number");
                }
                if ((key.equals("required_count") || key.equals("stops"))
                        && (n.intValue() != n.doubleValue() || n.intValue() < 1 || n.intValue() > 6)) {
                    throw new IllegalArgumentException(key + " must be an integer between 1 and 6");
                }
            } else if (FLAGS.contains(key)) {
                if (!(value instanceof Boolean)) throw new IllegalArgumentException(key + " must be boolean");
            } else if (key.equals("arrival_time")) {
                if (!Set.of("day", "night").contains(value)) throw new IllegalArgumentException("arrival_time must be day or night");
            } else if (!key.equals("route_kind")) {
                throw new IllegalArgumentException("Unsupported Porter requirement: " + key);
            }
        }
        if (number(parameters, "minimum_leg_distance", 256) < 256
                || number(parameters, "maximum_leg_distance", 3072) < number(parameters, "minimum_leg_distance", 256)
                || number(parameters, "maximum_route_distance", 16384) < number(parameters, "minimum_route_distance", 0)) {
            throw new IllegalArgumentException("Invalid Porter distance range");
        }
        if (number(parameters, "stops", 1) > 4) throw new IllegalArgumentException("At most four distinct destinations are supported");
        if (Set.of("nether", "three_realms").contains(parameters.get("route_kind"))
                && !Boolean.TRUE.equals(parameters.get("dimensional_travel_allowed"))) {
            throw new IllegalArgumentException("Dimension routes must allow dimensional travel");
        }
    }

    static double number(Map<String, ?> map, String key, double fallback) {
        return map.get(key) instanceof Number value ? value.doubleValue() : fallback;
    }
}
