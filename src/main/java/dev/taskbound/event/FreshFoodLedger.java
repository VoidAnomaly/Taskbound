package dev.taskbound.event;

import static dev.taskbound.event.FreshFoodBatches.reconcile;
import static dev.taskbound.event.FreshFoodBatches.take;
import dev.taskbound.event.FreshFoodBatches.Batch;
import dev.taskbound.persistence.FoodProductionClock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** FIFO production batches stay on the appliance, preserving ordinary item stacking/recipes. */
public final class FreshFoodLedger {
    private static final String ROOT = "taskbound_food_batches";
    private FreshFoodLedger() { }

    public static void produced(BlockEntity station, String buffer, ItemStack before, ItemStack after) {
        if (!(station.getLevel() instanceof ServerLevel server) || after.isEmpty()) return;
        int beforeCount = ItemStack.isSameItemSameComponents(before, after) ? before.getCount() : 0;
        int made = after.getCount() - beforeCount;
        if (made <= 0) return;
        String item = item(after);
        var batches = read(station, buffer, item);
        reconcile(batches, beforeCount);
        batches.add(new Batch(FoodProductionClock.next(server.getServer()), made));
        write(station, buffer, item, batches);
    }

    public static void transferred(BlockEntity station, String source, String destination,
            ItemStack sourceBefore, ItemStack destinationBefore, ItemStack sourceAfter, ItemStack destinationAfter) {
        if (!(station.getLevel() instanceof ServerLevel) || sourceBefore.isEmpty()) return;
        int moved = sourceBefore.getCount() - sourceAfter.getCount();
        if (moved <= 0 || destinationAfter.getCount() - destinationBefore.getCount() != moved
                || !ItemStack.isSameItemSameComponents(sourceBefore, destinationAfter)) return;
        String item = item(sourceBefore);
        var from = read(station, source, item);
        reconcile(from, sourceBefore.getCount());
        var to = read(station, destination, item);
        reconcile(to, destinationBefore.isEmpty() ? 0 : destinationBefore.getCount());
        to.addAll(take(from, moved));
        write(station, source, item, from);
        write(station, destination, item, to);
    }

    public static void collected(ServerPlayer player, BlockEntity station, String buffer, ItemStack remaining,
            int count, String appliance) {
        if (!(station.getLevel() instanceof ServerLevel) || count <= 0) return;
        var entry = station.getPersistentData().getCompound(ROOT).getCompound(buffer);
        String item = remaining.isEmpty() ? entry.getString("item") : item(remaining);
        if (item.isEmpty()) return;
        var batches = read(station, buffer, item);
        // Account for hoppers/other collectors before consuming this exact player transaction.
        reconcile(batches, remaining.getCount() + count);
        List<Batch> collected = take(batches, count);
        write(station, buffer, item, batches);
        for (Batch batch : collected) if (batch.sequence() > 0) {
            FarmerGameplayEvents.cooked(player, item, appliance, batch.count(), batch.sequence(), station.getBlockPos());
        }
    }

    private static ArrayList<Batch> read(BlockEntity station, String buffer, String item) {
        var entry = station.getPersistentData().getCompound(ROOT).getCompound(buffer);
        var result = new ArrayList<Batch>();
        if (!item.equals(entry.getString("item"))) return result;
        for (Tag value : entry.getList("batches", Tag.TAG_COMPOUND)) {
            CompoundTag batch = (CompoundTag) value;
            if (batch.getInt("count") > 0) result.add(new Batch(Math.max(0, batch.getLong("sequence")), batch.getInt("count")));
        }
        return result;
    }

    private static void write(BlockEntity station, String buffer, String item, List<Batch> batches) {
        var root = station.getPersistentData().getCompound(ROOT);
        var entry = new CompoundTag();
        entry.putString("item", item);
        var list = new ListTag();
        for (Batch batch : batches) {
            var tag = new CompoundTag();
            tag.putLong("sequence", batch.sequence()); tag.putInt("count", batch.count()); list.add(tag);
        }
        entry.put("batches", list); root.put(buffer, entry); station.getPersistentData().put(ROOT, root);
        station.setChanged();
    }

    private static String item(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
}
