package dev.taskbound.mixin;

import dev.taskbound.event.StarcatcherCatchEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the actual percentile chosen by Starcatcher, including catches spawned as living fish. */
@Pseudo
@Mixin(targets = "com.wdiscute.starcatcher.data.FishCaughtCounter", remap = false)
abstract class StarcatcherCatchMetadataMixin {
    @Unique private static boolean taskbound$catchMetadataHook() { return true; }

    @Inject(method = "awardFishCaughtCounter(Lcom/wdiscute/starcatcher/fish/FishProperties;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/entity/player/Player;IFZZZZ)V", at = @At("HEAD"), remap = false)
    private static void taskbound$metadata(@Coerce Object fish, ResourceLocation id, Player player, int ticks,
            float percentile, boolean perfect, boolean awardToTeam, boolean golden, boolean displayToast, CallbackInfo ci) {
        StarcatcherCatchEvents.metadata(fish, id, player, percentile, perfect, awardToTeam);
    }
}
