package dev.taskbound.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mutable selection state scoped to exactly one player and one task giver. */
public final class SelectionHistory {
    public static final int DEFAULT_RECENT_TEMPLATE_LIMIT = 8;

    private final String playerId;
    private final String giverId;
    private final int recentTemplateLimit;
    private long generationCount;
    private final Map<String, CategoryHistory> categoryHistories;
    private final List<String> recentTemplateIds;

    public SelectionHistory(String playerId, String giverId) {
        this(playerId, giverId, DEFAULT_RECENT_TEMPLATE_LIMIT);
    }

    public SelectionHistory(String playerId, String giverId, int recentTemplateLimit) {
        this(playerId, giverId, recentTemplateLimit, 0, Map.of(), List.of());
    }

    private SelectionHistory(
            String playerId,
            String giverId,
            int recentTemplateLimit,
            long generationCount,
            Map<String, CategoryHistory> categoryHistories,
            List<String> recentTemplateIds
    ) {
        if (playerId.isBlank() || giverId.isBlank()) {
            throw new IllegalArgumentException("Player and giver IDs cannot be blank");
        }
        if (recentTemplateLimit < 1 || generationCount < 0) {
            throw new IllegalArgumentException("Invalid selection-history bounds");
        }
        if (recentTemplateIds.size() > recentTemplateLimit) {
            throw new IllegalArgumentException("Recent-template history exceeds its configured limit");
        }
        this.playerId = playerId;
        this.giverId = giverId;
        this.recentTemplateLimit = recentTemplateLimit;
        this.generationCount = generationCount;
        this.categoryHistories = new LinkedHashMap<>(categoryHistories);
        this.recentTemplateIds = new ArrayList<>(recentTemplateIds);
    }

    public String playerId() {
        return playerId;
    }

    public String giverId() {
        return giverId;
    }

    public long generationCount() {
        return generationCount;
    }

    public Map<String, CategoryHistory> categoryHistories() {
        return Map.copyOf(categoryHistories);
    }

    public CategoryHistory category(String categoryId) {
        return categoryHistories.get(categoryId);
    }

    public List<String> recentTemplateIds() {
        return List.copyOf(recentTemplateIds);
    }

    /** Activates newly reputation-unlocked categories without consuming any boost draw. */
    public void activateUnlockedCategories(Collection<TaskCategoryDefinition> categories, int reputationLevel) {
        categories.stream()
                .filter(category -> category.giverId().equals(giverId))
                .filter(category -> category.unlockedAt(reputationLevel))
                .sorted(Comparator.comparing(TaskCategoryDefinition::id))
                .forEach(category -> categoryHistories.computeIfAbsent(
                        category.id(), ignored -> new CategoryHistory(category.newUnlockBoostDraws())));
    }

    /** Commits history only after the complete task and rewards have been generated. */
    public void recordSelection(String selectedCategoryId, String selectedTemplateId, Set<String> eligibleCategoryIds) {
        if (!eligibleCategoryIds.contains(selectedCategoryId)) {
            throw new IllegalArgumentException("Selected category must be eligible");
        }
        CategoryHistory selected = categoryHistories.get(selectedCategoryId);
        if (selected == null) {
            throw new IllegalStateException("Selected category was never activated as unlocked");
        }
        generationCount = Math.addExact(generationCount, 1);
        eligibleCategoryIds.stream().sorted().forEach(categoryId -> {
            CategoryHistory history = categoryHistories.get(categoryId);
            if (history == null) {
                throw new IllegalStateException("Eligible category was never activated: " + categoryId);
            }
            if (categoryId.equals(selectedCategoryId)) {
                history.recordSelected(generationCount);
            } else {
                history.recordEligibleMiss();
            }
        });
        recentTemplateIds.add(0, selectedTemplateId);
        if (recentTemplateIds.size() > recentTemplateLimit) {
            recentTemplateIds.subList(recentTemplateLimit, recentTemplateIds.size()).clear();
        }
    }

    public Map<String, Object> toMap() {
        var histories = new LinkedHashMap<String, Object>();
        categoryHistories.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> histories.put(entry.getKey(), entry.getValue().toMap()));
        var map = new LinkedHashMap<String, Object>();
        map.put("player_id", playerId);
        map.put("giver_id", giverId);
        map.put("recent_template_limit", recentTemplateLimit);
        map.put("generation_count", generationCount);
        map.put("category_histories", histories);
        map.put("recent_template_ids", recentTemplateIds());
        return map;
    }

    public static SelectionHistory fromMap(Map<String, ?> map) {
        var histories = new LinkedHashMap<String, CategoryHistory>();
        DataMaps.map(map, "category_histories").forEach((categoryId, value) -> {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Category histories must be objects");
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> history = (Map<String, ?>) raw;
            histories.put(categoryId, CategoryHistory.fromMap(history));
        });
        return new SelectionHistory(
                DataMaps.string(map, "player_id"),
                DataMaps.string(map, "giver_id"),
                DataMaps.integer(map, "recent_template_limit"),
                DataMaps.longValue(map, "generation_count"),
                histories,
                DataMaps.strings(map, "recent_template_ids")
        );
    }
}
