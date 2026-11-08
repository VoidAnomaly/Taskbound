package dev.taskbound.core;

import java.util.Map;
import java.util.Set;

/** Optional default catalogs change new offers without rewriting accepted tasks or custom definitions. */
public final class CatalogProfiles {
    public static final Set<String> PROFILES = Set.of("always", "vanilla_angler", "starcatcher",
            "farmer", "farmer_base", "farmersdelight", "porter", "retired_gathering");

    private CatalogProfiles() { }

    public static boolean eligible(String templateId, Map<String, ?> conditions, Set<String> loadedMods) {
        String profile = conditions.get("catalog_profile") instanceof String text ? text : inferredProfile(templateId, conditions);
        return switch (profile) {
            case "vanilla_angler" -> !loadedMods.contains("starcatcher");
            case "starcatcher" -> loadedMods.contains("starcatcher");
            case "farmer_base" -> !loadedMods.contains("farmersdelight");
            case "farmersdelight" -> loadedMods.contains("farmersdelight");
            case "retired_gathering" -> false;
            case "always", "farmer", "porter" -> true;
            default -> false;
        };
    }

    private static String inferredProfile(String templateId, Map<String, ?> conditions) {
        // Released default files retain their IDs for saved tasks. Explicit custom profiles can opt out.
        if (!conditions.containsKey("source_catalog_id")) return "always";
        if (templateId.startsWith("taskbound:angler/catalog/")) return "vanilla_angler";
        // The new Farmer giver selects new categories. Explicitly retained old categories belong to the owner.
        return "always";
    }

    public static String completionKey(String templateId, Map<String, ?> conditions) {
        return conditions.get("completion_group") instanceof String group && !group.isBlank() ? group : templateId;
    }
}
