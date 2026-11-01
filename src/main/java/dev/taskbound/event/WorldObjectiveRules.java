package dev.taskbound.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Strict schemas for harvesting and the craft/place/activate task stages. */
public final class WorldObjectiveRules {
    private static final Set<String> TYPES = Set.of("tracked_gather", "harvest_berries", "tracked_craft",
            "place_crafted", "activate_crafter", "structure_exit");
    private WorldObjectiveRules() { }

    public static boolean supports(String type) { return TYPES.contains(type); }
    public static Set<String> supportedCapabilities() {
        return TYPES.stream().map(type -> "minecraft:" + type).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static void validate(String type, Map<String, ?> parameters) {
        if (!supports(type)) throw new IllegalArgumentException("Unsupported world objective " + type);
        var base = new LinkedHashMap<String, Object>(parameters);
        for (String flag : Set.of("natural", "same_structure", "guard_warden")) {
            if (!base.containsKey(flag)) continue;
            if (!(base.remove(flag) instanceof Boolean)) throw new IllegalArgumentException(flag + " must be boolean");
            if (flag.equals("natural") && !type.equals("tracked_gather")) throw new IllegalArgumentException("natural requires tracked_gather");
            if (!flag.equals("natural") && !Set.of("tracked_gather", "tracked_craft", "place_crafted").contains(type)) {
                throw new IllegalArgumentException(flag + " is only supported for tracked gathering, crafting and placement");
            }
            if (!flag.equals("natural") && !parameters.containsKey("required_structure")) {
                throw new IllegalArgumentException(flag + " requires required_structure");
            }
        }
        if (type.equals("place_crafted") || type.equals("activate_crafter")) {
            Object stage = base.remove("provenance_stage");
            if (!(stage instanceof String text) || !text.matches("objective_[0-9]+")) {
                throw new IllegalArgumentException("A crafted placement needs its provenance_stage objective key");
            }
        }
        if (type.equals("structure_exit")) {
            if (!parameters.containsKey("required_structure")) throw new IllegalArgumentException("structure_exit needs required_structure");
            if (!Set.of("required_structure", "required_count").containsAll(parameters.keySet())) {
                throw new IllegalArgumentException("structure_exit supports only required_structure and required_count");
            }
            base.put("block", "minecraft:air");
        }
        VanillaEventRules.validate(Set.of("tracked_gather", "harvest_berries", "structure_exit").contains(type)
                ? "gather_block" : "craft_item", base);
        if (type.equals("harvest_berries") && !"minecraft:cave_vines".equals(parameters.get("block"))) {
            throw new IllegalArgumentException("harvest_berries requires minecraft:cave_vines");
        }
        if (Boolean.TRUE.equals(parameters.get("natural")) && !Set.of("minecraft:calcite", "minecraft:ancient_debris",
                "minecraft:deepslate_diamond_ore", "minecraft:sculk_catalyst").contains(parameters.get("block"))) {
            throw new IllegalArgumentException("Natural-source tracking is limited to calcite, ancient debris, deepslate diamond ore and sculk catalysts");
        }
        if (Boolean.TRUE.equals(parameters.get("guard_warden")) && !Boolean.TRUE.equals(parameters.get("same_structure"))) {
            throw new IllegalArgumentException("guard_warden requires same_structure");
        }
        if (type.equals("activate_crafter") && !"minecraft:crafter".equals(parameters.get("item"))) {
            throw new IllegalArgumentException("activate_crafter requires minecraft:crafter");
        }
        if (type.equals("structure_exit") && ((Number) parameters.get("required_count")).intValue() != 1) {
            throw new IllegalArgumentException("A structure exit is a single stage");
        }
    }
}
