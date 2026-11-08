package dev.taskbound.event;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** The explicit, stateless objective specification supported by the first vanilla-event catalog. */
public final class VanillaEventRules {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Set<String> CONTEXT = Set.of(
            "required_count", "required_biome", "required_biome_tag", "required_dimension",
            "required_time", "required_weather", "required_structure", "required_structure_tag",
            "min_y", "max_y", "required_effect", "minimum_effect_amplifier",
            "required_effect_extra", "minimum_effect_extra_amplifier", "max_health",
            "required_weapon", "required_weapon_tag", "required_tool", "required_tool_tag",
            "tool_enchantment", "minimum_tool_enchantment_level");
    private static final Map<String, Set<String>> PARAMETERS = Map.of(
            "kill_entity", Set.of("entity", "entity_tag", "hostile_only", "method",
                    "required_damage_type", "required_damage_type_tag"),
            "catch_item", Set.of("fish_item", "fish_item_tag"),
            "craft_item", Set.of("item"),
            "gather_block", Set.of("block"),
            "harvest_crop", Set.of("block"),
            "enchant_item", Set.of("item", "method", "required_enchantment", "minimum_enchantment_level"),
            "challenge_action", Set.of("action", "minimum_distance"));
    private static final Set<String> ACTIONS = Set.of(
            "shield_block", "shield_block_skeleton_arrows", "shield_block_piglin", "bow_hit_on_hostile");
    private static final Set<String> NUMBERS = Set.of("required_count", "min_y", "max_y", "max_health",
            "minimum_effect_amplifier", "minimum_effect_extra_amplifier", "minimum_tool_enchantment_level",
            "minimum_enchantment_level", "minimum_distance");

    private VanillaEventRules() {
    }

    public static Set<String> supportedCapabilities() {
        var result = new LinkedHashSet<String>();
        PARAMETERS.keySet().forEach(type -> result.add("minecraft:" + type));
        result.addAll(ProductionObjectiveRules.supportedCapabilities());
        result.addAll(WorldObjectiveRules.supportedCapabilities());
        result.addAll(EncounterObjectiveRules.supportedCapabilities());
        result.addAll(StarcatcherObjectiveRules.supportedCapabilities());
        result.addAll(FarmerObjectiveRules.supportedCapabilities());
        result.addAll(dev.taskbound.porter.PorterObjectiveRules.supportedCapabilities());
        return Set.copyOf(result);
    }

    /** Call for catalog definitions on reload. Unknown requirements must never become free passes. */
    public static void validate(String type, Map<String, ?> parameters) {
        if (FarmerObjectiveRules.supports(type)) { FarmerObjectiveRules.validate(type, parameters); return; }
        if (dev.taskbound.porter.PorterObjectiveRules.supports(type)) { dev.taskbound.porter.PorterObjectiveRules.validate(type, parameters); return; }
        if (StarcatcherObjectiveRules.supports(type)) { StarcatcherObjectiveRules.validate(type, parameters); return; }
        if (ProductionObjectiveRules.supports(type)) { ProductionObjectiveRules.validate(type, parameters); return; }
        if (WorldObjectiveRules.supports(type)) { WorldObjectiveRules.validate(type, parameters); return; }
        if (EncounterObjectiveRules.supports(type)) { EncounterObjectiveRules.validate(type, parameters); return; }
        Set<String> specific = PARAMETERS.get(type);
        if (specific == null) {
            throw new IllegalArgumentException("Unsupported vanilla objective: " + type);
        }
        for (var entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!CONTEXT.contains(key) && !specific.contains(key)) {
                throw new IllegalArgumentException("Unsupported " + type + " requirement: " + key);
            }
            if (NUMBERS.contains(key)) {
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                    throw new IllegalArgumentException(key + " must be a finite number");
                }
                if (!Set.of("max_health", "minimum_distance").contains(key)
                        && n.doubleValue() != n.intValue()) {
                    throw new IllegalArgumentException(key + " must be an integer");
                }
                if (!Set.of("min_y", "max_y").contains(key) && n.doubleValue() < 0
                        || Set.of("required_count", "minimum_enchantment_level", "minimum_tool_enchantment_level")
                                .contains(key) && n.doubleValue() < 1) {
                    throw new IllegalArgumentException(key + " is out of range");
                }
            } else if (key.equals("hostile_only")) {
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException("hostile_only must be boolean");
                }
            } else if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(key + " must be non-empty text");
            } else if (!Set.of("required_time", "required_weather", "method", "action").contains(key)
                    && !ID.matcher(text).matches()) {
                throw new IllegalArgumentException(key + " must be a resource ID");
            }
        }
        require(parameters, "required_count");
        switch (type) {
            case "kill_entity" -> {
                if (!parameters.containsKey("entity") && !parameters.containsKey("entity_tag")
                        && !Boolean.TRUE.equals(parameters.get("hostile_only"))) {
                    throw new IllegalArgumentException("Kill objectives need an entity, tag, or hostile_only");
                }
                choice(parameters, "method", Set.of("melee"));
            }
            case "catch_item" -> {
                if (!parameters.containsKey("fish_item") && !parameters.containsKey("fish_item_tag")) {
                    throw new IllegalArgumentException("Catch objectives need a fish_item or fish_item_tag");
                }
                for (String key : Set.of("required_tool", "required_tool_tag", "tool_enchantment",
                        "minimum_tool_enchantment_level", "required_weapon", "required_weapon_tag")) {
                    if (parameters.containsKey(key)) {
                        throw new IllegalArgumentException("Fishing equipment provenance is deferred: " + key);
                    }
                }
            }
            case "craft_item", "enchant_item" -> require(parameters, "item");
            case "gather_block", "harvest_crop" -> require(parameters, "block");
            case "challenge_action" -> {
                require(parameters, "action");
                choice(parameters, "action", ACTIONS);
                if (parameters.containsKey("minimum_distance")
                        && !"bow_hit_on_hostile".equals(parameters.get("action"))) {
                    throw new IllegalArgumentException("minimum_distance applies only to bow hits");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported objective " + type);
        }
        if (type.equals("enchant_item")) {
            choice(parameters, "method", Set.of("table", "anvil"));
        }
        if (type.equals("harvest_crop") && !Set.of("minecraft:wheat", "minecraft:cocoa")
                .contains(parameters.get("block"))) {
            throw new IllegalArgumentException("Only mature wheat and cocoa harvests are supported");
        }
        choice(parameters, "required_time", Set.of("day", "night"));
        choice(parameters, "required_weather", Set.of("rain", "snow", "clear", "thunder"));
        if (parameters.containsKey("min_y") && parameters.containsKey("max_y")
                && ((Number) parameters.get("min_y")).intValue() > ((Number) parameters.get("max_y")).intValue()) {
            throw new IllegalArgumentException("min_y exceeds max_y");
        }
        paired(parameters, "minimum_effect_amplifier", "required_effect");
        paired(parameters, "minimum_effect_extra_amplifier", "required_effect_extra");
        paired(parameters, "minimum_tool_enchantment_level", "tool_enchantment");
        paired(parameters, "minimum_enchantment_level", "required_enchantment");
    }

    private static void require(Map<String, ?> values, String key) {
        if (!values.containsKey(key)) {
            throw new IllegalArgumentException("Missing objective parameter " + key);
        }
    }

    private static void paired(Map<String, ?> values, String field, String required) {
        if (values.containsKey(field)) {
            require(values, required);
        }
    }

    private static void choice(Map<String, ?> values, String key, Set<String> choices) {
        if (values.containsKey(key) && !choices.contains(values.get(key))) {
            throw new IllegalArgumentException("Unsupported " + key + ": " + values.get(key));
        }
    }

    public static boolean matchesTime(String required, long ticks) {
        long time = Math.floorMod(ticks, 24_000L);
        return required == null || switch (required) {
            case "day" -> time < 12_000L;
            case "night" -> time >= 13_000L && time < 23_000L;
            default -> false;
        };
    }

    public static boolean enchantmentIncreased(int before, int after, int minimum) {
        return minimum > 0 && after >= minimum && after > before;
    }
}
