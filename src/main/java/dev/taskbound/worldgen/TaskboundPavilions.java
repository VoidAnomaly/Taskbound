package dev.taskbound.worldgen;

import dev.taskbound.TaskboundMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Standalone dimensional pavilions; the existing village pool remains independent. */
public final class TaskboundPavilions {
    private static final DeferredRegister<StructureType<?>> STRUCTURES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, TaskboundMod.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, TaskboundMod.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<PavilionStructure>> NETHER =
            STRUCTURES.register("nether_pavilion", () -> () -> PavilionStructure.NETHER_CODEC);
    public static final DeferredHolder<StructureType<?>, StructureType<PavilionStructure>> END =
            STRUCTURES.register("end_pavilion", () -> () -> PavilionStructure.END_CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE =
            PIECES.register("pavilion", () -> (context, tag) -> new PavilionPiece(context.structureTemplateManager(), tag));

    private TaskboundPavilions() { }

    public static void register(IEventBus bus) {
        STRUCTURES.register(bus);
        PIECES.register(bus);
    }
}
