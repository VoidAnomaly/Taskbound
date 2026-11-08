package dev.taskbound.mixin;

import dev.taskbound.event.FarmerGameplayEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class FarmerBirthSpawnMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void taskbound$born(Entity baby, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) FarmerGameplayEvents.born(baby);
    }
}
