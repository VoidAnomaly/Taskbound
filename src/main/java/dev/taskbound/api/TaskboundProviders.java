package dev.taskbound.api;

import dev.taskbound.api.fishing.FishProperty;
import dev.taskbound.api.fishing.FishingTaskProvider;
import dev.taskbound.api.magic.MagicTaskProvider;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;

/** Public registry for optional compatibility providers. Registration is safe during mod construction. */
public final class TaskboundProviders {
    private static final List<MagicTaskProvider> MAGIC = new CopyOnWriteArrayList<>();
    private static final List<FishingTaskProvider> FISHING = new CopyOnWriteArrayList<>();

    private TaskboundProviders() {
    }

    public static void registerMagic(MagicTaskProvider provider) {
        requireUnique(provider.id(), MAGIC.stream().map(MagicTaskProvider::id).toList());
        MAGIC.add(provider);
        provider.registerProgressListener((player, action) -> TaskboundRuntime.get().advanceMatching(
                player,
                objective -> {
                    if (!provider.id().equals(action.providerId())) {
                        return false;
                    }
                    if (!objective.type().equals("perform_magic_action")) {
                        return false;
                    }
                    String requiredAction = TaskboundRuntime.stringParameter(objective, "action");
                    String actionId = action.actionType().toString();
                    if (requiredAction != null
                            && !requiredAction.equals(actionId)
                            && !requiredAction.equals(action.actionType().getPath())) {
                        return false;
                    }
                    String requiredProvider = TaskboundRuntime.stringParameter(
                            objective, "compatibility_provider_id");
                    if (requiredProvider != null && !requiredProvider.equals(action.providerId().toString())) {
                        return false;
                    }
                    String requiredSpell = TaskboundRuntime.stringParameter(objective, "spell_id");
                    if (requiredSpell != null
                            && action.spellId().map(ResourceLocation::toString).filter(requiredSpell::equals).isEmpty()) {
                        return false;
                    }
                    String requiredTarget = TaskboundRuntime.stringParameter(objective, "target_entity");
                    return requiredTarget == null || action.targetEntityId()
                            .map(ResourceLocation::toString)
                            .filter(requiredTarget::equals)
                            .isPresent();
                },
                1
        ));
    }

    public static void registerFishing(FishingTaskProvider provider) {
        requireUnique(provider.id(), FISHING.stream().map(FishingTaskProvider::id).toList());
        FISHING.add(provider);
    }

    public static List<MagicTaskProvider> magicProviders() {
        return List.copyOf(MAGIC);
    }

    public static List<FishingTaskProvider> fishingProviders() {
        return List.copyOf(FISHING);
    }

    public static Set<String> capabilities() {
        var result = new LinkedHashSet<String>();
        result.addAll(List.of(
                "minecraft:kill_entity",
                "minecraft:deliver_package",
                "minecraft:brew_potion",
                "minecraft:cure_zombie_villager",
                "minecraft:catch_fish",
                "fishing:species",
                "fishing:biome",
                "fishing:dimension",
                "fishing:time",
                "fishing:weather",
                "fishing:rod",
                "fishing:method"
        ));
        for (FishingTaskProvider provider : FISHING) {
            result.add("fishing:provider/" + provider.id());
            for (FishProperty property : FishProperty.values()) {
                if (provider.supportsProperty(property)) {
                    result.add("fishing:" + property.name().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        for (MagicTaskProvider provider : MAGIC) {
            result.add("magic:provider/" + provider.id());
            if (provider.supportsAction(ResourceLocation.fromNamespaceAndPath("magic", "spell_casting"))
                    || provider.supportsAction(ResourceLocation.fromNamespaceAndPath("taskbound", "cast_spell"))) {
                result.add("magic:spell_casting");
            }
        }
        return Set.copyOf(result);
    }

    private static void requireUnique(ResourceLocation id, Collection<ResourceLocation> existing) {
        if (id == null) {
            throw new IllegalArgumentException("Provider ID cannot be null");
        }
        if (existing.contains(id)) {
            throw new IllegalArgumentException("A Taskbound provider is already registered as " + id);
        }
    }
}
