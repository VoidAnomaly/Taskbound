package dev.taskbound.mixin;

import dev.taskbound.event.FarmerGameplayEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.TomatoBlock", remap = false)
abstract class FarmerDelightTomatoMixin {
    @Inject(method = "useWithoutItem", at = @At("RETURN"), remap = false)
    private void taskbound$harvest(BlockState before, Level level, BlockPos pos, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (player instanceof ServerPlayer serverPlayer && callback.getReturnValue().consumesAction()
                && before.getBlock() instanceof CropBlock crop && crop.isMaxAge(before)
                && level.getBlockState(pos).is(before.getBlock())
                && !crop.isMaxAge(level.getBlockState(pos))) FarmerGameplayEvents.harvested(serverPlayer, before, pos);
    }
}
