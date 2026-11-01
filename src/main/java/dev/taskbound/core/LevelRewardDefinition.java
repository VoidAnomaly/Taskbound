package dev.taskbound.core;

import java.util.List;
import java.util.Map;

/** Fixed, separately reloadable intermediate-level gifts, claimed once per giver and level. */
public record LevelRewardDefinition(String id, List<Gift> rewards) {
    public LevelRewardDefinition {
        if (id.isBlank()) throw new IllegalArgumentException("Level reward giver is required");
        rewards = List.copyOf(rewards);
        if (rewards.stream().map(Gift::level).distinct().count() != rewards.size()) {
            throw new IllegalArgumentException("Duplicate level reward for " + id);
        }
    }

    public record ItemGift(String item, int count) {
        public ItemGift {
            if (item.isBlank() || count < 1 || count > 64) throw new IllegalArgumentException("Invalid level gift");
        }
    }

    public record Gift(int level, List<ItemGift> items) {
        public Gift {
            if (level < 2 || level >= 20 || level % 5 == 0 || items.isEmpty()) {
                throw new IllegalArgumentException("Gifts are only allowed at intermediate reputation levels");
            }
            items = List.copyOf(items);
        }
    }

    @SuppressWarnings("unchecked")
    public static LevelRewardDefinition fromMap(Map<String, ?> map) {
        return new LevelRewardDefinition(DataMaps.string(map, "id"), DataMaps.list(map, "rewards").stream().map(value -> {
            if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Gift must be an object");
            Map<String, ?> gift = (Map<String, ?>) raw;
            return new Gift(DataMaps.integer(gift, "level"), DataMaps.list(gift, "items").stream().map(item -> {
                if (!(item instanceof Map<?, ?> itemRaw)) throw new IllegalArgumentException("Gift item must be an object");
                Map<String, ?> entry = (Map<String, ?>) itemRaw;
                return new ItemGift(DataMaps.string(entry, "item"), DataMaps.integer(entry, "count"));
            }).toList());
        }).toList());
    }
}
