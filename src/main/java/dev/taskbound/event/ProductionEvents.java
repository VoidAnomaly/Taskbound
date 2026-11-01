package dev.taskbound.event;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;

/** Attributed transactions which cannot be reconstructed from inventory pickup events. */
public final class ProductionEvents {
    private static final String CYCLE_OWNER = "taskbound_brewer";
    private static final String CAST_ROD = "taskbound_cast_rod";
    private static final String TREASURE_ORIGIN = "taskbound_fishing_origin";
    private static final Map<AbstractContainerMenu, ItemStack> REAGENT_BEFORE = new WeakHashMap<>();
    private static final Map<AbstractContainerMenu, ItemStack> ANVIL_OUTPUT = new WeakHashMap<>();
    private static final ThreadLocal<Deque<BrewSnapshot>> BREWS = ThreadLocal.withInitial(ArrayDeque::new);

    public static void beforeMenuClick(AbstractContainerMenu menu, ServerPlayer player) {
        if (menu instanceof BrewingStandMenu) REAGENT_BEFORE.put(menu, menu.getSlot(3).getItem().copy());
        prepareAnvil(menu, player);
    }

    public static void afterMenuClick(AbstractContainerMenu menu, ServerPlayer player) {
        ANVIL_OUTPUT.remove(menu);
        ItemStack before = REAGENT_BEFORE.remove(menu);
        if (before == null) return;
        ItemStack after = menu.getSlot(3).getItem();
        if (!after.isEmpty() && (!ItemStack.isSameItemSameComponents(before, after) || after.getCount() > before.getCount())) {
            TaskItemIdentity.setBrewer(after, player.getUUID());
            menu.getSlot(3).setChanged();
        }
    }

    public static void beginBrewCycle(BrewingStandBlockEntity stand) {
        UUID owner = TaskItemIdentity.brewer(stand.getItem(3));
        CompoundTag data = stand.getPersistentData();
        if (owner == null) data.remove(CYCLE_OWNER); else data.putUUID(CYCLE_OWNER, owner);
    }

    public static void beforeBrew(Level level, BlockPos pos, NonNullList<ItemStack> items) {
        UUID owner = level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand
                && stand.getPersistentData().hasUUID(CYCLE_OWNER) ? stand.getPersistentData().getUUID(CYCLE_OWNER) : null;
        if (owner != null && !owner.equals(TaskItemIdentity.brewer(items.get(3)))) owner = null;
        BREWS.get().push(new BrewSnapshot(owner, new ItemStack[] {items.get(0).copy(), items.get(1).copy(), items.get(2).copy()}));
    }

    public static void afterBrew(Level level, BlockPos pos, NonNullList<ItemStack> items) {
        BrewSnapshot before = BREWS.get().pop();
        if (BREWS.get().isEmpty()) BREWS.remove();
        if (!(level instanceof ServerLevel server) || before.owner() == null) return;
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(before.owner());
        if (player == null && server.getPlayerByUUID(before.owner()) instanceof ServerPlayer loadedPlayer) player = loadedPlayer;
        if (player == null) return;
        final ServerPlayer brewer = player;
        var runtime = TaskboundRuntime.get();
        var transformed = new java.util.ArrayList<ItemStack>();
        for (int slot = 0; slot < 3; slot++) {
            ItemStack result = items.get(slot);
            ItemStack original = before.bottles()[slot];
            if (result.isEmpty() || original.isEmpty() || result.getItem() == original.getItem()
                    && result.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                    .equals(original.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY))) continue;
            transformed.add(result);
        }
        record BrewObjective(String taskId, String key) { }
        Map<ItemStack, java.util.Set<BrewObjective>> qualified = new java.util.IdentityHashMap<>();
        // A brewing cycle transforms its bottles together. Mark the entire qualifying batch
        // before any counter completes, and freeze stage eligibility at that transaction boundary.
        for (ActiveTask active : runtime.activeTasks(brewer)) {
            if (dev.taskbound.core.TaskTimer.expired(active, server.getGameTime())) continue;
            for (GeneratedObjective objective : active.task().objectives()) {
                if (!objective.type().equals("brew_potion") || !runtime.isObjectiveEligible(active, objective)
                        || !VanillaObjectiveConditions.matches(objective, brewer, server, pos, ItemStack.EMPTY, false)) continue;
                String stage = VanillaObjectiveConditions.text(objective.parameters(), "provenance_stage");
                var key = new BrewObjective(active.task().taskId(), objective.key());
                for (ItemStack result : transformed) {
                    if (!TaskHandIns.matches(brewer, result, objective.parameters())) continue;
                    qualified.computeIfAbsent(result, ignored -> new java.util.HashSet<>()).add(key);
                    if (stage != null) TaskItemIdentity.mark(result, active.task().taskId(), stage, result.getCount());
                }
            }
        }
        for (ItemStack result : transformed) {
            var eligible = qualified.get(result);
            if (eligible == null) continue;
            runtime.advanceMatching(brewer, (active, objective) -> eligible.contains(
                    new BrewObjective(active.task().taskId(), objective.key())), result.getCount());
        }
    }

    public static void cast(FishingHook hook, ItemStack rod) {
        TaskItemIdentity.ensure(rod);
        hook.getPersistentData().put(CAST_ROD, rod.save(hook.registryAccess()));
    }

    public static void fishingLoot(LootTable table, LootContext context, Consumer<ItemStack> consumer) {
        if ("minecraft:gameplay/fishing/treasure".equals(String.valueOf(table.getLootTableId()))
                && context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof FishingHook hook) {
            table.getRandomItemsRaw(context, stack -> {
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.putUUID(TREASURE_ORIGIN, hook.getUUID());
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                consumer.accept(stack);
            });
        } else table.getRandomItemsRaw(context, consumer);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFish(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FishingHook hook = event.getHookEntity();
        ItemStack rod = ItemStack.parseOptional(player.registryAccess(), hook.getPersistentData().getCompound(CAST_ROD));
        for (ItemStack caught : event.getDrops()) {
            CompoundTag data = caught.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            boolean treasure = data.hasUUID(TREASURE_ORIGIN) && data.getUUID(TREASURE_ORIGIN).equals(hook.getUUID());
            data.remove(TREASURE_ORIGIN);
            if (data.isEmpty()) caught.remove(DataComponents.CUSTOM_DATA); else caught.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            if (event.isCanceled() || rod.isEmpty()) continue;
            TaskboundRuntime.get().advanceMatching(player, (active, objective) -> {
                Map<String, Object> p = objective.parameters();
                if (!objective.type().equals("tracked_fish") || Boolean.TRUE.equals(p.get("treasure")) && !treasure
                        || !VanillaObjectiveConditions.itemMatches(caught, VanillaObjectiveConditions.text(p, "fish_item"), VanillaObjectiveConditions.text(p, "fish_item_tag"))
                        || !VanillaObjectiveConditions.matches(objective, player, player.serverLevel(), hook.blockPosition(), rod, true)) return false;
                String extra = VanillaObjectiveConditions.text(p, "rod_enchantment_extra");
                if (extra != null && VanillaObjectiveConditions.enchantmentLevel(player.serverLevel(), rod, extra)
                        < VanillaObjectiveConditions.number(p, "minimum_rod_enchantment_extra_level", 1)) return false;
                if (Boolean.TRUE.equals(p.get("same_rod"))) {
                    String identity = TaskItemIdentity.get(rod);
                    Object locked = active.progress().additionalData().get("production_rod");
                    if (identity == null || locked != null && !identity.equals(locked)) return false;
                    active.progress().putAdditionalData("production_rod", identity);
                }
                return true;
            }, caught.getCount());
        }
    }

    /** Mark the actual output before vanilla can split/copy it into the cursor or inventory. */
    public static void prepareAnvil(AbstractContainerMenu menu, ServerPlayer player) {
        if (!(menu instanceof AnvilMenu) || menu.getSlot(0).getItem().isEmpty() || menu.getSlot(2).getItem().isEmpty()) return;
        ItemStack input = menu.getSlot(0).getItem();
        ItemStack output = menu.getSlot(2).getItem();
        TaskItemIdentity.inheritIdentity(input, output);
        for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
            for (GeneratedObjective objective : active.task().objectives()) {
                if (!objective.type().equals("tracked_enchant") || !TaskboundRuntime.get().isObjectiveEligible(active, objective)) continue;
                String stage = VanillaObjectiveConditions.text(objective.parameters(), "provenance_stage");
                if (stage != null) TaskItemIdentity.unmark(output, active.task().taskId(), stage);
                if (enchantTransaction(active, objective, player, input, output, false) && stage != null) TaskItemIdentity.mark(output, active.task().taskId(), stage, 1);
            }
        }
        ANVIL_OUTPUT.put(menu, output.copy());
    }

    public static ItemStack anvilOutput(ServerPlayer player, ItemStack eventOutput) {
        ItemStack prepared = ANVIL_OUTPUT.get(player.containerMenu);
        return eventOutput.isEmpty() && prepared != null ? prepared : eventOutput;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAnvil(AnvilRepairEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack output = anvilOutput(player, event.getOutput());
            if (!output.isEmpty()) applyEnchant(player, event.getLeft(), output);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEnchant(PlayerEnchantItemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.getEnchantments().isEmpty()) applyEnchant(player, ItemStack.EMPTY, event.getEnchantedItem());
    }

    private static void applyEnchant(ServerPlayer player, ItemStack before, ItemStack output) {
        TaskItemIdentity.ensure(output);
        TaskboundRuntime.get().advanceMatching(player, (active, objective) -> {
            if (!objective.type().equals("tracked_enchant")) return false;
            boolean done = enchantTransaction(active, objective, player, before, output, true);
            String stage = VanillaObjectiveConditions.text(objective.parameters(), "provenance_stage");
            if (done && stage != null) TaskItemIdentity.mark(output, active.task().taskId(), stage, 1);
            return done;
        }, 1);
    }

    private static boolean enchantTransaction(ActiveTask active, GeneratedObjective objective, ServerPlayer player,
            ItemStack before, ItemStack output, boolean commit) {
        if (!VanillaObjectiveConditions.itemMatches(output, VanillaObjectiveConditions.text(objective.parameters(), "item"), null)
                || !VanillaObjectiveConditions.matches(objective, player, player.serverLevel(), player.blockPosition(), ItemStack.EMPTY, false)) return false;
        String identity = TaskItemIdentity.get(output);
        String stateKey = "production_enchant_" + objective.key();
        Map<String, Object> state = new HashMap<>();
        Object saved = active.progress().additionalData().get(stateKey);
        if (saved instanceof Map<?, ?> map) map.forEach((key, value) -> state.put(key.toString(), value));
        if (identity == null || state.containsKey("identity") && !identity.equals(state.get("identity"))) return false;
        Map<?, ?> required = (Map<?, ?>) objective.parameters().get("required_enchantments");
        boolean gained = false;
        for (var entry : required.entrySet()) {
            String enchantment = entry.getKey().toString();
            int minimum = ((Number) entry.getValue()).intValue();
            int afterLevel = VanillaObjectiveConditions.enchantmentLevel(player.serverLevel(), output, enchantment);
            int beforeLevel = VanillaObjectiveConditions.enchantmentLevel(player.serverLevel(), before, enchantment);
            if (afterLevel >= minimum && afterLevel > beforeLevel) { state.put(enchantment, true); gained = true; }
        }
        if (!gained) return false;
        state.put("identity", identity);
        if (commit) { active.progress().putAdditionalData(stateKey, state); TaskboundRuntime.get().markChanged(player); }
        return required.keySet().stream().allMatch(key -> Boolean.TRUE.equals(state.get(key.toString())))
                && TaskHandIns.matches(player, output, objective.parameters());
    }

    private record BrewSnapshot(UUID owner, ItemStack[] bottles) { }
}
