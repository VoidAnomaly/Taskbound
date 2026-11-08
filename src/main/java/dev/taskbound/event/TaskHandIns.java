package dev.taskbound.event;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedObjective;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/** Read-only preflight and atomic consumption of the explicitly selected submission container. */
public final class TaskHandIns {
    private TaskHandIns() { }

    public static boolean hasHandIns(ActiveTask active) {
        return active.task().objectives().stream().anyMatch(objective -> objective.type().equals("hand_in_item"));
    }

    public static boolean canSubmit(ServerPlayer player, ActiveTask active) { return plan(player, active).isPresent(); }

    public static boolean nonHandInsComplete(ActiveTask active) {
        return active.task().objectives().stream().filter(objective -> !objective.type().equals("hand_in_item"))
                .allMatch(objective -> active.progress().progressFor(objective.key())
                        >= VanillaObjectiveConditions.number(objective.parameters(), "required_count", 1));
    }

    /** Inventory inspection is for readiness only; interactive claims use the menu's offered container. */
    public static Optional<Submission> plan(ServerPlayer player, ActiveTask active) {
        return plan(player, active, player.getInventory());
    }

    public static Optional<Submission> plan(ServerPlayer player, ActiveTask active, Container source) {
        if (!hasHandIns(active) || !active.task().playerId().equals(player.getStringUUID())
                || !nonHandInsComplete(active)) return Optional.empty();
        int size = source.getContainerSize();
        int[] reserved = new int[size];
        for (GeneratedObjective objective : active.task().objectives()) {
            if (!objective.type().equals("hand_in_item")) continue;
            if (!VanillaObjectiveConditions.matches(objective, player, player.serverLevel(),
                    player.blockPosition(), player.getMainHandItem(), false)) return Optional.empty();
            int remaining = VanillaObjectiveConditions.number(objective.parameters(), "required_count", 1);
            for (int slot = 0; slot < size && remaining > 0; slot++) {
                ItemStack stack = source.getItem(slot);
                if (!matches(player, stack, objective.parameters())) continue;
                int eligible = stack.getCount();
                String stage = VanillaObjectiveConditions.text(objective.parameters(), "provenance_stage");
                if (stage != null) eligible = TaskItemIdentity.qualifiedCount(stack, active.task().taskId(), stage);
                int take = Math.min(remaining, Math.max(0, eligible - reserved[slot]));
                reserved[slot] += take;
                remaining -= take;
            }
            if (remaining > 0) return Optional.empty();
        }
        var takes = new ArrayList<Take>();
        for (int slot = 0; slot < size; slot++) if (reserved[slot] > 0) takes.add(new Take(slot, source.getItem(slot).copy(), reserved[slot]));
        return Optional.of(new Submission(player, source, List.copyOf(takes)));
    }

    public static boolean matches(Player player, ItemStack stack, Map<String, Object> parameters) {
        if (stack.isEmpty() || !VanillaObjectiveConditions.itemMatches(stack, VanillaObjectiveConditions.text(parameters, "item"),
                VanillaObjectiveConditions.text(parameters, "item_tag"))) return false;
        String potion = VanillaObjectiveConditions.text(parameters, "potion");
        if (potion != null && !stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion()
                .flatMap(holder -> holder.unwrapKey()).map(key -> key.location().toString().equals(potion)).orElse(false)) return false;
        if (parameters.get("required_enchantments") instanceof Map<?, ?> enchantments) {
            for (var entry : enchantments.entrySet()) {
                int level = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .getHolder(ResourceLocation.parse(entry.getKey().toString()))
                        .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
                if (level < ((Number) entry.getValue()).intValue()) return false;
            }
        }
        return true;
    }

    private record Take(int slot, ItemStack snapshot, int count) { }

    public static final class Submission {
        private final ServerPlayer player;
        private final Container source;
        private final List<Take> takes;
        private boolean consumed;
        private Submission(ServerPlayer player, Container source, List<Take> takes) {
            this.player = player;
            this.source = source;
            this.takes = takes;
        }

        /** Call only after reward/reputation preflight succeeds, immediately before committing the claim. */
        public void consume() {
            if (consumed) throw new IllegalStateException("This submission was already consumed");
            for (Take take : takes) {
                ItemStack current = source.getItem(take.slot());
                if (!ItemStack.isSameItemSameComponents(current, take.snapshot()) || current.getCount() < take.count()) throw new IllegalStateException("Hand-in inventory changed; try again");
            }
            for (Take take : takes) source.getItem(take.slot()).shrink(take.count());
            consumed = true;
            source.setChanged();
            player.containerMenu.broadcastChanges();
        }
    }
}
