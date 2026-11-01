package dev.taskbound.mixin;

import dev.taskbound.event.EncounterGameplayEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla awards these statistics from its movement checks, excluding teleports. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMovementMixin {
    @Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("HEAD"))
    private void taskbound$movement(Stat<?> stat, int amount, CallbackInfo callback) {
        EncounterGameplayEvents.movement((ServerPlayer)(Object)this, stat, amount);
    }
}
