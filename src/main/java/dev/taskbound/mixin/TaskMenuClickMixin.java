package dev.taskbound.mixin;

import dev.taskbound.event.ProductionEvents;
import dev.taskbound.event.TaskboundWorldEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures actual transfer inputs before vanilla splits or copies the source stack. */
@Mixin(AbstractContainerMenu.class)
abstract class TaskMenuClickMixin {
    @Inject(method = "clicked", at = @At("HEAD"))
    private void taskbound$beforeClick(int slotId, int button, ClickType type, Player player, CallbackInfo callback) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var menu = (AbstractContainerMenu) (Object) this;
        ProductionEvents.beforeMenuClick(menu, serverPlayer);
        if (slotId >= 0 && slotId < menu.slots.size() && menu.slots.get(slotId) instanceof ResultSlot result) {
            TaskboundWorldEvents.prepareCraftOutput(serverPlayer, result.getItem());
        }
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void taskbound$afterClick(int slotId, int button, ClickType type, Player player, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer) {
            ProductionEvents.afterMenuClick((AbstractContainerMenu) (Object) this, serverPlayer);
        }
    }
}
