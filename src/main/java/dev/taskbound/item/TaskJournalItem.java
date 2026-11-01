package dev.taskbound.item;

import dev.taskbound.network.TaskboundNetworking;
import dev.taskbound.runtime.TaskboundRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A physical journal that displays the holder's server-owned tasks and reputation. */
public final class TaskJournalItem extends Item {
    public TaskJournalItem(Properties properties) { super(properties); }

    public static boolean hasJournal(Player player) {
        return player.getInventory().contains(stack -> stack.getItem() instanceof TaskJournalItem);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            TaskboundNetworking.openJournal(serverPlayer, TaskboundRuntime.get().journalSnapshot(serverPlayer));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
