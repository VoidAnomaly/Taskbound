package dev.taskbound.ui;

import java.util.LinkedHashMap;
import java.util.Map;

public record LockedCategoryView(String id, String displayName, int requiredLevel) {
    public LockedCategoryView {
        if (id.isBlank() || displayName.isBlank() || requiredLevel < 1) {
            throw new IllegalArgumentException("Invalid locked-category view");
        }
    }

    Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        map.put("display_name", displayName);
        map.put("required_level", requiredLevel);
        return map;
    }

    static LockedCategoryView fromMap(Map<String, ?> map) {
        return new LockedCategoryView(
                UiJson.string(map, "id"),
                UiJson.string(map, "display_name"),
                UiJson.integer(map, "required_level")
        );
    }
}
