package dev.taskbound.porter;

import dev.taskbound.block.TaskBoardBlockEntity;
import dev.taskbound.config.TaskboundServerConfig;
import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.core.GeneratedTask;
import dev.taskbound.core.GeneratedTaskDraft;
import dev.taskbound.core.TaskStatus;
import dev.taskbound.core.TaskTimer;
import dev.taskbound.item.TaskPackageItem;
import dev.taskbound.network.TaskboundNetworking;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.registry.TaskboundContent;
import dev.taskbound.runtime.TaskboundRuntime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Accepted courier manifests and their transactional board interactions. */
public final class PorterTasks {
    private static final String BINDING = "taskbound_porter";
    private static final String DAMAGE = "porter_damage";
    private static final ThreadLocal<Integer> PORTAL_DEPTH = ThreadLocal.withInitial(() -> 0);

    private PorterTasks() { }

    public static void visitBoard(ServerPlayer player, TaskBoardBlockEntity board) {
        if (board.getLevel() != player.serverLevel() || board.isRemoved()
                || player.distanceToSqr(board.getBlockPos().getCenter()) > 64
                || player.serverLevel().getBlockEntity(board.getBlockPos()) != board) return;
        var saved = TaskboundSavedData.get(player.server);
        saved.registerBoard(board.boardId(), player.serverLevel(), board.getBlockPos());
        saved.visitBoard(player.getUUID(), board.boardId(), saved.boardBiome(board.boardId()));
    }

    public static Optional<GeneratedObjective> objective(GeneratedTask task) {
        return task.objectives().stream().filter(o -> PorterObjectiveRules.supports(o.type())).findFirst();
    }

    public static boolean eligible(ServerPlayer player, TaskboundSavedData.BoardRecord origin, Map<String, ?> parameters) {
        return route(player, origin, parameters).isPresent();
    }

    private static Optional<PorterRoutePlanner.Route> route(ServerPlayer player, TaskboundSavedData.BoardRecord origin, Map<String, ?> parameters) {
        var saved = TaskboundSavedData.get(player.server);
        List<PorterRoutePlanner.Board> known = saved.boards().stream()
                .filter(b -> b.dimension().equals(origin.dimension()) || saved.portalDimensions(player.getUUID()).contains(b.dimension()))
                .map(b -> routeBoard(saved, b)).toList();
        return PorterRoutePlanner.plan(routeBoard(saved, origin), known, parameters,
                chosen -> routeAvailable(player, routeKey(routeBoard(saved, origin), chosen.stops())));
    }

    private static PorterRoutePlanner.Board routeBoard(TaskboundSavedData saved, TaskboundSavedData.BoardRecord board) {
        return new PorterRoutePlanner.Board(board.boardId().toString(), board.dimension(), board.position().getX(),
                board.position().getY(), board.position().getZ(), saved.boardBiome(board.boardId()));
    }

    public static GeneratedTaskDraft customize(ServerPlayer player, TaskboundSavedData.BoardRecord origin, GeneratedTaskDraft draft) {
        if (draft.objectives().stream().noneMatch(o -> PorterObjectiveRules.supports(o.type()))) return draft;
        List<GeneratedObjective> objectives = new ArrayList<>();
        Map<String, Object> conditions = new LinkedHashMap<>(draft.conditions());
        for (GeneratedObjective original : draft.objectives()) {
            if (!PorterObjectiveRules.supports(original.type())) { objectives.add(original); continue; }
            var chosen = route(player, origin, original.parameters()).orElseThrow(() -> new IllegalStateException("No eligible known delivery route"));
            Map<String, Object> parameters = new LinkedHashMap<>(original.parameters());
            parameters.put("route", chosen.stops().stream().map(PorterTasks::stopMap).toList());
            parameters.put("initial_cargo", chosen.initialCargo());
            parameters.put("required_count", chosen.stops().size());
            parameters.put("route_key", routeKey(routeBoard(TaskboundSavedData.get(player.server), origin), chosen.stops()));
            parameters.put("teleportation_allowed", Boolean.TRUE.equals(parameters.get("teleportation_allowed"))
                    || TaskboundServerConfig.PORTER_ALLOW_TELEPORTATION.get());
            conditions.put("porter_route_distance", chosen.distance());
            conditions.put("porter_route_legs", chosen.stops().size());
            if (Boolean.TRUE.equals(parameters.get("no_death")) || parameters.containsKey("maximum_damage")) conditions.put("no_death", true);
            objectives.add(new GeneratedObjective(original.key(), original.type(), parameters));
        }
        return new GeneratedTaskDraft(draft.taskId(), draft.playerId(), draft.boardId(), draft.giverId(), draft.categoryId(),
                draft.templateId(), draft.variantId(), draft.displayText(), draft.title(), draft.description(), objectives,
                conditions, draft.requiredReputationLevel(), draft.baseDifficulty(), draft.difficultyComponents(), draft.acceptedAt(), draft.expiresAt());
    }

    private static Map<String, Object> stopMap(PorterRoutePlanner.Stop stop) {
        var board = stop.board();
        return Map.of("board_id", board.id(), "dimension", board.dimension(), "x", board.x(), "y", board.y(), "z", board.z(),
                "deliver", stop.deliver(), "collect", stop.collect(), "carry", stop.carry());
    }

    private static String routeKey(PorterRoutePlanner.Board origin, List<PorterRoutePlanner.Stop> stops) {
        var locations = new java.util.TreeSet<String>();
        locations.add(locationKey(origin));
        stops.forEach(stop -> locations.add(locationKey(stop.board())));
        return "taskbound:porter_route/" + UUID.nameUUIDFromBytes(String.join("|", locations).getBytes(StandardCharsets.UTF_8));
    }

    private static String locationKey(PorterRoutePlanner.Board board) {
        return board.dimension() + ":" + board.x() + ":" + board.y() + ":" + board.z();
    }

    private static boolean routeAvailable(ServerPlayer player, String key) {
        var state = TaskboundSavedData.get(player.server).player(player.getUUID());
        if (!state.canOffer(key, Map.of(), player.serverLevel().getGameTime(), TaskboundServerConfig.REPEAT_COOLDOWN_TICKS.get())) return false;
        return state.activeTasks().values().stream().noneMatch(active -> objective(active.task())
                .map(o -> key.equals(o.parameters().get("route_key"))).orElse(false));
    }

    public static void validateAcceptance(ServerPlayer player, GeneratedTask task) {
        objective(task).ifPresent(objective -> {
            List<Map<String, Object>> route = stops(objective);
            if (route.isEmpty() || route.size() > 5 || !routeAvailable(player, string(objective.parameters(), "route_key"))) {
                throw new IllegalStateException(Component.translatable("taskbound.porter.route_unavailable").getString());
            }
            for (Map<String, Object> stop : route) {
                if (!boardValid(player, stop, true)) throw new IllegalStateException(Component.translatable("taskbound.porter.board_missing").getString());
            }
        });
    }

    public static void accepted(ServerPlayer player, ActiveTask task) {
        objective(task.task()).ifPresent(objective -> {
            task.progress().putAdditionalData("porter_nonce", UUID.randomUUID().toString());
            for (String cargo : strings(objective.parameters().get("initial_cargo"))) give(player, parcel(task, cargo));
            PorterNavigation.update(player, task);
            TaskboundSavedData.get(player.server).changed();
        });
    }

    public static void validateClaim(ServerPlayer player, ActiveTask task, TaskBoardBlockEntity board) {
        objective(task.task()).ifPresent(objective -> {
            List<Map<String, Object>> route = stops(objective);
            if (route.isEmpty() || task.progress().progressFor(objective.key()) != route.size()
                    || !board.boardId().toString().equals(string(route.getLast(), "board_id"))) {
                throw new IllegalStateException(Component.translatable("taskbound.porter.claim_destination").getString());
            }
        });
    }

    public static void claimed(ServerPlayer player, GeneratedTask task) {
        objective(task).ifPresent(objective -> {
            var saved = TaskboundSavedData.get(player.server);
            saved.player(player.getUUID()).recordTemplateCompletion(string(objective.parameters(), "route_key"), player.serverLevel().getGameTime());
            removeCargo(player, task.taskId());
            saved.changed();
        });
    }

    /** Server-side interaction: cargo is consumed only after the objective commits successfully. */
    public static boolean interact(ServerPlayer player, TaskBoardBlockEntity board) {
        if (!player.isAlive() || player.isSpectator() || player.isCreative() || board.isRemoved()
                || board.getLevel() != player.serverLevel() || player.distanceToSqr(board.getBlockPos().getCenter()) > 64) return false;
        visitBoard(player, board);
        var runtime = TaskboundRuntime.get();
        for (ActiveTask active : runtime.activeTasks(player)) {
            Optional<GeneratedObjective> found = objective(active.task());
            if (found.isEmpty()) continue;
            GeneratedObjective objective = found.get();
            List<Map<String, Object>> route = stops(objective);
            int index = active.progress().progressFor(objective.key());
            if (index >= route.size()) continue;
            Map<String, Object> stop = route.get(index);
            if (!board.boardId().toString().equals(string(stop, "board_id"))) continue;
            if (TaskTimer.expired(active, player.serverLevel().getGameTime())) {
                runtime.failActiveTask(player, active.task().taskId(), Component.translatable("taskbound.porter.expired"));
                return true;
            }
            if (!runtime.isObjectiveEligible(active, objective)) continue;
            if (!remainingBoardsValid(player, objective, index)) {
                cancelInvalid(player, active);
                return true;
            }
            if (index == route.size() - 1 && !arrivalTime(player.serverLevel(), objective.parameters())) {
                player.sendSystemMessage(Component.translatable("taskbound.porter.arrival_time", string(objective.parameters(), "arrival_time")));
                return true;
            }
            List<ItemStack> consumption = new ArrayList<>();
            for (String cargo : strings(stop.get("deliver"))) {
                ItemStack item = findParcel(player, active, cargo);
                if (item.isEmpty()) { player.sendSystemMessage(Component.translatable("taskbound.porter.missing_cargo")); return true; }
                consumption.add(item);
            }
            for (String cargo : strings(stop.get("carry"))) {
                if (findParcel(player, active, cargo).isEmpty()) {
                    player.sendSystemMessage(Component.translatable("taskbound.porter.missing_cargo")); return true;
                }
            }
            if (!runtime.advanceTaskObjective(player, active.task().taskId(), o -> o.key().equals(objective.key()), 1)) return true;
            consumption.forEach(stack -> stack.shrink(1));
            for (String cargo : strings(stop.get("collect"))) give(player, parcel(active, cargo));
            if (index == route.size() - 1) {
                runtime.claim(player, active.task().taskId(), board);
            } else {
                PorterNavigation.update(player, active);
                player.sendSystemMessage(Component.translatable("taskbound.porter.stop_complete", index + 1, route.size()));
            }
            TaskboundSavedData.get(player.server).changed();
            TaskboundNetworking.syncPlayer(player);
            return true;
        }
        return false;
    }

    private static boolean arrivalTime(ServerLevel level, Map<String, Object> parameters) {
        String time = string(parameters, "arrival_time");
        long dayTime = Math.floorMod(level.getDayTime(), 24000);
        return time.isEmpty() || time.equals("day") && dayTime < 12000 || time.equals("night") && dayTime >= 13000 && dayTime < 23000;
    }

    public static void tick(ServerPlayer player) {
        var runtime = TaskboundRuntime.get();
        if (player.tickCount % 20 == 0) PorterNavigation.reconcile(player, runtime.activeTasks(player));
        for (ActiveTask active : runtime.activeTasks(player)) {
            Optional<GeneratedObjective> found = objective(active.task());
            if (found.isEmpty()) continue;
            GeneratedObjective objective = found.get();
            if (player.tickCount % 20 == 0) refreshParcelStamps(player, active);
            if (active.task().status() == TaskStatus.ACTIVE && Boolean.TRUE.equals(objective.parameters().get("on_foot"))
                    && (player.isPassenger() || player.isFallFlying() || player.getAbilities().flying)) {
                runtime.failActiveTask(player, active.task().taskId(), Component.translatable("taskbound.porter.on_foot_failed"));
                continue;
            }
            int next = Math.min(active.progress().progressFor(objective.key()), stops(objective).size() - 1);
            if (player.tickCount % 40 == 0 && !remainingBoardsValid(player, objective, Math.max(0, next))) cancelInvalid(player, active);
        }
    }

    public static void damaged(ServerPlayer player, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return;
        var runtime = TaskboundRuntime.get();
        for (ActiveTask active : runtime.activeTasks(player)) {
            objective(active.task()).ifPresent(objective -> {
                if (!(objective.parameters().get("maximum_damage") instanceof Number maximum) || active.task().status() != TaskStatus.ACTIVE) return;
                double damage = number(active.progress().additionalData(), DAMAGE, 0) + amount;
                active.progress().putAdditionalData(DAMAGE, damage);
                runtime.markChanged(player);
                if (damage > maximum.doubleValue()) runtime.failActiveTask(player, active.task().taskId(), Component.translatable("taskbound.porter.fragile_failed"));
            });
        }
    }

    public static void teleported(ServerPlayer player) {
        for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
            if (active.task().status() != TaskStatus.ACTIVE) continue;
            objective(active.task()).ifPresent(o -> {
                if (!Boolean.TRUE.equals(o.parameters().get("teleportation_allowed")) || Boolean.TRUE.equals(o.parameters().get("on_foot"))) {
                    TaskboundRuntime.get().failActiveTask(player, active.task().taskId(), Component.translatable("taskbound.porter.teleport_failed"));
                }
            });
        }
    }

    public static void dimensionTravel(ServerPlayer player) {
        if (PORTAL_DEPTH.get() > 0) TaskboundSavedData.get(player.server)
                .visitPortalDimension(player.getUUID(), player.level().dimension().location().toString());
        for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
            if (active.task().status() != TaskStatus.ACTIVE) continue;
            objective(active.task()).ifPresent(o -> {
                if (!Boolean.TRUE.equals(o.parameters().get("dimensional_travel_allowed"))) {
                    TaskboundRuntime.get().failActiveTask(player, active.task().taskId(), Component.translatable("taskbound.porter.dimension_failed"));
                } else if (PORTAL_DEPTH.get() == 0) teleported(player);
            });
        }
    }

    public static void enterPortal() { PORTAL_DEPTH.set(PORTAL_DEPTH.get() + 1); }
    public static void leavePortal() { int depth = PORTAL_DEPTH.get() - 1; if (depth <= 0) PORTAL_DEPTH.remove(); else PORTAL_DEPTH.set(depth); }

    private static boolean remainingBoardsValid(ServerPlayer player, GeneratedObjective objective, int index) {
        List<Map<String, Object>> route = stops(objective);
        for (int i = index; i < route.size(); i++) if (!boardValid(player, route.get(i), false)) return false;
        return true;
    }

    private static boolean boardValid(ServerPlayer player, Map<String, Object> stop, boolean load) {
        try {
            UUID id = UUID.fromString(string(stop, "board_id"));
            var saved = TaskboundSavedData.get(player.server);
            var record = saved.board(id).orElse(null);
            BlockPos pos = new BlockPos((int) number(stop, "x", 0), (int) number(stop, "y", 0), (int) number(stop, "z", 0));
            if (record == null
                    || !record.dimension().equals(string(stop, "dimension")) || !record.position().equals(pos)) return false;
            ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(record.dimension())));
            if (level == null) return false;
            if (!load && !level.hasChunkAt(pos)) return true;
            return level.getBlockEntity(pos) instanceof TaskBoardBlockEntity board && board.boardId().equals(id);
        } catch (IllegalArgumentException malformed) { return false; }
    }

    private static void cancelInvalid(ServerPlayer player, ActiveTask active) {
        TaskboundSavedData.get(player.server).player(player.getUUID()).removeActiveTask(active.task().taskId());
        removeCargo(player, active.task().taskId());
        TaskboundSavedData.get(player.server).changed();
        player.sendSystemMessage(Component.translatable("taskbound.porter.cancelled_missing_board"));
        TaskboundNetworking.syncPlayer(player);
    }

    private static ItemStack parcel(ActiveTask active, String cargo) {
        String nonce = string(active.progress().additionalData(), "porter_nonce");
        UUID packageId = UUID.nameUUIDFromBytes((active.task().taskId() + ":" + nonce + ":" + cargo).getBytes(StandardCharsets.UTF_8));
        ItemStack stack = TaskPackageItem.seal(TaskboundContent.TASK_PACKAGE.get(), packageId,
                UUID.fromString(active.task().taskId()), UUID.fromString(active.task().boardId()));
        stampParcel(stack, active, cargo);
        CompoundTag binding = new CompoundTag();
        binding.putString("player", active.task().playerId()); binding.putString("cargo", cargo); binding.putString("nonce", nonce);
        CompoundTag data = new CompoundTag(); data.put(BINDING, binding);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(cargo.startsWith("receipt_") ? "taskbound.porter.receipt" : "taskbound.porter.parcel"));
        return stack;
    }

    private static void stampParcel(ItemStack stack, ActiveTask active, String cargo) {
        objective(active.task()).stream().flatMap(objective -> stops(objective).stream())
                .filter(stop -> strings(stop.get("deliver")).contains(cargo))
                .findFirst().ifPresent(stop -> TaskPackageItem.stamp(stack, ResourceLocation.parse(string(stop, "dimension")),
                        new BlockPos((int) number(stop, "x", 0), (int) number(stop, "y", 0), (int) number(stop, "z", 0))));
    }

    /** Existing parcels gain their stamp without changing the original sealed cargo data. */
    private static void refreshParcelStamps(ServerPlayer player, ActiveTask active) {
        UUID taskId = UUID.fromString(active.task().taskId());
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(TaskboundContent.TASK_PACKAGE.get()) || stack.has(TaskboundContent.PACKAGE_DESTINATION.get())
                    || TaskPackageItem.taskId(stack).filter(taskId::equals).isEmpty()) continue;
            var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(BINDING);
            if (active.task().playerId().equals(data.getString("player"))
                    && string(active.progress().additionalData(), "porter_nonce").equals(data.getString("nonce"))) {
                stampParcel(stack, active, data.getString("cargo"));
            }
        }
    }

    private static ItemStack findParcel(ServerPlayer player, ActiveTask active, String cargo) {
        ItemStack expected = parcel(active, cargo);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(expected.getItem())
                    && TaskPackageItem.packageId(stack).equals(TaskPackageItem.packageId(expected))
                    && TaskPackageItem.taskId(stack).equals(TaskPackageItem.taskId(expected))
                    && expected.get(DataComponents.CUSTOM_DATA).equals(stack.get(DataComponents.CUSTOM_DATA))) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static void removeCargo(ServerPlayer player, String taskId) {
        PorterNavigation.remove(player, taskId);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(TaskboundContent.TASK_PACKAGE.get()) && TaskPackageItem.taskId(stack).map(id -> id.toString().equals(taskId)).orElse(false)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) { if (!player.getInventory().add(stack)) player.drop(stack, false); }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> stops(GeneratedObjective objective) {
        if (!(objective.parameters().get("route") instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(value -> (Map<String, Object>) value).toList();
    }

    private static List<String> strings(Object value) {
        return value instanceof List<?> list ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList() : List.of();
    }
    private static String string(Map<String, ?> map, String key) { return map.get(key) instanceof String value ? value : ""; }
    private static double number(Map<String, ?> map, String key, double fallback) { return map.get(key) instanceof Number n ? n.doubleValue() : fallback; }
}
