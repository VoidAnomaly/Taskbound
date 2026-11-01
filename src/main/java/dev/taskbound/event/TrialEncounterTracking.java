package dev.taskbound.event;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.TaskStatus;
import dev.taskbound.mixin.TrialSpawnerDataAccessor;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;

/** Credits registered participation only after vanilla finishes the entire encounter. */
public final class TrialEncounterTracking {
    private static final String CYCLE = "taskbound.trial_attempt";
    private static final String PARTICIPATED = "encounter.trials_participated";
    private static final String COMPLETED = "encounter.trials_completed";
    private TrialEncounterTracking() { }

    public static void observe(ServerLevel level, BlockPos pos, TrialSpawner spawner) {
        if (spawner.getState() != TrialSpawnerState.ACTIVE) return;
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;
        if (!blockEntity.getPersistentData().hasUUID(CYCLE)) {
            blockEntity.getPersistentData().putUUID(CYCLE, UUID.randomUUID());
            blockEntity.setChanged();
        }
        String cycle = blockEntity.getPersistentData().getUUID(CYCLE).toString();
        for (UUID uuid : ((TrialSpawnerDataAccessor)spawner.getData()).taskbound$participants()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel() != level
                    || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 256) continue;
            for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
                for (var objective : active.task().objectives()) {
                    if (!objective.type().equals("trial_encounter") || !TaskboundRuntime.get().isObjectiveEligible(active, objective)
                            || EncounterGameplayEvents.truth(objective, "ominous") && !spawner.isOminous()
                            || !VanillaObjectiveConditions.matches(objective, player, level, pos, ItemStack.EMPTY, false)) continue;
                    active.progress().putAdditionalData(EncounterGameplayEvents.TRIAL_STARTED, true);
                    if (!EncounterGameplayEvents.checkEquipment(player, active)) break;
                    List<String> participated = values(active, PARTICIPATED);
                    if (!participated.contains(cycle)) {
                        participated.add(cycle);
                        active.progress().putAdditionalData(PARTICIPATED, participated);
                        TaskboundRuntime.get().markChanged(player);
                    }
                    break;
                }
            }
        }
    }

    public static void transition(ServerLevel level, BlockPos pos, TrialSpawner spawner, TrialSpawnerState previous) {
        if (spawner.getState() == TrialSpawnerState.ACTIVE) { observe(level, pos, spawner); return; }
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !blockEntity.getPersistentData().hasUUID(CYCLE)) return;
        String cycle = blockEntity.getPersistentData().getUUID(CYCLE).toString();
        if (previous == TrialSpawnerState.ACTIVE && spawner.getState() == TrialSpawnerState.WAITING_FOR_REWARD_EJECTION) {
            String location = level.dimension().location() + ":" + pos.asLong();
            TaskboundSavedData saved = TaskboundSavedData.get(level.getServer());
            for (UUID uuid : ((TrialSpawnerDataAccessor)spawner.getData()).taskbound$participants()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                for (ActiveTask active : List.copyOf(saved.player(uuid).activeTasks().values())) {
                    if (active.task().status() != TaskStatus.ACTIVE || !values(active, PARTICIPATED).contains(cycle)
                            || player != null && !EncounterGameplayEvents.checkEquipment(player, active)) continue;
                    TaskboundRuntime.get().advanceTaskObjective(level.getServer(), uuid, active.task().taskId(), objective -> {
                        if (!objective.type().equals("trial_encounter") || EncounterGameplayEvents.truth(objective, "ominous") && !spawner.isOminous()) return false;
                        String key = EncounterGameplayEvents.truth(objective, "distinct_spawners") ? location : cycle;
                        List<String> completed = values(active, COMPLETED);
                        if (completed.contains(key)) return false;
                        completed.add(key);
                        active.progress().putAdditionalData(COMPLETED, completed);
                        return true;
                    }, 1);
                }
            }
        }
        blockEntity.getPersistentData().remove(CYCLE);
        blockEntity.setChanged();
    }

    private static List<String> values(ActiveTask active, String key) {
        Object value = active.progress().additionalData().get(key);
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        return new ArrayList<>(list.stream().filter(String.class::isInstance).map(String.class::cast).toList());
    }
}
