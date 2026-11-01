package dev.taskbound.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict data validation for attributed fishing, brewing, enchanting and hand-ins. */
public final class ProductionObjectiveRules {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> TYPES = Set.of("tracked_fish", "brew_potion", "tracked_enchant", "hand_in_item");
    private static final Map<String, Set<String>> EXTRA = Map.of(
            "tracked_fish", Set.of("fish_item", "fish_item_tag", "treasure", "same_rod", "rod_enchantment_extra", "minimum_rod_enchantment_extra_level"),
            "brew_potion", Set.of("potion", "provenance_stage"),
            "tracked_enchant", Set.of("required_enchantments", "provenance_stage"),
            "hand_in_item", Set.of("potion", "required_enchantments", "provenance_stage"));

    private ProductionObjectiveRules() { }
    public static boolean supports(String type) { return TYPES.contains(type); }
    public static Set<String> supportedCapabilities() {
        return TYPES.stream().map(type -> "minecraft:" + type).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static void validate(String type, Map<String, ?> parameters) {
        if (!supports(type)) throw new IllegalArgumentException("Unknown production objective " + type);
        var common = new LinkedHashMap<String, Object>();
        parameters.forEach(common::put);
        for (String key : EXTRA.get(type)) common.remove(key);
        if (type.equals("tracked_fish")) common.put("item", "minecraft:cod");
        VanillaEventRules.validate("craft_item", common);
        if (type.equals("tracked_fish")) {
            if (parameters.containsKey("item")) throw new IllegalArgumentException("Tracked fishing uses fish_item, not item");
            if (!parameters.containsKey("fish_item") && !parameters.containsKey("fish_item_tag")
                    && !Boolean.TRUE.equals(parameters.get("treasure"))) {
                throw new IllegalArgumentException("Tracked fishing requires a fish item, tag, or treasure source");
            }
            for (String key : Set.of("fish_item", "fish_item_tag", "rod_enchantment_extra")) optionalId(parameters, key);
            for (String key : Set.of("treasure", "same_rod")) {
                if (parameters.containsKey(key) && !(parameters.get(key) instanceof Boolean)) throw new IllegalArgumentException(key + " must be boolean");
            }
            if (parameters.containsKey("minimum_rod_enchantment_extra_level")) {
                if (!parameters.containsKey("rod_enchantment_extra")) throw new IllegalArgumentException("Missing extra rod enchantment");
                positive(parameters.get("minimum_rod_enchantment_extra_level"), "minimum_rod_enchantment_extra_level");
            }
        }
        if (type.equals("brew_potion") && !parameters.containsKey("potion")) throw new IllegalArgumentException("Brewing requires potion");
        optionalId(parameters, "potion");
        if (parameters.containsKey("potion") && !Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion").contains(parameters.get("item"))) {
            throw new IllegalArgumentException("Potion objective item must specify normal, splash or lingering form");
        }
        if (type.equals("tracked_enchant")) {
            if (!parameters.containsKey("required_enchantments")) throw new IllegalArgumentException("Tracked enchanting requires enchantments");
            if (!(parameters.get("required_count") instanceof Number count) || count.intValue() != 1) throw new IllegalArgumentException("Tracked enchanting binds one item; required_count must be 1");
        }
        if (parameters.containsKey("required_enchantments")) {
            if (!(parameters.get("required_enchantments") instanceof Map<?, ?> map) || map.isEmpty()) throw new IllegalArgumentException("required_enchantments must be a nonempty map");
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !ID.matcher(key).matches()) throw new IllegalArgumentException("Invalid enchantment ID");
                positive(entry.getValue(), "enchantment level");
            }
        }
        if (parameters.containsKey("provenance_stage")) {
            if (!(parameters.get("provenance_stage") instanceof String value) || !value.matches("[a-zA-Z0-9_.-]+")) throw new IllegalArgumentException("Invalid provenance_stage");
        }
    }

    private static void optionalId(Map<String, ?> map, String field) {
        if (map.containsKey(field) && (!(map.get(field) instanceof String value) || !ID.matcher(value).matches())) throw new IllegalArgumentException(field + " must be an ID");
    }

    private static void positive(Object value, String field) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != number.intValue() || number.intValue() < 1) throw new IllegalArgumentException(field + " must be a positive integer");
    }
}
