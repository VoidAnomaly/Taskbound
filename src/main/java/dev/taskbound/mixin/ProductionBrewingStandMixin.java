package dev.taskbound.mixin;

import dev.taskbound.event.ProductionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BrewingStandBlockEntity.class)
abstract class ProductionBrewingStandMixin {
    @Shadow int brewTime;
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void taskbound$start(Level level, BlockPos pos, BlockState state, BrewingStandBlockEntity stand, CallbackInfo ci) {
        if (((ProductionBrewingStandMixin) (Object) stand).brewTime == 0) ProductionEvents.beginBrewCycle(stand);
    }
    @Inject(method = "doBrew", at = @At("HEAD"))
    private static void taskbound$before(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
        ProductionEvents.beforeBrew(level, pos, items);
    }
    @Inject(method = "doBrew", at = @At("RETURN"))
    private static void taskbound$after(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
        ProductionEvents.afterBrew(level, pos, items);
    }
}
