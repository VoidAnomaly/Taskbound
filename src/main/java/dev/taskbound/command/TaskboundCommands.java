package dev.taskbound.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.PlayerCoreState;
import dev.taskbound.data.TaskboundDefinitionManager;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator administration; players manage tasks at boards and in their journal. */
public final class TaskboundCommands {
    private TaskboundCommands() { }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("taskbound")
                .requires(source -> source.hasPermission(2))
                .then(TaskboundDebugCommands.root())
                .then(Commands.literal("complete").executes(TaskboundCommands::complete))
                .then(Commands.literal("reputation")
                        .then(Commands.literal("set")
                                .then(giverArgument()
                                        .then(Commands.argument("points", IntegerArgumentType.integer(0))
                                                .executes(context -> reputation(context, ReputationAction.SET)))))
                        .then(Commands.literal("add")
                                .then(giverArgument()
                                        .then(Commands.argument("points", IntegerArgumentType.integer(0))
                                                .executes(context -> reputation(context, ReputationAction.ADD))))))
                .then(Commands.literal("reset-history").then(giverArgument().executes(TaskboundCommands::resetHistory)))
                .then(Commands.literal("reload").executes(TaskboundCommands::reload)));
    }

    private static List<String> givers() {
        return TaskboundDefinitionManager.current().enabledGivers().stream()
                .map(giver -> giver.id().startsWith("taskbound:") ? giver.id().substring(10) : giver.id()).toList();
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> giverArgument() {
        return Commands.argument("giver", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(givers(), builder));
    }

    private static int complete(CommandContext<CommandSourceStack> context) {
        return withPlayer(context, player -> {
            ActiveTask completed = TaskboundRuntime.get().forceComplete(player);
            player.sendSystemMessage(Component.literal("Debug-completed " + completed.task().taskId() + "."));
        });
    }

    private static int reputation(CommandContext<CommandSourceStack> context, ReputationAction action) {
        return withPlayer(context, player -> {
            String giverId = giverId(context);
            var definitions = TaskboundDefinitionManager.current();
            var giver = definitions.giver(giverId);
            TaskboundSavedData savedData = TaskboundSavedData.get(player.server);
            PlayerCoreState state = savedData.player(player.getUUID());
            var reputation = state.reputation().forGiver(giverId);
            int previousLevel = reputation.level(giver.reputationThresholds());
            switch (action) {
                case SET -> reputation.setPoints(
                        IntegerArgumentType.getInteger(context, "points"),
                        giver.reputationThresholds(),
                        player.serverLevel().getGameTime());
                case ADD -> reputation.addPoints(
                        IntegerArgumentType.getInteger(context, "points"),
                        giver.reputationThresholds(),
                        player.serverLevel().getGameTime());
            }
            if (reputation.level(giver.reputationThresholds()) != previousLevel) state.clearGeneratedOffers(giverId);
            savedData.changed();
            dev.taskbound.network.TaskboundNetworking.syncPlayer(player);
            player.sendSystemMessage(Component.literal(giverId + " · " + reputation.points()
                    + " points · level " + reputation.level(giver.reputationThresholds())));
        });
    }

    private static int resetHistory(CommandContext<CommandSourceStack> context) {
        return withPlayer(context, player -> {
            String giverId = giverId(context);
            TaskboundSavedData savedData = TaskboundSavedData.get(player.server);
            savedData.player(player.getUUID()).resetHistory(giverId);
            savedData.changed();
            player.sendSystemMessage(Component.literal("Reset Taskbound selection history for " + giverId + "."));
        });
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var server = source.getServer();
        source.sendSuccess(() -> Component.literal("Reloading server resources and Taskbound definitions…"), true);
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, error) ->
                server.execute(() -> {
                    if (error == null) {
                        source.sendSuccess(() -> Component.literal("Taskbound definitions reloaded."), true);
                    } else {
                        source.sendFailure(Component.literal("Taskbound reload failed: " + error.getMessage()));
                    }
                }));
        return 1;
    }

    private static String giverId(CommandContext<CommandSourceStack> context) {
        String value = StringArgumentType.getString(context, "giver");
        String id = value.contains(":") ? value : "taskbound:" + value;
        TaskboundDefinitionManager.current().giver(id);
        return id;
    }

    private static int withPlayer(CommandContext<CommandSourceStack> context, PlayerAction action) {
        try {
            action.run(context.getSource().getPlayerOrException());
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage()));
            return 0;
        }
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(ServerPlayer player) throws Exception;
    }

    private enum ReputationAction { SET, ADD }
}
