package dev.taskbound.core;

import java.util.List;
import java.util.Map;

/** Explicit stage groups allow unordered work within each ordered phase. */
public final class ObjectiveSequence {
    private ObjectiveSequence() { }

    public static void validate(Map<String, ?> conditions, int objectives) {
        if (!conditions.containsKey("objective_stages")) return;
        if (conditions.containsKey("objective_order")) throw new IllegalArgumentException("Use objective_stages or objective_order, not both");
        if (!(conditions.get("objective_stages") instanceof List<?> stages) || stages.size() != objectives) {
            throw new IllegalArgumentException("objective_stages must contain one integer per objective");
        }
        int previous = 0;
        for (int i = 0; i < stages.size(); i++) {
            if (!(stages.get(i) instanceof Number value) || value.doubleValue() != value.intValue()
                    || value.intValue() < previous || value.intValue() > previous + 1
                    || i == 0 && value.intValue() != 0) {
                throw new IllegalArgumentException("objective_stages must start at zero and advance by at most one");
            }
            previous = value.intValue();
        }
    }

    /** Hand-ins are consumed atomically at claim; they cannot gate later work. */
    public static void validateHandIns(Map<String, ?> conditions, List<String> types) {
        List<?> stages = conditions.get("objective_stages") instanceof List<?> list ? list : null;
        boolean strict = "strict".equals(conditions.get("objective_order"));
        if (stages == null && !strict) return;
        for (int i = 0; i < types.size(); i++) {
            if (!types.get(i).equals("hand_in_item")) continue;
            int phase = stages == null ? i : ((Number)stages.get(i)).intValue();
            for (int j = i + 1; j < types.size(); j++) {
                int later = stages == null ? j : ((Number)stages.get(j)).intValue();
                if (later > phase && !types.get(j).equals("hand_in_item")) {
                    throw new IllegalArgumentException("Hand-ins must be in the final phase after all other work");
                }
            }
        }
    }

    public static boolean allows(ActiveTask active, int index) {
        Object grouped = active.task().conditions().get("objective_stages");
        List<?> stages = grouped instanceof List<?> list ? list : null;
        if (stages == null && !"strict".equals(active.task().conditions().get("objective_order"))) return true;
        int currentStage = stages == null ? index : ((Number) stages.get(index)).intValue();
        for (int i = 0; i < index; i++) {
            int previousStage = stages == null ? i : ((Number) stages.get(i)).intValue();
            GeneratedObjective objective = active.task().objectives().get(i);
            int required = ((Number) objective.parameters().getOrDefault("required_count", 1)).intValue();
            if (previousStage < currentStage && active.progress().progressFor(objective.key()) < required) return false;
        }
        return true;
    }
}
