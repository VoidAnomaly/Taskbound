package dev.taskbound.mixin;

import dev.taskbound.event.ProductionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemCombinerMenu.class)
abstract class ProductionAnvilMenuMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void taskbound$prepare(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 2 && player instanceof ServerPlayer server) ProductionEvents.prepareAnvil((AbstractContainerMenu) (Object) this, server);
    }
}
