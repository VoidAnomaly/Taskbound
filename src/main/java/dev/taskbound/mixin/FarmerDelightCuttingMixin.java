package dev.taskbound.mixin;

import dev.taskbound.event.FarmerGameplayEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity", remap = false)
abstract class FarmerDelightCuttingMixin {
    @Shadow public abstract ItemStack getStoredItem();
    @Unique private ItemStack taskbound$cutInput = ItemStack.EMPTY;
    @Inject(method = "processStoredItemUsingTool", at = @At("HEAD"), remap = false)
    private void taskbound$before(ItemStack tool, Player player, CallbackInfoReturnable<Boolean> callback) { taskbound$cutInput = getStoredItem().copy(); }
    @Inject(method = "processStoredItemUsingTool", at = @At("RETURN"), remap = false)
    private void taskbound$after(ItemStack tool, Player player, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && player instanceof ServerPlayer serverPlayer
                && getStoredItem().getCount() < taskbound$cutInput.getCount())
            FarmerGameplayEvents.cut(serverPlayer, taskbound$cutInput, ((BlockEntity) (Object) this).getBlockPos());
    }
}
