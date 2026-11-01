package dev.taskbound.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.taskbound.TaskboundMod;
import dev.taskbound.core.PlayerCoreState;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld-scoped state for every player's reputation, history, offers, tasks, and progress. */
public final class TaskboundSavedData extends SavedData {
    private static final String FILE_NAME = "taskbound";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();
    private static final Factory<TaskboundSavedData> FACTORY =
            new Factory<>(TaskboundSavedData::new, TaskboundSavedData::load);

    private final Map<UUID, PlayerCoreState> players = new LinkedHashMap<>();
    private final Map<UUID, BoardRecord> boards = new LinkedHashMap<>();
    private final Map<UUID, PendingCure> pendingCures = new LinkedHashMap<>();

    public static TaskboundSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public PlayerCoreState player(UUID playerId) {
        return players.computeIfAbsent(playerId, id -> {
            setDirty();
            return new PlayerCoreState(id.toString());
        });
    }

    public void replacePlayer(UUID playerId, PlayerCoreState state) {
        if (!playerId.toString().equals(state.playerId())) {
            throw new IllegalArgumentException("Player state UUID does not match its storage key");
        }
        players.put(playerId, state);
        setDirty();
    }

    public void changed() {
        setDirty();
    }

    /** Invalidates unaccepted offers and their refresh timers after definitions change. */
    public void clearGeneratedOffers() {
        players.values().forEach(PlayerCoreState::clearGeneratedOffers);
        setDirty();
    }

    public void registerBoard(UUID boardId, String dimension, BlockPos position) {
        BoardRecord next = new BoardRecord(boardId, dimension, position.immutable());
        if (!next.equals(boards.put(boardId, next))) {
            setDirty();
        }
    }

    public void unregisterBoard(UUID boardId) {
        if (boards.remove(boardId) != null) {
            setDirty();
        }
    }

    public Optional<BoardRecord> board(UUID boardId) {
        return Optional.ofNullable(boards.get(boardId));
    }

    public Optional<BoardRecord> destinationFor(UUID originBoardId, String dimension, BlockPos origin) {
        return destinationFor(originBoardId, dimension, origin, 0.0D, Double.POSITIVE_INFINITY);
    }

    public Optional<BoardRecord> destinationFor(
            UUID originBoardId,
            String dimension,
            BlockPos origin,
            double minimumDistance,
            double maximumDistance
    ) {
        double minimumDistanceSquared = minimumDistance * minimumDistance;
        double maximumDistanceSquared = maximumDistance * maximumDistance;
        return boards.values().stream()
                .filter(board -> !board.boardId().equals(originBoardId))
                .filter(board -> board.dimension().equals(dimension))
                .filter(board -> {
                    double distanceSquared = board.position().distSqr(origin);
                    return distanceSquared >= minimumDistanceSquared && distanceSquared <= maximumDistanceSquared;
                })
                .min(java.util.Comparator.comparingDouble(board -> board.position().distSqr(origin)));
    }

    public void rememberPendingCure(UUID zombieId, UUID playerId, String taskId) {
        pendingCures.put(zombieId, new PendingCure(playerId, taskId));
        setDirty();
    }

    public PendingCure takePendingCure(UUID zombieId) {
        PendingCure result = pendingCures.remove(zombieId);
        if (result != null) {
            setDirty();
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerList = new ListTag();
        players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (!entry.getKey().toString().equals(entry.getValue().playerId())) {
                throw new IllegalStateException("Player state UUID does not match its storage key");
            }
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("player_id", entry.getKey());
            Map<String, Object> stateMap = entry.getValue().toMap();
            playerTag.putString("reputation", GSON.toJson(stateMap.get("reputation")));
            putJsonRecords(playerTag, "selection_histories", stateMap.get("selection_histories"));
            putJsonRecords(playerTag, "generated_offers", stateMap.get("generated_offers"));
            putJsonRecords(playerTag, "active_tasks", stateMap.get("active_tasks"));
            putLongMap(playerTag, "completed_templates", stateMap.get("completed_templates"));
            putLongMap(playerTag, "offer_refresh_times", stateMap.get("offer_refresh_times"));
            ListTag claimedRewards = new ListTag();
            for (Object reward : (List<?>) stateMap.get("claimed_level_rewards")) {
                claimedRewards.add(StringTag.valueOf((String) reward));
            }
            playerTag.put("claimed_level_rewards", claimedRewards);
            ListTag seenOffers = new ListTag();
            for (Object offer : (List<?>) stateMap.get("seen_offer_ids")) seenOffers.add(StringTag.valueOf((String) offer));
            playerTag.put("seen_offer_ids", seenOffers);
            playerTag.putBoolean("journal_granted", entry.getValue().journalGranted());
            playerList.add(playerTag);
        });
        tag.put("players", playerList);
        ListTag boardList = new ListTag();
        boards.values().stream().sorted(java.util.Comparator.comparing(BoardRecord::boardId)).forEach(board -> {
            CompoundTag boardTag = new CompoundTag();
            boardTag.putUUID("board_id", board.boardId());
            boardTag.putString("dimension", board.dimension());
            boardTag.putInt("x", board.position().getX());
            boardTag.putInt("y", board.position().getY());
            boardTag.putInt("z", board.position().getZ());
            boardList.add(boardTag);
        });
        tag.put("boards", boardList);

        ListTag cureList = new ListTag();
        pendingCures.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag cureTag = new CompoundTag();
            cureTag.putUUID("zombie_id", entry.getKey());
            cureTag.putUUID("player_id", entry.getValue().playerId());
            cureTag.putString("task_id", entry.getValue().taskId());
            cureList.add(cureTag);
        });
        tag.put("pending_cures", cureList);
        return tag;
    }

    private static TaskboundSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TaskboundSavedData result = new TaskboundSavedData();
        ListTag playerList = tag.getList("players", Tag.TAG_COMPOUND);
        for (Tag value : playerList) {
            CompoundTag playerTag = (CompoundTag) value;
            try {
                if (!playerTag.hasUUID("player_id")) {
                    throw new IllegalArgumentException("Stored player state has no valid UUID");
                }
                UUID playerId = playerTag.getUUID("player_id");
                Map<String, Object> stateMap = readPlayerState(playerTag);
                PlayerCoreState state = PlayerCoreState.fromMap(stateMap);
                if (!playerId.toString().equals(state.playerId())) {
                    throw new IllegalArgumentException("Stored UUID does not match serialized player state");
                }
                result.players.put(playerId, state);
            } catch (RuntimeException exception) {
                TaskboundMod.LOGGER.error("Skipping corrupt Taskbound player state", exception);
            }
        }
        for (Tag value : tag.getList("boards", Tag.TAG_COMPOUND)) {
            CompoundTag boardTag = (CompoundTag) value;
            if (boardTag.hasUUID("board_id")) {
                UUID boardId = boardTag.getUUID("board_id");
                result.boards.put(boardId, new BoardRecord(
                        boardId,
                        boardTag.getString("dimension"),
                        new BlockPos(boardTag.getInt("x"), boardTag.getInt("y"), boardTag.getInt("z"))
                ));
            }
        }
        for (Tag value : tag.getList("pending_cures", Tag.TAG_COMPOUND)) {
            CompoundTag cureTag = (CompoundTag) value;
            if (cureTag.hasUUID("zombie_id") && cureTag.hasUUID("player_id")) {
                result.pendingCures.put(
                        cureTag.getUUID("zombie_id"),
                        new PendingCure(cureTag.getUUID("player_id"), cureTag.getString("task_id"))
                );
            }
        }
        return result;
    }

    private static void putJsonRecords(CompoundTag parent, String key, Object value) {
        if (!(value instanceof List<?> records)) {
            throw new IllegalArgumentException("Player state field " + key + " must be a list");
        }
        ListTag result = new ListTag();
        for (Object record : records) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.putString("json", GSON.toJson(record));
            result.add(recordTag);
        }
        parent.put(key, result);
    }

    private static Map<String, Object> readPlayerState(CompoundTag playerTag) {
        if (!playerTag.contains("reputation", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Stored player state has no reputation data");
        }
        Map<String, Object> stateMap = new LinkedHashMap<>();
        stateMap.put("reputation", readJsonMap(playerTag.getString("reputation"), "reputation"));
        stateMap.put("selection_histories", readJsonRecords(playerTag, "selection_histories"));
        stateMap.put("generated_offers", readJsonRecords(playerTag, "generated_offers"));
        stateMap.put("active_tasks", readJsonRecords(playerTag, "active_tasks"));
        stateMap.put("completed_templates", readLongMap(playerTag, "completed_templates"));
        stateMap.put("offer_refresh_times", readLongMap(playerTag, "offer_refresh_times"));
        if (playerTag.contains("claimed_level_rewards") && !playerTag.contains("claimed_level_rewards", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Stored claimed_level_rewards must be a list");
        }
        List<String> claimedRewards = new ArrayList<>();
        for (Tag reward : playerTag.getList("claimed_level_rewards", Tag.TAG_STRING)) {
            claimedRewards.add(reward.getAsString());
        }
        stateMap.put("claimed_level_rewards", claimedRewards);
        if (playerTag.contains("seen_offer_ids") && !playerTag.contains("seen_offer_ids", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Stored seen_offer_ids must be a list");
        }
        List<String> seenOffers = new ArrayList<>();
        for (Tag offer : playerTag.getList("seen_offer_ids", Tag.TAG_STRING)) seenOffers.add(offer.getAsString());
        stateMap.put("seen_offer_ids", seenOffers);
        stateMap.put("journal_granted", playerTag.getBoolean("journal_granted"));
        return stateMap;
    }

    private static void putLongMap(CompoundTag parent, String key, Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("Player state field " + key + " must be a map");
        }
        CompoundTag result = new CompoundTag();
        values.forEach((id, timestamp) -> result.putLong((String) id, ((Number) timestamp).longValue()));
        parent.put(key, result);
    }

    private static Map<String, Long> readLongMap(CompoundTag parent, String key) {
        // An omitted optional ledger starts empty; malformed stored data is rejected.
        if (parent.contains(key) && !parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Stored " + key + " must be a compound");
        }
        CompoundTag stored = parent.getCompound(key);
        Map<String, Long> result = new LinkedHashMap<>();
        for (String id : stored.getAllKeys()) {
            if (!stored.contains(id, Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Stored " + key + " timestamp must be a long");
            }
            result.put(id, stored.getLong(id));
        }
        return result;
    }

    private static List<Map<String, Object>> readJsonRecords(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Stored player state has no " + key + " list");
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Tag value : parent.getList(key, Tag.TAG_COMPOUND)) {
            CompoundTag recordTag = (CompoundTag) value;
            if (!recordTag.contains("json", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Stored " + key + " entry has no JSON payload");
            }
            records.add(readJsonMap(recordTag.getString("json"), key + " entry"));
        }
        return records;
    }

    private static Map<String, Object> readJsonMap(String json, String description) {
        Map<String, Object> result = GSON.fromJson(json, MAP_TYPE);
        if (result == null) {
            throw new IllegalArgumentException("Stored " + description + " is null");
        }
        return result;
    }

    public record BoardRecord(UUID boardId, String dimension, BlockPos position) {
    }

    public record PendingCure(UUID playerId, String taskId) {
    }
}
