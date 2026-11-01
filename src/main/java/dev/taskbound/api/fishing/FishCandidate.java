package dev.taskbound.api.fishing;

import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** A currently achievable species and the properties the provider can report for that species. */
public record FishCandidate(
        ResourceLocation speciesId,
        Set<FishProperty> properties,
        int difficultyModifier,
        Map<String, String> additionalProperties
) {
    public FishCandidate {
        properties = Set.copyOf(properties);
        additionalProperties = Map.copyOf(additionalProperties);
    }
}
