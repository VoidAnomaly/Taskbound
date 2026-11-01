package dev.taskbound.event;

import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.registry.TaskboundContent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Grants one starter journal, including existing players on their first login after updating. */
public final class TaskboundJournalEvents {
    @SubscribeEvent
    public void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var saved = TaskboundSavedData.get(player.server);
        var state = saved.player(player.getUUID());
        if (state.journalGranted()) return;
        var journal = TaskboundContent.TASK_JOURNAL.get().getDefaultInstance();
        state.markJournalGranted();
        saved.changed();
        if (!dev.taskbound.item.TaskJournalItem.hasJournal(player) && !player.getInventory().add(journal)) player.drop(journal, false);
    }
}
