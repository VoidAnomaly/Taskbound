package dev.taskbound.event;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Server-authored identity and qualification marks; preserves unrelated custom data. */
public final class TaskItemIdentity {
    private static final String ROOT = "taskbound";

    private TaskItemIdentity() { }

    public static String get(ItemStack stack) {
        String id = data(stack).getString("identity");
        return id.isEmpty() ? null : id;
    }

    public static String ensure(ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Empty stacks cannot have an identity");
        String existing = get(stack);
        if (existing != null) return existing;
        String id = UUID.randomUUID().toString();
        update(stack, tag -> tag.putString("identity", id));
        return id;
    }

    public static void inheritIdentity(ItemStack from, ItemStack to) {
        String identity = ensure(from);
        update(to, data -> data.putString("identity", identity));
    }

    public static void mark(ItemStack stack, String taskId, String stageKey, int count) {
        if (stack.isEmpty() || count < 1) return;
        update(stack, tag -> {
            CompoundTag tasks = tag.getCompound("qualified");
            CompoundTag stages = tasks.getCompound(taskId);
            stages.putInt(stageKey, Math.min(stack.getCount(), count));
            tasks.put(taskId, stages);
            tag.put("qualified", tasks);
        });
    }

    public static int qualifiedCount(ItemStack stack, String taskId, String stageKey) {
        return Math.min(stack.getCount(), data(stack).getCompound("qualified").getCompound(taskId).getInt(stageKey));
    }

    public static void unmark(ItemStack stack, String taskId, String stageKey) {
        update(stack, tag -> {
            CompoundTag tasks = tag.getCompound("qualified");
            CompoundTag stages = tasks.getCompound(taskId);
            stages.remove(stageKey);
            if (stages.isEmpty()) tasks.remove(taskId); else tasks.put(taskId, stages);
            if (tasks.isEmpty()) tag.remove("qualified"); else tag.put("qualified", tasks);
        });
    }

    public static void clearMarks(ItemStack stack, String taskId) {
        update(stack, tag -> {
            CompoundTag tasks = tag.getCompound("qualified");
            tasks.remove(taskId);
            if (tasks.isEmpty()) tag.remove("qualified"); else tag.put("qualified", tasks);
        });
    }

    public static void setBrewer(ItemStack ingredient, UUID playerId) {
        update(ingredient, tag -> tag.putUUID("brewer", playerId));
    }

    public static UUID brewer(ItemStack ingredient) {
        CompoundTag tag = data(ingredient);
        return tag.hasUUID("brewer") ? tag.getUUID("brewer") : null;
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(ROOT);
    }

    private static void update(ItemStack stack, java.util.function.Consumer<CompoundTag> change) {
        if (stack.isEmpty()) return;
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag tag = custom.getCompound(ROOT);
        change.accept(tag);
        if (tag.isEmpty()) custom.remove(ROOT); else custom.put(ROOT, tag);
        if (custom.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
}
