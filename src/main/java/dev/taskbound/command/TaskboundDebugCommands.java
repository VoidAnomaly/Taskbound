package dev.taskbound.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.taskbound.block.TaskBoardBlockEntity;
import dev.taskbound.core.ActiveTask;
import dev.taskbound.data.TaskboundDefinitionManager;
import dev.taskbound.network.TaskboundNetworking;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Operator-only tooling. Mutations use the same saved player state and UI synchronization as gameplay. */
final class TaskboundDebugCommands {
    private TaskboundDebugCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("debug").requires(source -> source.hasPermission(2))
                .then(Commands.literal("offers").then(player().then(guild()
                        .executes(context -> refresh(context, null))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, TaskboundRuntime.MAX_OFFER_COUNT))
                                .executes(context -> refresh(context, IntegerArgumentType.getInteger(context, "count")))))))
                .then(Commands.literal("assign").then(player().then(guild().then(
                        Commands.argument("template", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(templates(context), builder))
                                .executes(context -> execute(context, target -> {
                                    String giver = guildId(context);
                                    String value = rawArgument(context, "template");
                                    String template = value.contains(":") ? value : value.contains("/") ? "taskbound:" + value : giver + "/catalog/" + value;
                                    var task = TaskboundRuntime.get().debugAssign(target, giver, template, board(target));
                                    success(context, "Assigned " + task.templateId() + " to " + target.getScoreboardName() + ": " + task.taskId());
                                }))))))
                .then(Commands.literal("progress").then(player().then(task().then(
                        Commands.argument("objective", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(objectives(context), builder))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(context -> execute(context, target -> {
                                            String key = StringArgumentType.getString(context, "objective");
                                            var active = TaskboundRuntime.get().debugSetProgress(target, taskId(context), key,
                                                    IntegerArgumentType.getInteger(context, "value"));
                                            success(context, "Set " + active.task().taskId() + " / " + key + " to " + active.progress().progressFor(key)
                                                    + "; " + (active.readyToClaim(target.serverLevel().getGameTime()) ? "ready to claim" : "in progress") + ".");
                                        })))))))
                .then(Commands.literal("complete").then(player().then(task().executes(context -> execute(context, target -> {
                    var active = TaskboundRuntime.get().debugComplete(target, taskId(context));
                    success(context, "Task " + active.task().taskId() + " is ready to claim for " + target.getScoreboardName() + ". No rewards granted.");
                })))))
                .then(Commands.literal("reputation").then(player().then(guild().then(
                        Commands.argument("points", IntegerArgumentType.integer(0)).executes(context -> execute(context, target -> {
                            String guild = guildId(context);
                            int points = IntegerArgumentType.getInteger(context, "points");
                            int level = TaskboundRuntime.get().debugSetReputation(target, guild, points);
                            success(context, "Set " + target.getScoreboardName() + " / " + guild + " to " + points + " reputation (level " + level + ").");
                        }))))))
                .then(Commands.literal("inspect").then(player().executes(context -> execute(context, target -> inspect(context, target)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> player() {
        return Commands.argument("player", EntityArgument.player());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> guild() {
        return Commands.argument("guild", ResourceLocationArgument.id()).suggests((context, builder) ->
                SharedSuggestionProvider.suggest(TaskboundDefinitionManager.current().enabledGivers().stream()
                        .map(giver -> giver.id().startsWith("taskbound:") ? giver.id().substring(10) : giver.id()), builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, java.util.UUID> task() {
        return Commands.argument("task", UuidArgument.uuid()).suggests((context, builder) ->
                SharedSuggestionProvider.suggest(TaskboundRuntime.get().activeTasks(EntityArgument.getPlayer(context, "player"))
                        .stream().map(active -> active.task().taskId()), builder));
    }

    private static List<String> templates(CommandContext<CommandSourceStack> context) {
        var definitions = TaskboundDefinitionManager.current();
        var giver = definitions.giver(guildId(context));
        return definitions.templates().values().stream().filter(template -> giver.categoryIds().contains(template.categoryId()))
                .map(template -> template.id()).sorted().toList();
    }

    private static List<String> objectives(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var target = EntityArgument.getPlayer(context, "player");
        ActiveTask active = TaskboundSavedData.get(target.server).player(target.getUUID()).activeTask(taskId(context));
        return active == null ? List.of() : active.task().objectives().stream().map(objective -> objective.key()).toList();
    }

    private static String guildId(CommandContext<CommandSourceStack> context) {
        String value = rawArgument(context, "guild");
        String id = value.contains(":") ? value : "taskbound:" + value;
        var giver = TaskboundDefinitionManager.current().giver(id);
        if (!giver.enabled()) throw new IllegalArgumentException("This guild is deferred in the vanilla-event release");
        return id;
    }

    private static String taskId(CommandContext<CommandSourceStack> context) {
        return UuidArgument.getUuid(context, "task").toString();
    }

    private static String rawArgument(CommandContext<CommandSourceStack> context, String name) {
        return context.getNodes().stream().filter(node -> node.getNode().getName().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing " + name)).getRange().get(context.getInput());
    }

    private static TaskBoardBlockEntity board(ServerPlayer target) {
        var current = TaskboundNetworking.currentBoard(target);
        if (current.isPresent()) return current.get();
        BlockPos origin = target.blockPosition();
        var level = target.serverLevel();
        return BlockPos.betweenClosedStream(origin.offset(-8, -8, -8), origin.offset(8, 8, 8))
                .filter(level::isLoaded).filter(position -> target.distanceToSqr(position.getX() + 0.5,
                        position.getY() + 0.5, position.getZ() + 0.5) <= 64)
                .map(level::getBlockEntity).filter(TaskBoardBlockEntity.class::isInstance).map(TaskBoardBlockEntity.class::cast)
                .min(Comparator.comparingDouble(value -> target.distanceToSqr(value.getBlockPos().getX() + 0.5,
                        value.getBlockPos().getY() + 0.5, value.getBlockPos().getZ() + 0.5)))
                .orElseThrow(() -> new IllegalStateException("The target player must be within 8 blocks of a Task Board (or have it open)."));
    }

    private static int refresh(CommandContext<CommandSourceStack> context, Integer count) {
        return execute(context, target -> {
            String guild = guildId(context);
            var board = board(target);
            var runtime = TaskboundRuntime.get();
            var offers = runtime.debugRefreshOffers(target, guild, board, count == null ? runtime.offerCount(target, guild) : count);
            success(context, "Generated " + offers.size() + " unseen " + guild + " offers for " + target.getScoreboardName()
                    + " at " + board.getBlockPos().toShortString() + (offers.isEmpty() ? "; no eligible templates currently available." : "."));
        });
    }

    private static void inspect(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        var state = TaskboundSavedData.get(target.server).player(target.getUUID());
        long now = target.serverLevel().getGameTime();
        var tasks = TaskboundRuntime.get().activeTasks(target);
        var offers = TaskboundRuntime.get().offers(target);
        success(context, target.getScoreboardName() + ": " + tasks.size() + " active tasks; " + offers.size() + " offers; "
                + offers.stream().filter(offer -> !state.isOfferSeen(offer.taskId())).count() + " unseen.");
        for (var active : tasks) {
            success(context, active.task().taskId() + " | " + active.task().giverId() + " | " + active.task().templateId()
                    + " | " + active.task().status() + " | claimable=" + active.readyToClaim(now));
            for (var objective : active.task().objectives()) success(context, "  " + objective.key() + " (" + objective.type() + "): "
                    + active.progress().progressFor(objective.key()) + "/" + TaskboundRuntime.requiredCount(objective));
        }
        for (var offer : offers) success(context, "Offer " + offer.taskId() + " | " + offer.giverId() + " | board=" + offer.boardId()
                + " | " + (state.isOfferSeen(offer.taskId()) ? "seen" : "UNSEEN"));
    }

    private static int execute(CommandContext<CommandSourceStack> context, DebugAction action) {
        // Recheck at execution, in addition to hiding/rejecting the entire command branch during parsing.
        if (!context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("Taskbound debug commands require operator permission level 2."));
            return 0;
        }
        try {
            action.run(EntityArgument.getPlayer(context, "player"));
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
            return 0;
        }
    }

    private static void success(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    @FunctionalInterface private interface DebugAction { void run(ServerPlayer target) throws Exception; }
}
