package dev.taskbound.api.magic;

import dev.taskbound.api.TaskGenerationContext;
import java.util.Collection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Optional spell or ritual integrations implement this interface without creating a hard dependency. */
public interface MagicTaskProvider {
    ResourceLocation id();

    boolean supportsAction(ResourceLocation actionType);

    Collection<MagicActionCandidate> getEligibleActions(ServerPlayer player, TaskGenerationContext context);

    void registerProgressListener(MagicProgressListener listener);
}
