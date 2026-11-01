package dev.taskbound.event;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** A persisted identity and boundary for the particular structure where a task began. */
public final class TaskStructureIdentity {
    private TaskStructureIdentity() { }

    @Nullable
    public static Map<String, Object> capture(ServerLevel level, BlockPos position, String structureId) {
        var start = level.structureManager().getStructureWithPieceAt(position,
                holder -> holder.unwrapKey().map(key -> key.location().toString().equals(structureId)).orElse(false));
        if (!start.isValid()) return null;
        var bounds = start.getBoundingBox();
        var result = new LinkedHashMap<String, Object>();
        result.put("dimension", level.dimension().location().toString());
        result.put("structure", structureId);
        result.put("chunk_x", start.getChunkPos().x);
        result.put("chunk_z", start.getChunkPos().z);
        result.put("min_x", bounds.minX());
        result.put("min_y", bounds.minY());
        result.put("min_z", bounds.minZ());
        result.put("max_x", bounds.maxX());
        result.put("max_y", bounds.maxY());
        result.put("max_z", bounds.maxZ());
        return result;
    }

    public static boolean contains(ServerLevel level, BlockPos position, Map<String, ?> identity) {
        return level.dimension().location().toString().equals(identity.get("dimension"))
                && position.getX() >= number(identity, "min_x") && position.getX() <= number(identity, "max_x")
                && position.getY() >= number(identity, "min_y") && position.getY() <= number(identity, "max_y")
                && position.getZ() >= number(identity, "min_z") && position.getZ() <= number(identity, "max_z");
    }

    private static int number(Map<String, ?> values, String key) {
        if (!(values.get(key) instanceof Number value)) throw new IllegalArgumentException("Invalid structure boundary " + key);
        return value.intValue();
    }
}
