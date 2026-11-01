package dev.taskbound.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class UiJson {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private UiJson() {
    }

    static String write(Map<String, Object> value) {
        return GSON.toJson(value);
    }

    static Map<String, Object> read(String json) {
        Map<String, Object> result = GSON.fromJson(json, MAP_TYPE);
        if (result == null) {
            throw new IllegalArgumentException("Taskbound UI payload cannot be null");
        }
        return result;
    }

    static Object required(Map<String, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing Taskbound UI field '" + key + "'");
        }
        return value;
    }

    static String string(Map<String, ?> map, String key) {
        Object value = required(map, key);
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("Taskbound UI field '" + key + "' must be a string");
        }
        return string;
    }

    static int integer(Map<String, ?> map, String key) {
        Object value = required(map, key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Taskbound UI field '" + key + "' must be numeric");
        }
        return number.intValue();
    }

    static long longValue(Map<String, ?> map, String key) {
        Object value = required(map, key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Taskbound UI field '" + key + "' must be numeric");
        }
        return number.longValue();
    }

    static Map<String, ?> object(Object value, String field) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Taskbound UI field '" + field + "' must contain objects");
        }
        var result = new java.util.LinkedHashMap<String, Object>();
        raw.forEach((key, nested) -> {
            if (!(key instanceof String stringKey)) {
                throw new IllegalArgumentException("Taskbound UI object keys must be strings");
            }
            result.put(stringKey, nested);
        });
        return result;
    }

    static List<?> list(Map<String, ?> map, String key) {
        Object value = required(map, key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Taskbound UI field '" + key + "' must be an array");
        }
        return list;
    }

    static <T> List<T> objects(
            Map<String, ?> map,
            String key,
            java.util.function.Function<Map<String, ?>, T> parser
    ) {
        var result = new ArrayList<T>();
        for (Object value : list(map, key)) {
            result.add(parser.apply(object(value, key)));
        }
        return List.copyOf(result);
    }
}
