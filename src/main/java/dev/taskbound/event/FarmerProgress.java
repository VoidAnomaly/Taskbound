package dev.taskbound.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure persisted aggregation rules used by the actual farming hooks and regression tests. */
public final class FarmerProgress {
    private FarmerProgress() { }

    public static int cropProgress(Map<String, Object> state, String crop, int amount, int types, int perType) {
        var counts = new LinkedHashMap<String, Object>();
        if (state.get("crops") instanceof Map<?, ?> map) map.forEach((k, v) -> counts.put(k.toString(), v));
        int old = counts.get(crop) instanceof Number n ? n.intValue() : 0;
        counts.put(crop, Math.min(perType, old + amount));
        state.put("crops", counts);
        return counts.values().stream().map(Number.class::cast).map(Number::intValue)
                .sorted(Comparator.reverseOrder()).limit(types).mapToInt(Integer::intValue).sum();
    }

    public static int animalProgress(Map<String, Object> state, String animal, String color, boolean distinct,
            int required, int minimumColors) {
        var animals = strings(state.get("animals"));
        if (distinct && animals.contains(animal)) return progress(state, required, minimumColors);
        var colors = strings(state.get("colors"));
        // Once the head count is met, retain only animals that supply a missing color.
        // This bounds saved identities without preventing a late qualifying color.
        if (animals.size() >= required && (color == null || colors.contains(color))) return progress(state, required, minimumColors);
        animals.add(animal);
        state.put("animals", animals);
        if (color != null && !colors.contains(color)) colors.add(color);
        state.put("colors", colors);
        return progress(state, required, minimumColors);
    }

    private static int progress(Map<String, Object> state, int required, int minimumColors) {
        int count = strings(state.get("animals")).size();
        // Hold one counter short until both conditions are satisfied. Further valid sheep may add colors.
        return Math.min(count, strings(state.get("colors")).size() >= minimumColors ? required : required - 1);
    }

    private static List<String> strings(Object value) {
        var result = new ArrayList<String>();
        if (value instanceof List<?> list) list.stream().filter(String.class::isInstance).map(String.class::cast).forEach(result::add);
        return result;
    }
}
