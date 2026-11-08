package dev.taskbound.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated, attributed farming transactions; inventory possession is never an action. */
public final class FarmerObjectiveRules {
    private static final Set<String> ACTIONS = Set.of("harvest", "milk", "shear", "breed", "honey", "honeycomb", "cut", "cook", "craft");
    private static final Set<String> EXTRA = Set.of("action", "entity", "distinct", "minimum_colors", "minimum_crop_types", "per_crop_count", "station");
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private FarmerObjectiveRules() { }
    public static boolean supports(String type) { return "farm_action".equals(type); }
    public static Set<String> supportedCapabilities() { return Set.of("minecraft:farm_action"); }

    public static void validate(String type, Map<String, ?> parameters) {
        if (!supports(type) || !(parameters.get("action") instanceof String) || !ACTIONS.contains(parameters.get("action"))) throw new IllegalArgumentException("Unknown farming action");
        String action = (String) parameters.get("action");
        var common = new LinkedHashMap<String, Object>();
        parameters.forEach(common::put);
        EXTRA.forEach(common::remove);
        common.putIfAbsent("item", "minecraft:wheat");
        VanillaEventRules.validate("craft_item", common);
        int count = integer(parameters, "required_count", 1, 4096);
        if (parameters.containsKey("item") && !Set.of("harvest", "cut", "cook", "craft").contains(action))
            throw new IllegalArgumentException("item only applies to harvest/cut/cook/craft");
        if (parameters.containsKey("entity") && (!(parameters.get("entity") instanceof String entity) || !ID.matcher(entity).matches())) throw new IllegalArgumentException("entity must be an ID");
        if (action.equals("breed") && !parameters.containsKey("entity")) throw new IllegalArgumentException("Breeding requires entity");
        if (!action.equals("breed") && parameters.containsKey("entity")) throw new IllegalArgumentException("entity only applies to breeding");
        if (parameters.containsKey("distinct")) {
            if (!Set.of("milk", "shear").contains(action) || !(parameters.get("distinct") instanceof Boolean)) throw new IllegalArgumentException("distinct only applies to milk/shear and must be boolean");
        }
        if (parameters.containsKey("minimum_colors")) {
            if (!action.equals("shear") || integer(parameters, "minimum_colors", 1, 16) > count) throw new IllegalArgumentException("Invalid sheep color requirement");
        }
        if (parameters.containsKey("minimum_crop_types") || parameters.containsKey("per_crop_count")) {
            if (!action.equals("harvest") || parameters.containsKey("item")) throw new IllegalArgumentException("Crop variety requires harvest without a fixed item");
            int types = integer(parameters, "minimum_crop_types", 1, 8);
            int each = integer(parameters, "per_crop_count", 1, 512);
            if (count != types * each) throw new IllegalArgumentException("Crop total must equal minimum_crop_types * per_crop_count");
        } else if (Set.of("harvest", "cut", "cook", "craft").contains(action) && !parameters.containsKey("item")) {
            throw new IllegalArgumentException(action + " requires item");
        }
        if (action.equals("cook")) {
            if (!(parameters.get("station") instanceof String) || !Set.of("furnace", "cooking_pot").contains(parameters.get("station"))) throw new IllegalArgumentException("cook requires station furnace or cooking_pot");
        } else if (parameters.containsKey("station")) throw new IllegalArgumentException("station only applies to cook");
    }

    private static int integer(Map<String, ?> p, String key, int minimum, int maximum) {
        if (!(p.get(key) instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() != n.intValue()
                || n.intValue() < minimum || n.intValue() > maximum) throw new IllegalArgumentException("Invalid " + key);
        return n.intValue();
    }
}
