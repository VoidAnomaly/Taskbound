package dev.taskbound.ui;

import dev.taskbound.core.LevelRewardDefinition.ItemGift;
import java.util.List;
import java.util.Map;

public record LevelRewardView(int level, boolean eligible, boolean claimed, String description, List<ItemGift> items) {
    public LevelRewardView { items = List.copyOf(items); }

    public Map<String, Object> toMap() {
        return Map.of("level", level, "eligible", eligible, "claimed", claimed, "description", description,
                "items", items.stream().map(item -> Map.of("item", item.item(), "count", item.count())).toList());
    }
    public static LevelRewardView fromMap(Map<String, ?> map) {
        return new LevelRewardView(UiJson.integer(map, "level"), Boolean.TRUE.equals(map.get("eligible")),
                Boolean.TRUE.equals(map.get("claimed")), UiJson.string(map, "description"),
                UiJson.objects(map, "items", item -> new ItemGift(UiJson.string(item, "item"), UiJson.integer(item, "count"))));
    }
}
