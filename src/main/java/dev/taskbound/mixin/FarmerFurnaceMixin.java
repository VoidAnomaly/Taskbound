package dev.taskbound.mixin;

import dev.taskbound.event.FreshFoodLedger;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class FarmerFurnaceMixin {
    @Unique private ItemStack taskbound$beforeFood = ItemStack.EMPTY;
    @Inject(method = "burn", at = @At("HEAD"))
    private static void taskbound$before(RegistryAccess registries, RecipeHolder<?> recipe, NonNullList<ItemStack> items, int maximum,
            AbstractFurnaceBlockEntity furnace, CallbackInfoReturnable<Boolean> callback) {
        ((FarmerFurnaceMixin) (Object) furnace).taskbound$beforeFood = items.get(2).copy();
    }
    @Inject(method = "burn", at = @At("RETURN"))
    private static void taskbound$after(RegistryAccess registries, RecipeHolder<?> recipe, NonNullList<ItemStack> items, int maximum,
            AbstractFurnaceBlockEntity furnace, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) FreshFoodLedger.produced(furnace, "output", ((FarmerFurnaceMixin) (Object) furnace).taskbound$beforeFood, items.get(2));
    }
}
