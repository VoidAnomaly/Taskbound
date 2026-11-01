package dev.taskbound.mixin;

import dev.taskbound.worldgen.TaskboundVillageStructures;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Prefer one pavilion while retaining vanilla fit checks and rejecting repeated candidates. */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
abstract class VillagePavilionLimitMixin {
    @Shadow @Final private List<? super PoolElementStructurePiece> pieces;

    @Redirect(method = "tryPlacingChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;getShuffledTemplates(Lnet/minecraft/util/RandomSource;)Ljava/util/List;",
            ordinal = 0), require = 1, expect = 1, allow = 1)
    private List<StructurePoolElement> taskbound$prioritizePavilion(StructureTemplatePool pool, RandomSource random) {
        // Preserve vanilla's shuffle and the relative order of every other house candidate.
        var candidates = pool.getShuffledTemplates(random);
        if (pieces.stream().noneMatch(existing -> existing instanceof PoolElementStructurePiece piece
                && TaskboundVillageStructures.isPavilion(piece.getElement()))) {
            for (int index = 0; index < candidates.size(); index++) {
                if (TaskboundVillageStructures.isPavilion(candidates.get(index))) {
                    // Try our existing pool entry before ordinary houses or an empty-lot candidate.
                    // Vanilla still chooses a compatible rotation and checks terrain and collisions.
                    candidates.addFirst(candidates.remove(index));
                    break;
                }
            }
        }
        return candidates;
    }

    @Redirect(method = "tryPlacingChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructurePoolElement;getShuffledJigsawBlocks(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/util/RandomSource;)Ljava/util/List;",
            ordinal = 1), require = 1, expect = 1, allow = 1)
    private List<StructureTemplate.StructureBlockInfo> taskbound$limitPavilions(StructurePoolElement candidate,
            StructureTemplateManager manager, BlockPos position, Rotation rotation, RandomSource random) {
        if (TaskboundVillageStructures.isPavilion(candidate)
                && pieces.stream().anyMatch(existing -> existing instanceof PoolElementStructurePiece piece
                        && TaskboundVillageStructures.isPavilion(piece.getElement()))) {
            // This list belongs to one layout, so neighboring or concurrently generated villages remain independent.
            return List.of();
        }
        return candidate.getShuffledJigsawBlocks(manager, position, rotation, random);
    }
}
