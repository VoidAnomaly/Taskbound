package dev.taskbound.porter;

import dev.taskbound.block.TaskBoardBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Normal interaction and final-damage hooks, kept separate from vanilla task events. */
public final class PorterEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof TaskBoardBlockEntity board)) return;
        PorterTasks.visitBoard(player, board);
        if (player.isShiftKeyDown()) return;
        boolean handled;
        try { handled = PorterTasks.interact(player, board); }
        catch (IllegalArgumentException | IllegalStateException failure) {
            // A reload can invalidate a reward after delivery; retain the completed task for retry.
            player.sendSystemMessage(Component.literal(failure.getMessage()));
            handled = true;
        }
        if (!handled) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    @SubscribeEvent
    public void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) PorterTasks.tick(player);
    }

    @SubscribeEvent
    public void damage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) PorterTasks.damaged(player, event.getNewDamage());
    }
}
