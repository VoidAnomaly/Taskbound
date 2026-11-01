package dev.taskbound.compat;

import dev.taskbound.api.TaskGenerationContext;
import dev.taskbound.api.fishing.FishCandidate;
import dev.taskbound.api.fishing.FishCatchData;
import dev.taskbound.api.fishing.FishProperty;
import dev.taskbound.api.fishing.FishingCatchContext;
import dev.taskbound.api.fishing.FishingTaskProvider;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla deliberately exposes no synthetic weight, length, or rarity values. */
public final class VanillaFishingProvider implements FishingTaskProvider {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("taskbound", "vanilla");
    private static final Set<FishProperty> PROPERTIES = Set.of(
            FishProperty.SPECIES,
            FishProperty.BIOME,
            FishProperty.DIMENSION,
            FishProperty.TIME,
            FishProperty.WEATHER,
            FishProperty.ROD,
            FishProperty.METHOD
    );
    private static final List<ItemStack> FISH = List.of(
            new ItemStack(Items.COD),
            new ItemStack(Items.SALMON),
            new ItemStack(Items.TROPICAL_FISH),
            new ItemStack(Items.PUFFERFISH)
    );

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean supportsProperty(FishProperty property) {
        return PROPERTIES.contains(property);
    }

    @Override
    public Optional<FishCatchData> readCatch(ServerPlayer player, ItemStack caughtItem, FishingCatchContext context) {
        if (FISH.stream().noneMatch(stack -> stack.is(caughtItem.getItem()))) {
            return Optional.empty();
        }
        var level = context.level();
        var biome = level.getBiome(context.hookPosition()).unwrapKey().orElseThrow().location();
        var dimension = level.dimension().location();
        var species = BuiltInRegistries.ITEM.getKey(caughtItem.getItem());
        var rod = BuiltInRegistries.ITEM.getKey(context.rod().getItem());
        return Optional.of(new FishCatchData(
                ID, species, caughtItem, Optional.empty(), Optional.empty(), Optional.empty(), biome, dimension,
                level.getGameTime(), rod, context.methodId(), Map.of()
        ));
    }

    @Override
    public Collection<FishCandidate> getEligibleFish(ServerPlayer player, TaskGenerationContext context) {
        return FISH.stream()
                .map(stack -> new FishCandidate(BuiltInRegistries.ITEM.getKey(stack.getItem()), PROPERTIES, 0, Map.of()))
                .toList();
    }
}
