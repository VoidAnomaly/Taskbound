package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class ReputationThresholds {
    private final NavigableMap<Integer, Integer> pointsByLevel;

    public ReputationThresholds(Map<Integer, Integer> pointsByLevel) {
        if (pointsByLevel.isEmpty()) {
            throw new IllegalArgumentException("At least one reputation threshold is required");
        }
        var sorted = new TreeMap<Integer, Integer>(pointsByLevel);
        int previousPoints = -1;
        for (var entry : sorted.entrySet()) {
            if (entry.getKey() < 1) {
                throw new IllegalArgumentException("Reputation levels must be positive");
            }
            if (entry.getValue() < 0 || entry.getValue() <= previousPoints) {
                throw new IllegalArgumentException("Reputation point thresholds must be non-negative and strictly increasing");
            }
            previousPoints = entry.getValue();
        }
        if (sorted.firstEntry().getValue() != 0) {
            throw new IllegalArgumentException("The first reputation threshold must start at zero points");
        }
        this.pointsByLevel = java.util.Collections.unmodifiableNavigableMap(sorted);
    }

    public int levelForPoints(int points) {
        if (points < 0) {
            throw new IllegalArgumentException("Reputation points cannot be negative");
        }
        int result = pointsByLevel.firstKey();
        for (var entry : pointsByLevel.entrySet()) {
            if (points < entry.getValue()) {
                break;
            }
            result = entry.getKey();
        }
        return result;
    }

    public int pointsForLevel(int level) {
        Integer points = pointsByLevel.get(level);
        if (points == null) {
            throw new IllegalArgumentException("No threshold is configured for level " + level);
        }
        return points;
    }

    public NavigableMap<Integer, Integer> pointsByLevel() {
        return pointsByLevel;
    }

    public Map<String, Object> toMap() {
        var thresholds = new LinkedHashMap<String, Object>();
        pointsByLevel.forEach((level, points) -> thresholds.put(Integer.toString(level), points));
        var map = new LinkedHashMap<String, Object>();
        map.put("thresholds", thresholds);
        return map;
    }

    public static ReputationThresholds fromMap(Map<String, ?> map) {
        var values = new LinkedHashMap<Integer, Integer>();
        DataMaps.map(map, "thresholds").forEach((level, points) -> {
            if (!(points instanceof Number number)) {
                throw new IllegalArgumentException("Reputation thresholds must be numeric");
            }
            values.put(Integer.parseInt(level), number.intValue());
        });
        return new ReputationThresholds(values);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReputationThresholds thresholds && pointsByLevel.equals(thresholds.pointsByLevel);
    }

    @Override
    public int hashCode() {
        return pointsByLevel.hashCode();
    }
}
