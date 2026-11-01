package dev.taskbound.mixin;

import dev.taskbound.event.DragonEncounterTracking;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Capture the lethal source before vanilla restores one health for its death animation. */
@Mixin(EnderDragon.class)
abstract class EnderDragonFinisherMixin {
    @Inject(method = "reallyHurt", at = @At("RETURN"))
    private void taskbound$finisher(DamageSource source, float damage, CallbackInfoReturnable<Boolean> callback) {
        DragonEncounterTracking.damaged((EnderDragon)(Object)this, source, callback.getReturnValueZ());
    }
}
