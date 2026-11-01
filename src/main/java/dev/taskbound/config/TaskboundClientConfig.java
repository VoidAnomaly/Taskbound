package dev.taskbound.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local presentation preferences; never changes server-owned task data. */
public final class TaskboundClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue COMPACT_TASK_LIST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("ui");
        COMPACT_TASK_LIST = builder.comment("Use compact board task rows. False keeps the relaxed layout.")
                .define("compact_task_list", false);
        builder.pop();
        SPEC = builder.build();
    }

    private TaskboundClientConfig() { }
}
