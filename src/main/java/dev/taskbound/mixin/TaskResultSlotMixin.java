package dev.taskbound.mixin;

import dev.taskbound.event.TaskboundWorldEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultSlot.class)
abstract class TaskResultSlotMixin {
    @Shadow @Final private Player player;

    @Inject(method = "remove", at = @At("HEAD"))
    private void taskbound$prepareSplit(int count, CallbackInfoReturnable<ItemStack> callback) {
        if (player instanceof ServerPlayer serverPlayer) {
            TaskboundWorldEvents.prepareCraftOutput(serverPlayer, ((ResultSlot) (Object) this).getItem());
        }
    }
}
