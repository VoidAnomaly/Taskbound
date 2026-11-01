package dev.taskbound.mixin;

import dev.taskbound.event.TaskboundWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CaveVines.class)
interface TaskBerryHarvestMixin {
    @Inject(method = "use", at = @At("RETURN"))
    private static void taskbound$harvest(Entity entity, BlockState before, Level level, BlockPos position,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (entity instanceof ServerPlayer player && level instanceof ServerLevel serverLevel
                && callback.getReturnValue().consumesAction() && CaveVines.hasGlowBerries(before)
                && !CaveVines.hasGlowBerries(level.getBlockState(position))) {
            TaskboundWorldEvents.berriesHarvested(player, serverLevel, position);
        }
    }
}
