package dev.taskbound.mixin;

import dev.taskbound.compat.FarmersDelightCompat;
import dev.taskbound.event.FreshFoodLedger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.container.CookingPotResultSlot", remap = false)
abstract class FarmerDelightResultMixin {
    @Shadow @Final private Player player;
    @Shadow private int removeCount;
    @Inject(method = "checkTakeAchievements", at = @At("HEAD"), remap = false)
    private void taskbound$collect(ItemStack output, CallbackInfo callback) {
        Slot slot = (Slot) (Object) this;
        BlockEntity pot = FarmersDelightCompat.pot(slot);
        if (pot != null && player instanceof ServerPlayer serverPlayer)
            FreshFoodLedger.collected(serverPlayer, pot, "output", slot.getItem(), removeCount, "cooking_pot");
    }
}
