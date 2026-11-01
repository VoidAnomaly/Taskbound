package dev.taskbound.item;

import dev.taskbound.registry.TaskboundContent;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A sealed delivery item whose task and package identifiers cannot be replaced by an ordinary item. */
public final class TaskPackageItem extends Item {
    public TaskPackageItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack seal(Item item, UUID packageId, UUID taskId, UUID originBoardId) {
        ItemStack stack = new ItemStack(item);
        stack.set(TaskboundContent.PACKAGE_UUID.get(), packageId);
        stack.set(TaskboundContent.PACKAGE_TASK_UUID.get(), taskId);
        stack.set(TaskboundContent.PACKAGE_ORIGIN_BOARD_UUID.get(), originBoardId);
        return stack;
    }

    public static Optional<UUID> packageId(ItemStack stack) {
        return Optional.ofNullable(stack.get(TaskboundContent.PACKAGE_UUID.get()));
    }

    public static Optional<UUID> taskId(ItemStack stack) {
        return Optional.ofNullable(stack.get(TaskboundContent.PACKAGE_TASK_UUID.get()));
    }

    public static Optional<UUID> originBoardId(ItemStack stack) {
        return Optional.ofNullable(stack.get(TaskboundContent.PACKAGE_ORIGIN_BOARD_UUID.get()));
    }
}
