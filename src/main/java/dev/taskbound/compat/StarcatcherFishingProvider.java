package dev.taskbound.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.taskbound.TaskboundMod;
import dev.taskbound.api.TaskGenerationContext;
import dev.taskbound.api.fishing.FishCandidate;
import dev.taskbound.api.fishing.FishCatchData;
import dev.taskbound.api.fishing.FishProperty;
import dev.taskbound.api.fishing.FishingCatchContext;
import dev.taskbound.api.fishing.FishingTaskProvider;
import dev.taskbound.event.StarcatcherCatchEvents;
import dev.taskbound.event.StarcatcherObjectiveRules;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/** Optional adapter: the installed fish registry owns species metadata, never an item-name whitelist. */
public final class StarcatcherFishingProvider implements FishingTaskProvider {
    public static final ResourceLocation ID = ResourceLocation.parse("taskbound:starcatcher");
    private static final ResourceKey<Registry<Object>> FISH_REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.parse("starcatcher:fish"));
    private static final Set<FishProperty> PROPERTIES = Set.of(FishProperty.SPECIES, FishProperty.WEIGHT,
            FishProperty.LENGTH, FishProperty.RARITY, FishProperty.BIOME, FishProperty.DIMENSION,
            FishProperty.TIME, FishProperty.WEATHER, FishProperty.ROD, FishProperty.METHOD);
    private static final Map<Registry<?>, List<FishDefinition>> DEFINITIONS = new WeakHashMap<>();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static StarcatcherFishingProvider instance;
    private final Reflection api;

    private StarcatcherFishingProvider(Reflection api) { this.api = api; }

    public static Optional<StarcatcherFishingProvider> createIfAvailable() {
        try {
            instance = new StarcatcherFishingProvider(Reflection.load());
            return Optional.of(instance);
        } catch (ClassNotFoundException absent) {
            return Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(failure);
            return Optional.empty();
        }
    }

    public static void invalidate() { DEFINITIONS.clear(); }
    public static boolean ready(ServerPlayer player) { return instance != null && !definitions(player).isEmpty(); }

    /** Temporary weather/time windows do not hide otherwise obtainable species. */
    public static boolean eligible(ServerPlayer player, Map<String, Object> parameters) {
        if (!ready(player)) return false;
        if (Boolean.TRUE.equals(parameters.get("perfect")) && !instance.api.minigameEnabled()) return false;
        var candidates = definitions(player).stream().filter(fish -> matchesDefinition(fish, parameters)).toList();
        int required = ((Number) parameters.getOrDefault("required_count", 1)).intValue();
        return switch (String.valueOf(parameters.getOrDefault("count_mode", "catches"))) {
            case "species" -> candidates.size() >= required;
            case "habitats" -> candidates.stream().map(FishDefinition::habitat).distinct().count() >= required;
            default -> !candidates.isEmpty();
        };
    }

    public static boolean matchesDefinition(FishDefinition fish, Map<String, Object> p) {
        if (!fish.repeatable() || !listMatches(p.get("species"), fish.species())
                || !listMatches(p.get("habitats"), fish.habitat())) return false;
        if (p.get("rarity") instanceof String rarity && !rarity.equals(fish.rarity())) return false;
        if (p.get("minimum_rarity") instanceof String rarity
                && StarcatcherObjectiveRules.rarityRank(fish.rarity()) < StarcatcherObjectiveRules.rarityRank(rarity)) return false;
        if (p.containsKey("minimum_weight_percentile") && !(fish.weightDeviation() > 0)) return false;
        if (p.get("required_time") instanceof String time && !fish.allowedTimes().contains(time)) return false;
        if (p.get("required_weather") instanceof String weather
                && !(fish.allowedWeather().contains(weather) || weather.equals("rain") && fish.allowedWeather().contains("thunder"))) return false;
        return !Boolean.TRUE.equals(p.get("perfect")) || !fish.skipsMinigame();
    }

    private static boolean listMatches(Object value, String key) {
        return !(value instanceof List<?> entries) || entries.contains(key);
    }

    public static List<FishDefinition> definitions(ServerPlayer player) {
        if (instance == null) return List.of();
        Registry<Object> registry = player.registryAccess().registry(FISH_REGISTRY).orElse(null);
        if (registry == null) return List.of();
        List<FishDefinition> cached = DEFINITIONS.get(registry);
        if (cached != null) return cached;
        try {
            var values = new ArrayList<FishDefinition>();
            for (var entry : registry.entrySet()) {
                FishDefinition fish = instance.api.definition(entry.getKey().location(), entry.getValue(), player.registryAccess());
                if (fish != null) values.add(fish);
            }
            List<FishDefinition> result = List.copyOf(values);
            DEFINITIONS.put(registry, result);
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(failure);
            return List.of();
        }
    }

    /** Reject constructed/forged fish definitions even when their supplied ID names a real entry. */
    public static Optional<FishDefinition> definition(ServerPlayer player, Object value, ResourceLocation id) {
        if (id == null || instance == null) return Optional.empty();
        Registry<Object> registry = player.registryAccess().registry(FISH_REGISTRY).orElse(null);
        if (registry == null || registry.get(id) != value) return Optional.empty();
        return definitions(player).stream().filter(fish -> fish.species().equals(id.toString())).findFirst();
    }

    @Override public ResourceLocation id() { return ID; }
    @Override public boolean supportsProperty(FishProperty property) { return PROPERTIES.contains(property); }

    @Override public Optional<FishCatchData> readCatch(ServerPlayer player, ItemStack caught, FishingCatchContext context) {
        // Item components alone cannot prove a catch. Support old accepted catch_fish objectives
        // only inside the real Starcatcher completion transaction.
        var evidence = StarcatcherCatchEvents.current(player);
        if (evidence == null || !evidence.fish().matchesItem(caught)) return Optional.empty();
        var fish = evidence.fish();
        double raw = evidence.rawPercentile();
        double weight = StarcatcherFishingRules.measurement(fish.weightAverage(), fish.weightDeviation(), raw);
        double length = StarcatcherFishingRules.measurement(fish.lengthAverage(), fish.lengthDeviation(), raw);
        return Optional.of(new FishCatchData(ID, ResourceLocation.parse(fish.species()), caught,
                Optional.of(weight), Optional.of(length), Optional.of(ResourceLocation.parse("starcatcher:" + fish.rarity())),
                context.level().getBiome(context.hookPosition()).unwrapKey().orElseThrow().location(),
                context.level().dimension().location(), context.level().getGameTime(),
                BuiltInRegistries.ITEM.getKey(context.rod().getItem()), "starcatcher:rod",
                Map.of("length_unit", "cm", "weight_unit", "g")));
    }

    @Override public Collection<FishCandidate> getEligibleFish(ServerPlayer player, TaskGenerationContext context) {
        return definitions(player).stream().filter(FishDefinition::repeatable).map(fish ->
                new FishCandidate(ResourceLocation.parse(fish.species()), PROPERTIES, 0,
                        Map.of("compatibility_mod", "starcatcher", "length_unit", "cm", "weight_unit", "g",
                                "habitat", fish.habitat(), "rarity", fish.rarity()))).toList();
    }

    public record FishDefinition(String species, String item, String bucket, String entity,
            String rarity, String habitat, double weightAverage, double weightDeviation,
            double lengthAverage, double lengthDeviation, boolean repeatable, boolean skipsMinigame,
            Set<String> allowedTimes, Set<String> allowedWeather) {
        public boolean matchesItem(ItemStack stack) {
            if (stack.isEmpty()) return false;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            return id.equals(item) || id.equals(bucket);
        }
        public boolean matchesEntity(EntityType<?> type) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().equals(entity);
        }
    }

    private record Reflection(Class<?> properties, Codec<Object> codec, Field enableMinigame) {
        @SuppressWarnings("unchecked")
        private static Reflection load() throws ReflectiveOperationException {
            ClassLoader loader = StarcatcherFishingProvider.class.getClassLoader();
            Class<?> properties = Class.forName("com.wdiscute.starcatcher.fish.FishProperties", false, loader);
            Class<?> fishApi = Class.forName("com.wdiscute.starcatcher.fish.FishApi", false, loader);
            Class<?> counter = Class.forName("com.wdiscute.starcatcher.data.FishCaughtCounter", false, loader);
            // The optional mixins add these markers. Missing/changed integration must fail closed.
            fishApi.getDeclaredMethod("taskbound$catchTransactionHook");
            counter.getDeclaredMethod("taskbound$catchMetadataHook");
            fishApi.getMethod("spawnFishFromPlayerFishing", ServerPlayer.class, boolean.class, int.class,
                    boolean.class, boolean.class, int.class);
            Class<?> config = Class.forName("com.wdiscute.starcatcher.SCConfig", false, loader);
            return new Reflection(properties, (Codec<Object>) properties.getField("CODEC").get(null),
                    config.getField("ENABLE_MINIGAME"));
        }

        private boolean minigameEnabled() {
            try {
                Object value = enableMinigame.get(null);
                return Boolean.TRUE.equals(value.getClass().getMethod("get").invoke(value));
            } catch (ReflectiveOperationException | RuntimeException failure) {
                warn(failure);
                return false;
            }
        }

        private FishDefinition definition(ResourceLocation id, Object value, RegistryAccess access)
                throws ReflectiveOperationException {
            if (!properties.isInstance(value)) return null;
            JsonObject data = codec.encodeStart(RegistryOps.create(JsonOps.INSTANCE, access), value)
                    .result().map(JsonElement::getAsJsonObject).orElse(null);
            if (data == null || data.get("base_chance").getAsInt() <= 0) return null;
            JsonObject info = data.getAsJsonObject("catch_info");
            if (!text(info, "type", "fish").equals("fish")) return null;
            String rarity = text(data, "rarity", "");
            if (StarcatcherObjectiveRules.rarityRank(rarity) < 0) return null;
            String item = item(info, "item");
            String bucket = item(info, "fish_bucket");
            String entity = text(info, "entity", "starcatcher:fish");
            boolean hasItem = validItem(item) || validItem(bucket);
            boolean hasEntity = !entity.equals("starcatcher:fish")
                    && BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(entity));
            if (!hasItem && !hasEntity) return null;
            JsonArray restrictions = data.getAsJsonArray("restrictions");
            boolean repeatable = true;
            for (JsonElement restriction : restrictions) {
                JsonObject r = restriction.getAsJsonObject();
                String type = text(r, "type", "");
                if (type.endsWith(":caught_limit") && r.has("limit") && r.get("limit").getAsInt() < Integer.MAX_VALUE)
                    repeatable = false;
                if (type.endsWith(":percentage_chance") && r.has("chance") && r.get("chance").getAsDouble() <= 0)
                    repeatable = false;
            }
            JsonObject weight = data.getAsJsonObject("size_and_weight");
            Set<String> times = Set.of("day", "night").stream().filter(time -> StarcatcherFishingRules.permitsTime(restrictions, time))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> weather = Set.of("clear", "rain", "thunder").stream().filter(valueWeather -> StarcatcherFishingRules.permitsWeather(restrictions, valueWeather))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new FishDefinition(id.toString(), item, bucket, entity, rarity, habitat(restrictions),
                    number(weight, "average_weight_grams"), number(weight, "deviation_weight_grams"),
                    number(weight, "average_size_cm"), number(weight, "deviation_size_cm"),
                    repeatable && !times.isEmpty() && !weather.isEmpty(), data.get("skips_minigame").getAsBoolean(), times, weather);
        }
    }

    /** Uses installed restriction categories, including a datapack's changed dimension or habitat. */
    static String habitat(JsonArray restrictions) {
        boolean river = false, ocean = false, water = false, lava = false, cave = false;
        for (JsonElement value : restrictions) {
            JsonObject r = value.getAsJsonObject();
            String dimension = text(r, "dimension_entry", "");
            if (dimension.equals("nether")) return "nether";
            if (dimension.equals("end")) return "end";
            if (r.has("max_y") && r.get("max_y").getAsInt() < 50) cave = true;
            if (r.has("biomes")) for (JsonElement biome : r.getAsJsonArray("biomes")) {
                String id = biome.getAsString();
                river |= id.contains("river"); ocean |= id.contains("ocean") || id.contains("beach");
                cave |= id.contains("cave") || id.endsWith("deep_dark");
            }
            if (r.has("fluids")) for (JsonElement fluid : r.getAsJsonArray("fluids")) {
                water |= fluid.getAsString().equals("minecraft:water");
                lava |= fluid.getAsString().equals("minecraft:lava");
            }
        }
        if (cave) return "cave";
        if (lava) return "lava";
        if (river) return "river";
        if (ocean) return "ocean";
        return water ? "lake" : "sky";
    }

    private static boolean validItem(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed != null && !id.equals("minecraft:air") && BuiltInRegistries.ITEM.containsKey(parsed);
    }
    private static String item(JsonObject info, String key) {
        return info.has(key) && info.get(key).isJsonObject() ? text(info.getAsJsonObject(key), "identifier", "") : "";
    }
    private static String text(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
    private static double number(JsonObject object, String key) {
        double value = object.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid Starcatcher measurement");
        return value;
    }
    private static void warn(Throwable failure) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) TaskboundMod.LOGGER.warn(
                "Starcatcher task tracking is unavailable for this API/data shape; vanilla Angler offers remain disabled", failure);
    }
}
