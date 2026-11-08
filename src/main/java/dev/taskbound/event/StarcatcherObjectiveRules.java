package dev.taskbound.event;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Stable JSON boundary for catches proved by the optional Starcatcher transaction hook. */
public final class StarcatcherObjectiveRules {
    public static final String TYPE = "starcatcher_catch";
    public static final String CAPABILITY = "starcatcher:catch";
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> RARITIES = Set.of("common", "uncommon", "rare", "epic", "legendary");
    private static final Set<String> HABITATS = Set.of("lake", "river", "ocean", "cave", "lava", "nether", "end", "sky");
    private static final Set<String> KEYS = Set.of("required_count", "count_mode", "species", "habitats", "rarity",
            "minimum_rarity", "minimum_weight_percentile", "perfect", "required_time", "required_weather");

    private StarcatcherObjectiveRules() { }
    public static boolean supports(String type) { return TYPE.equals(type); }
    public static Set<String> supportedCapabilities() { return Set.of(CAPABILITY); }

    public static void validate(String type, Map<String, ?> parameters) {
        if (!supports(type) || !KEYS.containsAll(parameters.keySet())) throw new IllegalArgumentException("Unsupported Starcatcher requirement");
        Object count = parameters.get("required_count");
        if (!(count instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue() || number.intValue() < 1 || number.intValue() > 256)
            throw new IllegalArgumentException("Starcatcher required_count must be between 1 and 256");
        choice(parameters, "count_mode", Set.of("catches", "species", "habitats"));
        choice(parameters, "rarity", RARITIES);
        choice(parameters, "minimum_rarity", RARITIES);
        choice(parameters, "required_time", Set.of("day", "night"));
        choice(parameters, "required_weather", Set.of("clear", "rain", "thunder"));
        for (String key : List.of("species", "habitats")) {
            if (!parameters.containsKey(key)) continue;
            if (!(parameters.get(key) instanceof List<?> values) || values.isEmpty() || values.size() > 256
                    || values.stream().anyMatch(value -> !(value instanceof String text)
                            || (key.equals("species") ? !ID.matcher(text).matches() : !HABITATS.contains(text))))
                throw new IllegalArgumentException("Invalid Starcatcher " + key + " list");
        }
        if (parameters.containsKey("minimum_weight_percentile")) {
            if (!(parameters.get("minimum_weight_percentile") instanceof Number weight)
                    || !Double.isFinite(weight.doubleValue()) || weight.doubleValue() < 0 || weight.doubleValue() > 100)
                throw new IllegalArgumentException("Weight percentile must be 0 to 100");
        }
        if (parameters.containsKey("perfect") && !(parameters.get("perfect") instanceof Boolean))
            throw new IllegalArgumentException("perfect must be boolean");
    }

    private static void choice(Map<String, ?> map, String key, Set<String> choices) {
        if (map.containsKey(key) && !choices.contains(map.get(key))) throw new IllegalArgumentException("Invalid " + key);
    }

    public static int rarityRank(String rarity) {
        return switch (rarity) { case "common" -> 0; case "uncommon" -> 1; case "rare" -> 2;
            case "epic" -> 3; case "legendary" -> 4; default -> -1; };
    }

    public static double normalizedWeightPercentile(double rawPercentile) {
        return Double.isFinite(rawPercentile) ? Math.max(0, Math.min(100, 100 - rawPercentile)) : Double.NaN;
    }

    /** Matches Starcatcher's inspected DaytimeRestriction DAY/NIGHT presets, including dusk and dawn. */
    public static boolean matchesTime(String required, long ticks) {
        long time = Math.floorMod(ticks, 24_000L);
        return switch (required) {
            case "day" -> time > 0 && time < 12_700L;
            case "night" -> time > 12_700L;
            default -> false;
        };
    }
}
