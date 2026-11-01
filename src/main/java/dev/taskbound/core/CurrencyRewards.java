package dev.taskbound.core;

import java.util.LinkedHashMap;

/** Converts only generated emerald payments; selection, reward budgets and other items stay intact. */
public final class CurrencyRewards {

    private CurrencyRewards() { }

    public static GeneratedTask convert(GeneratedTask task, String itemId, int itemsPerEmerald) {
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("Currency item is required");
        if (itemsPerEmerald < 1) throw new IllegalArgumentException("Items per emerald must be positive");
        if ("minecraft:emerald".equals(itemId) && itemsPerEmerald == 1) return task;
        RewardBundle original = task.rewards();
        if (original.rewards().stream().noneMatch(CurrencyRewards::isEmerald)) return task;
        var rewards = original.rewards().stream().map(reward -> {
            if (!isEmerald(reward)) return reward;
            var payload = new LinkedHashMap<>(reward.payload());
            int emeralds = payload.get("count") instanceof Number count ? count.intValue() : 1;
            int items = Math.multiplyExact(emeralds, itemsPerEmerald);
            // Validate the final grant as well as its per-entry count before publishing an offer.
            Math.multiplyExact(reward.count(), items);
            payload.put("item", itemId);
            payload.put("count", items);
            // Saved payment identity remains recognizable after the server changes currency.
            payload.put("currency", true);
            return new GeneratedReward(reward.entryId(), reward.type(), reward.count(), reward.totalCost(), payload);
        }).toList();
        return new GeneratedTask(task.taskId(), task.playerId(), task.boardId(), task.giverId(),
                task.categoryId(), task.templateId(), task.variantId(), task.displayText(), task.title(), task.description(),
                task.objectives(), task.conditions(), task.requiredReputationLevel(), task.difficulty(),
                new RewardBundle(original.budget(), original.spent(), rewards), task.reputationReward(),
                task.acceptedAt(), task.expiresAt(), task.status());
    }

    private static boolean isEmerald(GeneratedReward reward) {
        return reward.type() == RewardType.ITEM && "minecraft:emerald".equals(reward.payload().get("item"));
    }
}
