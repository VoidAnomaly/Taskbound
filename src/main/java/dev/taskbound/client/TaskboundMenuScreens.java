package dev.taskbound.client;

import dev.taskbound.TaskboundMod;
import dev.taskbound.registry.TaskboundContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only registration keeps native screens out of dedicated-server class loading. */
@EventBusSubscriber(modid = TaskboundMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TaskboundMenuScreens {
    private TaskboundMenuScreens() { }

    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(TaskboundContent.TASK_HAND_IN_MENU.get(), TaskHandInScreen::new);
    }
}
