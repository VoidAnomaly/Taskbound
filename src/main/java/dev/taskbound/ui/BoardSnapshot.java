package dev.taskbound.ui;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedTask;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable, server-authored state displayed by the task-board screen.
 *
 * <p>The client receives complete task details for presentation, but never
 * sends those details back when performing an action.</p>
 */
public record BoardSnapshot(
        String boardId,
        int boardX,
        int boardY,
        int boardZ,
        String giverId,
        String giverDisplayName,
        int reputationPoints,
        int reputationLevel,
        int reputationLevelFloor,
        int nextReputationLevelThreshold,
        String rankName,
        long gameTime,
        List<GeneratedTask> offers,
        List<ActiveTask> activeTasks,
        List<LockedCategoryView> lockedCategories,
        String notice,
        List<GuildView> guilds,
        Map<String, List<String>> unseenOfferIdsByGuild,
        List<String> claimableTaskIds
) {
    public BoardSnapshot(String boardId, int boardX, int boardY, int boardZ, String giverId,
            String giverDisplayName, int reputationPoints, int reputationLevel, int reputationLevelFloor,
            int nextReputationLevelThreshold, String rankName, long gameTime, List<GeneratedTask> offers,
            List<ActiveTask> activeTasks, List<LockedCategoryView> lockedCategories, String notice, List<GuildView> guilds) {
        this(boardId, boardX, boardY, boardZ, giverId, giverDisplayName, reputationPoints, reputationLevel,
                reputationLevelFloor, nextReputationLevelThreshold, rankName, gameTime, offers, activeTasks,
                lockedCategories, notice, guilds, Map.of(), activeTasks.stream().filter(active -> active.readyToClaim(gameTime))
                        .map(active -> active.task().taskId()).toList());
    }
    public BoardSnapshot(String boardId, int boardX, int boardY, int boardZ, String giverId,
            String giverDisplayName, int reputationPoints, int reputationLevel, int reputationLevelFloor,
            int nextReputationLevelThreshold, String rankName, long gameTime, List<GeneratedTask> offers,
            List<ActiveTask> activeTasks, List<LockedCategoryView> lockedCategories, String notice) {
        this(boardId, boardX, boardY, boardZ, giverId, giverDisplayName, reputationPoints, reputationLevel,
                reputationLevelFloor, nextReputationLevelThreshold, rankName, gameTime, offers, activeTasks,
                lockedCategories, notice, List.of());
    }

    public BoardSnapshot {
        UUID.fromString(boardId);
        if (giverId.isBlank() || giverDisplayName.isBlank() || rankName.isBlank()
                || reputationPoints < 0 || reputationLevel < 1 || reputationLevelFloor < 0
                || reputationPoints < reputationLevelFloor
                || nextReputationLevelThreshold < reputationLevelFloor
                || (nextReputationLevelThreshold > reputationLevelFloor
                    && reputationPoints >= nextReputationLevelThreshold)
                || gameTime < 0L) {
            throw new IllegalArgumentException("Invalid task-board snapshot");
        }
        offers = List.copyOf(offers);
        activeTasks = List.copyOf(activeTasks);
        lockedCategories = List.copyOf(lockedCategories);
        guilds = List.copyOf(guilds);
        var unseen = new LinkedHashMap<String, List<String>>();
        unseenOfferIdsByGuild.forEach((guild, ids) -> unseen.put(guild, List.copyOf(ids)));
        unseenOfferIdsByGuild = Map.copyOf(unseen);
        claimableTaskIds = List.copyOf(claimableTaskIds);
        notice = notice == null ? "" : notice;
    }

    public String toJson() {
        var map = new LinkedHashMap<String, Object>();
        map.put("board_id", boardId);
        map.put("board_x", boardX);
        map.put("board_y", boardY);
        map.put("board_z", boardZ);
        map.put("giver_id", giverId);
        map.put("giver_display_name", giverDisplayName);
        map.put("reputation_points", reputationPoints);
        map.put("reputation_level", reputationLevel);
        map.put("reputation_level_floor", reputationLevelFloor);
        map.put("next_reputation_level_threshold", nextReputationLevelThreshold);
        map.put("rank_name", rankName);
        map.put("game_time", gameTime);
        map.put("offers", offers.stream().map(GeneratedTask::toMap).toList());
        map.put("active_tasks", activeTasks.stream().map(ActiveTask::toMap).toList());
        map.put("locked_categories", lockedCategories.stream().map(LockedCategoryView::toMap).toList());
        map.put("notice", notice);
        map.put("guilds", guilds.stream().map(GuildView::toMap).toList());
        map.put("unseen_offer_ids_by_guild", unseenOfferIdsByGuild);
        map.put("claimable_task_ids", claimableTaskIds);
        return UiJson.write(map);
    }

    public BoardSnapshot withNotice(String newNotice) {
        return new BoardSnapshot(
                boardId, boardX, boardY, boardZ, giverId, giverDisplayName,
                reputationPoints, reputationLevel, reputationLevelFloor, nextReputationLevelThreshold,
                rankName, gameTime,
                offers, activeTasks, lockedCategories, newNotice, guilds, unseenOfferIdsByGuild, claimableTaskIds
        );
    }

    public static BoardSnapshot fromJson(String json) {
        Map<String, Object> map = UiJson.read(json);
        return new BoardSnapshot(
                UiJson.string(map, "board_id"),
                UiJson.integer(map, "board_x"),
                UiJson.integer(map, "board_y"),
                UiJson.integer(map, "board_z"),
                UiJson.string(map, "giver_id"),
                UiJson.string(map, "giver_display_name"),
                UiJson.integer(map, "reputation_points"),
                UiJson.integer(map, "reputation_level"),
                UiJson.integer(map, "reputation_level_floor"),
                UiJson.integer(map, "next_reputation_level_threshold"),
                UiJson.string(map, "rank_name"),
                UiJson.longValue(map, "game_time"),
                UiJson.objects(map, "offers", GeneratedTask::fromMap),
                UiJson.objects(map, "active_tasks", ActiveTask::fromMap),
                UiJson.objects(map, "locked_categories", LockedCategoryView::fromMap),
                UiJson.string(map, "notice"),
                map.containsKey("guilds") ? UiJson.objects(map, "guilds", GuildView::fromMap) : List.of(),
                unseenOffers(map),
                UiJson.list(map, "claimable_task_ids").stream().map(value -> (String) value).toList()
        );
    }

    private static Map<String, List<String>> unseenOffers(Map<String, ?> map) {
        var result = new LinkedHashMap<String, List<String>>();
        UiJson.object(UiJson.required(map, "unseen_offer_ids_by_guild"), "unseen offers").forEach((guild, value) -> {
            if (!(value instanceof List<?> ids)) throw new IllegalArgumentException("Unseen offers must be a list");
            result.put(guild, ids.stream().map(id -> (String) id).toList());
        });
        return result;
    }
}
