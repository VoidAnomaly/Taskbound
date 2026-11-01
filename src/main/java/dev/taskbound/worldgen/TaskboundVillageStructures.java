package dev.taskbound.worldgen;

import com.mojang.datafixers.util.Pair;
import dev.taskbound.TaskboundMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** Adds the authored pavilion to vanilla village streets without replacing anyone else's pools. */
public final class TaskboundVillageStructures {
    public static final ResourceLocation PAVILION = ResourceLocation.fromNamespaceAndPath(
            TaskboundMod.MOD_ID, "village/task_pavilion");
    public static final List<String> VILLAGE_TYPES = List.of("plains", "desert", "savanna", "snowy", "taiga");
    private static final int WEIGHT = 3;

    private TaskboundVillageStructures() { }

    public static boolean isPavilion(StructurePoolElement element) {
        return element instanceof SinglePoolElement single && single.template.left().filter(PAVILION::equals).isPresent();
    }

    public static void serverAboutToStart(ServerAboutToStartEvent event) {
        addToVillagePools(event.getServer().registryAccess());
    }

    public static void addToVillagePools(RegistryAccess registries) {
        var pools = registries.registryOrThrow(Registries.TEMPLATE_POOL);
        for (String village : VILLAGE_TYPES) {
            for (String variant : List.of("", "zombie/")) {
                var id = ResourceLocation.withDefaultNamespace("village/" + village + "/" + variant + "houses");
                StructureTemplatePool pool = pools.get(id);
                if (pool == null) continue; // A world datapack may intentionally omit vanilla villages.
                StructurePoolElement pavilion = StructurePoolElement.single(PAVILION.toString())
                        .apply(StructureTemplatePool.Projection.RIGID);
                // Repeated startup/test registration must not inflate spawn weight.
                if (pool.rawTemplates.stream().anyMatch(entry -> isPavilion(entry.getFirst()))) continue;
                var entries = new ArrayList<>(pool.rawTemplates);
                entries.add(Pair.of(pavilion, WEIGHT));
                pool.rawTemplates = entries;
                for (int i = 0; i < WEIGHT; i++) pool.templates.add(pavilion);
                pool.maxSize = Integer.MIN_VALUE;
            }
        }
    }
}
