package dev.taskbound.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned settings that affect newly generated Taskbound tasks. */
public final class TaskboundServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue USE_NUMISMATICS_REWARDS;
    public static final ModConfigSpec.IntValue NUMISMATICS_SPURS_PER_EMERALD;
    public static final ModConfigSpec.BooleanValue USE_CUSTOM_CURRENCY_REWARDS;
    public static final ModConfigSpec.ConfigValue<String> CUSTOM_CURRENCY_ITEM;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_TASKS;
    public static final ModConfigSpec.IntValue REPEAT_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue OFFER_REFRESH_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("rewards");
        USE_NUMISMATICS_REWARDS = builder
                .comment(
                        "Automatically replace emerald rewards in newly generated tasks with Create: Numismatics spurs.",
                        "Other rewards are retained. Uses emeralds when Numismatics is not installed.",
                        "Existing accepted tasks keep their saved rewards; set false to disable conversion."
                )
                .define("use_numismatics_rewards", true);
        NUMISMATICS_SPURS_PER_EMERALD = builder
                .comment("Number of spurs per emerald reward. Default: 1 emerald = 1 spur.")
                .defineInRange("numismatics_spurs_per_emerald", 1, 1, 1024);
        USE_CUSTOM_CURRENCY_REWARDS = builder
                .comment("Use custom_currency_item instead of automatic emerald/spur payments for new tasks.",
                        "Custom currency takes priority over Numismatics, at one item per emerald.",
                        "Existing generated/accepted tasks and other item rewards retain their saved payouts.")
                .define("use_custom_currency_rewards", false);
        CUSTOM_CURRENCY_ITEM = builder
                .comment("Registered item ID for custom currency, for example minecraft:diamond.",
                        "Only used when use_custom_currency_rewards is true.",
                        "Invalid, missing or air items fall back to automatic emerald/spur rewards with a log warning.")
                .define("custom_currency_item", "minecraft:emerald");
        builder.pop();
        builder.push("tasks");
        MAX_ACTIVE_TASKS = builder.comment("Maximum unclaimed active/completed tasks per player.")
                .defineInRange("maximum_active_tasks", 3, 1, 64);
        REPEAT_COOLDOWN_TICKS = builder.comment("Default same-template cooldown after reward claim, in server ticks.")
                .defineInRange("repeat_cooldown_ticks", 24000, 0, Integer.MAX_VALUE);
        OFFER_REFRESH_TICKS = builder.comment("Automatic offer rotation interval per player, board and guild. Default: five minutes. Accepted tasks are retained. Automatic rotation has a minimum of 20 ticks.")
                .defineInRange("offer_refresh_ticks", 6000, 0, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private TaskboundServerConfig() {
    }

    public static boolean useNumismaticsRewards() {
        return USE_NUMISMATICS_REWARDS.getAsBoolean();
    }
}
