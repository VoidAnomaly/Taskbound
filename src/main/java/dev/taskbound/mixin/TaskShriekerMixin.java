package dev.taskbound.mixin;

import dev.taskbound.event.TaskboundWorldEvents;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The warning and spawn are ninety ticks apart; retain attribution across chunk saves. */
@Mixin(SculkShriekerBlockEntity.class)
abstract class TaskShriekerMixin {
    @Shadow private int warningLevel;
    @Unique private UUID taskbound$responsiblePlayer;

    @Inject(method = "shriek", at = @At("HEAD"))
    private void taskbound$capturePlayer(ServerLevel level, Entity source, CallbackInfo callback) {
        taskbound$responsiblePlayer = source instanceof ServerPlayer player ? player.getUUID() : null;
        ((SculkShriekerBlockEntity) (Object) this).setChanged();
        if (warningLevel >= 4 && taskbound$responsiblePlayer != null) {
            TaskboundWorldEvents.wardenWarning(level, ((SculkShriekerBlockEntity) (Object) this).getBlockPos(), taskbound$responsiblePlayer);
        }
    }

    @Inject(method = "tryRespond", at = @At("RETURN"))
    private void taskbound$responseFinished(ServerLevel level, CallbackInfo callback) {
        if (taskbound$responsiblePlayer != null) {
            TaskboundWorldEvents.wardenResponseFinished(level, ((SculkShriekerBlockEntity) (Object) this).getBlockPos(), taskbound$responsiblePlayer);
        }
    }

    @Inject(method = "trySummonWarden", at = @At("RETURN"))
    private void taskbound$spawnedWarden(ServerLevel level, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && taskbound$responsiblePlayer != null) {
            TaskboundWorldEvents.wardenSummoned(level, ((SculkShriekerBlockEntity) (Object) this).getBlockPos(), taskbound$responsiblePlayer);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void taskbound$saveAttribution(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        if (taskbound$responsiblePlayer != null) tag.putUUID("taskbound_responsible_player", taskbound$responsiblePlayer);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void taskbound$loadAttribution(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        taskbound$responsiblePlayer = tag.hasUUID("taskbound_responsible_player") ? tag.getUUID("taskbound_responsible_player") : null;
    }
}
