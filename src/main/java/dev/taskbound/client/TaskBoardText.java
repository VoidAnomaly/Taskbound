package dev.taskbound.client;

import dev.taskbound.runtime.RewardCurrency;
import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedTask;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.core.GeneratedReward;
import dev.taskbound.core.TaskProgress;
import dev.taskbound.ui.BoardTaskQuery;
import dev.taskbound.ui.BoardTaskSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Presentation of immutable server task snapshots; never used to validate progress. */
final class TaskBoardText {
    private TaskBoardText() { }

    static ItemStack rewardIcon() {
        return item(RewardCurrency.itemId());
    }

    static Component taskTitle(GeneratedTask task) {
        if (task.conditions().get("title") instanceof String title) return Component.translatable(title);
        if (task.title() != null) return Component.literal(task.title());
        String key = task.displayText();
        return Component.translatable(key.endsWith(".description")
                ? key.substring(0, key.length() - ".description".length()) + ".title" : key);
    }

    static Component taskDescription(GeneratedTask task) {
        return task.description() != null ? Component.literal(task.description()) : Component.translatable(task.displayText());
    }

    static List<Component> details(GeneratedTask task, @Nullable TaskProgress progress, long gameTime) {
        var lines = new ArrayList<Component>();
        lines.addAll(descriptionLines(task, progress));
        lines.add(Component.empty());
        lines.add(Component.literal(humanize(task.difficulty().tier().name()) + " difficulty"));
        lines.add(Component.literal("Requires reputation level " + task.requiredReputationLevel()));
        lines.add(time(task, gameTime, progress != null));
        lines.add(Component.empty());
        lines.add(Component.literal("Objectives"));
        int index = 0;
        int previousPhase = -1;
        List<?> stages = task.conditions().get("objective_stages") instanceof List<?> values ? values : null;
        for (GeneratedObjective objective : task.objectives()) {
            if (stages != null) {
                int phase = ((Number)stages.get(index)).intValue();
                if (phase != previousPhase) lines.add(Component.translatable("taskbound.task.step", phase + 1));
                previousPhase = phase;
            }
            int required = requiredCount(objective);
            int current = progress == null ? 0 : Math.min(required, progress.progressFor(objective.key()));
            String count = progress == null ? " (" + required + ")" : " (" + current + " / " + required + ")";
            var marker = task.objectives().size() > 1 ? subtaskMarker(progress, objective) : Component.literal((index + 1) + ". ");
            lines.add(marker.append(objectiveDescription(objective)).append(count));
            index++;
            lines.addAll(objectiveConditions(objective));
            if (objective.type().equals("porter_route")) {
                routeDetails(lines, objective, progress);
            }
        }
        if (Boolean.TRUE.equals(task.conditions().get("strict_sequence"))
                || "strict".equals(task.conditions().get("objective_order"))) {
            lines.add(Component.literal("Complete objectives in order."));
        }
        if (stages != null) lines.add(Component.translatable("taskbound.task.stages"));
        if (Boolean.TRUE.equals(task.conditions().get("no_death"))) lines.add(Component.literal("Do not die before claiming."));
        if (Boolean.TRUE.equals(task.conditions().get("reset_on_death"))) lines.add(Component.literal("Progress resets on death."));
        lines.add(Component.empty());
        lines.add(Component.literal("Rewards"));
        for (GeneratedReward reward : task.rewards().rewards()) lines.add(rewardDescription(reward));
        lines.add(Component.literal("+" + task.reputationReward() + " guild reputation"));
        if (Boolean.TRUE.equals(task.conditions().get("one_time"))
                || "one_time_per_player".equals(task.conditions().get("repeatability"))) {
            lines.add(Component.translatable("taskbound.task.one_time"));
        }
        return List.copyOf(lines);
    }

    private static net.minecraft.network.chat.MutableComponent subtaskMarker(@Nullable TaskProgress progress, GeneratedObjective objective) {
        boolean complete = progress != null && progress.progressFor(objective.key()) >= requiredCount(objective);
        return Component.literal(complete ? "\u2713 " : "\u2022 ");
    }

    /** Shipped multi-step descriptions have one bullet per objective; prose rules remain unmarked. */
    static List<Component> descriptionLines(GeneratedTask task, @Nullable TaskProgress progress) {
        Component description = taskDescription(task);
        String[] text = description.getString().split("\\R", -1);
        long bulletCount = java.util.Arrays.stream(text).filter(line -> line.startsWith("\u2022 ")).count();
        if (task.objectives().size() < 2 || bulletCount != task.objectives().size()) return List.of(description);
        var lines = new ArrayList<Component>();
        int index = 0;
        for (String line : text) {
            if (line.startsWith("\u2022 ")) lines.add(subtaskMarker(progress, task.objectives().get(index++)).append(line.substring(2)));
            else lines.add(Component.literal(line));
        }
        return List.copyOf(lines);
    }

    static Component objectiveDescription(GeneratedObjective objective) {
        Map<String, Object> p = objective.parameters();
        if (p.get("description") instanceof String description) return Component.translatable(description);
        if (p.get("display_text") instanceof String description) return Component.translatable(description);
        return switch (objective.type()) {
            case "kill_entity", "tracked_kill" -> Component.literal("Defeat ").append(namedEntity(string(p, "entity", string(p, "entity_tag", "targets"))));
            case "catch_fish", "catch_item" -> Component.literal("Catch ").append(namedItem(string(p, "fish_item", string(p, "fish_item_tag", string(p, "species_id", "fish")))));
            case "craft_item", "tracked_craft" -> Component.literal("Craft ").append(namedItem(string(p, "item", string(p, "target", "items"))));
            case "gather_block", "tracked_gather", "break_block", "mine_block" -> Component.literal("Mine ").append(namedResource(string(p, "block", string(p, "target", "blocks"))));
            case "harvest_crop", "harvest_block" -> Component.literal("Harvest ").append(namedResource(string(p, "block", string(p, "target", "crops"))));
            case "hand_in_item", "handin_item", "submit_item" -> Component.literal("Hand in ").append(namedItem(string(p, "item", string(p, "item_tag", string(p, "target", "items")))));
            case "farm_action" -> farmObjective(p);
            case "starcatcher_catch" -> starcatcherObjective(p);
            case "porter_route" -> Component.translatable("taskbound.objective.porter_route");
            case "visit_biome" -> Component.literal("Visit ").append(namedResource(string(p, "biome", "a biome")));
            case "visit_structure" -> Component.literal("Explore ").append(namedResource(string(p, "structure", "a structure")));
            case "visit_dimension" -> Component.literal("Enter ").append(namedResource(string(p, "dimension", "a dimension")));
            case "enchant_item", "tracked_enchant" -> Component.literal("Enchant ").append(namedItem(string(p, "item", "equipment")));
            case "advancement" -> Component.literal("Complete ").append(namedResource(string(p, "advancement", "the advancement")));
            case "challenge_action" -> Component.literal(humanize(string(p, "action", "Complete the challenge")));
            case "deliver_package" -> Component.literal("Deliver the sealed package");
            case "perform_magic_action" -> magicObjective(p);
            case "tracked_fish" -> Boolean.TRUE.equals(p.get("treasure"))
                    ? Component.translatable("taskbound.objective.fishing_treasure")
                    : Component.translatable("taskbound.objective.catch", namedItem(string(p, "fish_item", string(p, "fish_item_tag", "fish"))));
            case "brew_potion" -> Component.translatable("taskbound.objective.brew", potionItem(p));
            case "harvest_berries" -> Component.translatable("taskbound.objective.berries");
            case "place_crafted" -> Component.translatable("taskbound.objective.place_crafted", namedItem(string(p, "item", "block")));
            case "activate_crafter" -> Component.translatable("taskbound.objective.activate_crafter");
            case "structure_exit" -> Component.translatable("taskbound.objective.structure_exit");
            case "combat_action" -> Component.translatable("taskbound.objective." + string(p, "action", "combat"));
            case "travel_distance" -> Component.translatable("taskbound.objective.travel." + string(p, "mode", "travel"));
            case "survive_fall" -> Component.translatable("taskbound.objective.survive_fall", p.get("minimum_fall_distance"));
            case "trial_encounter" -> Component.translatable(Boolean.TRUE.equals(p.get("ominous")) ? "taskbound.objective.ominous_trial" : "taskbound.objective.trial");
            case "dragon_encounter" -> Component.translatable("taskbound.objective." + string(p, "action", "dragon"));
            default -> Component.literal(humanize(objective.type()));
        };
    }

    private static Component potionItem(Map<String, Object> parameters) {
        ItemStack stack = item(string(parameters, "item", "minecraft:potion"));
        var level = net.minecraft.client.Minecraft.getInstance().level;
        ResourceLocation id = ResourceLocation.tryParse(string(parameters, "potion", "minecraft:water"));
        if (level != null && id != null) {
            level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.POTION).getHolder(id)
                    .ifPresent(holder -> stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                            new net.minecraft.world.item.alchemy.PotionContents(holder)));
        }
        return stack.getHoverName();
    }

    private static Component farmObjective(Map<String, Object> p) {
        String action = string(p, "action", "harvest");
        Component target = action.equals("breed") ? namedEntity(string(p, "entity", "animals"))
                : namedItem(string(p, "item", "crops"));
        return Component.translatable("taskbound.objective.farm." + action, target);
    }

    private static Component starcatcherObjective(Map<String, Object> p) {
        if (p.get("species") instanceof List<?> species && !species.isEmpty()) {
            var names = Component.empty();
            for (Object id : species) {
                if (!names.getString().isEmpty()) names.append(Component.translatable("taskbound.list.or"));
                String value = id.toString();
                names.append(Component.translatableWithFallback("fish." + value.replace(':', '.'), humanize(value)));
            }
            return Component.translatable("taskbound.objective.catch", names);
        }
        return Component.translatable("taskbound.objective.starcatcher." + string(p, "count_mode", "catches"));
    }

    private static void routeDetails(List<Component> lines, GeneratedObjective objective, @Nullable TaskProgress progress) {
        if (!(objective.parameters().get("route") instanceof List<?> stops)) return;
        int completed = progress == null ? 0 : progress.progressFor(objective.key());
        for (int i = 0; i < stops.size(); i++) {
            if (!(stops.get(i) instanceof Map<?, ?> stop)) continue;
            String marker = i < completed ? "\u2713 " : "\u2022 ";
            lines.add(Component.literal(marker).append(Component.translatable("taskbound.porter.route_stop", i + 1,
                    namedResource(String.valueOf(stop.get("dimension"))), integer(stop.get("x"), 0),
                    integer(stop.get("y"), 0), integer(stop.get("z"), 0))));
            for (String action : List.of("deliver", "collect", "carry")) {
                if (stop.get(action) instanceof List<?> cargo && !cargo.isEmpty())
                    lines.add(Component.translatable("taskbound.porter.route_" + action, cargo.size()));
            }
        }
        if (progress != null && objective.parameters().get("maximum_damage") instanceof Number limit) {
            Object taken = progress.additionalData().get("porter_damage");
            double remaining = Math.max(0, limit.doubleValue() - (taken instanceof Number n ? n.doubleValue() : 0));
            lines.add(Component.translatable("taskbound.porter.damage_remaining", String.format(Locale.ROOT, "%.1f", remaining / 2)));
        }
        lines.add(Component.translatable("taskbound.porter.final_payout"));
    }

    private static Component magicObjective(Map<String, Object> p) {
        return switch (string(p, "action", "magic")) {
            case "brew_potion" -> Component.literal("Brew ").append(namedResource(string(p, "potion", "a potion")));
            case "drink_potion" -> Component.literal("Drink ").append(namedResource(string(p, "potion", "a potion")));
            case "apply_effect" -> Component.literal("Apply ").append(namedResource(string(p, "effect", "an effect")))
                    .append(" to ").append(namedEntity(string(p, "target_entity", "the target")));
            case "cure_entity" -> Component.literal("Cure ").append(namedEntity(string(p, "target_entity", "a zombie villager")));
            case "cast_spell" -> Component.literal("Cast ").append(namedResource(string(p, "spell_id", "a spell")));
            default -> Component.literal(humanize(string(p, "action", "magical action")));
        };
    }

    static List<Component> objectiveConditions(GeneratedObjective objective) {
        Map<String, Object> p = objective.parameters();
        var lines = new ArrayList<Component>();
        add(lines, p, "required_dimension", "Dimension");
        add(lines, p, "required_biome", "Biome");
        add(lines, p, "required_biome_tag", "Biome");
        add(lines, p, "required_structure", "Structure");
        add(lines, p, "required_structure_tag", "Structure");
        add(lines, p, "required_weapon", "Weapon");
        add(lines, p, "required_weapon_tag", "Weapon");
        for (String key : List.of("required_damage_type", "required_damage_type_tag")) {
            if (p.get(key) instanceof String value) {
                lines.add(Component.translatable("taskbound.condition." + key, namedResource(value)));
            }
        }
        add(lines, p, "required_tool", "Tool");
        add(lines, p, "required_tool_tag", "Tool");
        add(lines, p, "required_weather", "Weather");
        add(lines, p, "required_time", "Time");
        add(lines, p, "required_effect", "Effect");
        add(lines, p, "required_enchantment", "Enchantment");
        add(lines, p, "tool_enchantment", "Tool enchantment");
        add(lines, p, "method", "Method");
        if (p.get("minimum_enchantment_level") instanceof Number n) lines.add(Component.literal("  Enchantment level: " + n.intValue() + "+"));
        if (p.get("minimum_tool_enchantment_level") instanceof Number n) lines.add(Component.literal("  Tool enchantment level: " + n.intValue() + "+"));
        if (p.get("minimum_effect_amplifier") instanceof Number n) lines.add(Component.literal("  Effect level: " + (n.intValue() + 1) + "+"));
        if (p.get("min_y") instanceof Number n) lines.add(Component.literal("  Minimum height: " + n.intValue()));
        if (p.get("max_y") instanceof Number n) lines.add(Component.literal("  Maximum height: " + n.intValue()));
        if (p.get("minimum_y") instanceof Number n) lines.add(Component.literal("  Minimum height: " + n.intValue()));
        if (p.get("maximum_y") instanceof Number n) lines.add(Component.literal("  Maximum height: " + n.intValue()));
        if (p.get("max_health") instanceof Number n) lines.add(Component.literal("  At most " + n + " health points"));
        if (p.get("minimum_distance") instanceof Number n) lines.add(Component.literal("  Minimum distance: " + n + " blocks"));
        if (Boolean.TRUE.equals(p.get("no_death"))) lines.add(Component.literal("  Do not die."));
        for (String flag : List.of("same_rod", "same_weapon", "natural", "no_healing", "no_potions", "no_totem", "no_death_after_start", "prevent_departure", "distinct_spawners")) {
            if (Boolean.TRUE.equals(p.get(flag))) lines.add(Component.translatable("taskbound.condition." + flag));
        }
        if (p.containsKey("provenance_stage")) lines.add(Component.translatable("taskbound.condition.provenance"));
        if (p.containsKey("potion") && !objective.type().equals("brew_potion")) lines.add(potionItem(p));
        if (p.get("required_enchantments") instanceof Map<?, ?> enchantments) enchantments.forEach((key, value) ->
                lines.add(Component.literal("  ").append(namedResource(key.toString())).append(" " + ((Number)value).intValue() + "+")));
        if (p.containsKey("armor_set")) lines.add(Component.translatable("taskbound.condition.iron_armor"));
        if (p.containsKey("weapons")) lines.add(Component.translatable("taskbound.condition.iron_weapons"));
        for (String flag : List.of("distinct", "perfect", "on_foot", "different_biomes")) {
            if (Boolean.TRUE.equals(p.get(flag))) lines.add(Component.translatable("taskbound.condition." + flag));
        }
        for (String value : List.of("rarity", "minimum_rarity", "station", "arrival_time")) {
            if (p.get(value) instanceof String text) lines.add(Component.translatable("taskbound.condition." + value, namedResource(text)));
        }
        for (String value : List.of("minimum_colors", "minimum_weight_percentile")) {
            if (p.get(value) instanceof Number n) lines.add(Component.translatable("taskbound.condition." + value, n.intValue()));
        }
        if (p.get("minimum_crop_types") instanceof Number types && p.get("per_crop_count") instanceof Number count)
            lines.add(Component.translatable("taskbound.condition.crop_variety", types.intValue(), count.intValue()));
        if (p.get("habitats") instanceof List<?> habitats) for (Object habitat : habitats)
            lines.add(Component.translatable("taskbound.condition.habitat", namedResource(habitat.toString())));
        if (p.get("maximum_damage") instanceof Number limit)
            lines.add(Component.translatable("taskbound.condition.maximum_damage", limit.doubleValue() / 2));
        if (objective.type().equals("porter_route")) {
            if (!Boolean.TRUE.equals(p.get("teleportation_allowed"))) lines.add(Component.translatable("taskbound.condition.no_teleport"));
            if (!Boolean.TRUE.equals(p.get("dimensional_travel_allowed"))) lines.add(Component.translatable("taskbound.condition.no_portals"));
        }
        return List.copyOf(lines);
    }

    private static void add(List<Component> lines, Map<String, Object> parameters, String key, String label) {
        if (parameters.get(key) instanceof String value) {
            lines.add(Component.literal("  " + label + ": ").append(namedResource(value)));
        }
    }

    static Component location(GeneratedTask task) {
        for (GeneratedObjective objective : task.objectives()) {
            Map<String, Object> p = objective.parameters();
            for (String key : List.of("required_biome", "required_biome_tag", "required_structure", "required_structure_tag", "biome", "structure", "required_dimension", "dimension")) {
                if (p.get(key) instanceof String value) return namedResource(value);
            }
        }
        return Component.literal("Any location");
    }

    static BoardTaskSearch.Document searchDocument(GeneratedTask task, Component guild) {
        var locations = new ArrayList<String>();
        var objectives = new ArrayList<String>();
        var rewards = new ArrayList<String>();
        for (var objective : task.objectives()) {
            for (String key : BoardTaskSearch.LOCATION_KEYS) {
                if (objective.parameters().get(key) instanceof String value) locations.add(namedResource(value).getString());
            }
            objectives.add(objectiveDescription(objective).getString());
            objectiveConditions(objective).forEach(line -> objectives.add(line.getString()));
        }
        for (var reward : task.rewards().rewards()) {
            rewards.add(rewardDescription(reward).getString());
            if (reward.payload().get("item") instanceof String item) rewards.add(namedItem(item).getString());
        }
        return BoardTaskSearch.document(task, taskTitle(task).getString(), Map.of(
                "text", List.of(taskDescription(task).getString()),
                "location", locations, "objective", objectives, "reward", rewards, "guild", List.of(guild.getString())));
    }

    static Component time(GeneratedTask task, long gameTime) {
        return time(task, gameTime, true);
    }

    static Component time(GeneratedTask task, long gameTime, boolean accepted) {
        if (accepted && task.status() == dev.taskbound.core.TaskStatus.COMPLETED && task.expiresAt() != null) {
            return Component.translatable("taskbound.time.completed");
        }
        return switch (BoardTaskQuery.timing(task)) {
            case TIMED -> Component.literal(formatTicks(BoardTaskQuery.durationOrRemainingTicks(task, accepted, gameTime)) + (accepted ? " remaining" : " duration"));
            case DAY -> Component.literal("Daytime");
            case NIGHT -> Component.literal("Nighttime");
            case UNLIMITED -> Component.literal("No time limit");
        };
    }

    static String stars(GeneratedTask task) {
        int filled = task.difficulty().tier().ordinal() + 1;
        return "★".repeat(filled) + "☆".repeat(5 - filled);
    }

    static Component progressLabel(ActiveTask active) {
        var counts = dev.taskbound.ui.BoardTaskPresentation.progressCounts(active);
        return Component.literal(counts.completed() + " / " + counts.required() + " • " + humanize(active.task().status().name()));
    }

    static Component rewardDescription(GeneratedReward reward) {
        Map<String, Object> payload = reward.payload();
        return switch (reward.type()) {
            case ITEM -> Component.literal((long) reward.count() * integer(payload.get("count"), 1) + " × ")
                    .append(namedItem(string(payload, "item", reward.entryId())));
            case EXPERIENCE -> Component.literal("+" + (long) reward.count()
                    * integer(payload.get("experience_points"), integer(payload.get("amount"), 1)) + " experience");
            case REPUTATION -> Component.literal("+" + (long) reward.count() * integer(payload.get("points"), 1) + " reputation");
            case LOOT_TABLE -> Component.literal(reward.count() + " × ").append(namedResource(string(payload, "loot_table", reward.entryId())));
            case CALLBACK -> namedResource(reward.entryId());
        };
    }

    static ItemStack item(String idText) {
        ResourceLocation id = ResourceLocation.tryParse(idText);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : new ItemStack(Items.PAPER);
    }

    static ItemStack icon(GeneratedTask task) {
        if (task.conditions().get("icon") instanceof String value) return item(value);
        GeneratedObjective first = task.objectives().getFirst();
        Map<String, Object> p = first.parameters();
        for (String key : List.of("item", "fish_item", "block", "potion_item")) {
            if (p.get(key) instanceof String value) {
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return item(value);
            }
        }
        return item(switch (first.type()) {
            case "kill_entity", "tracked_kill", "combat_action", "dragon_encounter" -> "minecraft:iron_sword";
            case "catch_fish", "catch_item", "tracked_fish", "starcatcher_catch" -> "minecraft:fishing_rod";
            case "farm_action" -> switch (string(p, "action", "harvest")) {
                case "milk" -> "minecraft:milk_bucket";
                case "shear" -> "minecraft:shears";
                case "honey", "honeycomb" -> "minecraft:honeycomb";
                default -> "minecraft:wheat";
            };
            case "porter_route" -> "taskbound:task_package";
            case "perform_magic_action", "enchant_item" -> "minecraft:enchanted_book";
            case "craft_item" -> "minecraft:crafting_table";
            case "gather_block", "break_block", "mine_block" -> "minecraft:iron_pickaxe";
            case "visit_biome", "visit_structure", "visit_dimension" -> "minecraft:compass";
            case "challenge_action", "trial_encounter", "survive_fall" -> "minecraft:shield";
            case "travel_distance", "structure_exit" -> "minecraft:compass";
            case "harvest_berries" -> "minecraft:glow_berries";
            case "activate_crafter" -> "minecraft:crafter";
            case "deliver_package" -> "minecraft:bundle";
            default -> "minecraft:paper";
        });
    }

    static Component namedItem(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return item(value).getHoverName();
        return namedResource(value);
    }

    private static Component namedEntity(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
        return namedResource(value);
    }

    static Component namedResource(String value) { return Component.literal(humanize(value)); }

    static String humanize(String value) {
        String cleaned = value;
        int separator = cleaned.indexOf(':');
        if (separator >= 0 && separator + 1 < cleaned.length()) cleaned = cleaned.substring(separator + 1);
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < cleaned.length()) cleaned = cleaned.substring(slash + 1);
        cleaned = cleaned.replace('_', ' ').replace('-', ' ');
        StringBuilder result = new StringBuilder(cleaned.length());
        boolean capitalize = true;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            result.append(capitalize ? Character.toUpperCase(c) : Character.toLowerCase(c));
            capitalize = Character.isWhitespace(c);
        }
        return result.toString();
    }

    static int requiredCount(GeneratedObjective objective) {
        return dev.taskbound.ui.BoardTaskPresentation.requiredCount(objective);
    }

    static Component elide(Font font, Component text, int width) {
        if (font.width(text) <= width) return text;
        return Component.literal(font.substrByWidth(text, Math.max(0, width - font.width(Component.literal("…").withStyle(text.getStyle())))).getString() + "…")
                .withStyle(text.getStyle());
    }

    private static int integer(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static String string(Map<String, Object> map, String key, String fallback) {
        return map.get(key) instanceof String value && !value.isBlank() ? value : fallback;
    }

    static String formatTicks(long ticks) {
        long seconds = (ticks + 19) / 20;
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }
}
