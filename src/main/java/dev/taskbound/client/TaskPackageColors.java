package dev.taskbound.client;

import dev.taskbound.TaskboundMod;
import dev.taskbound.item.TaskPackageItem;
import dev.taskbound.registry.TaskboundContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Uses Minecraft's standard tinted item layers, including in inventories and held-item views. */
@EventBusSubscriber(modid = TaskboundMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TaskPackageColors {
    private TaskPackageColors() { }

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Item event) {
        event.register(TaskPackageItem::color, TaskboundContent.TASK_PACKAGE.get());
    }
}
