package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CategoryHistory {
    private int eligibleMisses;
    private long lastSelectedGeneration;
    private int timesSelected;
    private int newlyUnlockedBoostDrawsRemaining;

    public CategoryHistory(int newlyUnlockedBoostDrawsRemaining) {
        this(0, -1L, 0, newlyUnlockedBoostDrawsRemaining);
    }

    public CategoryHistory(
            int eligibleMisses,
            long lastSelectedGeneration,
            int timesSelected,
            int newlyUnlockedBoostDrawsRemaining
    ) {
        if (eligibleMisses < 0 || timesSelected < 0 || newlyUnlockedBoostDrawsRemaining < 0) {
            throw new IllegalArgumentException("Category history counters cannot be negative");
        }
        if (lastSelectedGeneration < -1) {
            throw new IllegalArgumentException("Last-selected generation cannot be less than -1");
        }
        this.eligibleMisses = eligibleMisses;
        this.lastSelectedGeneration = lastSelectedGeneration;
        this.timesSelected = timesSelected;
        this.newlyUnlockedBoostDrawsRemaining = newlyUnlockedBoostDrawsRemaining;
    }

    public int eligibleMisses() {
        return eligibleMisses;
    }

    public long lastSelectedGeneration() {
        return lastSelectedGeneration;
    }

    public int timesSelected() {
        return timesSelected;
    }

    public int newlyUnlockedBoostDrawsRemaining() {
        return newlyUnlockedBoostDrawsRemaining;
    }

    void recordSelected(long generation) {
        eligibleMisses = 0;
        lastSelectedGeneration = generation;
        timesSelected = Math.addExact(timesSelected, 1);
        consumeNewUnlockDraw();
    }

    void recordEligibleMiss() {
        eligibleMisses = Math.addExact(eligibleMisses, 1);
        consumeNewUnlockDraw();
    }

    private void consumeNewUnlockDraw() {
        if (newlyUnlockedBoostDrawsRemaining > 0) {
            newlyUnlockedBoostDrawsRemaining--;
        }
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("eligible_misses", eligibleMisses);
        map.put("last_selected_generation", lastSelectedGeneration);
        map.put("times_selected", timesSelected);
        map.put("newly_unlocked_boost_draws_remaining", newlyUnlockedBoostDrawsRemaining);
        return map;
    }

    public static CategoryHistory fromMap(Map<String, ?> map) {
        return new CategoryHistory(
                DataMaps.integer(map, "eligible_misses"),
                DataMaps.longValue(map, "last_selected_generation"),
                DataMaps.integer(map, "times_selected"),
                DataMaps.integer(map, "newly_unlocked_boost_draws_remaining")
        );
    }
}
