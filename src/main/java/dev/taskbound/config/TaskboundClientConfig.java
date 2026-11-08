package dev.taskbound.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local presentation preferences; never changes server-owned task data. */
public final class TaskboundClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue COMPACT_TASK_LIST;
    public static final ModConfigSpec.BooleanValue PORTER_XAERO_WAYPOINTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("ui");
        COMPACT_TASK_LIST = builder.comment("Use compact board task rows. False keeps the relaxed layout.")
                .define("compact_task_list", false);
        PORTER_XAERO_WAYPOINTS = builder.comment("Show temporary delivery waypoints when Xaero's Minimap is installed.",
                        "Also appears in Xaero's World Map when both mods are installed. World Map alone uses the delivery compass.",
                        "Turn off to keep only the compass. Your personal waypoints are never changed.")
                .define("porter_xaero_waypoints", true);
        builder.pop();
        SPEC = builder.build();
    }

    private TaskboundClientConfig() { }
}
