package dev.taskbound.event;

import java.util.ArrayList;
import java.util.List;

/** FIFO accounting independent of the native appliance and NBT storage. */
public final class FreshFoodBatches {
    public record Batch(long sequence, int count) { }
    private FreshFoodBatches() { }

    public static void reconcile(List<Batch> batches, int actualCount) {
        int known = batches.stream().mapToInt(Batch::count).sum();
        if (known > actualCount) take(batches, known - actualCount);
        else if (known < actualCount) batches.addFirst(new Batch(0, actualCount - known));
    }

    public static List<Batch> take(List<Batch> batches, int count) {
        var taken = new ArrayList<Batch>();
        while (count > 0 && !batches.isEmpty()) {
            Batch first = batches.removeFirst();
            int used = Math.min(count, first.count());
            taken.add(new Batch(first.sequence(), used));
            if (used < first.count()) batches.addFirst(new Batch(first.sequence(), first.count() - used));
            count -= used;
        }
        return taken;
    }
}
