package dev.taskbound.event;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.core.TaskStatus;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.persistence.TaskboundWorldData;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual world actions for natural materials and the two crafting finales. */
public final class TaskboundWorldEvents {
    private static final String STRUCTURE = "world_structure";
    private static final String PENDING_SHRIEKS = "world_pending_shrieks";
    private static final String CRAFTER_BINDING = "taskbound_crafted";
    private static final ThreadLocal<ArrayDeque<Placement>> PLACING = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<Position, Placement> PLACED_ITEMS = new HashMap<>();
    private static final Map<Position, PistonMove> PISTON_MOVES = new HashMap<>();

    private record Position(ServerLevel level, BlockPos position) { }
    private record Placement(ServerLevel level, BlockPos position, UUID player, ItemStack stack, long tick) { }
    private record PistonMove(ServerLevel level, long tick, Map<BlockPos, Boolean> destinations, List<BlockPos> sources) { }

    public static void beginPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player) {
            PLACING.get().push(new Placement(level, context.getClickedPos().immutable(), player.getUUID(),
                    context.getItemInHand().copy(), level.getGameTime()));
        }
    }

    public static void endPlacement(BlockPlaceContext context, boolean succeeded) {
        if (!(context.getLevel() instanceof ServerLevel) || !(context.getPlayer() instanceof ServerPlayer)) return;
        var stack = PLACING.get();
        if (stack.isEmpty()) return;
        var placed = stack.pop();
        if (stack.isEmpty()) PLACING.remove();
        // The enclosing ItemStack use emits the cancellable placement event after BlockItem returns.
        if (succeeded) PLACED_ITEMS.put(new Position(placed.level(), placed.position()), placed);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            multi.getReplacedBlockSnapshots().forEach(snapshot -> rememberPlacement(level, snapshot.getPos()));
        } else rememberPlacement(level, event.getPos());
        var placement = PLACED_ITEMS.remove(new Position(level, event.getPos()));
        if (placement == null || !(event.getEntity() instanceof ServerPlayer player)
                || !placement.player().equals(player.getUUID()) || placement.tick() != level.getGameTime()) return;
        craftedBlockPlaced(player, event.getPos(), placement.stack());
    }

    private static void rememberPlacement(ServerLevel level, BlockPos position) {
        var data = TaskboundWorldData.get(level.getServer());
        if (needsProvenance(level.getBlockState(position))) data.placed(level, position);
        else data.removed(level, position);
    }

    private static boolean needsProvenance(BlockState state) {
        return state.is(Blocks.ANCIENT_DEBRIS) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                || state.is(Blocks.SCULK_CATALYST) || state.is(Blocks.CALCITE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHarvested(BlockDropsEvent event) {
        if (event.isCanceled()) return;
        var level = event.getLevel();
        var data = TaskboundWorldData.get(level.getServer());
        boolean placed = data.wasPlaced(level, event.getPos());
        data.removed(level, event.getPos());
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) return;
        String block = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        var runtime = TaskboundRuntime.get();
        for (var active : runtime.activeTasks(player)) {
            for (var objective : active.task().objectives()) {
                if (!objective.type().equals("tracked_gather") || !runtime.isObjectiveEligible(active, objective)
                        || !block.equals(objective.parameters().get("block"))
                        || Boolean.TRUE.equals(objective.parameters().get("natural")) && placed
                        || !VanillaObjectiveConditions.matches(objective, player, level, event.getPos(), event.getTool(), false)
                        || !scopeAllows(active, objective, level, event.getPos())) continue;
                if (!bindScope(active, objective, level, event.getPos())) continue;
                runtime.advanceTaskObjective(player, active.task().taskId(), candidate -> candidate.key().equals(objective.key()), 1);
                break;
            }
        }
    }

    /** Called only after CaveVines.use successfully removed berries and produced their item. */
    public static void berriesHarvested(ServerPlayer player, ServerLevel level, BlockPos position) {
        if (player.isCreative() || player.isSpectator()) return;
        TaskboundRuntime.get().advanceMatching(player, objective -> objective.type().equals("harvest_berries")
                && VanillaObjectiveConditions.matches(objective, player, level, position, player.getMainHandItem(), false), 1);
    }

    /** Tag outgoing recipe results before vanilla copies or splits them into the inventory. */
    public static void prepareCraftOutput(ServerPlayer player, ItemStack output) {
        if (output.isEmpty()) return;
        var runtime = TaskboundRuntime.get();
        for (var active : runtime.activeTasks(player)) {
            for (var objective : active.task().objectives()) {
                if (!objective.type().equals("tracked_craft")) continue;
                TaskItemIdentity.unmark(output, active.task().taskId(), objective.key());
                if (runtime.isObjectiveEligible(active, objective)
                        && VanillaObjectiveConditions.itemMatches(output, text(objective, "item"), null)
                        && VanillaObjectiveConditions.matches(objective, player, player.serverLevel(), player.blockPosition(), player.getMainHandItem(), false)
                        && scopeAllows(active, objective, player.serverLevel(), player.blockPosition())) {
                    TaskItemIdentity.mark(output, active.task().taskId(), objective.key(), output.getCount());
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getInventory() instanceof CraftingContainer)
                || !(player.containerMenu instanceof CraftingMenu || player.containerMenu instanceof InventoryMenu)) return;
        var output = event.getCrafting();
        var runtime = TaskboundRuntime.get();
        for (var active : runtime.activeTasks(player)) {
            for (var objective : active.task().objectives()) {
                if (!objective.type().equals("tracked_craft") || !runtime.isObjectiveEligible(active, objective)
                        || !VanillaObjectiveConditions.itemMatches(output, text(objective, "item"), null)
                        || TaskItemIdentity.qualifiedCount(output, active.task().taskId(), objective.key()) < 1
                        || !VanillaObjectiveConditions.matches(objective, player, player.serverLevel(), player.blockPosition(), player.getMainHandItem(), false)
                        || !bindScope(active, objective, player.serverLevel(), player.blockPosition())) continue;
                runtime.advanceTaskObjective(player, active.task().taskId(), candidate -> candidate.key().equals(objective.key()), output.getCount());
                break;
            }
        }
    }

    private static void craftedBlockPlaced(ServerPlayer player, BlockPos position, ItemStack originalStack) {
        var runtime = TaskboundRuntime.get();
        var level = player.serverLevel();
        for (var active : runtime.activeTasks(player)) {
            for (var objective : active.task().objectives()) {
                if (!objective.type().equals("place_crafted") || !runtime.isObjectiveEligible(active, objective)
                        || !VanillaObjectiveConditions.itemMatches(originalStack, text(objective, "item"), null)
                        || level.getBlockState(position).getBlock().asItem() != originalStack.getItem()
                        || TaskItemIdentity.qualifiedCount(originalStack, active.task().taskId(), text(objective, "provenance_stage")) < 1
                        || !VanillaObjectiveConditions.matches(objective, player, level, position, originalStack, false)
                        || !scopeAllows(active, objective, level, position)) continue;
                if (level.getBlockEntity(position) instanceof CrafterBlockEntity crafter) {
                    var binding = new CompoundTag();
                    binding.putUUID("player", player.getUUID());
                    binding.putString("task", active.task().taskId());
                    binding.putString("stage", text(objective, "provenance_stage"));
                    binding.putString("item_identity", TaskItemIdentity.ensure(originalStack));
                    crafter.getPersistentData().put(CRAFTER_BINDING, binding);
                    crafter.setChanged();
                }
                runtime.advanceTaskObjective(player, active.task().taskId(), candidate -> candidate.key().equals(objective.key()), 1);
                break;
            }
        }
    }

    /** Called after a real successful crafter output, not merely a redstone pulse. */
    public static void crafterProduced(ServerLevel level, BlockPos position) {
        if (!(level.getBlockEntity(position) instanceof CrafterBlockEntity crafter)) return;
        var tag = crafter.getPersistentData().getCompound(CRAFTER_BINDING);
        if (!tag.hasUUID("player")) return;
        var player = level.getServer().getPlayerList().getPlayer(tag.getUUID("player"));
        if (player == null || player.serverLevel() != level) return;
        var runtime = TaskboundRuntime.get();
        runtime.advanceTaskObjective(player, tag.getString("task"), objective -> objective.type().equals("activate_crafter")
                && tag.getString("stage").equals(text(objective, "provenance_stage"))
                && VanillaObjectiveConditions.matches(objective, player, level, position, ItemStack.EMPTY, false), 1);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        var runtime = TaskboundRuntime.get();
        for (var active : runtime.activeTasks(player)) {
            var scope = scope(active);
            if (scope == null && active.task().status() == TaskStatus.ACTIVE) {
                for (var objective : active.task().objectives()) {
                    if (Boolean.TRUE.equals(objective.parameters().get("guard_warden"))
                            && bindScope(active, objective, player.serverLevel(), player.blockPosition())) {
                        runtime.markChanged(player);
                        scope = scope(active);
                        break;
                    }
                }
            }
            if (scope == null || hasPendingWardenResponse(active)
                    || TaskStructureIdentity.contains(player.serverLevel(), player.blockPosition(), scope)) continue;
            var identity = scope;
            runtime.advanceTaskObjective(player, active.task().taskId(), objective -> objective.type().equals("structure_exit")
                    && objective.parameters().get("required_structure").equals(identity.get("structure")), 1);
        }
    }

    /** A fourth warning may spawn later, after the player has finished the final objective. */
    public static void wardenWarning(ServerLevel level, BlockPos position, UUID responsiblePlayer) {
        var saved = TaskboundSavedData.get(level.getServer());
        for (var active : saved.player(responsiblePlayer).activeTasks().values()) {
            if (active.task().status() != TaskStatus.ACTIVE) continue;
            for (var objective : active.task().objectives()) {
                if (!Boolean.TRUE.equals(objective.parameters().get("guard_warden"))
                        || !bindScope(active, objective, level, position)) continue;
                var pending = pendingShrieks(active);
                pending.put(shriekerKey(level, position), true);
                active.progress().putAdditionalData(PENDING_SHRIEKS, pending);
                saved.setDirty();
                break;
            }
        }
    }

    public static boolean hasPendingWardenResponse(ActiveTask active) {
        return !pendingShrieks(active).isEmpty();
    }

    /** The shrieker records its responsible player; another player's Warden does not fail this task. */
    public static void wardenSummoned(ServerLevel level, BlockPos shriekerPosition, UUID responsiblePlayer) {
        var saved = TaskboundSavedData.get(level.getServer());
        var state = saved.player(responsiblePlayer);
        for (var active : List.copyOf(state.activeTasks().values())) {
            if (!pendingShrieks(active).containsKey(shriekerKey(level, shriekerPosition))) continue;
            var scope = scope(active);
            if (scope != null && TaskStructureIdentity.contains(level, shriekerPosition, scope)) {
                TaskboundRuntime.get().failActiveTask(level.getServer(), responsiblePlayer, active.task().taskId(),
                        Component.translatable("message.taskbound.warden_awakened").getString());
            }
        }
    }

    public static void wardenResponseFinished(ServerLevel level, BlockPos position, UUID responsiblePlayer) {
        var saved = TaskboundSavedData.get(level.getServer());
        for (var active : saved.player(responsiblePlayer).activeTasks().values()) {
            var pending = pendingShrieks(active);
            if (pending.remove(shriekerKey(level, position)) != null) {
                active.progress().putAdditionalData(PENDING_SHRIEKS, pending);
                saved.setDirty();
            }
        }
    }

    private static String shriekerKey(ServerLevel level, BlockPos position) {
        return level.dimension().location() + "/" + position.asLong();
    }

    private static Map<String, Object> pendingShrieks(ActiveTask active) {
        var result = new LinkedHashMap<String, Object>();
        if (active.progress().additionalData().get(PENDING_SHRIEKS) instanceof Map<?, ?> map) {
            map.forEach((key, value) -> { if (key instanceof String text) result.put(text, value); });
        }
        return result;
    }

    private static boolean scopeAllows(ActiveTask active, GeneratedObjective objective, ServerLevel level, BlockPos position) {
        if (!Boolean.TRUE.equals(objective.parameters().get("same_structure"))) return true;
        var existing = scope(active);
        return existing == null || TaskStructureIdentity.contains(level, position, existing);
    }

    private static boolean bindScope(ActiveTask active, GeneratedObjective objective, ServerLevel level, BlockPos position) {
        if (!scopeAllows(active, objective, level, position)) return false;
        if (Boolean.TRUE.equals(objective.parameters().get("same_structure")) && scope(active) == null) {
            var identity = TaskStructureIdentity.capture(level, position, text(objective, "required_structure"));
            if (identity == null) return false;
            active.progress().putAdditionalData(STRUCTURE, identity);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> scope(ActiveTask active) {
        return active.progress().additionalData().get(STRUCTURE) instanceof Map<?, ?> value ? (Map<String, ?>) value : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void pistonPre(PistonEvent.Pre event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var helper = event.getStructureHelper();
        if (helper == null || !helper.resolve()) return;
        var direction = event.getPistonMoveType().isExtend ? event.getDirection() : event.getDirection().getOpposite();
        var data = TaskboundWorldData.get(level.getServer());
        var destinations = new HashMap<BlockPos, Boolean>();
        for (var from : helper.getToPush()) {
            destinations.put(from.relative(direction).immutable(), data.wasPlaced(level, from));
        }
        if (!destinations.isEmpty()) PISTON_MOVES.put(new Position(level, event.getPos()),
                new PistonMove(level, level.getGameTime(), destinations, List.copyOf(helper.getToPush())));
    }

    @SubscribeEvent
    public void pistonPost(PistonEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var movement = PISTON_MOVES.remove(new Position(level, event.getPos()));
        if (movement == null) return;
        var data = TaskboundWorldData.get(level.getServer());
        movement.sources().forEach(from -> data.removed(level, from));
        movement.destinations().forEach((to, wasPlaced) -> { if (wasPlaced) data.placed(level, to); else data.removed(level, to); });
    }

    @SubscribeEvent
    public void cleanupTick(ServerTickEvent.Post event) {
        PLACED_ITEMS.entrySet().removeIf(entry -> entry.getValue().tick() < entry.getKey().level().getGameTime());
        PISTON_MOVES.entrySet().removeIf(entry -> entry.getValue().tick() < entry.getKey().level().getGameTime());
    }

    @SubscribeEvent
    public void serverStopped(ServerStoppedEvent event) {
        PLACED_ITEMS.clear();
        PISTON_MOVES.clear();
        PLACING.remove();
    }

    private static String text(GeneratedObjective objective, String key) {
        return VanillaObjectiveConditions.text(objective.parameters(), key);
    }
}
