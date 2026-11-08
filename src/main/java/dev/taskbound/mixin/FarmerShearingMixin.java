package dev.taskbound.mixin;

import dev.taskbound.event.FarmerGameplayEvents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.IShearable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IShearable.class, remap = false)
interface FarmerShearingMixin {
    @Inject(method = "onSheared", at = @At("RETURN"), remap = false)
    private void taskbound$sheared(Player player, ItemStack tool, Level level, BlockPos pos, CallbackInfoReturnable<List<ItemStack>> callback) {
        if (player instanceof ServerPlayer serverPlayer && (Object) this instanceof Sheep sheep
                && callback.getReturnValue() != null && !callback.getReturnValue().isEmpty()) FarmerGameplayEvents.sheared(serverPlayer, sheep);
    }
}
