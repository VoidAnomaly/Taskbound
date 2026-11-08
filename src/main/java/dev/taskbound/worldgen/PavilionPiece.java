package dev.taskbound.worldgen;

import dev.taskbound.TaskboundMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** A rotated authored template with a shallow matching foundation. */
public final class PavilionPiece extends TemplateStructurePiece {
    public PavilionPiece(StructureTemplateManager manager, BlockPos origin, Rotation rotation, boolean nether) {
        super(TaskboundPavilions.PIECE.get(), 0, manager, templateId(nether), templateId(nether).toString(), settings(rotation), origin);
    }

    public PavilionPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(TaskboundPavilions.PIECE.get(), tag, manager, id -> settings(Rotation.valueOf(tag.getString("Rot"))));
    }

    private static ResourceLocation templateId(boolean nether) {
        return ResourceLocation.fromNamespaceAndPath(TaskboundMod.MOD_ID, (nether ? "nether" : "end") + "/task_pavilion");
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(new BlockPos(3, 0, 3))
                .setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", getRotation().name());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        super.postProcess(level, structures, generator, random, box, chunkPos, pos);
        var foundation = (templateName.equals(templateId(true).toString()) ? Blocks.BLACKSTONE : Blocks.END_STONE).defaultBlockState();
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            for (int depth = 1; depth <= 2; depth++) {
                BlockPos support = templatePosition.offset(x, -depth, z);
                if (!box.isInside(support) || !level.getBlockState(support).isAir()) break;
                level.setBlock(support, foundation, 2);
            }
        }
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) { }
}
