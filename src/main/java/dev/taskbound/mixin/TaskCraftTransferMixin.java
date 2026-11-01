package dev.taskbound.mixin;

import dev.taskbound.event.TaskboundWorldEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shift-click moves the output into inventory before firing ItemCraftedEvent. */
@Mixin({CraftingMenu.class, InventoryMenu.class})
abstract class TaskCraftTransferMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void taskbound$prepareOutput(Player player, int slot, CallbackInfoReturnable<ItemStack> callback) {
        if (slot == 0 && player instanceof ServerPlayer serverPlayer) {
            TaskboundWorldEvents.prepareCraftOutput(serverPlayer, ((AbstractContainerMenu) (Object) this).slots.getFirst().getItem());
        }
    }
}
