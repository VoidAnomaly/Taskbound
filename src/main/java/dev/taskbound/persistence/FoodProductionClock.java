package dev.taskbound.persistence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Orders acceptance and real cooking even when both happen in the same server tick. */
public final class FoodProductionClock extends SavedData {
    private long sequence;
    public static long next(MinecraftServer server) {
        FoodProductionClock clock = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(FoodProductionClock::new, FoodProductionClock::load, null), "taskbound_food_clock");
        clock.sequence = Math.addExact(clock.sequence, 1);
        clock.setDirty();
        return clock.sequence;
    }
    private static FoodProductionClock load(CompoundTag tag, HolderLookup.Provider provider) {
        var clock = new FoodProductionClock();
        clock.sequence = Math.max(0, tag.getLong("sequence"));
        return clock;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("sequence", sequence);
        return tag;
    }
}
