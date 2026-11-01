package dev.taskbound.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DataMaps {
    private DataMaps() {
    }

    static Map<String, Object> copy(Map<String, ?> source) {
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> result.put(key, copyValue(value)));
        return java.util.Collections.unmodifiableMap(result);
    }

    static Object copyValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                result.put(stringKey, copyValue(nested));
            });
            return java.util.Collections.unmodifiableMap(result);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(DataMaps::copyValue).toList();
        }
        throw new IllegalArgumentException("Value is not JSON-compatible: " + value.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<String, ?> source, String key) {
        Object value = required(source, key);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected object field '" + key + "'");
        }
        return (Map<String, Object>) value;
    }

    static List<?> list(Map<String, ?> source, String key) {
        Object value = required(source, key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected array field '" + key + "'");
        }
        return list;
    }

    static List<String> strings(Map<String, ?> source, String key) {
        var result = new ArrayList<String>();
        for (Object value : list(source, key)) {
            if (!(value instanceof String string)) {
                throw new IllegalArgumentException("Expected strings in array field '" + key + "'");
            }
            result.add(string);
        }
        return List.copyOf(result);
    }

    static Set<String> stringSet(Map<String, ?> source, String key) {
        return Set.copyOf(new LinkedHashSet<>(strings(source, key)));
    }

    static String string(Map<String, ?> source, String key) {
        Object value = required(source, key);
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("Expected string field '" + key + "'");
        }
        return string;
    }

    static String optionalText(Map<String, ?> source, String key) {
        if (!source.containsKey(key)) return null;
        String value = string(source, key);
        if (value.isBlank()) throw new IllegalArgumentException("Optional field '" + key + "' cannot be blank");
        return value;
    }

    static int integer(Map<String, ?> source, String key) {
        Object value = required(source, key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Expected numeric field '" + key + "'");
        }
        return number.intValue();
    }

    static long longValue(Map<String, ?> source, String key) {
        Object value = required(source, key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Expected numeric field '" + key + "'");
        }
        return number.longValue();
    }

    static double decimal(Map<String, ?> source, String key) {
        Object value = required(source, key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Expected numeric field '" + key + "'");
        }
        return number.doubleValue();
    }

    static boolean bool(Map<String, ?> source, String key) {
        Object value = required(source, key);
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException("Expected boolean field '" + key + "'");
        }
        return bool;
    }

    static Integer nullableInteger(Map<String, ?> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Expected numeric or null field '" + key + "'");
        }
        return number.intValue();
    }

    static Long nullableLong(Map<String, ?> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Expected numeric or null field '" + key + "'");
        }
        return number.longValue();
    }

    private static Object required(Map<String, ?> source, String key) {
        if (!source.containsKey(key) || source.get(key) == null) {
            throw new IllegalArgumentException("Missing required field '" + key + "'");
        }
        return source.get(key);
    }
}
