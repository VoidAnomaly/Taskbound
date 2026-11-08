package dev.taskbound.mixin;

import dev.taskbound.event.FreshFoodLedger;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity", remap = false)
abstract class FarmerDelightPotMixin {
    @Shadow public abstract ItemStack getMeal();
    @Shadow public abstract ItemStackHandler getInventory();
    @Unique private ItemStack taskbound$beforeMeal = ItemStack.EMPTY;
    @Unique private ItemStack taskbound$transferMeal = ItemStack.EMPTY;
    @Unique private ItemStack taskbound$transferOutput = ItemStack.EMPTY;
    @Inject(method = "processCooking", at = @At("HEAD"), remap = false)
    private void taskbound$beforeCook(CallbackInfoReturnable<Boolean> callback) { taskbound$beforeMeal = getMeal().copy(); }
    @Inject(method = "processCooking", at = @At("RETURN"), remap = false)
    private void taskbound$afterCook(CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) FreshFoodLedger.produced((BlockEntity) (Object) this, "meal", taskbound$beforeMeal, getMeal());
    }
    @Inject(method = {"moveMealToOutput", "useStoredContainersOnMeal"}, at = @At("HEAD"), remap = false)
    private void taskbound$beforeMove(CallbackInfo callback) {
        taskbound$transferMeal = getMeal().copy(); taskbound$transferOutput = getInventory().getStackInSlot(8).copy();
    }
    @Inject(method = {"moveMealToOutput", "useStoredContainersOnMeal"}, at = @At("RETURN"), remap = false)
    private void taskbound$afterMove(CallbackInfo callback) {
        FreshFoodLedger.transferred((BlockEntity) (Object) this, "meal", "output", taskbound$transferMeal,
                taskbound$transferOutput, getMeal(), getInventory().getStackInSlot(8));
    }
}
