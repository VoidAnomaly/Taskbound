package dev.taskbound.porter;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.item.TaskPackageItem;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.registry.TaskboundContent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;

/** A task-bound vanilla compass; its synchronized target also supplies optional client waypoints. */
public final class PorterNavigation {
    private static final String BINDING = "taskbound_porter_guide";
    private static final String ISSUED = "porter_guide_issued";

    private PorterNavigation() { }

    public static Optional<String> guideTaskId(ItemStack stack) {
        return stack.is(TaskboundContent.DELIVERY_COMPASS.get()) && binding(stack).hasUUID("player")
                ? TaskPackageItem.taskId(stack).map(UUID::toString) : Optional.empty();
    }

    public static Optional<UUID> guideOwnerId(ItemStack stack) {
        var tag = binding(stack);
        return guideTaskId(stack).isPresent() ? Optional.of(tag.getUUID("player")) : Optional.empty();
    }

    /** Issue once per accepted task, including existing deliveries from before guides were added. */
    public static void update(ServerPlayer player, ActiveTask active) {
        var objective = PorterTasks.objective(active.task()).orElse(null);
        if (objective == null) return;
        var stops = PorterTasks.stops(objective);
        if (stops.isEmpty()) return;
        int index = Math.min(active.progress().progressFor(objective.key()), stops.size() - 1);
        var stop = stops.get(index);
        if (!Boolean.TRUE.equals(active.progress().additionalData().get(ISSUED))) {
            ItemStack guide = new ItemStack(TaskboundContent.DELIVERY_COMPASS.get());
            guide.set(TaskboundContent.PACKAGE_TASK_UUID.get(), UUID.fromString(active.task().taskId()));
            CompoundTag binding = new CompoundTag();
            binding.putUUID("player", player.getUUID());
            binding.putString("nonce", String.valueOf(active.progress().additionalData().get("porter_nonce")));
            CompoundTag data = new CompoundTag();
            data.put(BINDING, binding);
            guide.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            target(guide, active, stop);
            active.progress().putAdditionalData(ISSUED, true);
            TaskboundSavedData.get(player.server).changed();
            if (!player.getInventory().add(guide)) player.drop(guide, false);
        }
        for (ItemStack stack : player.getInventory().items) {
            if (matches(stack, player, active)) target(stack, active, stop);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (matches(stack, player, active)) target(stack, active, stop);
        }
    }

    /** Also cleans up an old guide picked up after its task ended or removed while the player was offline. */
    public static void reconcile(ServerPlayer player, List<ActiveTask> tasks) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (guideOwnerId(stack).filter(player.getUUID()::equals).isEmpty()) continue;
            boolean valid = tasks.stream().anyMatch(active -> PorterTasks.objective(active.task()).isPresent()
                    && matches(stack, player, active));
            if (!valid) player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        tasks.forEach(active -> update(player, active));
    }

    public static void remove(ServerPlayer player, String taskId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (guideTaskId(player.getInventory().getItem(slot)).filter(taskId::equals).isPresent())
                player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    private static boolean matches(ItemStack stack, ServerPlayer player, ActiveTask active) {
        return guideTaskId(stack).filter(active.task().taskId()::equals).isPresent()
                && guideOwnerId(stack).filter(player.getUUID()::equals).isPresent()
                && binding(stack).getString("nonce").equals(String.valueOf(active.progress().additionalData().get("porter_nonce")));
    }

    private static CompoundTag binding(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(BINDING);
    }

    private static void target(ItemStack stack, ActiveTask active, Map<String, Object> stop) {
        String dimension = (String) stop.get("dimension");
        BlockPos position = new BlockPos(((Number) stop.get("x")).intValue(), ((Number) stop.get("y")).intValue(),
                ((Number) stop.get("z")).intValue());
        // No physical lodestone is required, and vanilla must not invalidate a Task Board target.
        var tracker = new LodestoneTracker(Optional.of(GlobalPos.of(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension)), position)), false);
        if (tracker.equals(stack.get(DataComponents.LODESTONE_TRACKER))) return;
        stack.set(DataComponents.LODESTONE_TRACKER, tracker);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("taskbound.porter.guide"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(active.task().title()),
                Component.translatable("taskbound.porter.guide_destination", dimension, position.getX(), position.getY(), position.getZ()),
                Component.translatable("taskbound.porter.guide_hint"))));
    }
}
