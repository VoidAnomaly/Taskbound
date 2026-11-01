package dev.taskbound.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.UUID;

/** Deterministic task-first generation pipeline: content, difficulty, rewards, then history commit. */
public final class TaskGenerator {
    private final TaskCatalog catalog;
    private final TaskSelectionService selectionService;
    private final DifficultyCalculator difficultyCalculator;
    private final RewardGenerator rewardGenerator;
    private final TaskDraftCustomizer draftCustomizer;

    public TaskGenerator(TaskCatalog catalog) {
        this(catalog, new TaskSelectionService(), new DifficultyCalculator(), new RewardGenerator(),
                TaskDraftCustomizer.IDENTITY);
    }

    public TaskGenerator(TaskCatalog catalog, TaskDraftCustomizer draftCustomizer) {
        this(catalog, new TaskSelectionService(), new DifficultyCalculator(), new RewardGenerator(), draftCustomizer);
    }

    public TaskGenerator(TaskCatalog catalog, TaskVariantEligibility variantEligibility) {
        this(catalog, new TaskSelectionService(variantEligibility), new DifficultyCalculator(), new RewardGenerator(),
                TaskDraftCustomizer.IDENTITY);
    }

    public TaskGenerator(
            TaskCatalog catalog,
            TaskVariantEligibility variantEligibility,
            TaskDraftCustomizer draftCustomizer
    ) {
        this(catalog, new TaskSelectionService(variantEligibility), new DifficultyCalculator(), new RewardGenerator(),
                draftCustomizer);
    }

    public TaskGenerator(
            TaskCatalog catalog,
            TaskSelectionService selectionService,
            DifficultyCalculator difficultyCalculator,
            RewardGenerator rewardGenerator
    ) {
        this(catalog, selectionService, difficultyCalculator, rewardGenerator, TaskDraftCustomizer.IDENTITY);
    }

    public TaskGenerator(
            TaskCatalog catalog,
            TaskSelectionService selectionService,
            DifficultyCalculator difficultyCalculator,
            RewardGenerator rewardGenerator,
            TaskDraftCustomizer draftCustomizer
    ) {
        this.catalog = catalog;
        this.selectionService = selectionService;
        this.difficultyCalculator = difficultyCalculator;
        this.rewardGenerator = rewardGenerator;
        this.draftCustomizer = Objects.requireNonNull(draftCustomizer, "draftCustomizer");
    }

    public GeneratedTask generate(
            TaskGenerationContext context,
            TaskGiverDefinition giver,
            SelectionHistory history,
            long seed
    ) {
        validateScope(context, giver, history);
        var random = new SplittableRandom(seed);
        List<TaskCategoryDefinition> giverCategories = catalog.categoriesForGiver(giver.id());
        if (!giver.categoryIds().isEmpty()) {
            var configuredIds = java.util.Set.copyOf(giver.categoryIds());
            giverCategories = giverCategories.stream().filter(category -> configuredIds.contains(category.id())).toList();
            if (giverCategories.size() != configuredIds.size()) {
                throw new IllegalArgumentException("Giver references a category missing from its catalog");
            }
        }
        TaskSelection selection = selectionService.choose(
                giver.id(),
                context.reputationLevel(),
                context.capabilities(),
                giverCategories,
                catalog.templates(),
                history,
                random
        );

        GeneratedTaskDraft draft = Objects.requireNonNull(
                draftCustomizer.customize(createDraft(context, selection, random)),
                "Task draft customizer returned null"
        );
        DifficultyResult difficulty = difficultyCalculator.calculate(draft);
        RewardBundle rewards = rewardGenerator.generate(
                difficulty, giver, context.reputationLevel(), catalog.rewards(), random);
        GeneratedTask task = new GeneratedTask(
                draft.taskId(),
                draft.playerId(),
                draft.boardId(),
                draft.giverId(),
                draft.categoryId(),
                draft.templateId(),
                draft.variantId(),
                draft.displayText(),
                draft.title(),
                draft.description(),
                draft.objectives(),
                draft.conditions(),
                draft.requiredReputationLevel(),
                difficulty,
                rewards,
                difficulty.tier().reputationReward(),
                draft.acceptedAt(),
                draft.expiresAt(),
                TaskStatus.OFFERED
        );

        history.recordSelection(
                selection.category().id(), selection.template().id(), selection.eligibleCategoryIds());
        return task;
    }

    private static GeneratedTaskDraft createDraft(
            TaskGenerationContext context,
            TaskSelection selection,
            SplittableRandom random
    ) {
        TaskTemplateDefinition template = selection.template();
        TaskVariantDefinition variant = selection.variant();
        String taskId = new UUID(random.nextLong(), random.nextLong()).toString();
        var objectives = new ArrayList<GeneratedObjective>();
        var difficulty = new ArrayList<DifficultyComponent>();
        for (int index = 0; index < variant.objectives().size(); index++) {
            ObjectiveDefinition objective = variant.objectives().get(index);
            objectives.add(new GeneratedObjective("objective_" + index, objective.type(), objective.parameters()));
            difficulty.addAll(objective.difficultyComponents());
        }
        difficulty.addAll(variant.difficultyComponents());
        int requiredLevel = Math.max(selection.category().minimumReputationLevel(), template.minimumReputationLevel());
        var conditions = new java.util.LinkedHashMap<>(variant.conditions());
        Long expiresAt = context.expiresAt();
        if (template.timeLimitTicks() > 0) {
            conditions.put("time_limit_ticks", template.timeLimitTicks());
            expiresAt = Math.addExact(context.acceptedAt(), template.timeLimitTicks());
        }
        return new GeneratedTaskDraft(
                taskId,
                context.playerId(),
                context.boardId(),
                context.giverId(),
                selection.category().id(),
                template.id(),
                variant.id(),
                template.displayText(),
                template.title(),
                template.description(),
                objectives,
                conditions,
                requiredLevel,
                template.baseDifficulty(),
                difficulty,
                context.acceptedAt(),
                expiresAt
        );
    }

    private static void validateScope(
            TaskGenerationContext context,
            TaskGiverDefinition giver,
            SelectionHistory history
    ) {
        if (!giver.id().equals(context.giverId()) || !giver.id().equals(history.giverId())) {
            throw new IllegalArgumentException("Generation context, giver, and history must have the same giver ID");
        }
        if (!context.playerId().equals(history.playerId())) {
            throw new IllegalArgumentException("Generation context and history must have the same player ID");
        }
    }
}
