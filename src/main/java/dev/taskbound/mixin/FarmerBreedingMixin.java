package dev.taskbound.mixin;

import dev.taskbound.event.FarmerGameplayEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
abstract class FarmerBreedingMixin {
    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("HEAD"))
    private void taskbound$parent(ServerLevel level, Animal mate, AgeableMob baby, CallbackInfo callback) {
        FarmerGameplayEvents.markBirth((Animal) (Object) this, mate, baby);
    }
}
