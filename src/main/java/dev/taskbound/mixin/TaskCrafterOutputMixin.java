package dev.taskbound.mixin;

import dev.taskbound.event.TaskboundWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrafterBlock.class)
abstract class TaskCrafterOutputMixin {
    @Inject(method = "dispenseFrom", at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0,
            target = "Lnet/minecraft/world/level/block/CrafterBlock;dispenseItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/CrafterBlockEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/item/crafting/RecipeHolder;)V"),
            require = 1, expect = 1, allow = 1)
    private void taskbound$producedOutput(BlockState state, ServerLevel level, BlockPos position, CallbackInfo callback) {
        TaskboundWorldEvents.crafterProduced(level, position);
    }
}
