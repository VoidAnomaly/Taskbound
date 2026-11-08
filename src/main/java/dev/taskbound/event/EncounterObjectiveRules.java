package dev.taskbound.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Explicit schemas for objectives backed by encounter and movement evidence. */
public final class EncounterObjectiveRules {
    private static final Set<String> CONTEXT = Set.of("required_count", "required_biome", "required_biome_tag",
            "required_dimension", "required_time", "required_weather", "required_structure", "required_structure_tag",
            "min_y", "max_y", "required_effect", "minimum_effect_amplifier", "required_effect_extra",
            "minimum_effect_extra_amplifier", "max_health", "required_weapon", "required_weapon_tag",
            "required_tool", "required_tool_tag", "tool_enchantment", "minimum_tool_enchantment_level");
    private static final Map<String, Set<String>> FIELDS = Map.of(
            "combat_action", Set.of("action", "entity", "hostile_only"),
            "travel_distance", Set.of("mode"),
            "survive_fall", Set.of("minimum_fall_distance"),
            "trial_encounter", Set.of("ominous", "distinct_spawners", "armor_set", "weapons", "no_healing", "no_potions", "no_totem", "no_death", "guard_from_acceptance"),
            "dragon_encounter", Set.of("action"),
            "tracked_kill", Set.of("entity", "method", "same_weapon", "no_death_after_start", "bind_structure", "prevent_departure",
                    "required_damage_type", "required_damage_type_tag"));
    private static final Set<String> FLAGS = Set.of("hostile_only", "ominous", "distinct_spawners", "no_healing", "no_potions", "no_totem", "no_death", "guard_from_acceptance", "same_weapon", "no_death_after_start", "bind_structure", "prevent_departure");

    private EncounterObjectiveRules() { }

    public static boolean supports(String type) { return FIELDS.containsKey(type); }
    public static Set<String> supportedCapabilities() {
        return FIELDS.keySet().stream().map(type -> "minecraft:" + type).collect(Collectors.toUnmodifiableSet());
    }

    public static void validate(String type, Map<String, ?> parameters) {
        Set<String> fields = FIELDS.get(type);
        if (fields == null) throw new IllegalArgumentException("Unsupported encounter objective: " + type);
        var context = new LinkedHashMap<String, Object>();
        for (var entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (CONTEXT.contains(key)) { context.put(key, value); continue; }
            if (!fields.contains(key)) throw new IllegalArgumentException("Unsupported " + type + " requirement: " + key);
            if (FLAGS.contains(key)) {
                if (!(value instanceof Boolean)) throw new IllegalArgumentException(key + " must be boolean");
            } else if (key.equals("minimum_fall_distance")) {
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() <= 0)
                    throw new IllegalArgumentException("minimum_fall_distance must be positive");
            } else if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(key + " must be nonblank text");
            }
        }
        context.put("action", "shield_block");
        VanillaEventRules.validate("challenge_action", context);
        switch (type) {
            case "combat_action" -> choice(parameters, "action", Set.of("critical_hit", "ghast_fireball_deflection", "returned_ghast_fireball_kill", "raid_pillager_kill"), true);
            case "travel_distance" -> choice(parameters, "mode", Set.of("swim", "sprint", "boat"), true);
            case "survive_fall" -> { if (!parameters.containsKey("minimum_fall_distance")) throw new IllegalArgumentException("Missing minimum_fall_distance"); }
            case "trial_encounter" -> {
                choice(parameters, "armor_set", Set.of("full_iron"), false);
                choice(parameters, "weapons", Set.of("iron_sword_and_shield"), false);
            }
            case "dragon_encounter" -> choice(parameters, "action", Set.of("bow_crystal", "sword_finish"), true);
            case "tracked_kill" -> {
                if (!(parameters.get("entity") instanceof String id) || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("tracked_kill needs an entity ID");
                for (String key : Set.of("required_damage_type", "required_damage_type_tag")) {
                    if (parameters.containsKey(key) && (!(parameters.get(key) instanceof String damageId)
                            || !damageId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))) {
                        throw new IllegalArgumentException(key + " must be a resource ID");
                    }
                }
                choice(parameters, "method", Set.of("melee", "projectile"), false);
                if ((Boolean.TRUE.equals(parameters.get("bind_structure")) || Boolean.TRUE.equals(parameters.get("prevent_departure")))
                        && !parameters.containsKey("required_structure")) throw new IllegalArgumentException("Structure binding needs required_structure");
            }
        }
        if (parameters.get("entity") instanceof String id && !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("entity must be a resource ID");
    }

    private static void choice(Map<String, ?> parameters, String key, Set<String> choices, boolean required) {
        if (required && !parameters.containsKey(key) || parameters.containsKey(key) && !choices.contains(parameters.get(key)))
            throw new IllegalArgumentException("Unsupported " + key + ": " + parameters.get(key));
    }
}
