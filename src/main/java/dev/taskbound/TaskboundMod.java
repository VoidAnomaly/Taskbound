package dev.taskbound;

import com.mojang.logging.LogUtils;
import dev.taskbound.api.TaskboundProviders;
import dev.taskbound.command.TaskboundCommands;
import dev.taskbound.compat.ArsNouveauMagicProvider;
import dev.taskbound.compat.StarcatcherFishingProvider;
import dev.taskbound.compat.VanillaFishingProvider;
import dev.taskbound.config.TaskboundServerConfig;
import dev.taskbound.config.TaskboundClientConfig;
import dev.taskbound.data.TaskboundDefinitionReloadListener;
import dev.taskbound.data.TaskboundDefinitionFiles;
import dev.taskbound.event.TaskboundGameplayEvents;
import dev.taskbound.network.TaskboundNetworking;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.registry.TaskboundContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(TaskboundMod.MOD_ID)
public final class TaskboundMod {
    public static final String MOD_ID = "taskbound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TaskboundMod(IEventBus modBus, ModContainer modContainer) {
        try {
            TaskboundDefinitionFiles.initialize(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("taskbound"));
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException("Could not initialize editable definitions in config/taskbound", error);
        }
        modContainer.registerConfig(ModConfig.Type.SERVER, TaskboundServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, TaskboundClientConfig.SPEC);
        TaskboundContent.register(modBus);
        dev.taskbound.worldgen.TaskboundPavilions.register(modBus);
        modBus.addListener(TaskboundNetworking::registerPayloads);
        TaskboundProviders.registerFishing(new VanillaFishingProvider());
        ArsNouveauMagicProvider.createIfAvailable().ifPresent(TaskboundProviders::registerMagic);
        StarcatcherFishingProvider.createIfAvailable().ifPresent(TaskboundProviders::registerFishing);

        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::datapacksSynced);
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
        NeoForge.EVENT_BUS.addListener(dev.taskbound.worldgen.TaskboundVillageStructures::serverAboutToStart);
        NeoForge.EVENT_BUS.addListener(TaskboundCommands::register);
        NeoForge.EVENT_BUS.addListener(TaskboundNetworking::playerLoggedOut);
        NeoForge.EVENT_BUS.addListener(TaskboundNetworking::serverStopped);
        NeoForge.EVENT_BUS.register(new TaskboundGameplayEvents());
        NeoForge.EVENT_BUS.register(new dev.taskbound.event.ProductionEvents());
        NeoForge.EVENT_BUS.register(new dev.taskbound.event.TaskboundWorldEvents());
        NeoForge.EVENT_BUS.register(new dev.taskbound.event.EncounterGameplayEvents());
        NeoForge.EVENT_BUS.register(new dev.taskbound.event.TaskboundJournalEvents());
        NeoForge.EVENT_BUS.register(new dev.taskbound.event.StarcatcherCatchEvents());
        NeoForge.EVENT_BUS.register(new dev.taskbound.event.FarmerGameplayEvents());
        NeoForge.EVENT_BUS.register(new dev.taskbound.porter.PorterEvents());
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new TaskboundDefinitionReloadListener());
    }

    private void datapacksSynced(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            TaskboundSavedData.get(event.getPlayerList().getServer()).clearGeneratedOffers();
        }
    }

    private void serverStarted(ServerStartedEvent event) {
        // Packs may have changed while offline. Accepted tasks remain frozen in saved state.
        TaskboundSavedData.get(event.getServer()).clearGeneratedOffers();
    }
}
