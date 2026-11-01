package dev.taskbound.api.magic;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record MagicActionData(
        ResourceLocation providerId,
        ResourceLocation actionType,
        Optional<ResourceLocation> spellId,
        Optional<ResourceLocation> effectId,
        ItemStack item,
        Optional<ResourceLocation> targetEntityId,
        ResourceLocation biome,
        ResourceLocation dimension,
        long performedAt,
        Map<String, String> additionalProperties
) {
    public MagicActionData {
        spellId = spellId == null ? Optional.empty() : spellId;
        effectId = effectId == null ? Optional.empty() : effectId;
        targetEntityId = targetEntityId == null ? Optional.empty() : targetEntityId;
        item = item.copy();
        additionalProperties = Map.copyOf(additionalProperties);
    }
}
