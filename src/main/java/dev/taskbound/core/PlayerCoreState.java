package dev.taskbound.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Serializable aggregate for reputation, per-giver selection histories, active tasks, and progress. */
public final class PlayerCoreState {
    private final ReputationLedger reputation;
    private final Map<String, SelectionHistory> historiesByGiver;
    private final Map<String, GeneratedTask> generatedOffers;
    private final Map<String, ActiveTask> activeTasks;
    private final Map<String, Long> completedTemplates = new LinkedHashMap<>();
    private final Map<String, Long> offerRefreshTimes = new LinkedHashMap<>();
    private final java.util.Set<String> seenOfferIds = new java.util.LinkedHashSet<>();
    private final java.util.Set<String> claimedLevelRewards = new java.util.LinkedHashSet<>();
    private boolean journalGranted;

    public PlayerCoreState(String playerId) {
        this(new ReputationLedger(playerId), Map.of(), Map.of(), Map.of());
    }

    public PlayerCoreState(
            ReputationLedger reputation,
            Map<String, SelectionHistory> historiesByGiver,
            Map<String, GeneratedTask> generatedOffers,
            Map<String, ActiveTask> activeTasks
    ) {
        this.reputation = reputation;
        this.historiesByGiver = new LinkedHashMap<>(historiesByGiver);
        this.generatedOffers = new LinkedHashMap<>(generatedOffers);
        this.activeTasks = new LinkedHashMap<>(activeTasks);
        validateScope();
    }

    public String playerId() {
        return reputation.playerId();
    }

    public ReputationLedger reputation() {
        return reputation;
    }

    public SelectionHistory historyFor(String giverId) {
        return historiesByGiver.computeIfAbsent(giverId, ignored -> new SelectionHistory(playerId(), giverId));
    }

    public Map<String, SelectionHistory> historiesByGiver() {
        return Map.copyOf(historiesByGiver);
    }

    public SelectionHistory resetHistory(String giverId) {
        historiesByGiver.remove(giverId);
        return historyFor(giverId);
    }

    public void putGeneratedOffer(GeneratedTask offer) {
        if (!offer.playerId().equals(playerId()) || offer.status() != TaskStatus.OFFERED) {
            throw new IllegalArgumentException("Generated offer must belong to this player and have OFFERED status");
        }
        generatedOffers.put(offer.taskId(), offer);
    }

    public GeneratedTask generatedOffer(String taskId) {
        return generatedOffers.get(taskId);
    }

    public Map<String, GeneratedTask> generatedOffers() {
        return Map.copyOf(generatedOffers);
    }

    public GeneratedTask removeGeneratedOffer(String taskId) {
        seenOfferIds.remove(taskId);
        return generatedOffers.remove(taskId);
    }

    public void clearGeneratedOffers() {
        generatedOffers.clear();
        offerRefreshTimes.clear();
        seenOfferIds.clear();
    }

    /** A guild's level changes its offer choices; accepted tasks and other guilds are unaffected. */
    public void clearGeneratedOffers(String giverId) {
        generatedOffers.values().removeIf(offer -> offer.giverId().equals(giverId));
        offerRefreshTimes.keySet().removeIf(key -> key.endsWith("|" + giverId));
        seenOfferIds.retainAll(generatedOffers.keySet());
    }

    public void replaceOffers(String boardId, String giverId, java.util.List<GeneratedTask> offers, long now) {
        if (now < 0 || offers.stream().anyMatch(offer -> !offer.boardId().equals(boardId) || !offer.giverId().equals(giverId))) {
            throw new IllegalArgumentException("Replacement offers must match their board and guild");
        }
        generatedOffers.values().removeIf(offer -> offer.boardId().equals(boardId) && offer.giverId().equals(giverId));
        offers.forEach(this::putGeneratedOffer);
        seenOfferIds.retainAll(generatedOffers.keySet());
        offerRefreshTimes.put(boardId + "|" + giverId, now);
    }

    public boolean isOfferSeen(String taskId) { return seenOfferIds.contains(taskId); }

    public java.util.List<String> unseenOfferIds(String boardId, String giverId) {
        return generatedOffers.values().stream()
                .filter(offer -> offer.boardId().equals(boardId) && offer.giverId().equals(giverId))
                .map(GeneratedTask::taskId).filter(id -> !seenOfferIds.contains(id)).sorted().toList();
    }

    /** Only acknowledge still-current offers belonging to this player and requested board/guild. */
    public boolean markOffersSeen(String boardId, String giverId, java.util.List<String> taskIds) {
        boolean changed = false;
        for (String id : taskIds) {
            GeneratedTask offer = generatedOffers.get(id);
            if (offer != null && offer.boardId().equals(boardId) && offer.giverId().equals(giverId)) {
                changed |= seenOfferIds.add(id);
            }
        }
        return changed;
    }

    public long lastOfferRefresh(String boardId, String giverId) {
        return offerRefreshTimes.getOrDefault(boardId + "|" + giverId, -1L);
    }

    public boolean offersNeedRefresh(String boardId, String giverId, long now, int interval) {
        long previous = lastOfferRefresh(boardId, giverId);
        // A zero manual cooldown must not cause recursive refreshes during snapshot pushes.
        return previous < 0 || now - previous >= Math.max(20, interval);
    }

    public boolean canOffer(String templateId, Map<String, ?> conditions, long now, int defaultCooldown) {
        if (activeTasks.values().stream().anyMatch(active -> active.task().templateId().equals(templateId))) return false;
        Long completed = completedTemplates.get(templateId);
        if (completed == null) return true;
        if (Boolean.TRUE.equals(conditions.get("one_time"))) return false;
        long cooldown = conditions.get("cooldown_ticks") instanceof Number number ? number.longValue() : defaultCooldown;
        return now >= completed && now - completed >= Math.max(0L, cooldown);
    }

    public void recordTemplateCompletion(String templateId, long now) {
        if (now < 0) throw new IllegalArgumentException("Completion time cannot be negative");
        completedTemplates.put(templateId, now);
    }

    public boolean levelRewardClaimed(String giverId, int level) {
        return claimedLevelRewards.contains(giverId + "|" + level);
    }

    public boolean journalGranted() { return journalGranted; }

    public void markJournalGranted() { journalGranted = true; }

    public void claimLevelReward(String giverId, int level) {
        if (level < 2 || level >= 20 || level % 5 == 0) throw new IllegalArgumentException("Only intermediate levels have gifts");
        if (!claimedLevelRewards.add(giverId + "|" + level)) throw new IllegalStateException("This level reward was already claimed");
    }

    public void putActiveTask(ActiveTask activeTask) {
        if (!activeTask.task().playerId().equals(playerId())) {
            throw new IllegalArgumentException("Active task belongs to a different player");
        }
        activeTasks.put(activeTask.task().taskId(), activeTask);
    }

    public ActiveTask activeTask(String taskId) {
        return activeTasks.get(taskId);
    }

    public ActiveTask removeActiveTask(String taskId) {
        return activeTasks.remove(taskId);
    }

    public Map<String, ActiveTask> activeTasks() {
        return Map.copyOf(activeTasks);
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("reputation", reputation.toMap());
        map.put("selection_histories", historiesByGiver.values().stream()
                .sorted(Comparator.comparing(SelectionHistory::giverId))
                .map(SelectionHistory::toMap)
                .toList());
        map.put("generated_offers", generatedOffers.values().stream()
                .sorted(Comparator.comparing(GeneratedTask::taskId))
                .map(GeneratedTask::toMap)
                .toList());
        map.put("active_tasks", activeTasks.values().stream()
                .sorted(Comparator.comparing(active -> active.task().taskId()))
                .map(ActiveTask::toMap)
                .toList());
        map.put("completed_templates", Map.copyOf(completedTemplates));
        map.put("offer_refresh_times", Map.copyOf(offerRefreshTimes));
        map.put("seen_offer_ids", seenOfferIds.stream().sorted().toList());
        map.put("claimed_level_rewards", claimedLevelRewards.stream().sorted().toList());
        map.put("journal_granted", journalGranted);
        return map;
    }

    public static PlayerCoreState fromMap(Map<String, ?> map) {
        ReputationLedger reputation = ReputationLedger.fromMap(DataMaps.map(map, "reputation"));
        var histories = new LinkedHashMap<String, SelectionHistory>();
        for (Object value : DataMaps.list(map, "selection_histories")) {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Selection histories must be objects");
            }
            @SuppressWarnings("unchecked")
            SelectionHistory history = SelectionHistory.fromMap((Map<String, ?>) raw);
            if (histories.put(history.giverId(), history) != null) {
                throw new IllegalArgumentException("Duplicate history for giver " + history.giverId());
            }
        }
        var generatedOffers = new LinkedHashMap<String, GeneratedTask>();
        for (Object value : DataMaps.list(map, "generated_offers")) {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Generated offers must be objects");
            }
            @SuppressWarnings("unchecked")
            GeneratedTask offer = GeneratedTask.fromMap((Map<String, ?>) raw);
            if (generatedOffers.put(offer.taskId(), offer) != null) {
                throw new IllegalArgumentException("Duplicate generated offer " + offer.taskId());
            }
        }
        var activeTasks = new LinkedHashMap<String, ActiveTask>();
        for (Object value : DataMaps.list(map, "active_tasks")) {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Active tasks must be objects");
            }
            @SuppressWarnings("unchecked")
            ActiveTask activeTask = ActiveTask.fromMap((Map<String, ?>) raw);
            if (activeTasks.put(activeTask.task().taskId(), activeTask) != null) {
                throw new IllegalArgumentException("Duplicate active task " + activeTask.task().taskId());
            }
        }
        PlayerCoreState result = new PlayerCoreState(reputation, histories, generatedOffers, activeTasks);
        if (map.containsKey("completed_templates")) readTimes(map, "completed_templates", result.completedTemplates);
        if (map.containsKey("offer_refresh_times")) readTimes(map, "offer_refresh_times", result.offerRefreshTimes);
        if (map.containsKey("seen_offer_ids")) result.seenOfferIds.addAll(DataMaps.strings(map, "seen_offer_ids"));
        result.seenOfferIds.retainAll(generatedOffers.keySet());
        if (map.containsKey("claimed_level_rewards")) result.claimedLevelRewards.addAll(DataMaps.strings(map, "claimed_level_rewards"));
        result.journalGranted = map.containsKey("journal_granted") && DataMaps.bool(map, "journal_granted");
        return result;
    }

    private static void readTimes(Map<String, ?> map, String key, Map<String, Long> target) {
        DataMaps.map(map, key).forEach((id, value) -> {
            if (!(value instanceof Number number) || number.longValue() < 0) {
                throw new IllegalArgumentException("Invalid saved timestamp in " + key);
            }
            target.put(id, number.longValue());
        });
    }

    private void validateScope() {
        historiesByGiver.forEach((giverId, history) -> {
            if (!giverId.equals(history.giverId()) || !playerId().equals(history.playerId())) {
                throw new IllegalArgumentException("Selection history is stored under the wrong scope");
            }
        });
        generatedOffers.forEach((taskId, offer) -> {
            if (!taskId.equals(offer.taskId()) || !playerId().equals(offer.playerId())
                    || offer.status() != TaskStatus.OFFERED) {
                throw new IllegalArgumentException("Generated offer is stored under the wrong scope or status");
            }
        });
        activeTasks.forEach((taskId, active) -> {
            if (!taskId.equals(active.task().taskId()) || !playerId().equals(active.task().playerId())) {
                throw new IllegalArgumentException("Active task is stored under the wrong scope");
            }
        });
    }
}
