package dev.taskbound.ui;

import dev.taskbound.core.GeneratedTask;
import dev.taskbound.core.RewardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Client presentation rules over server-authored tasks; never changes task state. */
public final class BoardTaskQuery {
    // Known currencies also work in saved rewards without the explicit currency marker.
    private static final List<String> CURRENCIES = List.of(
            "minecraft:emerald", "numismatics:spur", "numismatics:bevel", "numismatics:sprocket",
            "numismatics:cog", "numismatics:crown", "numismatics:sun");
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public enum Timing { UNLIMITED, TIMED, DAY, NIGHT }
    public enum Sort { DEFAULT, TITLE, DIFFICULTY, PAYMENT }

    /** A negative tier and null timing each mean all values. */
    public record Options(String search, int tierOrdinal, Timing timing,
                          Sort sort, String paymentCurrency) {
        public Options {
            Objects.requireNonNull(search);
            Objects.requireNonNull(sort);
            Objects.requireNonNull(paymentCurrency);
            if (tierOrdinal < -1 || tierOrdinal > 4) throw new IllegalArgumentException("Invalid difficulty tier");
            if (sort == Sort.PAYMENT && !ITEM_ID.matcher(paymentCurrency).matches()) {
                throw new IllegalArgumentException("Payment sorting requires a namespaced currency item ID");
            }
        }

        public static Options defaults() { return new Options("", -1, null, Sort.DEFAULT, ""); }
    }

    private BoardTaskQuery() { }

    /** Complete result list: callers calculate visible rows and scroll limits after this step. */
    public static List<GeneratedTask> apply(List<GeneratedTask> tasks, Options options,
            Function<GeneratedTask, String> title) {
        return applyIndexed(tasks, options, task -> BoardTaskSearch.document(task, title.apply(task), Map.of()));
    }

    public static List<GeneratedTask> applyIndexed(List<GeneratedTask> tasks, Options options,
            Function<GeneratedTask, BoardTaskSearch.Document> documents) {
        var search = BoardTaskSearch.parse(options.search());
        var result = tasks.stream()
                .filter(task -> search.matches(documents.apply(task)))
                .filter(task -> options.tierOrdinal() < 0 || task.difficulty().tier().ordinal() == options.tierOrdinal())
                .filter(task -> options.timing() == null || options.timing() == timing(task));
        Comparator<GeneratedTask> comparator = switch (options.sort()) {
            case DEFAULT -> null;
            case TITLE -> Comparator.comparing(task -> documents.apply(task).title().toLowerCase(Locale.ROOT));
            case DIFFICULTY -> Comparator.comparingInt(task -> task.difficulty().score());
            // Compare the automatically chosen primary currency, shown in the sort label.
            case PAYMENT -> Comparator.<GeneratedTask>comparingLong(
                    task -> payments(task).getOrDefault(options.paymentCurrency(), 0L)).reversed();
        };
        if (comparator != null) result = result.sorted(comparator);
        return result.toList();
    }

    /** Actual monetary item quantities, not reward-budget costs; each currency stays separate. */
    public static Map<String, Long> payments(GeneratedTask task) {
        // Saved markers survive configuration changes; bonuses of that same item count too.
        var currencies = new HashSet<>(CURRENCIES);
        for (var reward : task.rewards().rewards()) {
            if (reward.type() == RewardType.ITEM && Boolean.TRUE.equals(reward.payload().get("currency"))
                    && reward.payload().get("item") instanceof String item && ITEM_ID.matcher(item).matches()) {
                currencies.add(item);
            }
        }
        var totals = new LinkedHashMap<String, Long>();
        for (var reward : task.rewards().rewards()) {
            if (reward.type() != RewardType.ITEM || !(reward.payload().get("item") instanceof String item)
                    || !currencies.contains(item)) continue;
            int quantity = reward.payload().get("count") instanceof Number number ? number.intValue() : 1;
            if (quantity > 0) totals.merge(item, (long) reward.count() * quantity, Math::addExact);
        }
        var ordered = new LinkedHashMap<String, Long>();
        for (String currency : orderedCurrencies(totals.keySet())) ordered.put(currency, totals.get(currency));
        return Collections.unmodifiableMap(ordered);
    }

    /** Currencies that can meaningfully be offered as payment sort options for this task set. */
    public static List<String> currencies(List<GeneratedTask> tasks) {
        var present = new HashSet<String>();
        for (GeneratedTask task : tasks) present.addAll(payments(task).keySet());
        return orderedCurrencies(present);
    }

    private static List<String> orderedCurrencies(Set<String> present) {
        var ordered = new ArrayList<String>();
        for (String currency : CURRENCIES) if (present.contains(currency)) ordered.add(currency);
        present.stream().filter(currency -> !CURRENCIES.contains(currency)).sorted().forEach(ordered::add);
        return List.copyOf(ordered);
    }

    public static Timing timing(GeneratedTask task) {
        if (task.expiresAt() != null) return Timing.TIMED;
        Timing result = Timing.UNLIMITED;
        for (var objective : task.objectives()) {
            if ("night".equals(objective.parameters().get("required_time"))) return Timing.NIGHT;
            if ("day".equals(objective.parameters().get("required_time"))) result = Timing.DAY;
        }
        return result;
    }

    /** Offers show their full duration; only accepted tasks count down with world game time. */
    public static long durationOrRemainingTicks(GeneratedTask task, boolean accepted, long gameTime) {
        if (task.expiresAt() == null) return 0;
        return Math.max(0, task.expiresAt() - (accepted ? gameTime : task.acceptedAt()));
    }
}
