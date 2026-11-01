package dev.taskbound.ui;

import dev.taskbound.core.DifficultyTier;
import dev.taskbound.core.GeneratedTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small, client-independent search language shared by filtering and completion. */
public final class BoardTaskSearch {
    public static final List<String> FIELDS = List.of("time", "difficulty", "location", "task", "reward", "objective", "guild");
    public static final List<String> LOCATION_KEYS = List.of("required_biome", "required_biome_tag", "required_structure",
            "required_structure_tag", "biome", "structure", "required_dimension", "dimension");

    public record Document(String title, Map<String, List<String>> fields) {
        public Document {
            var copy = new LinkedHashMap<String, List<String>>();
            fields.forEach((key, values) -> copy.put(key, List.copyOf(new LinkedHashSet<>(values))));
            fields = Collections.unmodifiableMap(copy);
        }
    }

    public record Completion(int start, int end, List<String> values) {
        public String apply(String input, String value) {
            return input.substring(0, start) + value + input.substring(end);
        }
    }

    private record Token(int start, int end, String text) { }
    private record Term(String field, String value) { }

    public static final class Query {
        private final List<Term> terms;
        private Query(List<Term> terms) { this.terms = List.copyOf(terms); }

        public boolean matches(Document document) {
            return terms.stream().allMatch(term -> {
                if (term.value.isEmpty()) return true; // An unfinished field does not hide the list.
                if (term.field.isEmpty()) return document.fields.values().stream().flatMap(List::stream)
                        .anyMatch(value -> normalize(value).contains(term.value));
                var values = document.fields.getOrDefault(term.field, List.of());
                boolean exact = term.field.equals("time") || term.field.equals("difficulty");
                return values.stream().map(BoardTaskSearch::normalize)
                        .anyMatch(value -> exact ? value.equals(term.value) : value.contains(term.value));
            });
        }
    }

    private BoardTaskSearch() { }

    /** Include stable IDs as well as translated labels supplied by the client. */
    public static Document document(GeneratedTask task, String title, Map<String, List<String>> translated) {
        var fields = new LinkedHashMap<String, List<String>>();
        FIELDS.forEach(field -> fields.put(field, new ArrayList<>()));
        fields.put("text", new ArrayList<>());
        add(fields.get("task"), title);
        add(fields.get("text"), task.description() != null ? task.description() : task.displayText());
        add(fields.get("guild"), task.giverId());
        add(fields.get("difficulty"), task.difficulty().tier().name().toLowerCase(Locale.ROOT));
        add(fields.get("difficulty"), Integer.toString(task.difficulty().tier().ordinal() + 1));
        switch (BoardTaskQuery.timing(task)) {
            case UNLIMITED -> { add(fields.get("time"), "any"); add(fields.get("time"), "unlimited"); add(fields.get("time"), "no time limit"); }
            case TIMED -> add(fields.get("time"), "timed");
            case DAY -> { add(fields.get("time"), "day"); add(fields.get("time"), "daytime"); }
            case NIGHT -> { add(fields.get("time"), "night"); add(fields.get("time"), "nighttime"); }
        }
        for (var objective : task.objectives()) {
            LOCATION_KEYS.forEach(key -> addStrings(fields.get("location"), objective.parameters().get(key)));
            add(fields.get("objective"), objective.type());
            addStrings(fields.get("objective"), objective.parameters());
        }
        if (fields.get("location").isEmpty()) {
            add(fields.get("location"), "Any location");
            add(fields.get("location"), "anywhere");
        }
        for (var reward : task.rewards().rewards()) {
            add(fields.get("reward"), reward.type().name().toLowerCase(Locale.ROOT));
            addStrings(fields.get("reward"), reward.payload());
        }
        if (task.reputationReward() > 0) add(fields.get("reward"), "guild reputation");
        translated.forEach((field, values) -> values.forEach(value -> add(fields.computeIfAbsent(field, ignored -> new ArrayList<>()), value)));
        return new Document(title, fields);
    }

    public static Query parse(String input) {
        var terms = new ArrayList<Term>();
        for (Token token : tokens(input)) {
            String value = token.text;
            int colon = value.indexOf(':');
            String field = colon < 0 ? "" : field(value.substring(0, colon));
            terms.add(new Term(field, normalize(field.isEmpty() ? value : value.substring(colon + 1))));
        }
        return new Query(terms);
    }

    /** Replace just the token under the cursor; preserve every other term and quoted suffix. */
    public static Completion complete(String input, int cursor, List<Document> documents) {
        cursor = Math.clamp(cursor, 0, input.length());
        int start = cursor, end = cursor;
        for (Token token : tokens(input)) {
            if (cursor >= token.start && cursor <= token.end) { start = token.start; end = token.end; break; }
        }
        String prefix = input.substring(start, cursor);
        var prefixTokens = tokens(prefix);
        String typed = prefixTokens.isEmpty() ? "" : prefixTokens.getFirst().text;
        int colon = typed.indexOf(':');
        var candidates = new LinkedHashSet<String>();
        if (colon < 0) {
            for (String name : FIELDS) if (name.startsWith(normalize(typed))) candidates.add(name + ":");
        } else {
            String name = field(typed.substring(0, colon));
            String valuePrefix = normalize(typed.substring(colon + 1));
            List<String> values = switch (name) {
                case "time" -> List.of("any", "day", "night", "timed");
                case "difficulty" -> java.util.Arrays.stream(DifficultyTier.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).toList();
                default -> documents.stream().flatMap(document -> document.fields.getOrDefault(name, List.of()).stream())
                        .map(value -> value.toLowerCase(Locale.ROOT)).distinct().sorted().toList();
            };
            for (String value : values) if (normalize(value).startsWith(valuePrefix)) candidates.add(name + ":" + quote(value));
        }
        return new Completion(start, end, List.copyOf(candidates));
    }

    private static String field(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "title" -> "task";
            case "rewards" -> "reward";
            case "objectives" -> "objective";
            default -> FIELDS.contains(name.toLowerCase(Locale.ROOT)) ? name.toLowerCase(Locale.ROOT) : "";
        };
    }

    private static String normalize(String value) { return value.strip().toLowerCase(Locale.ROOT).replace('_', ' '); }
    private static String quote(String value) {
        return value.chars().anyMatch(Character::isWhitespace) || value.contains("\"") || value.contains("\\")
                ? "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : value;
    }

    private static void add(List<String> values, String value) {
        if (value == null || value.isBlank()) return;
        values.add(value);
        if (value.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) values.add(value.substring(value.indexOf(':') + 1).replace('_', ' '));
        else if (value.contains("_")) values.add(value.replace('_', ' '));
    }

    private static void addStrings(List<String> values, Object value) {
        if (value instanceof String text) add(values, text);
        else if (value instanceof Map<?, ?> map) map.values().forEach(entry -> addStrings(values, entry));
        else if (value instanceof Iterable<?> list) list.forEach(entry -> addStrings(values, entry));
    }

    /** Quoted phrases can start after a field colon; unfinished quotes remain searchable while typing. */
    private static List<Token> tokens(String input) {
        var result = new ArrayList<Token>();
        int i = 0;
        while (i < input.length()) {
            if (Character.isWhitespace(input.charAt(i))) { i++; continue; }
            int start = i;
            boolean quoted = false;
            var text = new StringBuilder();
            while (i < input.length()) {
                char c = input.charAt(i);
                if (!quoted && Character.isWhitespace(c)) break;
                if (c == '"') { quoted = !quoted; i++; }
                else if (c == '\\' && i + 1 < input.length() && (input.charAt(i + 1) == '"' || input.charAt(i + 1) == '\\')) {
                    text.append(input.charAt(i + 1)); i += 2;
                } else { text.append(c); i++; }
            }
            result.add(new Token(start, i, text.toString()));
        }
        return result;
    }
}
