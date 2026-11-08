package dev.taskbound.mixin;

import dev.taskbound.event.FreshFoodLedger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
abstract class FarmerFurnaceResultMixin {
    @Shadow @Final private Player player;
    @Shadow private int removeCount;
    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void taskbound$collected(ItemStack stack, CallbackInfo callback) {
        Slot slot = (Slot) (Object) this;
        if (player instanceof ServerPlayer serverPlayer && slot.container instanceof AbstractFurnaceBlockEntity furnace)
            FreshFoodLedger.collected(serverPlayer, furnace, "output", slot.getItem(), removeCount, "furnace");
    }
}
