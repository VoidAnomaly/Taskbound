package dev.taskbound.persistence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Tracks only the scarce block positions for which a task requires placement provenance. */
public final class TaskboundWorldData extends SavedData {
    private final Map<String, Set<Long>> placed = new HashMap<>();

    public static TaskboundWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TaskboundWorldData::new, TaskboundWorldData::load, null), "taskbound_world");
    }

    public boolean wasPlaced(ServerLevel level, BlockPos position) {
        return placed.getOrDefault(level.dimension().location().toString(), Set.of()).contains(position.asLong());
    }

    public void placed(ServerLevel level, BlockPos position) {
        if (placed.computeIfAbsent(level.dimension().location().toString(), ignored -> new HashSet<>()).add(position.asLong())) setDirty();
    }

    public void removed(ServerLevel level, BlockPos position) {
        var positions = placed.get(level.dimension().location().toString());
        if (positions != null && positions.remove(position.asLong())) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var dimensions = new CompoundTag();
        placed.forEach((key, positions) -> dimensions.putLongArray(key, positions.stream().mapToLong(Long::longValue).sorted().toArray()));
        tag.put("placed_blocks", dimensions);
        return tag;
    }

    public static TaskboundWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new TaskboundWorldData();
        var dimensions = tag.getCompound("placed_blocks");
        for (String dimension : dimensions.getAllKeys()) {
            var positions = new HashSet<Long>();
            for (long position : dimensions.getLongArray(dimension)) positions.add(position);
            data.placed.put(dimension, positions);
        }
        return data;
    }
}
