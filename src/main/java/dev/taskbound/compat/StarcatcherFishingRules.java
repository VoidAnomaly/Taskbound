package dev.taskbound.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

/** Starcatcher's source percentile runs from largest (0) to smallest (100). */
final class StarcatcherFishingRules {
    private StarcatcherFishingRules() {
    }

    static double measurement(double average, double deviation, double rawPercentile) {
        return Math.round(average + deviation * (1.0 - rawPercentile / 50.0));
    }

    static boolean permitsTime(JsonArray restrictions, String time) {
        int minimum = time.equals("day") ? 0 : 12_700;
        int maximum = time.equals("day") ? 12_700 : 24_000;
        for (JsonElement entry : restrictions) {
            JsonObject restriction = entry.getAsJsonObject();
            if (!restriction.get("type").getAsString().equals("starcatcher:daytime_restriction")) continue;
            boolean overlap = false;
            for (JsonElement value : restriction.getAsJsonArray("ranges")) {
                JsonObject range = value.getAsJsonObject();
                // Starcatcher's min/max endpoints are exclusive and world time is in integer ticks.
                overlap |= Math.max(minimum, range.get("min").getAsInt()) + 1
                        < Math.min(maximum, range.get("max").getAsInt());
            }
            if (!overlap) return false;
        }
        return true;
    }

    static boolean permitsWeather(JsonArray restrictions, String weather) {
        for (JsonElement entry : restrictions) {
            JsonObject restriction = entry.getAsJsonObject();
            if (!restriction.get("type").getAsString().equals("starcatcher:weather_restriction")) continue;
            Set<String> allowed = switch (restriction.get("weather").getAsString()) {
                case "clear" -> Set.of("clear");
                case "rain", "rain_or_thunder" -> Set.of("rain", "thunder");
                case "thunder" -> Set.of("thunder");
                case "clear_or_rain" -> Set.of("clear", "rain", "thunder");
                case "clear_or_thunder" -> Set.of("clear", "thunder");
                default -> Set.of();
            };
            if (!allowed.contains(weather)) return false;
        }
        return true;
    }
}
