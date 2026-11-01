package dev.taskbound.compat;

import dev.taskbound.TaskboundMod;
import dev.taskbound.api.TaskGenerationContext;
import dev.taskbound.api.fishing.FishCandidate;
import dev.taskbound.api.fishing.FishCatchData;
import dev.taskbound.api.fishing.FishProperty;
import dev.taskbound.api.fishing.FishingCatchContext;
import dev.taskbound.api.fishing.FishingTaskProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Optional Starcatcher adapter that never links against Starcatcher classes at compile time. */
public final class StarcatcherFishingProvider implements FishingTaskProvider {
    public static final ResourceLocation ID = id("taskbound", "starcatcher");
    static final ResourceLocation RIPPLE_CATFISH = ResourceLocation.parse(StarcatcherFishingRules.RIPPLE_CATFISH);
    static final ResourceLocation CAUGHT_FISH_INFO = id("starcatcher", "caught_fish_info");
    private static final ResourceLocation UNKNOWN_BIOME = id("minecraft", "unknown");
    private static final Set<FishProperty> PROPERTIES = Set.of(
            FishProperty.SPECIES,
            FishProperty.WEIGHT,
            FishProperty.LENGTH,
            FishProperty.RARITY,
            FishProperty.BIOME,
            FishProperty.DIMENSION,
            FishProperty.TIME,
            FishProperty.WEATHER,
            FishProperty.ROD,
            FishProperty.METHOD
    );
    private static final FishCandidate RIPPLE_CATFISH_CANDIDATE = new FishCandidate(
            RIPPLE_CATFISH,
            PROPERTIES,
            0,
            Map.of(
                    "compatibility_mod", "starcatcher",
                    "length_unit", "cm",
                    "weight_unit", "g"
            )
    );

    private final StarcatcherReflection starcatcher;
    private final AtomicBoolean runtimeFailureLogged = new AtomicBoolean();

    private StarcatcherFishingProvider(StarcatcherReflection starcatcher) {
        this.starcatcher = starcatcher;
    }

    /** Creates the provider only when the reflected Starcatcher API has the expected shape. */
    public static Optional<StarcatcherFishingProvider> createIfAvailable() {
        try {
            return Optional.of(new StarcatcherFishingProvider(StarcatcherReflection.load()));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            TaskboundMod.LOGGER.warn("Starcatcher compatibility could not be initialized", exception);
            return Optional.empty();
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean supportsProperty(FishProperty property) {
        return PROPERTIES.contains(property);
    }

    @Override
    public Optional<FishCatchData> readCatch(
            ServerPlayer player,
            ItemStack caughtItem,
            FishingCatchContext context
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(caughtItem, "caughtItem");
        Objects.requireNonNull(context, "context");
        ResourceLocation species = BuiltInRegistries.ITEM.getKey(caughtItem.getItem());
        if (caughtItem.isEmpty() || !StarcatcherFishingRules.isSupportedSpecies(species.toString())) {
            return Optional.empty();
        }

        try {
            DataComponentType<?> component = caughtFishInfoComponent();
            if (!candidateAvailable(component)) {
                return Optional.empty();
            }
            Object caughtFishInfo = getRawComponent(caughtItem, component);
            if (!starcatcher.isCaughtFishInfo(caughtFishInfo)) {
                return Optional.empty();
            }

            StarcatcherFishingRules.CatchMeasurements measurements = starcatcher.measurements(caughtFishInfo);
            var level = context.level();
            ResourceLocation biome = level.getBiome(context.hookPosition()).unwrapKey()
                    .map(key -> key.location())
                    .orElse(UNKNOWN_BIOME);
            ResourceLocation rod = BuiltInRegistries.ITEM.getKey(context.rod().getItem());
            return Optional.of(new FishCatchData(
                    ID,
                    species,
                    caughtItem,
                    Optional.of(measurements.weightGrams()),
                    Optional.of(measurements.lengthCentimeters()),
                    Optional.of(ResourceLocation.parse(measurements.rarityId())),
                    biome,
                    level.dimension().location(),
                    level.getGameTime(),
                    rod,
                    context.methodId(),
                    Map.of(
                            "length_unit", "cm",
                            "weight_unit", "g"
                    )
            ));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logRuntimeFailure("read a Starcatcher catch", exception);
            return Optional.empty();
        }
    }

    @Override
    public Collection<FishCandidate> getEligibleFish(
            ServerPlayer player,
            TaskGenerationContext context
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");
        try {
            return candidateAvailable(caughtFishInfoComponent())
                    ? List.of(RIPPLE_CATFISH_CANDIDATE)
                    : List.of();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logRuntimeFailure("inspect Starcatcher candidate availability", exception);
            return List.of();
        }
    }

    private boolean candidateAvailable(DataComponentType<?> component) throws ReflectiveOperationException {
        return StarcatcherFishingRules.candidateAvailable(
                BuiltInRegistries.ITEM.containsKey(RIPPLE_CATFISH),
                component != null && BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(CAUGHT_FISH_INFO),
                starcatcher.savesDataToItems()
        );
    }

    private static DataComponentType<?> caughtFishInfoComponent() {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.get(CAUGHT_FISH_INFO);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getRawComponent(ItemStack stack, DataComponentType<?> component) {
        return stack.get((DataComponentType) component);
    }

    private void logRuntimeFailure(String operation, Throwable exception) {
        if (runtimeFailureLogged.compareAndSet(false, true)) {
            TaskboundMod.LOGGER.warn("Taskbound could not {}", operation, exception);
        }
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private record StarcatcherReflection(
            Class<?> caughtFishInfo,
            Method size,
            Method weight,
            Method rarity,
            Method raritySerializedName,
            Field saveDataToItems,
            Method configValueGet
    ) {
        private static StarcatcherReflection load() throws ReflectiveOperationException {
            ClassLoader loader = StarcatcherFishingProvider.class.getClassLoader();
            Class<?> caughtFishInfo = Class.forName(
                    "com.wdiscute.starcatcher.data.CaughtFishInfo", false, loader);
            Class<?> config = Class.forName("com.wdiscute.starcatcher.SCConfig", false, loader);
            Method rarity = caughtFishInfo.getMethod("rarity");
            Field saveDataToItems = config.getField("SAVE_DATA_TO_ITEMS");
            return new StarcatcherReflection(
                    caughtFishInfo,
                    caughtFishInfo.getMethod("size"),
                    caughtFishInfo.getMethod("weight"),
                    rarity,
                    rarity.getReturnType().getMethod("getSerializedName"),
                    saveDataToItems,
                    saveDataToItems.getType().getMethod("get")
            );
        }

        private boolean isCaughtFishInfo(Object value) {
            return value != null && caughtFishInfo.isInstance(value);
        }

        private boolean savesDataToItems() throws ReflectiveOperationException {
            Object configValue = saveDataToItems.get(null);
            return Boolean.TRUE.equals(configValueGet.invoke(configValue));
        }

        private StarcatcherFishingRules.CatchMeasurements measurements(Object value)
                throws ReflectiveOperationException {
            Object sizeValue = size.invoke(value);
            Object weightValue = weight.invoke(value);
            Object rarityValue = rarity.invoke(value);
            Object serializedRarity = raritySerializedName.invoke(rarityValue);
            if (!(sizeValue instanceof Number sizeNumber)
                    || !(weightValue instanceof Number weightNumber)
                    || !(serializedRarity instanceof String rarityName)) {
                throw new ReflectiveOperationException("Starcatcher caught-fish data returned unexpected values");
            }
            return StarcatcherFishingRules.measurements(sizeNumber, weightNumber, rarityName);
        }
    }
}
