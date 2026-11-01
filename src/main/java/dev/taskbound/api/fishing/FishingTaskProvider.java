package dev.taskbound.api.fishing;

import dev.taskbound.api.TaskGenerationContext;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Optional fishing integrations expose real species and measurements through this interface. */
public interface FishingTaskProvider {
    ResourceLocation id();

    boolean supportsProperty(FishProperty property);

    Optional<FishCatchData> readCatch(ServerPlayer player, ItemStack caughtItem, FishingCatchContext context);

    Collection<FishCandidate> getEligibleFish(ServerPlayer player, TaskGenerationContext context);
}
