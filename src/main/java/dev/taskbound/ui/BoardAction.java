package dev.taskbound.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Minimal client intent. Every field is revalidated against live server state. */
public record BoardAction(
        String boardId,
        int boardX,
        int boardY,
        int boardZ,
        Type type,
        String taskId,
        String giverId,
        int rewardLevel,
        java.util.List<String> seenOfferIds
) {
    public BoardAction(String boardId, int boardX, int boardY, int boardZ, Type type, String taskId, String giverId, int rewardLevel) {
        this(boardId, boardX, boardY, boardZ, type, taskId, giverId, rewardLevel, java.util.List.of());
    }
    public BoardAction(String boardId, int boardX, int boardY, int boardZ, Type type, String taskId, String giverId) {
        this(boardId, boardX, boardY, boardZ, type, taskId, giverId, 0);
    }
    private static final Pattern GIVER_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public enum Type {
        ACCEPT,
        CLAIM,
        REFRESH,
        CYCLE_GIVER,
        SELECT_GIVER,
        REFRESH_VIEW,
        ABANDON,
        CLAIM_LEVEL_REWARD,
        MARK_OFFERS_SEEN
    }

    public BoardAction {
        UUID.fromString(boardId);
        seenOfferIds = java.util.List.copyOf(seenOfferIds);
        if (seenOfferIds.size() > 16 || (type != Type.MARK_OFFERS_SEEN && !seenOfferIds.isEmpty())) {
            throw new IllegalArgumentException("Invalid seen-offer acknowledgement");
        }
        seenOfferIds.forEach(UUID::fromString);
        if (taskId == null) {
            taskId = "";
        }
        if (giverId == null) {
            giverId = "";
        }
        if ((type == Type.ACCEPT || type == Type.CLAIM || type == Type.ABANDON) && taskId.isBlank()) {
            throw new IllegalArgumentException(type + " requires a task ID");
        }
        if (!taskId.isBlank()) {
            UUID.fromString(taskId);
        }
        if ((type == Type.SELECT_GIVER || type == Type.MARK_OFFERS_SEEN)
                && (giverId.isBlank() || !GIVER_ID.matcher(giverId).matches())) {
            throw new IllegalArgumentException("SELECT_GIVER requires a valid giver ID");
        }
        if (!giverId.isBlank() && !GIVER_ID.matcher(giverId).matches()) {
            throw new IllegalArgumentException("Invalid giver ID");
        }
        if (type == Type.CLAIM_LEVEL_REWARD && (giverId.isBlank() || rewardLevel < 2 || rewardLevel >= 20 || rewardLevel % 5 == 0)) {
            throw new IllegalArgumentException("Invalid intermediate-level reward request");
        }
        if (type != Type.CLAIM_LEVEL_REWARD && rewardLevel != 0) {
            throw new IllegalArgumentException("Unexpected reward level");
        }
    }

    public static BoardAction forBoard(BoardSnapshot snapshot, Type type) {
        return forTask(snapshot, type, "");
    }

    public static BoardAction forTask(BoardSnapshot snapshot, Type type, String taskId) {
        return new BoardAction(
                snapshot.boardId(),
                snapshot.boardX(),
                snapshot.boardY(),
                snapshot.boardZ(),
                type,
                taskId,
                snapshot.giverId()
        );
    }

    public static BoardAction accept(BoardSnapshot snapshot, String taskId) {
        return forTask(snapshot, Type.ACCEPT, taskId);
    }

    public static BoardAction markOffersSeen(BoardSnapshot snapshot, java.util.List<String> ids) {
        return new BoardAction(snapshot.boardId(), snapshot.boardX(), snapshot.boardY(), snapshot.boardZ(),
                Type.MARK_OFFERS_SEEN, "", snapshot.giverId(), 0, ids);
    }

    public static BoardAction claim(BoardSnapshot snapshot, String taskId) {
        return forTask(snapshot, Type.CLAIM, taskId);
    }

    public static BoardAction refresh(BoardSnapshot snapshot) {
        return forBoard(snapshot, Type.REFRESH);
    }

    public static BoardAction abandon(BoardSnapshot snapshot, String taskId) {
        return forTask(snapshot, Type.ABANDON, taskId);
    }

    public static BoardAction claimLevelReward(BoardSnapshot snapshot, String giverId, int level) {
        return new BoardAction(snapshot.boardId(), snapshot.boardX(), snapshot.boardY(), snapshot.boardZ(),
                Type.CLAIM_LEVEL_REWARD, "", giverId, level);
    }

    public static BoardAction cycleGiver(BoardSnapshot snapshot) {
        return forBoard(snapshot, Type.CYCLE_GIVER);
    }

    public static BoardAction selectGiver(BoardSnapshot snapshot, String giverId) {
        return new BoardAction(
                snapshot.boardId(),
                snapshot.boardX(),
                snapshot.boardY(),
                snapshot.boardZ(),
                Type.SELECT_GIVER,
                "",
                giverId
        );
    }

    public String toJson() {
        var map = new LinkedHashMap<String, Object>();
        map.put("board_id", boardId);
        map.put("board_x", boardX);
        map.put("board_y", boardY);
        map.put("board_z", boardZ);
        map.put("type", type.name());
        map.put("task_id", taskId);
        map.put("giver_id", giverId);
        map.put("reward_level", rewardLevel);
        map.put("seen_offer_ids", seenOfferIds);
        return UiJson.write(map);
    }

    public static BoardAction fromJson(String json) {
        Map<String, Object> map = UiJson.read(json);
        return new BoardAction(
                UiJson.string(map, "board_id"),
                UiJson.integer(map, "board_x"),
                UiJson.integer(map, "board_y"),
                UiJson.integer(map, "board_z"),
                Type.valueOf(UiJson.string(map, "type")),
                UiJson.string(map, "task_id"),
                UiJson.string(map, "giver_id"),
                map.containsKey("reward_level") ? UiJson.integer(map, "reward_level") : 0,
                map.containsKey("seen_offer_ids") ? UiJson.list(map, "seen_offer_ids").stream().map(value -> (String) value).toList() : java.util.List.of()
        );
    }
}
