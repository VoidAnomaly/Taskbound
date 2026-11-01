package dev.taskbound.mixin;

import dev.taskbound.event.TaskboundWorldEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class TaskBlockPlacementMixin {
    @Inject(method = "place", at = @At("HEAD"))
    private void taskbound$rememberItem(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> callback) {
        TaskboundWorldEvents.beginPlacement(context);
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void taskbound$finishPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> callback) {
        TaskboundWorldEvents.endPlacement(context, callback.getReturnValue().consumesAction());
    }
}
