package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerReputation {
    private final String giverId;
    private int points;
    private int completedTaskCount;
    private int failedTaskCount;
    private Long lastLevelUp;

    public PlayerReputation(String giverId) {
        this(giverId, 0, 0, 0, null);
    }

    public PlayerReputation(
            String giverId,
            int points,
            int completedTaskCount,
            int failedTaskCount,
            Long lastLevelUp
    ) {
        if (giverId.isBlank()) {
            throw new IllegalArgumentException("Giver ID cannot be blank");
        }
        if (points < 0 || completedTaskCount < 0 || failedTaskCount < 0) {
            throw new IllegalArgumentException("Reputation counters cannot be negative");
        }
        this.giverId = giverId;
        this.points = points;
        this.completedTaskCount = completedTaskCount;
        this.failedTaskCount = failedTaskCount;
        this.lastLevelUp = lastLevelUp;
    }

    public String giverId() {
        return giverId;
    }

    public int points() {
        return points;
    }

    public int completedTaskCount() {
        return completedTaskCount;
    }

    public int failedTaskCount() {
        return failedTaskCount;
    }

    public Long lastLevelUp() {
        return lastLevelUp;
    }

    public int level(ReputationThresholds thresholds) {
        return thresholds.levelForPoints(points);
    }

    public void addPoints(int amount, ReputationThresholds thresholds, long changedAt) {
        if (amount < 0) {
            throw new IllegalArgumentException("Reputation cannot be reduced through addPoints");
        }
        int previousLevel = level(thresholds);
        points = Math.addExact(points, amount);
        if (level(thresholds) > previousLevel) {
            lastLevelUp = changedAt;
        }
    }

    /** Administrative/debug update; lowering points never counts as a level-up. */
    public void setPoints(int newPoints, ReputationThresholds thresholds, long changedAt) {
        if (newPoints < 0) {
            throw new IllegalArgumentException("Reputation points cannot be negative");
        }
        int previousLevel = level(thresholds);
        points = newPoints;
        if (level(thresholds) > previousLevel) {
            lastLevelUp = changedAt;
        }
    }

    public void recordCompletion(int reputationReward, ReputationThresholds thresholds, long completedAt) {
        addPoints(reputationReward, thresholds, completedAt);
        completedTaskCount = Math.addExact(completedTaskCount, 1);
    }

    public void recordFailure() {
        failedTaskCount = Math.addExact(failedTaskCount, 1);
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("giver_id", giverId);
        map.put("points", points);
        map.put("completed_task_count", completedTaskCount);
        map.put("failed_task_count", failedTaskCount);
        map.put("last_level_up", lastLevelUp);
        return map;
    }

    public static PlayerReputation fromMap(Map<String, ?> map) {
        return new PlayerReputation(
                DataMaps.string(map, "giver_id"),
                DataMaps.integer(map, "points"),
                DataMaps.integer(map, "completed_task_count"),
                DataMaps.integer(map, "failed_task_count"),
                DataMaps.nullableLong(map, "last_level_up")
        );
    }
}
