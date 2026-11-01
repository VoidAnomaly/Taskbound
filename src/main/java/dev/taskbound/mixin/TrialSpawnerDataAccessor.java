package dev.taskbound.mixin;

import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read vanilla's registered participants before reward ejection removes them. */
@Mixin(TrialSpawnerData.class)
public interface TrialSpawnerDataAccessor {
    @Accessor("detectedPlayers") Set<UUID> taskbound$participants();
}
