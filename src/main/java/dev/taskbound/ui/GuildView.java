package dev.taskbound.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GuildView(String id, String displayName, String iconItem, int reputationPoints,
        int reputationLevel, int levelFloor, int nextLevelThreshold, String rankName,
        List<RankView> ranks, List<LevelRewardView> levelRewards) {
    public GuildView {
        ranks = List.copyOf(ranks);
        levelRewards = List.copyOf(levelRewards);
    }

    public boolean maximumLevel() { return nextLevelThreshold <= levelFloor; }

    public int levelProgressRequired() {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) nextLevelThreshold - levelFloor));
    }

    public int levelProgressPoints() {
        return (int) Math.max(0, Math.min(levelProgressRequired(), (long) reputationPoints - levelFloor));
    }

    /** Progress within the current level, rather than a fraction of lifetime reputation. */
    public double levelProgress() {
        return maximumLevel() ? 1 : levelProgressPoints() / (double) levelProgressRequired();
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", id); map.put("display_name", displayName); map.put("icon_item", iconItem);
        map.put("points", reputationPoints); map.put("level", reputationLevel); map.put("floor", levelFloor);
        map.put("next", nextLevelThreshold); map.put("rank", rankName);
        map.put("ranks", ranks.stream().map(RankView::toMap).toList());
        map.put("level_rewards", levelRewards.stream().map(LevelRewardView::toMap).toList());
        return map;
    }
    public static GuildView fromMap(Map<String, ?> map) {
        return new GuildView(UiJson.string(map, "id"), UiJson.string(map, "display_name"), UiJson.string(map, "icon_item"),
                UiJson.integer(map, "points"), UiJson.integer(map, "level"), UiJson.integer(map, "floor"),
                UiJson.integer(map, "next"), UiJson.string(map, "rank"), UiJson.objects(map, "ranks", RankView::fromMap),
                UiJson.objects(map, "level_rewards", LevelRewardView::fromMap));
    }
}
