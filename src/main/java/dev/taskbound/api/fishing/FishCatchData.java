package dev.taskbound.api.fishing;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record FishCatchData(
        ResourceLocation providerId,
        ResourceLocation speciesId,
        ItemStack item,
        Optional<Double> weight,
        Optional<Double> length,
        Optional<ResourceLocation> rarity,
        ResourceLocation biome,
        ResourceLocation dimension,
        long caughtAt,
        ResourceLocation rodItem,
        String methodId,
        Map<String, String> additionalProperties
) {
    public FishCatchData {
        item = item.copy();
        weight = weight == null ? Optional.empty() : weight;
        length = length == null ? Optional.empty() : length;
        rarity = rarity == null ? Optional.empty() : rarity;
        additionalProperties = Map.copyOf(additionalProperties);
    }
}
