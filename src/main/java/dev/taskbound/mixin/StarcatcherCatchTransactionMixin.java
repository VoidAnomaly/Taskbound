package dev.taskbound.mixin;

import dev.taskbound.event.StarcatcherCatchEvents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Starcatcher exposes no completed-catch event containing minigame and specimen evidence. */
@Pseudo
@Mixin(targets = "com.wdiscute.starcatcher.fish.FishApi", remap = false)
abstract class StarcatcherCatchTransactionMixin {
    @Unique private static boolean taskbound$catchTransactionHook() { return true; }

    @Inject(method = "spawnFishFromPlayerFishing(Lnet/minecraft/server/level/ServerPlayer;ZIZZI)V", at = @At("HEAD"), remap = false)
    private static void taskbound$begin(ServerPlayer player, boolean completed, int ticks, boolean treasure,
            boolean perfect, int hits, CallbackInfo ci) {
        StarcatcherCatchEvents.begin(player, completed);
    }
    @Inject(method = "spawnFishFromPlayerFishing(Lnet/minecraft/server/level/ServerPlayer;ZIZZI)V", at = @At("RETURN"), remap = false)
    private static void taskbound$end(ServerPlayer player, boolean completed, int ticks, boolean treasure,
            boolean perfect, int hits, CallbackInfo ci) {
        StarcatcherCatchEvents.end(player);
    }
}
