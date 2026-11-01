package dev.taskbound.api.magic;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** A currently achievable provider action; an empty target set cannot satisfy target-specific tasks. */
public record MagicActionCandidate(
        ResourceLocation actionType,
        Optional<ResourceLocation> spellId,
        Set<ResourceLocation> tags,
        Set<ResourceLocation> targetEntityIds,
        int difficultyModifier,
        Map<String, String> properties
) {
    public MagicActionCandidate(
            ResourceLocation actionType,
            Optional<ResourceLocation> spellId,
            Set<ResourceLocation> tags,
            int difficultyModifier,
            Map<String, String> properties
    ) {
        this(actionType, spellId, tags, Set.of(), difficultyModifier, properties);
    }

    public MagicActionCandidate {
        spellId = spellId == null ? Optional.empty() : spellId;
        tags = Set.copyOf(tags);
        targetEntityIds = Set.copyOf(targetEntityIds);
        properties = Map.copyOf(properties);
    }
}
