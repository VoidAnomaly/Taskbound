package dev.taskbound.core;

import java.util.List;
import java.util.Map;

/** Explicit catalog payments separate consumed goods and travel effort from the displayed difficulty tier. */
public final class CatalogRewards {
    private CatalogRewards() { }

    public static RewardBundle apply(GeneratedTaskDraft draft, TaskGiverDefinition giver, RewardBundle fallback) {
        if (!(draft.conditions().get("currency_reward") instanceof Number amount)) return fallback;
        double payment = amount.doubleValue();
        if (draft.conditions().get("currency_per_1024_blocks") instanceof Number rate
                && draft.conditions().get("porter_route_distance") instanceof Number distance) {
            payment += Math.floor(Math.min(8192.0, Math.max(0.0, distance.doubleValue())) / 1024.0) * rate.doubleValue();
        }
        if (!Double.isFinite(payment) || payment < 1 || payment > 4096)
            throw new IllegalArgumentException("Catalog currency payment is outside 1..4096");
        long scaled = Math.round(payment * giver.rewardMultiplier());
        if (scaled > 4096) throw new IllegalArgumentException("Scaled catalog currency payment exceeds 4096");
        if (scaled == 0) return new RewardBundle(0, 0, List.of());
        int count = (int) scaled;
        return new RewardBundle(count, count, List.of(new GeneratedReward("taskbound:catalog_payment",
                RewardType.ITEM, 1, count, Map.of("item", "minecraft:emerald", "count", count))));
    }
}
