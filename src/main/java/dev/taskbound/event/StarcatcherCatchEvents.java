package dev.taskbound.event;

import dev.taskbound.compat.StarcatcherFishingProvider;
import dev.taskbound.compat.StarcatcherFishingProvider.FishDefinition;
import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.core.TaskTimer;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One real Starcatcher fishing transaction, including live-entity rewards and canceled spawn rejection. */
public final class StarcatcherCatchEvents {
    private static final ThreadLocal<Deque<CatchFrame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    public record CatchEvidence(FishDefinition fish, double rawPercentile, boolean perfect) { }
    private static final class CatchFrame {
        private final ServerPlayer player;
        private final boolean completed;
        private CatchEvidence evidence;
        private final List<Entity> spawned = new ArrayList<>();
        private CatchFrame(ServerPlayer player, boolean completed) { this.player = player; this.completed = completed; }
    }

    public static void begin(ServerPlayer player, boolean completed) {
        // Any exception in a mod call must not leave an attribution window open on a later tick.
        Deque<CatchFrame> stack = FRAMES.get();
        if (stack.size() > 8) stack.clear();
        stack.push(new CatchFrame(player, completed));
    }

    public static void metadata(Object properties, ResourceLocation species, Player player, float percentile,
            boolean perfect, boolean awardToTeam) {
        CatchFrame frame = frame();
        if (frame == null || frame.player != player || !frame.completed || !awardToTeam
                || player.isCreative() || player.isSpectator() || !Float.isFinite(percentile)) return;
        // Team-shared/stat-only awards do not run inside the recipient's own successful transaction.
        StarcatcherFishingProvider.definition(frame.player, properties, species).ifPresent(
                fish -> frame.evidence = new CatchEvidence(fish, percentile, perfect && !fish.skipsMinigame()));
    }

    public static CatchEvidence current(ServerPlayer player) {
        CatchFrame frame = frame();
        return frame != null && frame.player == player ? frame.evidence : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void spawned(EntityJoinLevelEvent event) {
        CatchFrame frame = frame();
        if (frame == null || frame.evidence == null || event.isCanceled()
                || event.getLevel() != frame.player.level()) return;
        Entity entity = event.getEntity();
        FishDefinition fish = frame.evidence.fish();
        if (entity instanceof ItemEntity item ? fish.matchesItem(item.getItem()) : fish.matchesEntity(entity.getType()))
            frame.spawned.add(entity);
    }

    public static void end(ServerPlayer player) {
        Deque<CatchFrame> frames = FRAMES.get();
        if (frames.isEmpty()) return;
        CatchFrame frame = frames.pop();
        if (frames.isEmpty()) FRAMES.remove();
        if (frame.player != player || frame.evidence == null) return;
        boolean delivered = frame.spawned.stream().anyMatch(entity -> !entity.isRemoved()
                && player.serverLevel().getEntity(entity.getUUID()) == entity);
        if (delivered) record(player, frame.evidence);
    }

    /** Called only once the completion's fish has actually been added to the server world. */
    private static void record(ServerPlayer player, CatchEvidence catchData) {
        TaskboundRuntime runtime = TaskboundRuntime.get();
        record ObjectiveMatch(String taskId, String key, String uniqueValue) { }
        var eligible = new ArrayList<ObjectiveMatch>();
        // Freeze stage eligibility before advancing any counters, so one catch cannot skip an ordered stage.
        for (ActiveTask active : runtime.activeTasks(player)) {
            if (TaskTimer.expired(active, player.serverLevel().getGameTime())) continue;
            for (GeneratedObjective objective : active.task().objectives()) {
                if (!StarcatcherObjectiveRules.supports(objective.type()) || !runtime.isObjectiveEligible(active, objective)
                        || !matches(objective.parameters(), catchData, player)) continue;
                String mode = String.valueOf(objective.parameters().getOrDefault("count_mode", "catches"));
                String unique = switch (mode) {
                    case "species" -> catchData.fish().species();
                    case "habitats" -> catchData.fish().habitat();
                    default -> null;
                };
                if (unique != null && seen(active, objective).contains(unique)) continue;
                eligible.add(new ObjectiveMatch(active.task().taskId(), objective.key(), unique));
            }
        }
        for (ObjectiveMatch match : eligible) {
            // Match every independently eligible objective, allowing a single fish to fulfill
            // specimen, rarity, habitat and perfect-catch criteria at the same time.
            runtime.advanceTaskObjective(player, match.taskId(), objective -> {
                if (!objective.key().equals(match.key())) return false;
                if (match.uniqueValue() != null) {
                    ActiveTask current = runtime.activeTasks(player).stream()
                            .filter(a -> a.task().taskId().equals(match.taskId())).findFirst().orElse(null);
                    if (current == null) return false;
                    Set<String> values = seen(current, objective);
                    if (!values.add(match.uniqueValue())) return false;
                    current.progress().putAdditionalData(seenKey(objective), List.copyOf(values));
                }
                return true;
            }, 1);
        }
    }

    private static boolean matches(Map<String, Object> parameters, CatchEvidence data, ServerPlayer player) {
        if (!StarcatcherFishingProvider.matchesDefinition(data.fish(), parameters)) return false;
        if (Boolean.TRUE.equals(parameters.get("perfect")) && !data.perfect()) return false;
        if (parameters.get("minimum_weight_percentile") instanceof Number minimum
                && StarcatcherObjectiveRules.normalizedWeightPercentile(data.rawPercentile()) < minimum.doubleValue()) return false;
        if (parameters.get("required_time") instanceof String time
                && !StarcatcherObjectiveRules.matchesTime(time, player.serverLevel().getDayTime())) return false;
        if (parameters.get("required_weather") instanceof String weather) {
            boolean rain = player.serverLevel().isRaining();
            boolean thunder = player.serverLevel().isThundering();
            if (weather.equals("rain") && !rain || weather.equals("thunder") && !thunder
                    || weather.equals("clear") && (rain || thunder)) return false;
        }
        return true;
    }

    private static String seenKey(GeneratedObjective objective) { return "starcatcher.seen." + objective.key(); }
    private static Set<String> seen(ActiveTask active, GeneratedObjective objective) {
        var result = new HashSet<String>();
        if (active.progress().additionalData().get(seenKey(objective)) instanceof List<?> values)
            for (Object value : values) if (value instanceof String text) result.add(text);
        return result;
    }
    private static CatchFrame frame() {
        Deque<CatchFrame> frames = FRAMES.get();
        return frames.isEmpty() ? null : frames.peek();
    }
    @SubscribeEvent public void reloaded(OnDatapackSyncEvent event) { StarcatcherFishingProvider.invalidate(); }
    @SubscribeEvent public void tickFinished(ServerTickEvent.Post event) { FRAMES.remove(); }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        FRAMES.remove();
        StarcatcherFishingProvider.invalidate();
    }
}
