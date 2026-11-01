package dev.taskbound.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.taskbound.TaskboundMod;
import dev.taskbound.core.TaskCategoryDefinition;
import dev.taskbound.core.TaskGiverDefinition;
import dev.taskbound.core.TaskTemplateDefinition;
import dev.taskbound.core.RewardEntryDefinition;
import dev.taskbound.core.LevelRewardDefinition;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.loading.FMLPaths;

public final class TaskboundDefinitionReloadListener
        extends SimplePreparableReloadListener<TaskboundDefinitions> {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();
    private final Path configRoot = FMLPaths.CONFIGDIR.get().resolve("taskbound");

    @Override
    protected TaskboundDefinitions prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        try {
            TaskboundDefinitionFiles.initialize(configRoot);
        } catch (IOException exception) {
            throw new JsonParseException("Could not initialize Taskbound definitions in " + configRoot, exception);
        }
        Map<ResourceLocation, JsonElement> giverJson = scan(resourceManager, "task_givers");
        Map<ResourceLocation, JsonElement> categoryJson = scan(resourceManager, "task_categories");
        Map<ResourceLocation, JsonElement> templateJson = scan(resourceManager, "task_templates");
        Map<ResourceLocation, JsonElement> rewardJson = scan(resourceManager, "rewards");
        Map<ResourceLocation, JsonElement> levelRewardJson = scan(resourceManager, "level_rewards");

        var givers = parseById(giverJson, TaskGiverDefinition::fromMap, TaskGiverDefinition::id, "giver");
        var categories = parseList(categoryJson, TaskCategoryDefinition::fromMap, TaskCategoryDefinition::id, "category");
        var templates = parseList(templateJson, TaskTemplateDefinition::fromMap, TaskTemplateDefinition::id, "template");
        var rewards = parseList(rewardJson, RewardEntryDefinition::fromMap, RewardEntryDefinition::id, "reward");
        var levelRewards = parseList(levelRewardJson, LevelRewardDefinition::fromMap, LevelRewardDefinition::id, "level reward");
        return new TaskboundDefinitions(givers, categories, templates, rewards, levelRewards);
    }

    @Override
    protected void apply(TaskboundDefinitions definitions, ResourceManager resourceManager, ProfilerFiller profiler) {
        TaskboundDefinitionManager.replace(definitions);
        TaskboundMod.LOGGER.info(
                "Loaded {} task givers, {} categories, {} templates, and {} rewards",
                definitions.givers().size(),
                definitions.categories().size(),
                definitions.templates().size(),
                definitions.rewards().size()
        );
    }

    private Map<ResourceLocation, JsonElement> scan(ResourceManager manager, String directory) {
        var result = new LinkedHashMap<ResourceLocation, JsonElement>();
        try {
            TaskboundDefinitionFiles.read(configRoot, directory).forEach((id, json) ->
                    result.put(ResourceLocation.parse(id), json));
        } catch (IOException exception) {
            throw new JsonParseException("Could not read Taskbound " + directory + " from " + configRoot, exception);
        }
        scanPack(manager, directory, result);
        return result;
    }

    private void scanPack(ResourceManager manager, String directory, Map<ResourceLocation, JsonElement> result) {
        var converter = FileToIdConverter.json(directory);
        for (var entry : converter.listMatchingResources(manager).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonElement json = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                if (json == null || !json.isJsonObject()) throw new JsonParseException("Expected a JSON object");
                result.put(converter.fileToId(entry.getKey()), json);
            } catch (IOException | JsonParseException exception) {
                // Vanilla's scan helper logs and skips malformed files; these definitions must reload atomically.
                throw new JsonParseException("Invalid Taskbound datapack definition " + entry.getKey(), exception);
            }
        }
    }

    private static <T> Map<String, T> parseById(
            Map<ResourceLocation, JsonElement> json,
            java.util.function.Function<Map<String, ?>, T> parser,
            java.util.function.Function<T, String> id,
            String kind
    ) {
        var result = new LinkedHashMap<String, T>();
        json.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            T parsed = parse(entry.getKey(), entry.getValue(), parser, id, kind);
            if (result.put(id.apply(parsed), parsed) != null) {
                throw new JsonParseException("Duplicate " + kind + " ID " + id.apply(parsed));
            }
        });
        return Map.copyOf(result);
    }

    private static <T> List<T> parseList(
            Map<ResourceLocation, JsonElement> json,
            java.util.function.Function<Map<String, ?>, T> parser,
            java.util.function.Function<T, String> id,
            String kind
    ) {
        return new ArrayList<>(parseById(json, parser, id, kind).values());
    }

    private static <T> T parse(
            ResourceLocation resourceId,
            JsonElement json,
            java.util.function.Function<Map<String, ?>, T> parser,
            java.util.function.Function<T, String> id,
            String kind
    ) {
        try {
            Map<String, Object> map = GSON.fromJson(json, MAP_TYPE);
            T parsed = parser.apply(map);
            if (!resourceId.toString().equals(id.apply(parsed))) {
                throw new JsonParseException(
                        "The " + kind + " in " + resourceId + " declares mismatched ID " + id.apply(parsed));
            }
            return parsed;
        } catch (RuntimeException exception) {
            if (exception instanceof JsonParseException jsonException) {
                throw jsonException;
            }
            throw new JsonParseException("Invalid Taskbound " + kind + " " + resourceId, exception);
        }
    }
}
