package dev.taskbound.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReputationLedger {
    private final String playerId;
    private final Map<String, PlayerReputation> reputations;

    public ReputationLedger(String playerId) {
        this(playerId, Map.of());
    }

    public ReputationLedger(String playerId, Map<String, PlayerReputation> reputations) {
        if (playerId.isBlank()) {
            throw new IllegalArgumentException("Player ID cannot be blank");
        }
        this.playerId = playerId;
        this.reputations = new LinkedHashMap<>(reputations);
        this.reputations.forEach((id, reputation) -> {
            if (!id.equals(reputation.giverId())) {
                throw new IllegalArgumentException("Reputation map key must match its giver ID");
            }
        });
    }

    public String playerId() {
        return playerId;
    }

    public PlayerReputation forGiver(String giverId) {
        return reputations.computeIfAbsent(giverId, PlayerReputation::new);
    }

    public Map<String, PlayerReputation> reputations() {
        return Map.copyOf(reputations);
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("player_id", playerId);
        map.put("reputations", reputations.values().stream()
                .sorted(java.util.Comparator.comparing(PlayerReputation::giverId))
                .map(PlayerReputation::toMap)
                .toList());
        return map;
    }

    public static ReputationLedger fromMap(Map<String, ?> map) {
        var reputations = new LinkedHashMap<String, PlayerReputation>();
        for (Object value : DataMaps.list(map, "reputations")) {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Reputation entries must be objects");
            }
            @SuppressWarnings("unchecked")
            var entry = PlayerReputation.fromMap((Map<String, ?>) raw);
            if (reputations.put(entry.giverId(), entry) != null) {
                throw new IllegalArgumentException("Duplicate reputation giver: " + entry.giverId());
            }
        }
        return new ReputationLedger(DataMaps.string(map, "player_id"), reputations);
    }
}
