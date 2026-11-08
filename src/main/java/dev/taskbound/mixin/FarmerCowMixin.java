package dev.taskbound.mixin;

import dev.taskbound.event.FarmerGameplayEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Cow.class)
abstract class FarmerCowMixin {
    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V", shift = At.Shift.AFTER))
    private void taskbound$milk(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        if (player instanceof ServerPlayer serverPlayer) FarmerGameplayEvents.milked(serverPlayer, (Cow) (Object) this);
    }
}
