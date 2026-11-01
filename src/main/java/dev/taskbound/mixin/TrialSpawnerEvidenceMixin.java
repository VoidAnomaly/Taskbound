package dev.taskbound.mixin;

import dev.taskbound.event.TrialEncounterTracking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Completion is vanilla's ACTIVE -> reward transition, never an arbitrary mob kill. */
@Mixin(TrialSpawner.class)
abstract class TrialSpawnerEvidenceMixin {
    @Unique private TrialSpawnerState taskbound$previous;
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void taskbound$before(ServerLevel level, BlockPos pos, boolean ominous, CallbackInfo callback) {
        taskbound$previous = ((TrialSpawner)(Object)this).getState();
        TrialEncounterTracking.observe(level, pos, (TrialSpawner)(Object)this);
    }
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void taskbound$after(ServerLevel level, BlockPos pos, boolean ominous, CallbackInfo callback) {
        TrialEncounterTracking.transition(level, pos, (TrialSpawner)(Object)this, taskbound$previous);
    }
}
