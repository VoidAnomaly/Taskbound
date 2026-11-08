package dev.taskbound.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** Uses vanilla base terrain columns, as Nether fossils do, without loading or generating chunks. */
public final class PavilionStructure extends Structure {
    public static final MapCodec<PavilionStructure> NETHER_CODEC = simpleCodec(settings -> new PavilionStructure(settings, true));
    public static final MapCodec<PavilionStructure> END_CODEC = simpleCodec(settings -> new PavilionStructure(settings, false));
    private final boolean nether;

    private PavilionStructure(StructureSettings settings, boolean nether) {
        super(settings);
        this.nether = nether;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // The complete 7x7 footprint stays inside its starting chunk, for every rotation.
        int x = context.chunkPos().getBlockX(5);
        int z = context.chunkPos().getBlockZ(5);
        NoiseColumn center = column(context, x + 3, z + 3);
        var heights = new ArrayList<Integer>();
        if (nether) {
            // Do not use WORLD_SURFACE in the Nether: it identifies the bedrock roof.
            int bottom = Math.max(context.chunkGenerator().getSeaLevel() + 1, context.heightAccessor().getMinBuildHeight() + 3);
            int top = Math.min(112, context.heightAccessor().getMaxBuildHeight() - 6);
            for (int y = bottom; y <= top; y++) {
                if (!center.getBlock(y).isAir() && fitsColumn(center, y)) heights.add(y);
            }
        } else {
            int surface = context.chunkGenerator().getFirstOccupiedHeight(x + 3, z + 3,
                    Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            if (surface < 55) return Optional.empty(); // Reject void and thin low island edges.
            if (fitsColumn(center, surface)) heights.add(surface);
        }
        if (heights.isEmpty()) return Optional.empty();

        NoiseColumn[][] columns = new NoiseColumn[7][7];
        columns[3][3] = center;
        int first = nether ? context.random().nextInt(heights.size()) : 0;
        for (int attempt = 0; attempt < heights.size(); attempt++) {
            // Randomize cave floors, not elevations above a floor. Prefer the lowest fitting foundation.
            for (int rise = 0; rise <= 2; rise++) {
                int y = heights.get((first + attempt) % heights.size()) + rise;
                if (nether && y > 112 || !fitsFootprint(context, columns, x, y, z)) continue;
                BlockPos origin = new BlockPos(x, y, z);
                Rotation rotation = Rotation.getRandom(context.random());
                return Optional.of(new GenerationStub(origin.offset(3, 1, 3), builder ->
                        builder.addPiece(new PavilionPiece(context.structureTemplateManager(), origin, rotation, nether))));
            }
        }
        return Optional.empty();
    }

    private static NoiseColumn column(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
    }

    private static boolean fitsFootprint(GenerationContext context, NoiseColumn[][] columns, int x, int y, int z) {
        // Check the corners first, then every column. A supported center alone can leave a floating pavilion.
        for (int dx : new int[]{0, 6}) for (int dz : new int[]{0, 6}) {
            if (columns[dx][dz] == null) columns[dx][dz] = column(context, x + dx, z + dz);
            if (!fitsColumn(columns[dx][dz], y)) return false;
        }
        for (int dx = 0; dx < 7; dx++) for (int dz = 0; dz < 7; dz++) {
            if (columns[dx][dz] == null) columns[dx][dz] = column(context, x + dx, z + dz);
            if (!fitsColumn(columns[dx][dz], y)) return false;
        }
        return true;
    }

    private static boolean fitsColumn(NoiseColumn column, int floor) {
        for (int y = floor + 1; y <= floor + 5; y++) {
            if (!column.getBlock(y).isAir()) return false;
        }
        // The foundation may fill at most two air blocks to meet gently sloping ground.
        for (int y = floor; y >= floor - 2; y--) {
            BlockState state = column.getBlock(y);
            if (state.isAir()) continue;
            return state.getFluidState().isEmpty() && !state.is(Blocks.BEDROCK)
                    && state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP);
        }
        return false;
    }

    @Override
    public StructureType<?> type() {
        return nether ? TaskboundPavilions.NETHER.get() : TaskboundPavilions.END.get();
    }
}
