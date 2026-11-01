package dev.taskbound.data;

import java.util.concurrent.atomic.AtomicReference;

public final class TaskboundDefinitionManager {
    private static final AtomicReference<TaskboundDefinitions> CURRENT =
            new AtomicReference<>(TaskboundDefinitions.EMPTY);

    private TaskboundDefinitionManager() {
    }

    public static TaskboundDefinitions current() {
        return CURRENT.get();
    }

    static void replace(TaskboundDefinitions definitions) {
        CURRENT.set(definitions);
    }
}
