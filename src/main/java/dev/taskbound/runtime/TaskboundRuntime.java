package dev.taskbound.runtime;

import dev.taskbound.TaskboundMod;
import dev.taskbound.event.TaskHandIns;
import dev.taskbound.api.TaskboundProviders;
import dev.taskbound.block.TaskBoardBlockEntity;
import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.NoEligibleTaskException;
import dev.taskbound.core.OfferProgression;
import dev.taskbound.config.TaskboundServerConfig;
import dev.taskbound.ui.GuildView;
import dev.taskbound.ui.RankView;
import dev.taskbound.ui.LevelRewardView;
import dev.taskbound.ui.JournalSnapshot;
import dev.taskbound.core.CapabilitySet;
import dev.taskbound.core.DifficultyComponent;
import dev.taskbound.core.GeneratedTask;
import dev.taskbound.core.GeneratedTaskDraft;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.core.GeneratedReward;
import dev.taskbound.core.TaskCategoryDefinition;
import dev.taskbound.core.TaskGenerationContext;
import dev.taskbound.core.TaskGenerator;
import dev.taskbound.core.TaskProgress;
import dev.taskbound.core.TaskStatus;
import dev.taskbound.core.TaskVariantDefinition;
import dev.taskbound.core.PlayerCoreState;
import dev.taskbound.core.PlayerReputation;
import dev.taskbound.core.ReputationThresholds;
import dev.taskbound.core.RewardType;
import dev.taskbound.core.SelectionHistory;
import dev.taskbound.data.TaskboundDefinitionManager;
import dev.taskbound.data.TaskboundDefinitions;
import dev.taskbound.item.TaskPackageItem;
import dev.taskbound.menu.TaskHandInMenu;
import net.minecraft.world.SimpleMenuProvider;
import dev.taskbound.network.TaskboundNetworking;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.registry.TaskboundContent;
import dev.taskbound.ui.BoardSnapshot;
import dev.taskbound.ui.LockedCategoryView;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Server-side application service joining the pure engine to Minecraft state and player interactions. */
public final class TaskboundRuntime {
    public static final int MAX_OFFER_COUNT = 18;
    private static final TaskboundRuntime INSTANCE = new TaskboundRuntime();
    private static final ReputationThresholds RETIRED_GIVER_THRESHOLDS = new ReputationThresholds(Map.of(1, 0));

    private TaskboundRuntime() {
    }

    public static TaskboundRuntime get() {
        return INSTANCE;
    }

    public List<GeneratedTask> generateOffers(ServerPlayer player, String giverId, UUID boardId, int count) {
        return generateOffers(player, giverId, boardId, count, false);
    }

    public int offerCount(ServerPlayer player, String giverId) {
        var giver = definitions().giver(giverId);
        return OfferProgression.offerCount(state(player).reputation().forGiver(giverId).level(giver.reputationThresholds()));
    }

    public List<GeneratedTask> generateOffers(ServerPlayer player, String giverId, UUID boardId) {
        return generateOffers(player, giverId, boardId, offerCount(player, giverId));
    }

    /** Operator tooling refreshes the normal offer set without waiting for its timer. */
    public List<GeneratedTask> debugRefreshOffers(ServerPlayer player, String giverId, TaskBoardBlockEntity board, int count) {
        registerBoard(player.serverLevel(), board);
        return generateOffers(player, giverId, board.boardId(), count, true);
    }

    private List<GeneratedTask> generateOffers(ServerPlayer player, String giverId, UUID boardId, int count, boolean forceRefresh) {
        if (count < 1 || count > MAX_OFFER_COUNT) throw new IllegalArgumentException("Offer count must be between 1 and " + MAX_OFFER_COUNT);
        TaskboundDefinitions definitions = definitions();
        requireEnabledGiver(giverId);
        var giver = definitions.giver(giverId);
        TaskboundSavedData savedData = TaskboundSavedData.get(player.server);
        PlayerCoreState state = savedData.player(player.getUUID());
        long now = player.serverLevel().getGameTime();
        long previous = state.lastOfferRefresh(boardId.toString(), giverId);
        int refreshTicks = TaskboundServerConfig.OFFER_REFRESH_TICKS.get();
        if (!forceRefresh && previous >= 0 && now - previous < refreshTicks) {
            throw new IllegalStateException("Offers refresh in " + Math.max(1L, (refreshTicks - (now - previous) + 19) / 20) + " seconds.");
        }
        int level = state.reputation().forGiver(giverId).level(giver.reputationThresholds());
        Set<String> capabilityValues = capabilitiesFor(player, giverId, boardId, level);
        TaskboundSavedData.BoardRecord originBoard = savedData.board(boardId).orElse(
                new TaskboundSavedData.BoardRecord(boardId, player.serverLevel().dimension().location().toString(), player.blockPosition().immutable()));
        var generated = new ArrayList<GeneratedTask>();
        TaskGenerator generator = new TaskGenerator(definitions.catalog(),
                (category, template, variant) -> state.canOffer(template.id(), variant.conditions(), now,
                            TaskboundServerConfig.REPEAT_COOLDOWN_TICKS.get())
                        && generated.stream().noneMatch(offer -> offer.templateId().equals(template.id()))
                        && catalogProfileEligible(template.id(), variant.conditions())
                        && catalogWorldEligible(player, variant)
                        && porterEligible(player, originBoard, variant)
                        && variantRuntimeEligible(savedData, originBoard, variant),
                draft -> customizeDraft(player, savedData, originBoard, level, capabilityValues, draft));
        SelectionHistory history = state.historyFor(giverId);
        for (int index = 0; index < count; index++) {
            var context = new TaskGenerationContext(player.getUUID().toString(), boardId.toString(), giverId,
                    level, new CapabilitySet(capabilityValues), now, null);
            try {
                generated.add(RewardCurrency.convertRewards(generator.generate(context, giver, history,
                        offerSeed(player, boardId, giverId, history.generationCount(), index))));
            } catch (NoEligibleTaskException exhausted) {
                break;
            }
        }
        state.replaceOffers(boardId.toString(), giverId, generated, now);
        savedData.changed();
        TaskboundNetworking.syncPlayer(player);
        return List.copyOf(generated);
    }

    /** Generates one real catalog task while bypassing player progression/cap/cooldown gates for operator testing. */
    public GeneratedTask debugAssign(ServerPlayer player, String giverId, String templateId, TaskBoardBlockEntity board) {
        requireEnabledGiver(giverId);
        TaskboundDefinitions definitions = definitions();
        var template = definitions.templates().get(templateId);
        if (template == null) throw new IllegalArgumentException("Unknown task template: " + templateId);
        var giver = definitions.giver(giverId);
        if (!definitions.categories().get(template.categoryId()).giverId().equals(giverId)) {
            throw new IllegalArgumentException("That template does not belong to " + giverId);
        }
        var category = definitions.categories().get(template.categoryId());
        var debugGiver = new dev.taskbound.core.TaskGiverDefinition(giver.id(), giver.displayName(),
                giver.reputationThresholds(), giver.rankNames(), List.of(category.id()), giver.rewardPoolIds(),
                giver.rewardMultiplier(), giver.enabled(), giver.iconItem());
        int level = Math.max(category.minimumReputationLevel(), template.minimumReputationLevel());
        if (template.maximumReputationLevel() != null && level > template.maximumReputationLevel()) {
            throw new IllegalArgumentException("The template has no usable reputation range");
        }
        registerBoard(player.serverLevel(), board);
        var savedData = TaskboundSavedData.get(player.server);
        var state = state(player);
        var origin = savedData.board(board.boardId()).orElseThrow();
        Set<String> capabilities = capabilitiesFor(player, giverId, board.boardId(), level);
        var generator = new TaskGenerator(definitions.catalog(),
                (candidateCategory, candidateTemplate, variant) -> candidateTemplate.id().equals(templateId)
                        && variant.objectives().stream().noneMatch(objective -> objective.type().equals("deliver_package"))
                        && catalogWorldEligible(player, variant) && porterEligible(player, origin, variant)
                        && variantRuntimeEligible(savedData, origin, variant),
                draft -> customizeDraft(player, savedData, origin, level, capabilities, draft));
        long now = player.serverLevel().getGameTime();
        var context = new TaskGenerationContext(player.getStringUUID(), board.boardId().toString(), giverId,
                level, new CapabilitySet(capabilities), now, null);
        var history = state.historyFor(giverId);
        GeneratedTask offer;
        try {
            offer = RewardCurrency.convertRewards(generator.generate(context, debugGiver, history,
                    offerSeed(player, board.boardId(), giverId, history.generationCount(), 0)));
        } catch (NoEligibleTaskException exception) {
            throw new IllegalStateException("That template has no supported variant in the target player's world");
        }
        GeneratedTask active = offer.withStatus(TaskStatus.ACTIVE);
        var accepted = new ActiveTask(active, new TaskProgress());
        dev.taskbound.porter.PorterTasks.validateAcceptance(player, active);
        state.putActiveTask(accepted);
        dev.taskbound.event.FarmerGameplayEvents.accepted(player, accepted);
        dev.taskbound.porter.PorterTasks.accepted(player, accepted);
        savedData.changed();
        TaskboundNetworking.syncPlayer(player);
        return active;
    }

    public ActiveTask debugSetProgress(ServerPlayer player, String taskId, String objectiveKey, int value) {
        ActiveTask active = debugEditableTask(player, taskId);
        GeneratedObjective objective = active.task().objectives().stream().filter(candidate -> candidate.key().equals(objectiveKey))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown objective key: " + objectiveKey));
        int required = requiredCount(objective);
        if (value < 0 || value > required) throw new IllegalArgumentException("Progress must be between 0 and " + required);
        var counts = new LinkedHashMap<>(active.progress().objectiveProgress());
        counts.put(objectiveKey, value);
        var progress = new TaskProgress(counts, active.progress().playerDeaths(), active.progress().additionalData());
        return debugStoreProgress(player, active, progress);
    }

    public ActiveTask debugComplete(ServerPlayer player, String taskId) {
        ActiveTask active = debugEditableTask(player, taskId);
        var counts = new LinkedHashMap<>(active.progress().objectiveProgress());
        for (var objective : active.task().objectives()) counts.put(objective.key(), requiredCount(objective));
        return debugStoreProgress(player, active,
                new TaskProgress(counts, active.progress().playerDeaths(), active.progress().additionalData()));
    }

    public int debugSetReputation(ServerPlayer player, String giverId, int points) {
        requireEnabledGiver(giverId);
        if (points < 0) throw new IllegalArgumentException("Reputation points cannot be negative");
        var giver = definitions().giver(giverId);
        var state = state(player);
        var reputation = state.reputation().forGiver(giverId);
        int previousLevel = reputation.level(giver.reputationThresholds());
        reputation.setPoints(points, giver.reputationThresholds(), player.serverLevel().getGameTime());
        if (reputation.level(giver.reputationThresholds()) != previousLevel) state.clearGeneratedOffers(giverId);
        TaskboundSavedData.get(player.server).changed();
        TaskboundNetworking.syncPlayer(player);
        return reputation.level(giver.reputationThresholds());
    }

    private ActiveTask debugEditableTask(ServerPlayer player, String taskId) {
        ActiveTask active = state(player).activeTask(taskId);
        if (active == null) throw new IllegalArgumentException("No active task with ID " + taskId + " belongs to " + player.getScoreboardName());
        if (isExpired(active, player.serverLevel().getGameTime())) throw new IllegalStateException("The task has expired; assign a fresh task for testing");
        if (Boolean.TRUE.equals(active.task().conditions().get("no_death")) && active.progress().playerDeaths() > 0) {
            throw new IllegalStateException("Death invalidated this task; assign a fresh task for testing");
        }
        return active;
    }

    private ActiveTask debugStoreProgress(ServerPlayer player, ActiveTask active, TaskProgress progress) {
        boolean complete = active.task().objectives().stream().allMatch(objective -> progress.progressFor(objective.key()) >= requiredCount(objective));
        ActiveTask updated = new ActiveTask(active.task().withStatus(complete ? TaskStatus.COMPLETED : TaskStatus.ACTIVE), progress);
        state(player).putActiveTask(updated);
        TaskboundSavedData.get(player.server).changed();
        TaskboundNetworking.syncPlayer(player);
        return updated;
    }

    private String requireEnabledGiver(String giverId) {
        if (!definitions().giver(giverId).enabled()) throw new IllegalStateException("This guild is deferred in the vanilla-event release.");
        return giverId;
    }

    /** Uses vanilla world configuration/advancements; never scans chunks or tracks player routes. */
    private boolean catalogWorldEligible(ServerPlayer player, TaskVariantDefinition variant) {
        boolean catalog = variant.conditions().containsKey("source_catalog_id");
        for (var objective : variant.objectives()) {
            var parameters = objective.parameters();
            if (objective.type().equals("starcatcher_catch")
                    && !dev.taskbound.compat.StarcatcherFishingProvider.eligible(player, parameters)) return false;
            if (objective.type().equals("farm_action")
                    && !dev.taskbound.event.FarmerGameplayEvents.eligible(player, parameters)) return false;
            if (!catalog) continue;
            if ("minecraft:haste".equals(parameters.get("required_effect"))
                    && !player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED)) {
                var beacon = player.server.getAdvancements().get(ResourceLocation.withDefaultNamespace("nether/create_beacon"));
                if (beacon == null || !player.getAdvancements().getOrStartProgress(beacon).isDone()) return false;
            }
            String dimension = parameters.get("required_dimension") instanceof String id ? id : null;
            String biome = parameters.get("required_biome") instanceof String id ? id : null;
            String biomeTag = parameters.get("required_biome_tag") instanceof String id ? id : null;
            boolean possible = false;
            for (ServerLevel candidate : player.server.getAllLevels()) {
                if (dimension != null && !candidate.dimension().location().toString().equals(dimension)) continue;
                if ((parameters.containsKey("required_weather") || parameters.containsKey("required_time"))
                        && !candidate.dimensionType().hasSkyLight()) continue;
                if (number(parameters.get("min_y"), Integer.MIN_VALUE) >= candidate.getMaxBuildHeight()
                        || number(parameters.get("max_y"), Integer.MAX_VALUE) < candidate.getMinBuildHeight()) continue;
                if (biome != null || biomeTag != null) {
                    var possibleBiomes = candidate.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
                    boolean matches = possibleBiomes.stream().anyMatch(holder ->
                            (biome == null || holder.unwrapKey().map(key -> key.location().toString().equals(biome)).orElse(false))
                            && (biomeTag == null || holder.is(net.minecraft.tags.TagKey.create(
                                    net.minecraft.core.registries.Registries.BIOME, ResourceLocation.parse(biomeTag)))));
                    if (!matches) continue;
                }
                possible = true;
                break;
            }
            if (!possible) return false;
        }
        return true;
    }

    public BoardSnapshot openBoard(ServerPlayer player, TaskBoardBlockEntity board) {
        if (definitions().enabledGivers().isEmpty()) {
            registerBoard(player.serverLevel(), board);
            var active = activeTasks(player);
            long now = player.serverLevel().getGameTime();
            // Removed guilds must not make already accepted tasks inaccessible.
            return new BoardSnapshot(board.boardId().toString(), board.getBlockPos().getX(),
                    board.getBlockPos().getY(), board.getBlockPos().getZ(), board.giverId(),
                    "screen.taskbound.task_board.saved_tasks", 0, 1, 0, 0,
                    "screen.taskbound.task_board.no_guilds", now, List.of(), active, List.of(), "",
                    List.of(), Map.of(), active.stream().filter(value -> canClaim(player, value, now))
                            .map(value -> value.task().taskId()).toList());
        }
        String giver = board.giverId();
        if (!definitions().givers().containsKey(giver) || !definitions().giver(giver).enabled()) {
            giver = definitions().enabledGivers().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No enabled guilds are configured")).id();
        }
        return openBoard(player, board, giver);
    }

    public BoardSnapshot openBoard(ServerPlayer player, TaskBoardBlockEntity board, String giverId) {
        requireEnabledGiver(giverId);
        registerBoard(player.serverLevel(), board);
        dev.taskbound.porter.PorterTasks.visitBoard(player, board);
        var offers = currentBoardOffers(player, board, giverId);
        return boardSnapshot(player, board, giverId, offers, offerNotice(player, giverId, offers));
    }

    private String offerNotice(ServerPlayer player, String giverId, List<GeneratedTask> offers) {
        String notice = "";
        if (offers.isEmpty()) notice = Component.translatable(giverId.equals("taskbound:porter")
                ? "taskbound.porter.no_offers" : "taskbound.task.no_offers").getString();
        if (offers.isEmpty() && giverId.equals("taskbound:angler")
                && net.neoforged.fml.ModList.get().isLoaded("starcatcher")
                && !dev.taskbound.compat.StarcatcherFishingProvider.ready(player)) {
            notice = Component.translatable("taskbound.starcatcher.unavailable").getString();
        }
        return notice;
    }

    public BoardSnapshot viewBoard(ServerPlayer player, TaskBoardBlockEntity board, String giverId) {
        dev.taskbound.porter.PorterTasks.visitBoard(player, board);
        var giver = definitions().givers().get(giverId);
        if (giver == null || !giver.enabled()) return openBoard(player, board);
        var offers = currentBoardOffers(player, board, giverId);
        return boardSnapshot(player, board, giverId, offers, offerNotice(player, giverId, offers));
    }

    private List<GeneratedTask> currentBoardOffers(ServerPlayer player, TaskBoardBlockEntity board, String giverId) {
        if (state(player).offersNeedRefresh(board.boardId().toString(), giverId,
                player.serverLevel().getGameTime(), TaskboundServerConfig.OFFER_REFRESH_TICKS.get())) {
            registerBoard(player.serverLevel(), board);
            return generateOffers(player, giverId, board.boardId());
        }
        return boardOffers(player, board, giverId);
    }

    private List<GeneratedTask> boardOffers(ServerPlayer player, TaskBoardBlockEntity board, String giverId) {
        return offers(player).stream().filter(offer -> offer.boardId().equals(board.boardId().toString()))
                .filter(offer -> offer.giverId().equals(giverId)).toList();
    }

    public BoardSnapshot refreshBoardOffers(ServerPlayer player, TaskBoardBlockEntity board) {
        return refreshBoardOffers(player, board, board.giverId());
    }

    public BoardSnapshot refreshBoardOffers(ServerPlayer player, TaskBoardBlockEntity board, String giverId) {
        registerBoard(player.serverLevel(), board);
        var offers = generateOffers(player, giverId, board.boardId());
        return boardSnapshot(player, board, giverId, offers, offers.isEmpty() ? offerNotice(player, giverId, offers) : "Offers refreshed.");
    }

    public BoardSnapshot cycleBoardGiverAndOpen(ServerPlayer player, TaskBoardBlockEntity board) {
        board.cycleGiver();
        return openBoard(player, board);
    }

    public BoardSnapshot selectBoardGiverAndOpen(ServerPlayer player, TaskBoardBlockEntity board, String giverId) {
        return openBoard(player, board, giverId);
    }

    private BoardSnapshot boardSnapshot(ServerPlayer player, TaskBoardBlockEntity board, String giverId,
            List<GeneratedTask> offers, String notice) {
        GuildView guild = guildView(player, definitions().giver(giverId));
        var locked = definitions().catalog().categoriesForGiver(giverId).stream()
                .filter(category -> definitions().giver(giverId).categoryIds().contains(category.id()))
                .filter(this::categoryProfileEligible)
                .filter(category -> category.minimumReputationLevel() > guild.reputationLevel())
                .sorted(Comparator.comparingInt(TaskCategoryDefinition::minimumReputationLevel).thenComparing(TaskCategoryDefinition::id))
                .map(category -> new LockedCategoryView(category.id(), translationKey("task_category", category.id()), category.minimumReputationLevel())).toList();
        var unseen = new LinkedHashMap<String, List<String>>();
        for (var giver : definitions().enabledGivers()) {
            var ids = state(player).unseenOfferIds(board.boardId().toString(), giver.id());
            if (!ids.isEmpty()) unseen.put(giver.id(), ids);
        }
        var active = activeTasks(player);
        long now = player.serverLevel().getGameTime();
        return new BoardSnapshot(board.boardId().toString(), board.getBlockPos().getX(), board.getBlockPos().getY(), board.getBlockPos().getZ(),
                giverId, guild.displayName(), guild.reputationPoints(), guild.reputationLevel(), guild.levelFloor(), guild.nextLevelThreshold(),
                guild.rankName(), player.serverLevel().getGameTime(), offers,
                active, locked, notice, guildViews(player), unseen,
                active.stream().filter(value -> canClaim(player, value, now)).map(value -> value.task().taskId()).toList());
    }

    public JournalSnapshot journalSnapshot(ServerPlayer player) {
        return new JournalSnapshot(player.serverLevel().getGameTime(), activeTasks(player), guildViews(player), "");
    }

    private List<GuildView> guildViews(ServerPlayer player) {
        return definitions().enabledGivers().stream().map(giver -> guildView(player, giver)).toList();
    }

    private GuildView guildView(ServerPlayer player, dev.taskbound.core.TaskGiverDefinition giver) {
        PlayerCoreState state = state(player);
        PlayerReputation reputation = state.reputation().forGiver(giver.id());
        int level = reputation.level(giver.reputationThresholds());
        var thresholds = giver.reputationThresholds().pointsByLevel();
        int floor = thresholds.floorEntry(level).getValue();
        var next = thresholds.higherEntry(level);
        String rank = giver.rankNames().entrySet().stream().filter(entry -> entry.getKey() <= level)
                .max(Map.Entry.comparingByKey()).map(Map.Entry::getValue).orElse(giver.displayName());
        var ranks = List.of(1, 5, 10, 15, 20).stream().filter(thresholds::containsKey).map(rankLevel -> {
            var unlocks = giver.categoryIds().stream().map(definitions().categories()::get)
                    .filter(this::categoryProfileEligible)
                    .filter(category -> category.minimumReputationLevel() == rankLevel)
                    .map(category -> translationKey("task_category", category.id())).toList();
            return new RankView(rankLevel, thresholds.get(rankLevel), giver.rankNames().getOrDefault(rankLevel, "Level " + rankLevel), unlocks);
        }).toList();
        var gifts = definitions().levelRewards(giver.id()).stream().sorted(Comparator.comparingInt(dev.taskbound.core.LevelRewardDefinition.Gift::level))
                .map(gift -> new LevelRewardView(gift.level(), level >= gift.level() && !state.levelRewardClaimed(giver.id(), gift.level()),
                        state.levelRewardClaimed(giver.id(), gift.level()), gift.items().stream()
                            .map(item -> item.count() + " × " + BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.item())).getDescription().getString())
                            .collect(java.util.stream.Collectors.joining(", ")), gift.items())).toList();
        return new GuildView(giver.id(), giver.displayName(), giver.iconItem(), reputation.points(), level, floor,
                next == null ? floor : next.getValue(), rank, ranks, gifts);
    }

    public void claimLevelReward(ServerPlayer player, String giverId, int level) {
        requireEnabledGiver(giverId);
        var giver = definitions().giver(giverId);
        PlayerCoreState state = state(player);
        if (state.reputation().forGiver(giverId).level(giver.reputationThresholds()) < level) {
            throw new IllegalStateException("This reputation level has not been reached");
        }
        var gift = definitions().levelRewards(giverId).stream().filter(value -> value.level() == level).findFirst()
                .orElseThrow(() -> new IllegalStateException("No reward is configured for this level"));
        var items = gift.items().stream().map(item -> {
            ResourceLocation id = ResourceLocation.parse(item.item());
            if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("Unknown reward item: " + id);
            return new ItemGrant(BuiltInRegistries.ITEM.get(id), item.count());
        }).toList();
        state.claimLevelReward(giverId, level);
        TaskboundSavedData.get(player.server).changed();
        grantRewards(player, new RewardGrantPlan(items, 0, Map.of()));
        TaskboundNetworking.syncPlayer(player);
    }

    public void cycleBoardGiver(ServerPlayer player, TaskBoardBlockEntity board) {
        registerBoard(player.serverLevel(), board);
        player.sendSystemMessage(Component.literal("Default board guild: " + shortId(board.cycleGiver())));
    }

    public boolean tryDeliverPackage(ServerPlayer player, TaskBoardBlockEntity board, ItemStack stack) {
        if (!stack.is(TaskboundContent.TASK_PACKAGE.get())) {
            return false;
        }
        registerBoard(player.serverLevel(), board);
        Optional<UUID> taskUuid = TaskPackageItem.taskId(stack);
        Optional<UUID> packageUuid = TaskPackageItem.packageId(stack);
        Optional<UUID> originUuid = TaskPackageItem.originBoardId(stack);
        if (taskUuid.isEmpty() || packageUuid.isEmpty() || originUuid.isEmpty()
                || !packageUuid.get().equals(packageId(taskUuid.get()))
                || originUuid.get().equals(board.boardId())) {
            player.sendSystemMessage(Component.literal("That package is not a valid delivery for this board."));
            return true;
        }

        PlayerCoreState state = state(player);
        ActiveTask active = state.activeTask(taskUuid.get().toString());
        if (active == null || active.task().status() != TaskStatus.ACTIVE) {
            player.sendSystemMessage(Component.literal("That sealed package has no active task."));
            return true;
        }
        if (isExpired(active, player.serverLevel().getGameTime())) {
            failTask(player, active, "The delivery expired before it reached its destination.");
            return true;
        }
        GeneratedObjective delivery = active.task().objectives().stream()
                .filter(objective -> objective.type().equals("deliver_package"))
                .findFirst()
                .orElse(null);
        if (delivery == null) {
            player.sendSystemMessage(Component.literal("That package is not attached to a delivery objective."));
            return true;
        }
        Object expectedDestination = active.progress().additionalData().get("destination_board_uuid");
        if (!board.boardId().toString().equals(expectedDestination)) {
            player.sendSystemMessage(Component.literal("This is not the package's assigned destination board."));
            return true;
        }
        if (Boolean.TRUE.equals(delivery.parameters().get("checkpoint_required"))
                && !Boolean.TRUE.equals(active.progress().additionalData().get(checkpointKey(delivery)))) {
            player.sendSystemMessage(Component.literal("The required route checkpoint has not been visited."));
            return true;
        }
        boolean advanced = advanceTaskObjective(
                player,
                active.task().taskId(),
                objective -> objective.type().equals("deliver_package"),
                1
        );
        if (advanced) {
            stack.shrink(1);
            player.sendSystemMessage(Component.literal("The sealed package was accepted."));
        }
        return true;
    }

    public List<GeneratedTask> offers(ServerPlayer player) {
        return state(player).generatedOffers().values().stream()
                .sorted(Comparator.comparing(GeneratedTask::taskId))
                .toList();
    }

    public List<ActiveTask> activeTasks(ServerPlayer player) {
        return state(player).activeTasks().values().stream()
                .sorted(Comparator.comparing(active -> active.task().taskId()))
                .toList();
    }

    public GeneratedTask accept(ServerPlayer player, int oneBasedIndex) {
        List<GeneratedTask> offers = offers(player);
        if (oneBasedIndex < 1 || oneBasedIndex > offers.size()) {
            throw new IllegalArgumentException("Offer index must be between 1 and " + offers.size());
        }
        return acceptOffer(player, offers.get(oneBasedIndex - 1));
    }

    public GeneratedTask accept(
            ServerPlayer player,
            String taskId,
            TaskBoardBlockEntity board
    ) {
        GeneratedTask offer = state(player).generatedOffer(taskId);
        if (offer == null) {
            throw new IllegalStateException("That offer is no longer available");
        }
        if (!offer.playerId().equals(player.getUUID().toString())
                || !offer.boardId().equals(board.boardId().toString())
                || offer.status() != TaskStatus.OFFERED) {
            throw new IllegalStateException("That offer does not belong to this task board");
        }
        currentBoardOffers(player, board, offer.giverId());
        if (state(player).generatedOffer(taskId) == null) {
            throw new IllegalStateException("That offer expired. The board now has new tasks.");
        }
        return acceptOffer(player, offer);
    }

    private GeneratedTask acceptOffer(ServerPlayer player, GeneratedTask offer) {
        TaskboundDefinitions definitions = definitions();
        requireEnabledGiver(offer.giverId());
        PlayerCoreState current = state(player);
        if (current.offersNeedRefresh(offer.boardId(), offer.giverId(), player.serverLevel().getGameTime(),
                TaskboundServerConfig.OFFER_REFRESH_TICKS.get())) {
            generateOffers(player, offer.giverId(), UUID.fromString(offer.boardId()));
            throw new IllegalStateException("That offer expired. The board now has new tasks.");
        }
        if (!definitions.giver(offer.giverId()).categoryIds().contains(offer.categoryId())
                || !definitions.templates().containsKey(offer.templateId())) {
            throw new IllegalStateException("This offer is no longer part of the enabled catalog");
        }
        if (current.activeTasks().size() >= TaskboundServerConfig.MAX_ACTIVE_TASKS.get()) {
            throw new IllegalStateException("Your active-task limit has been reached");
        }
        if (number(offer.conditions().get("catalog_difficulty"), 0) == 5
                && current.activeTasks().values().stream().anyMatch(active -> number(active.task().conditions().get("catalog_difficulty"), 0) == 5)) {
            throw new IllegalStateException("Only one tier-five commission may be active at a time");
        }
        if (!current.canOffer(offer.templateId(), offer.conditions(), player.serverLevel().getGameTime(), TaskboundServerConfig.REPEAT_COOLDOWN_TICKS.get())) {
            throw new IllegalStateException("This task is already active, on cooldown, or already claimed once");
        }
        int level = state(player).reputation().forGiver(offer.giverId())
                .level(definitions.giver(offer.giverId()).reputationThresholds());
        if (level < offer.requiredReputationLevel()) {
            throw new IllegalStateException("This task requires reputation level " + offer.requiredReputationLevel());
        }
        var currentTemplate = definitions.templates().get(offer.templateId());
        var currentVariant = currentTemplate.variants().stream().filter(variant -> variant.id().equals(offer.variantId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("This offer changed after a data reload"));
        if (!catalogProfileEligible(currentTemplate.id(), currentVariant.conditions())
                || !currentVariant.supportedBy(new CapabilitySet(capabilitiesFor(player, offer.giverId(), UUID.fromString(offer.boardId()), level)))
                || !catalogWorldEligible(player, currentVariant)) {
            throw new IllegalStateException("This task is no longer achievable with the available capabilities");
        }

        TaskboundSavedData savedData = TaskboundSavedData.get(player.server);
        PlayerCoreState state = savedData.player(player.getUUID());
        TaskProgress progress = new TaskProgress();
        Optional<GeneratedObjective> delivery = offer.objectives().stream()
                .filter(objective -> objective.type().equals("deliver_package"))
                .findFirst();
        if (delivery.isPresent()) {
            GeneratedObjective objective = delivery.get();
            String destinationId = stringParameter(objective, "destination_board_uuid");
            if (destinationId == null) {
                throw new IllegalStateException("The generated delivery has no destination");
            }
            UUID destinationUuid = UUID.fromString(destinationId);
            var destination = savedData.board(destinationUuid).orElseThrow(() ->
                    new IllegalStateException("The generated destination board no longer exists"));
            progress.putAdditionalData("destination_board_uuid", destination.boardId().toString());
            progress.putAdditionalData("destination_dimension", destination.dimension());
            progress.putAdditionalData("destination_x", destination.position().getX());
            progress.putAdditionalData("destination_y", destination.position().getY());
            progress.putAdditionalData("destination_z", destination.position().getZ());
        }

        dev.taskbound.porter.PorterTasks.validateAcceptance(player, offer);
        state.removeGeneratedOffer(offer.taskId());
        long acceptedAt = player.serverLevel().getGameTime();
        Long activeExpiry = offer.expiresAt() == null
                ? null
                : Math.addExact(acceptedAt, Math.subtractExact(offer.expiresAt(), offer.acceptedAt()));
        GeneratedTask activeTask = offer.withTiming(acceptedAt, activeExpiry).withStatus(TaskStatus.ACTIVE);
        ActiveTask accepted = dev.taskbound.core.TaskTimer.resume(new ActiveTask(activeTask, progress), acceptedAt);
        activeTask = accepted.task();
        state.putActiveTask(accepted);
        dev.taskbound.event.FarmerGameplayEvents.accepted(player, accepted);
        dev.taskbound.porter.PorterTasks.accepted(player, accepted);
        savedData.changed();

        if (delivery.isPresent()) {
            UUID taskId = UUID.fromString(activeTask.taskId());
            UUID originBoard = UUID.fromString(activeTask.boardId());
            ItemStack sealed = TaskPackageItem.seal(
                    TaskboundContent.TASK_PACKAGE.get(), packageId(taskId), taskId, originBoard);
            var destination = progress.additionalData();
            TaskPackageItem.stamp(sealed, ResourceLocation.parse((String) destination.get("destination_dimension")),
                    new BlockPos(((Number) destination.get("destination_x")).intValue(),
                            ((Number) destination.get("destination_y")).intValue(), ((Number) destination.get("destination_z")).intValue()));
            if (!player.getInventory().add(sealed)) {
                player.drop(sealed, false);
            }
        }
        TaskboundNetworking.syncPlayer(player);
        return activeTask;
    }

    /** Shared eligibility for tracked events; final deadline validation still occurs when progress commits. */
    public boolean isObjectiveEligible(ActiveTask active, GeneratedObjective objective) {
        if (active.task().status() != TaskStatus.ACTIVE
                || dev.taskbound.core.TaskTimer.online(active.task()) && dev.taskbound.core.TaskTimer.remaining(active) == 0) return false;
        int index = active.task().objectives().indexOf(objective);
        return index >= 0 && sequenceAllows(active, index)
                && active.progress().progressFor(objective.key()) < requiredCount(objective);
    }

    public void markChanged(ServerPlayer player) {
        TaskboundSavedData.get(player.server).changed();
    }

    public void failActiveTask(MinecraftServer server, UUID playerId, String taskId, String message) {
        failActiveTask(server, playerId, taskId, Component.literal(message));
    }

    public void failActiveTask(MinecraftServer server, UUID playerId, String taskId, Component message) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            failActiveTask(player, taskId, message);
            return;
        }
        var saved = TaskboundSavedData.get(server);
        var state = saved.player(playerId);
        ActiveTask active = state.removeActiveTask(taskId);
        if (active != null) {
            state.reputation().forGiver(active.task().giverId()).recordFailure();
            saved.changed();
        }
    }

    public void failActiveTask(ServerPlayer player, String taskId, String message) {
        failActiveTask(player, taskId, Component.literal(message));
    }

    public void failActiveTask(ServerPlayer player, String taskId, Component message) {
        ActiveTask active = state(player).activeTask(taskId);
        if (active != null) failTask(player, active, message);
    }

    public boolean advanceMatching(ServerPlayer player,
            java.util.function.BiPredicate<ActiveTask, GeneratedObjective> predicate, int amount) {
        boolean changed = false;
        for (ActiveTask active : activeTasks(player)) {
            changed |= advanceTaskObjective(player, active.task().taskId(),
                    objective -> predicate.test(active, objective), amount);
        }
        return changed;
    }

    public void resumeTimers(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        for (ActiveTask active : activeTasks(player)) {
            if (!dev.taskbound.core.TaskTimer.online(active.task())) continue;
            state(player).putActiveTask(dev.taskbound.core.TaskTimer.resume(active, now));
            markChanged(player);
        }
    }

    public boolean advanceMatching(
            ServerPlayer player,
            Predicate<GeneratedObjective> predicate,
            int amount
    ) {
        boolean changed = false;
        for (ActiveTask active : activeTasks(player)) {
            changed |= advanceTaskObjective(player, active.task().taskId(), predicate, amount);
        }
        return changed;
    }

    public boolean advanceTaskObjective(
            ServerPlayer player,
            String taskId,
            Predicate<GeneratedObjective> predicate,
            int amount
    ) {
        return advanceTaskObjective(player.server, player.getUUID(), taskId, predicate, amount, player);
    }

    public boolean advanceTaskObjective(
            MinecraftServer server,
            UUID playerId,
            String taskId,
            Predicate<GeneratedObjective> predicate,
            int amount
    ) {
        return advanceTaskObjective(
                server, playerId, taskId, predicate, amount, server.getPlayerList().getPlayer(playerId));
    }

    private boolean advanceTaskObjective(
            MinecraftServer server,
            UUID playerId,
            String taskId,
            Predicate<GeneratedObjective> predicate,
            int amount,
            ServerPlayer onlinePlayer
    ) {
        if (amount < 1) {
            throw new IllegalArgumentException("Progress amount must be positive");
        }
        TaskboundSavedData savedData = TaskboundSavedData.get(server);
        PlayerCoreState state = savedData.player(playerId);
        ActiveTask active = state.activeTask(taskId);
        if (active == null || active.task().status() != TaskStatus.ACTIVE) {
            return false;
        }
        if (onlinePlayer != null && isExpired(active, onlinePlayer.serverLevel().getGameTime())) {
            failTask(onlinePlayer, active, "A timed task expired.");
            return false;
        }
        List<GeneratedObjective> objectives = active.task().objectives();
        for (int index = 0; index < objectives.size(); index++) {
            GeneratedObjective objective = objectives.get(index);
            if (!isObjectiveEligible(active, objective)) continue;
            int required = requiredCount(objective);
            int current = active.progress().progressFor(objective.key());
            if (!predicate.test(objective)) continue;
            active.progress().addProgress(objective.key(), Math.min(amount, required - current));
            completeIfReady(state, active, onlinePlayer);
            savedData.changed();
            if (onlinePlayer != null) TaskboundNetworking.syncPlayer(onlinePlayer);
            return true;
        }
        return false;
    }

    public Optional<String> firstEligibleTaskId(
            ServerPlayer player,
            Predicate<GeneratedObjective> predicate
    ) {
        for (ActiveTask active : activeTasks(player)) {
            if (active.task().status() != TaskStatus.ACTIVE) {
                continue;
            }
            List<GeneratedObjective> objectives = active.task().objectives();
            for (int index = 0; index < objectives.size(); index++) {
                GeneratedObjective objective = objectives.get(index);
                if (predicate.test(objective)
                        && sequenceAllows(active, index)
                        && active.progress().progressFor(objective.key()) < requiredCount(objective)) {
                    return Optional.of(active.task().taskId());
                }
            }
        }
        return Optional.empty();
    }

    public void recordPlayerDeath(ServerPlayer player) {
        TaskboundSavedData savedData = TaskboundSavedData.get(player.server);
        for (ActiveTask active : activeTasks(player)) {
            if (active.task().status() != TaskStatus.ACTIVE && active.task().status() != TaskStatus.COMPLETED) continue;
            active.progress().recordPlayerDeath();
            if (Boolean.TRUE.equals(active.task().conditions().get("no_death"))) {
                failTask(player, active, "Death invalidated a no-death task.");
            } else if (Boolean.TRUE.equals(active.task().conditions().get("reset_on_death"))) {
                active.progress().resetProgress();
                var restarted = new ActiveTask(active.task().withStatus(TaskStatus.ACTIVE), active.progress());
                if (active.task().status() == TaskStatus.COMPLETED) {
                    restarted = dev.taskbound.core.TaskTimer.resume(restarted, player.serverLevel().getGameTime());
                }
                state(player).putActiveTask(restarted);
                player.sendSystemMessage(Component.literal("Your task streak was reset by death."));
            }
            savedData.changed();
        }
        TaskboundNetworking.syncPlayer(player);
    }

    public void tickPlayer(ServerPlayer player) {
        long gameTime = player.serverLevel().getGameTime();
        TaskboundSavedData savedData = TaskboundSavedData.get(player.server);
        for (ActiveTask active : activeTasks(player)) {
            if (active.task().status() != TaskStatus.ACTIVE) {
                continue;
            }
            if (dev.taskbound.core.TaskTimer.online(active.task())) {
                active = dev.taskbound.core.TaskTimer.tick(active, gameTime);
                state(player).putActiveTask(active);
                savedData.changed();
            }
            if (isExpired(active, gameTime)) {
                failTask(player, active, "A timed task expired.");
                continue;
            }
            for (GeneratedObjective objective : active.task().objectives()) {
                if (!objective.type().equals("deliver_package")
                        || !Boolean.TRUE.equals(objective.parameters().get("checkpoint_required"))
                        || Boolean.TRUE.equals(active.progress().additionalData().get(checkpointKey(objective)))) {
                    continue;
                }
                String dimension = stringParameter(objective, "checkpoint_dimension");
                if (dimension != null && !dimension.equals(player.serverLevel().dimension().location().toString())) {
                    continue;
                }
                double checkpointX = decimal(objective.parameters().get("checkpoint_x"), Double.NaN);
                double checkpointY = decimal(objective.parameters().get("checkpoint_y"), Double.NaN);
                double checkpointZ = decimal(objective.parameters().get("checkpoint_z"), Double.NaN);
                double radius = decimal(objective.parameters().get("checkpoint_radius"), 24.0D);
                double verticalTolerance = decimal(
                        objective.parameters().get("checkpoint_vertical_tolerance"), 64.0D);
                double deltaX = player.getX() - checkpointX;
                double deltaY = player.getY() - checkpointY;
                double deltaZ = player.getZ() - checkpointZ;
                if (Double.isFinite(checkpointX) && Double.isFinite(checkpointY) && Double.isFinite(checkpointZ)
                        && deltaX * deltaX + deltaZ * deltaZ <= radius * radius
                        && Math.abs(deltaY) <= verticalTolerance) {
                    active.progress().putAdditionalData(checkpointKey(objective), true);
                    savedData.changed();
                    player.sendSystemMessage(Component.literal("Delivery checkpoint reached."));
                }
            }
        }
    }

    public void recordPlayerDamage(ServerPlayer player, float damage) {
        if (!(damage > 0.0F) || !Float.isFinite(damage)) {
            return;
        }
        TaskboundSavedData savedData = TaskboundSavedData.get(player.server);
        for (ActiveTask active : activeTasks(player)) {
            if (active.task().status() != TaskStatus.ACTIVE) {
                continue;
            }
            for (GeneratedObjective objective : active.task().objectives()) {
                Object maximumValue = objective.parameters().get("maximum_player_damage");
                if (!objective.type().equals("deliver_package") || !(maximumValue instanceof Number maximum)) {
                    continue;
                }
                String key = damageKey(objective);
                double current = decimal(active.progress().additionalData().get(key), 0.0D);
                double total = current + damage;
                active.progress().putAdditionalData(key, total);
                savedData.changed();
                if (total > maximum.doubleValue()) {
                    failTask(player, active, "The fragile cargo was damaged.");
                }
                break;
            }
        }
    }

    public void recordTeleport(ServerPlayer player) {
        failRestrictedDeliveries(player, "teleportation_allowed", "Teleporting invalidated the delivery task.");
    }

    public void recordDimensionChange(ServerPlayer player) {
        failRestrictedDeliveries(
                player,
                "dimensional_travel_allowed",
                "Dimensional travel invalidated the delivery task."
        );
    }

    public ActiveTask forceComplete(ServerPlayer player) {
        ActiveTask active = activeTasks(player).stream()
                .filter(value -> value.task().status() == TaskStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active task to complete"));
        return debugComplete(player, active.task().taskId());
    }

    public GeneratedTask abandon(ServerPlayer player) {
        ActiveTask active = activeTasks(player).stream()
                .filter(value -> value.task().status() == TaskStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active task to abandon"));
        PlayerCoreState state = state(player);
        state.removeActiveTask(active.task().taskId());
        state.reputation().forGiver(active.task().giverId()).recordFailure();
        removePackage(player, UUID.fromString(active.task().taskId()));
        TaskboundSavedData.get(player.server).changed();
        TaskboundNetworking.syncPlayer(player);
        return active.task().withStatus(TaskStatus.ABANDONED);
    }

    public GeneratedTask abandon(ServerPlayer player, String taskId) {
        ActiveTask active = state(player).activeTask(taskId);
        if (active == null) throw new IllegalStateException("This task is no longer active");
        state(player).removeActiveTask(taskId);
        state(player).reputation().forGiver(active.task().giverId()).recordFailure();
        removePackage(player, UUID.fromString(taskId));
        TaskboundSavedData.get(player.server).changed();
        TaskboundNetworking.syncPlayer(player);
        return active.task().withStatus(TaskStatus.ABANDONED);
    }

    public GeneratedTask claim(ServerPlayer player) {
        ActiveTask active = activeTasks(player).stream()
                .filter(value -> canClaim(player, value, player.serverLevel().getGameTime()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No completed task has rewards ready"));
        var hit = player.pick(8.0D, 0.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                || !(player.serverLevel().getBlockEntity(blockHit.getBlockPos()) instanceof TaskBoardBlockEntity board)) {
            throw new IllegalStateException("Look at a task board to claim your rewards");
        }
        return claim(player, active.task().taskId(), board);
    }

    public GeneratedTask claim(
            ServerPlayer player,
            String taskId,
            TaskBoardBlockEntity board
    ) {
        validateClaimBoard(player, board);
        ActiveTask active = state(player).activeTask(taskId);
        if (active == null || !canClaim(player, active, player.serverLevel().getGameTime())) {
            throw new IllegalStateException("That task does not have rewards ready");
        }
        if (!active.task().playerId().equals(player.getUUID().toString())) {
            throw new IllegalStateException("This task belongs to another player");
        }
        if (TaskHandIns.hasHandIns(active)) throw new IllegalStateException("Use Hand in to select and confirm the items to submit");
        dev.taskbound.porter.PorterTasks.validateClaim(player, active, board);
        return claimTask(player, active, null);
    }

    private void validateClaimBoard(ServerPlayer player, TaskBoardBlockEntity board) {
        if (!player.isAlive() || player.isSpectator() || board == null
                || board.getLevel() != player.serverLevel() || board.isRemoved()
                || !player.serverLevel().hasChunkAt(board.getBlockPos())
                || player.serverLevel().getBlockEntity(board.getBlockPos()) != board
                || player.distanceToSqr(board.getBlockPos().getX() + 0.5D, board.getBlockPos().getY() + 0.5D,
                        board.getBlockPos().getZ() + 0.5D) > 64.0D) {
            throw new IllegalStateException("Use a nearby task board to claim rewards");
        }
    }

    public boolean isHandInTask(ServerPlayer player, String taskId) {
        ActiveTask active = state(player).activeTask(taskId);
        return active != null && TaskHandIns.hasHandIns(active);
    }

    private ActiveTask requireHandIn(ServerPlayer player, String taskId, TaskBoardBlockEntity board) {
        validateClaimBoard(player, board);
        ActiveTask active = state(player).activeTask(taskId);
        if (active == null || !active.task().playerId().equals(player.getStringUUID())
                || !TaskHandIns.hasHandIns(active) || !TaskHandIns.nonHandInsComplete(active)
                || active.task().status() != TaskStatus.ACTIVE && active.task().status() != TaskStatus.COMPLETED) {
            throw new IllegalStateException("Complete the other objectives before handing in items");
        }
        if (isExpired(active, player.serverLevel().getGameTime())) throw new IllegalStateException("This task has expired");
        if (Boolean.TRUE.equals(active.task().conditions().get("no_death")) && active.progress().playerDeaths() > 0) {
            throw new IllegalStateException("Death invalidated this task");
        }
        if (dev.taskbound.event.TaskboundWorldEvents.hasPendingWardenResponse(active)) {
            throw new IllegalStateException("Wait for the nearby shrieker to settle");
        }
        return active;
    }

    public void openHandIn(ServerPlayer player, String taskId, TaskBoardBlockEntity board) {
        ActiveTask active = requireHandIn(player, taskId, board);
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                new TaskHandInMenu(id, inventory, active.task(), board),
                Component.translatable("taskbound.hand_in.title")),
                buffer -> TaskHandInMenu.writeOpeningData(buffer, active.task()));
    }

    public boolean canConfirmHandIn(ServerPlayer player, TaskHandInMenu menu) {
        if (player.containerMenu != menu || !menu.stillValid(player)) return false;
        try {
            ActiveTask active = requireHandIn(player, menu.task().taskId(), menu.board());
            return TaskHandIns.plan(player, active, menu.offered()).isPresent();
        } catch (IllegalStateException | IllegalArgumentException invalid) {
            return false;
        }
    }

    public GeneratedTask confirmHandIn(ServerPlayer player, TaskHandInMenu menu) {
        if (player.containerMenu != menu || !menu.stillValid(player)) throw new IllegalStateException("This hand-in is no longer open");
        ActiveTask active = requireHandIn(player, menu.task().taskId(), menu.board());
        var submission = TaskHandIns.plan(player, active, menu.offered())
                .orElseThrow(() -> new IllegalStateException("Choose all required items before confirming"));
        GeneratedTask claimed = claimTask(player, active, submission);
        player.closeContainer();
        TaskboundNetworking.openBoard(player, viewBoard(player, menu.board(), active.task().giverId()).withNotice("Rewards claimed."));
        return claimed;
    }

    private boolean canClaim(ServerPlayer player, ActiveTask active, long now) {
        if (isExpired(active, now) || Boolean.TRUE.equals(active.task().conditions().get("no_death"))
                && active.progress().playerDeaths() > 0) return false;
        if (active.task().status() != TaskStatus.ACTIVE && active.task().status() != TaskStatus.COMPLETED) return false;
        if (dev.taskbound.event.TaskboundWorldEvents.hasPendingWardenResponse(active)) return false;
        return TaskHandIns.hasHandIns(active) ? TaskHandIns.canSubmit(player, active) : active.readyToClaim(now);
    }

    private GeneratedTask claimTask(ServerPlayer player, ActiveTask active, TaskHandIns.Submission submission) {
        TaskboundDefinitions definitions = definitions();
        PlayerCoreState state = state(player);
        GeneratedTask task = active.task();
        if (Boolean.TRUE.equals(task.conditions().get("no_death")) && active.progress().playerDeaths() > 0) {
            throw new IllegalStateException("Death invalidated this task");
        }
        if (isExpired(active, player.serverLevel().getGameTime())) throw new IllegalStateException("This task has expired");
        if (dev.taskbound.event.TaskboundWorldEvents.hasPendingWardenResponse(active)) throw new IllegalStateException("Wait for the nearby shrieker to settle");
        if (TaskHandIns.hasHandIns(active) ? submission == null : !active.readyToClaim(player.serverLevel().getGameTime())) {
            throw new IllegalStateException("Complete the objectives and bring all required hand-in items");
        }
        RewardGrantPlan rewardPlan = planRewards(task);
        validateReputationPlan(state, task, rewardPlan);

        if (submission != null) submission.consume();
        state.removeActiveTask(task.taskId());
        long claimedAt = player.serverLevel().getGameTime();
        state.recordTemplateCompletion(task.templateId(), task.conditions(), claimedAt);
        for (var entry : rewardPlan.reputationPoints().entrySet()) {
            var thresholds = reputationThresholds(definitions, entry.getKey());
            var reputation = state.reputation().forGiver(entry.getKey());
            int previousLevel = reputation.level(thresholds);
            reputation.addPoints(entry.getValue(), thresholds, claimedAt);
            if (reputation.level(thresholds) != previousLevel) state.clearGeneratedOffers(entry.getKey());
        }
        var thresholds = reputationThresholds(definitions, task.giverId());
        var reputation = state.reputation().forGiver(task.giverId());
        int previousLevel = reputation.level(thresholds);
        reputation.recordCompletion(task.reputationReward(), thresholds, claimedAt);
        if (reputation.level(thresholds) != previousLevel) state.clearGeneratedOffers(task.giverId());
        TaskboundSavedData.get(player.server).changed();
        grantRewards(player, rewardPlan);
        dev.taskbound.porter.PorterTasks.claimed(player, task);
        TaskboundNetworking.syncPlayer(player);
        player.sendSystemMessage(Component.literal("Rewards claimed. " + task.reputationReward()
                + " " + shortId(task.giverId()) + " reputation gained."));
        return task.withStatus(TaskStatus.CLAIMED);
    }

    public String historyReport(ServerPlayer player, String giverId, UUID boardId) {
        TaskboundDefinitions definitions = definitions();
        var giver = definitions.giver(giverId);
        PlayerCoreState state = state(player);
        PlayerReputation reputation = state.reputation().forGiver(giverId);
        int level = reputation.level(giver.reputationThresholds());
        SelectionHistory history = state.historyFor(giverId);
        history.activateUnlockedCategories(definitions.catalog().categoriesForGiver(giverId), level);
        Set<String> capabilities = capabilitiesFor(player, giverId, boardId, level);
        TaskboundSavedData savedData = TaskboundSavedData.get(player.server);
        TaskboundSavedData.BoardRecord origin = savedData.board(boardId).orElse(new TaskboundSavedData.BoardRecord(
                boardId,
                player.serverLevel().dimension().location().toString(),
                player.blockPosition().immutable()
        ));
        var lines = new ArrayList<String>();
        lines.add(shortId(giverId) + ": " + reputation.points() + " points · level " + level);
        lines.add("Capabilities: " + String.join(", ", capabilities.stream().sorted().toList()));
        for (TaskCategoryDefinition category : definitions.catalog().categoriesForGiver(giverId)) {
            if (!category.unlockedAt(level)) {
                lines.add(category.id() + " · LOCKED (level " + category.minimumReputationLevel() + ")");
                continue;
            }
            boolean templateEligible = category.templateIds().stream()
                    .map(definitions.templates()::get)
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(template -> template.eligibleVariants(level, new CapabilitySet(capabilities)).stream()
                            .anyMatch(variant -> variantRuntimeEligible(savedData, origin, variant)));
            var categoryHistory = history.category(category.id());
            if (!capabilities.containsAll(category.requiredCapabilities()) || !templateEligible) {
                lines.add(category.id() + " · INELIGIBLE (capability/template requirements)");
                continue;
            }
            double urgency = (double) categoryHistory.eligibleMisses() / category.guaranteeAfter();
            lines.add(category.id() + " · misses=" + categoryHistory.eligibleMisses()
                    + " · base=" + String.format(java.util.Locale.ROOT, "%.2f", category.baseWeight())
                    + " · effective=" + String.format(
                            java.util.Locale.ROOT, "%.2f", category.effectiveWeight(categoryHistory))
                    + " · new-unlock-draws=" + categoryHistory.newlyUnlockedBoostDrawsRemaining()
                    + " · urgency=" + String.format(java.util.Locale.ROOT, "%.2f", urgency)
                    + (categoryHistory.eligibleMisses() >= category.guaranteeAfter() ? " · GUARANTEED" : ""));
        }
        TaskboundSavedData.get(player.server).changed();
        return String.join("\n", lines);
    }

    public static int requiredCount(GeneratedObjective objective) {
        Object value = objective.parameters().getOrDefault("required_count", 1);
        return value instanceof Number number ? Math.max(1, number.intValue()) : 1;
    }

    public static String stringParameter(GeneratedObjective objective, String key) {
        Object value = objective.parameters().get(key);
        return value instanceof String string ? string : null;
    }

    public void registerBoard(ServerLevel level, TaskBoardBlockEntity board) {
        TaskboundSavedData.get(level.getServer()).registerBoard(
                board.boardId(), level, board.getBlockPos());
    }

    private Set<String> capabilitiesFor(ServerPlayer player, String giverId, UUID boardId, int reputationLevel) {
        var result = new LinkedHashSet<>(TaskboundProviders.capabilities());
        result.addAll(dev.taskbound.event.VanillaEventRules.supportedCapabilities());
        player.server.levelKeys().forEach(levelKey -> result.add(
                "dimension:" + levelKey.location().getNamespace() + "/" + levelKey.location().getPath()));
        if (giverId.equals("taskbound:porter")) {
            TaskboundSavedData savedData = TaskboundSavedData.get(player.server);
            TaskboundSavedData.BoardRecord origin = savedData.board(boardId).orElse(new TaskboundSavedData.BoardRecord(
                    boardId,
                    player.serverLevel().dimension().location().toString(),
                    player.blockPosition().immutable()
            ));
            if (!hasBoardDeliveryDestination(savedData, origin, giverId, reputationLevel)) {
                result.remove("minecraft:deliver_package");
            }
        }
        var providerContext = new dev.taskbound.api.TaskGenerationContext(
                boardId, giverId, reputationLevel, result);
        boolean hasCastSpell = false;
        for (var provider : TaskboundProviders.magicProviders()) {
            for (var candidate : safeMagicCandidates(provider, player, providerContext)) {
                if (actionMatches(candidate.actionType(), "cast_spell") && candidate.spellId().isPresent()) {
                    result.add("magic:spell_casting");
                    result.add(magicActionCapability("cast_spell"));
                    candidate.targetEntityIds().forEach(target -> result.add(
                            magicTargetCapability("cast_spell", target.toString())));
                    hasCastSpell = true;
                }
            }
        }
        if (result.contains("magic:spell_casting") && !hasCastSpell) {
            result.remove("magic:spell_casting");
        }

        var candidateProperties = java.util.EnumSet.noneOf(dev.taskbound.api.fishing.FishProperty.class);
        for (var provider : TaskboundProviders.fishingProviders()) {
            for (var candidate : safeFishCandidates(provider, player, providerContext)) {
                for (var property : candidate.properties()) {
                    candidateProperties.add(property);
                    result.add(fishCandidateCapability(candidate.speciesId().toString(), property));
                }
            }
        }
        for (var property : List.of(
                dev.taskbound.api.fishing.FishProperty.WEIGHT,
                dev.taskbound.api.fishing.FishProperty.LENGTH,
                dev.taskbound.api.fishing.FishProperty.RARITY)) {
            String capability = "fishing:" + property.name().toLowerCase(java.util.Locale.ROOT);
            if (result.contains(capability) && !candidateProperties.contains(property)) {
                result.remove(capability);
            }
        }
        return Set.copyOf(result);
    }

    private static boolean catalogProfileEligible(String templateId, Map<String, ?> conditions) {
        var mods = new java.util.HashSet<String>();
        for (String id : List.of("starcatcher", "farmersdelight")) {
            if (net.neoforged.fml.ModList.get().isLoaded(id)) mods.add(id);
        }
        return dev.taskbound.core.CatalogProfiles.eligible(templateId, conditions, mods);
    }

    private boolean categoryProfileEligible(TaskCategoryDefinition category) {
        return category.templateIds().stream().map(definitions().templates()::get).filter(java.util.Objects::nonNull)
                .anyMatch(template -> template.variants().stream().anyMatch(variant -> catalogProfileEligible(template.id(), variant.conditions())));
    }

    private boolean hasBoardDeliveryDestination(
            TaskboundSavedData savedData,
            TaskboundSavedData.BoardRecord origin,
            String giverId,
            int reputationLevel
    ) {
        TaskboundDefinitions definitions = definitions();
        Set<String> configuredCategories = Set.copyOf(definitions.giver(giverId).categoryIds());
        return definitions.catalog().categoriesForGiver(giverId).stream()
                .filter(category -> configuredCategories.isEmpty() || configuredCategories.contains(category.id()))
                .filter(category -> category.unlockedAt(reputationLevel))
                .flatMap(category -> category.templateIds().stream())
                .map(definitions.templates()::get)
                .filter(java.util.Objects::nonNull)
                .filter(template -> template.unlockedAt(reputationLevel))
                .flatMap(template -> template.variants().stream())
                .filter(variant -> variant.objectives().stream().anyMatch(objective ->
                        objective.type().equals("deliver_package")
                                && "taskbound:task_board".equals(
                                        objective.parameters().get("destination_type"))))
                .anyMatch(variant -> variantRuntimeEligible(savedData, origin, variant));
    }

    private boolean porterEligible(ServerPlayer player, TaskboundSavedData.BoardRecord origin, TaskVariantDefinition variant) {
        return variant.objectives().stream().allMatch(objective -> !objective.type().equals("porter_route")
                || dev.taskbound.porter.PorterTasks.eligible(player, origin, objective.parameters()));
    }

    private boolean variantRuntimeEligible(
            TaskboundSavedData savedData,
            TaskboundSavedData.BoardRecord origin,
            TaskVariantDefinition variant
    ) {
        return variant.objectives().stream().allMatch(objective -> {
            if (!objective.type().equals("deliver_package")
                    || !"taskbound:task_board".equals(objective.parameters().get("destination_type"))) {
                return true;
            }
            double minimum = decimal(objective.parameters().get("minimum_distance"), 0.0D);
            double maximum = decimal(objective.parameters().get("maximum_distance"), Double.POSITIVE_INFINITY);
            return minimum >= 0.0D && maximum >= minimum && savedData.destinationFor(
                    origin.boardId(), origin.dimension(), origin.position(), minimum, maximum).isPresent();
        });
    }

    private GeneratedTaskDraft customizeDraft(
            ServerPlayer player,
            TaskboundSavedData savedData,
            TaskboundSavedData.BoardRecord originBoard,
            int reputationLevel,
            Set<String> capabilities,
            GeneratedTaskDraft draft
    ) {
        var objectives = new ArrayList<GeneratedObjective>();
        var difficulty = new ArrayList<>(draft.difficultyComponents());
        var conditions = new LinkedHashMap<String, Object>(draft.conditions());
        Long expiresAt = draft.expiresAt();
        var providerContext = new dev.taskbound.api.TaskGenerationContext(
                originBoard.boardId(), draft.giverId(), reputationLevel, capabilities);

        for (GeneratedObjective objective : draft.objectives()) {
            var parameters = new LinkedHashMap<String, Object>(objective.parameters());
            if (objective.type().equals("deliver_package")) {
                double minimumDistance = decimal(parameters.get("minimum_distance"), 0.0D);
                double maximumDistance = decimal(parameters.get("maximum_distance"), Double.POSITIVE_INFINITY);
                TaskboundSavedData.BoardRecord destination = savedData.destinationFor(
                        originBoard.boardId(),
                        originBoard.dimension(),
                        originBoard.position(),
                        minimumDistance,
                        maximumDistance
                ).orElseThrow(() -> new IllegalStateException(
                        "No eligible destination board is available between "
                                + Math.round(minimumDistance) + " and " + Math.round(maximumDistance) + " blocks"));
                double distance = Math.sqrt(destination.position().distSqr(originBoard.position()));
                UUID taskId = UUID.fromString(draft.taskId());
                parameters.put("package_uuid", packageId(taskId).toString());
                parameters.put("destination_board_uuid", destination.boardId().toString());
                parameters.put("destination_dimension", destination.dimension());
                parameters.put("destination_x", destination.position().getX());
                parameters.put("destination_y", destination.position().getY());
                parameters.put("destination_z", destination.position().getZ());
                parameters.put("distance_blocks", distance);

                int expirationTicks = number(parameters.get("expiration_ticks"), 0);
                if (expirationTicks > 0) {
                    expiresAt = Math.addExact(draft.acceptedAt(), expirationTicks);
                }
                Object checkpoints = parameters.get("ordered_checkpoints");
                if (checkpoints instanceof List<?> list && !list.isEmpty()) {
                    parameters.put("checkpoint_required", true);
                    parameters.put("checkpoint_dimension", originBoard.dimension());
                    parameters.put("checkpoint_x", (originBoard.position().getX() + destination.position().getX()) / 2);
                    parameters.put("checkpoint_y", (originBoard.position().getY() + destination.position().getY()) / 2);
                    parameters.put("checkpoint_z", (originBoard.position().getZ() + destination.position().getZ()) / 2);
                    parameters.put("checkpoint_radius", 24);
                    parameters.put("checkpoint_vertical_tolerance", 64);
                }

                difficulty.removeIf(component ->
                        component.description().equals("difficulty.taskbound.travel_250_to_500"));
                int distancePoints = distance > 1_000.0D ? 12 : distance > 500.0D ? 8 : distance >= 250.0D ? 4 : 0;
                if (distancePoints > 0) {
                    difficulty.add(new DifficultyComponent("difficulty.taskbound.travel_distance", distancePoints));
                }
                conditions.put("generated_destination_board", destination.boardId().toString());
            }

            if ("provider_candidate".equals(parameters.get("spell_id_source"))) {
                var selected = TaskboundProviders.magicProviders().stream()
                        .sorted(Comparator.comparing(provider -> provider.id().toString()))
                        .flatMap(provider -> safeMagicCandidates(provider, player, providerContext).stream()
                                .map(candidate -> Map.entry(provider, candidate)))
                        .filter(entry -> magicCandidateMatches(entry.getValue(), parameters))
                        .sorted(Comparator.comparing(entry -> entry.getValue().spellId()
                                .map(ResourceLocation::toString).orElse(entry.getValue().actionType().toString())))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No compatibility provider supplied an eligible spell"));
                selected.getValue().spellId().ifPresent(id -> parameters.put("spell_id", id.toString()));
                parameters.put("compatibility_provider_id", selected.getKey().id().toString());
                difficulty.add(new DifficultyComponent(
                        "difficulty.taskbound.provider_modifier", selected.getValue().difficultyModifier()));
            }

            if ("provider_candidate".equals(parameters.get("compatibility_provider_id_source"))
                    && objective.type().equals("catch_fish")) {
                String species = parameters.get("species_id") instanceof String value ? value : null;
                var selected = TaskboundProviders.fishingProviders().stream()
                        .sorted(Comparator.comparing(provider -> provider.id().toString()))
                        .flatMap(provider -> safeFishCandidates(provider, player, providerContext).stream()
                                .filter(candidate -> fishCandidateMatches(candidate, species, parameters))
                                .map(candidate -> Map.entry(provider, candidate)))
                        .sorted(Comparator
                                .comparing((Map.Entry<dev.taskbound.api.fishing.FishingTaskProvider,
                                        dev.taskbound.api.fishing.FishCandidate> entry) ->
                                        entry.getKey().id().toString())
                                .thenComparing(entry -> entry.getValue().speciesId().toString())
                                .thenComparingInt(entry -> entry.getValue().difficultyModifier()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No compatibility provider supplied the required fish"));
                parameters.put("compatibility_provider_id", selected.getKey().id().toString());
                difficulty.add(new DifficultyComponent(
                        "difficulty.taskbound.provider_modifier", selected.getValue().difficultyModifier()));
            }
            objectives.add(new GeneratedObjective(objective.key(), objective.type(), parameters));
        }
        return dev.taskbound.porter.PorterTasks.customize(player, originBoard, new GeneratedTaskDraft(
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
                objectives,
                conditions,
                draft.requiredReputationLevel(),
                draft.baseDifficulty(),
                difficulty,
                draft.acceptedAt(),
                expiresAt
        ));
    }

    private static List<dev.taskbound.api.magic.MagicActionCandidate> safeMagicCandidates(
            dev.taskbound.api.magic.MagicTaskProvider provider,
            ServerPlayer player,
            dev.taskbound.api.TaskGenerationContext context
    ) {
        try {
            return List.copyOf(provider.getEligibleActions(player, context));
        } catch (RuntimeException exception) {
            TaskboundMod.LOGGER.warn("Magic provider {} failed candidate generation", provider.id(), exception);
            return List.of();
        }
    }

    private static List<dev.taskbound.api.fishing.FishCandidate> safeFishCandidates(
            dev.taskbound.api.fishing.FishingTaskProvider provider,
            ServerPlayer player,
            dev.taskbound.api.TaskGenerationContext context
    ) {
        try {
            return List.copyOf(provider.getEligibleFish(player, context));
        } catch (RuntimeException exception) {
            TaskboundMod.LOGGER.warn("Fishing provider {} failed candidate generation", provider.id(), exception);
            return List.of();
        }
    }

    private static boolean magicCandidateMatches(
            dev.taskbound.api.magic.MagicActionCandidate candidate,
            Map<String, Object> parameters
    ) {
        String action = parameters.get("action") instanceof String value ? value : null;
        if (action != null && !actionMatches(candidate.actionType(), action)) {
            return false;
        }
        String requiredSpell = parameters.get("spell_id") instanceof String value ? value : null;
        if (requiredSpell != null && candidate.spellId().map(ResourceLocation::toString)
                .filter(requiredSpell::equals).isEmpty()) {
            return false;
        }
        if ("provider_candidate".equals(parameters.get("spell_id_source")) && candidate.spellId().isEmpty()) {
            return false;
        }
        String requiredTarget = parameters.get("target_entity") instanceof String value ? value : null;
        return requiredTarget == null || candidate.targetEntityIds().stream()
                .map(ResourceLocation::toString)
                .anyMatch(requiredTarget::equals);
    }

    private static boolean fishCandidateMatches(
            dev.taskbound.api.fishing.FishCandidate candidate,
            String species,
            Map<String, Object> parameters
    ) {
        if (species != null && !candidate.speciesId().toString().equals(species)) {
            return false;
        }
        if ((parameters.containsKey("minimum_weight") || parameters.containsKey("maximum_weight"))
                && !candidate.properties().contains(dev.taskbound.api.fishing.FishProperty.WEIGHT)) {
            return false;
        }
        if ((parameters.containsKey("minimum_length") || parameters.containsKey("maximum_length"))
                && !candidate.properties().contains(dev.taskbound.api.fishing.FishProperty.LENGTH)) {
            return false;
        }
        return !parameters.containsKey("exact_rarity")
                || candidate.properties().contains(dev.taskbound.api.fishing.FishProperty.RARITY);
    }

    private static boolean actionMatches(ResourceLocation candidate, String required) {
        return required.equals(candidate.toString()) || required.equals(candidate.getPath());
    }

    private static String magicActionCapability(String action) {
        return "magic:candidate/" + action;
    }

    private static String magicTargetCapability(String action, String target) {
        return magicActionCapability(action) + "/target/" + target;
    }

    private static String fishCandidateCapability(
            String species,
            dev.taskbound.api.fishing.FishProperty property
    ) {
        return "fishing:candidate/" + species + "/"
                + property.name().toLowerCase(java.util.Locale.ROOT);
    }

    private PlayerCoreState state(ServerPlayer player) {
        return TaskboundSavedData.get(player.server).player(player.getUUID());
    }

    private TaskboundDefinitions definitions() {
        return TaskboundDefinitionManager.current();
    }

    private static ReputationThresholds reputationThresholds(TaskboundDefinitions definitions, String giverId) {
        var giver = definitions.givers().get(giverId);
        // Accepted snapshots remain payable; points become visible again if this giver ID is restored.
        return giver == null ? RETIRED_GIVER_THRESHOLDS : giver.reputationThresholds();
    }

    private static long offerSeed(
            ServerPlayer player,
            UUID boardId,
            String giverId,
            long generationCount,
            int index
    ) {
        long value = player.serverLevel().getSeed();
        value ^= player.getUUID().getMostSignificantBits();
        value = Long.rotateLeft(value, 17) ^ player.getUUID().getLeastSignificantBits();
        value = Long.rotateLeft(value, 17) ^ boardId.getMostSignificantBits() ^ boardId.getLeastSignificantBits();
        value = Long.rotateLeft(value, 17) ^ giverId.hashCode();
        value ^= (generationCount + index + 1L) * 0x9E3779B97F4A7C15L;
        return value;
    }

    private static UUID packageId(UUID taskId) {
        return UUID.nameUUIDFromBytes((taskId + ":sealed_package").getBytes(StandardCharsets.UTF_8));
    }

    private static String shortId(String id) {
        int separator = id.indexOf(':');
        return separator >= 0 ? id.substring(separator + 1) : id;
    }

    private static String translationKey(String prefix, String id) {
        int separator = id.indexOf(':');
        String namespace = separator >= 0 ? id.substring(0, separator) : TaskboundMod.MOD_ID;
        String path = separator >= 0 ? id.substring(separator + 1) : id;
        return prefix + "." + namespace + "." + path.replace('/', '.');
    }

    private static boolean sequenceAllows(ActiveTask active, int objectiveIndex) {
        return dev.taskbound.core.ObjectiveSequence.allows(active, objectiveIndex);
    }

    private void failRestrictedDeliveries(ServerPlayer player, String restriction, String message) {
        for (ActiveTask active : activeTasks(player)) {
            boolean restricted = active.task().status() == TaskStatus.ACTIVE
                    && active.task().objectives().stream().anyMatch(objective ->
                            objective.type().equals("deliver_package")
                                    && Boolean.FALSE.equals(objective.parameters().get(restriction)));
            if (restricted) {
                failTask(player, active, message);
            }
        }
    }

    private void failTask(ServerPlayer player, ActiveTask active, String message) {
        failTask(player, active, Component.literal(message));
    }

    private void failTask(ServerPlayer player, ActiveTask active, Component message) {
        PlayerCoreState state = state(player);
        if (state.removeActiveTask(active.task().taskId()) == null) {
            return;
        }
        state.reputation().forGiver(active.task().giverId()).recordFailure();
        removePackage(player, UUID.fromString(active.task().taskId()));
        TaskboundSavedData.get(player.server).changed();
        TaskboundNetworking.syncPlayer(player);
        player.sendSystemMessage(message);
    }

    private static boolean isExpired(ActiveTask active, long gameTime) {
        return dev.taskbound.core.TaskTimer.expired(active, gameTime);
    }

    private static String checkpointKey(GeneratedObjective objective) {
        return "checkpoint_visited:" + objective.key();
    }

    private static String damageKey(GeneratedObjective objective) {
        return "player_damage:" + objective.key();
    }

    private static void completeIfReady(PlayerCoreState state, ActiveTask active, ServerPlayer player) {
        boolean complete = active.task().objectives().stream().allMatch(objective ->
                active.progress().progressFor(objective.key()) >= requiredCount(objective));
        if (!complete) {
            return;
        }
        ActiveTask completed = new ActiveTask(active.task().withStatus(TaskStatus.COMPLETED), active.progress());
        state.putActiveTask(completed);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("taskbound.task.complete"));
        }
    }

    private static RewardGrantPlan planRewards(GeneratedTask task) {
        var items = new ArrayList<ItemGrant>();
        var reputationPoints = new LinkedHashMap<String, Integer>();
        int experiencePoints = 0;
        for (GeneratedReward reward : task.rewards().rewards()) {
            int payloadCount = number(reward.payload().get("count"), 1);
            if (payloadCount < 1) {
                throw new IllegalStateException("Reward " + reward.entryId() + " has a non-positive payload count");
            }
            switch (reward.type()) {
                case ITEM -> {
                    Object itemValue = reward.payload().get("item");
                    if (!(itemValue instanceof String itemId)) {
                        throw new IllegalStateException("Reward " + reward.entryId() + " has no item payload");
                    }
                    ResourceLocation id = ResourceLocation.tryParse(itemId);
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                        throw new IllegalStateException(
                                "Reward " + reward.entryId() + " references unknown item " + itemId);
                    }
                    int total = Math.multiplyExact(reward.count(), payloadCount);
                    items.add(new ItemGrant(BuiltInRegistries.ITEM.get(id), total));
                }
                case EXPERIENCE -> {
                    int points = number(
                            reward.payload().get("experience_points"),
                            number(reward.payload().get("amount"), 1));
                    if (points < 0) {
                        throw new IllegalStateException("Reward " + reward.entryId() + " has negative experience");
                    }
                    experiencePoints = Math.addExact(
                            experiencePoints, Math.multiplyExact(reward.count(), points));
                }
                case REPUTATION -> {
                    String giverId = reward.payload().get("giver") instanceof String value
                            ? value
                            : task.giverId();
                    int points = Math.multiplyExact(reward.count(), number(reward.payload().get("points"), 1));
                    if (points < 0) {
                        throw new IllegalStateException("Reward " + reward.entryId() + " has negative reputation");
                    }
                    reputationPoints.merge(giverId, points, Math::addExact);
                }
                case LOOT_TABLE, CALLBACK -> TaskboundMod.LOGGER.warn(
                        "Reward type {} requires a registered runtime handler; skipped {}", reward.type(), reward.entryId());
            }
        }
        return new RewardGrantPlan(items, experiencePoints, reputationPoints);
    }

    private static void validateReputationPlan(
            PlayerCoreState state,
            GeneratedTask task,
            RewardGrantPlan plan
    ) {
        var totals = new LinkedHashMap<>(plan.reputationPoints());
        totals.merge(task.giverId(), task.reputationReward(), Math::addExact);
        totals.forEach((giverId, points) -> {
            Math.addExact(state.reputation().forGiver(giverId).points(), points);
        });
        Math.addExact(
                state.reputation().forGiver(task.giverId()).completedTaskCount(), 1);
    }

    private static void grantRewards(ServerPlayer player, RewardGrantPlan plan) {
        for (ItemGrant grant : plan.items()) {
            int remaining = grant.count();
            while (remaining > 0) {
                int batch = Math.min(remaining, grant.item().getDefaultMaxStackSize());
                ItemStack stack = new ItemStack(grant.item(), batch);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                remaining -= batch;
            }
        }
        if (plan.experiencePoints() > 0) {
            player.giveExperiencePoints(plan.experiencePoints());
        }
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double decimal(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static void removePackage(ServerPlayer player, UUID taskId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (TaskPackageItem.taskId(stack).filter(taskId::equals).isPresent()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private record ItemGrant(Item item, int count) {
    }

    private record RewardGrantPlan(
            List<ItemGrant> items,
            int experiencePoints,
            Map<String, Integer> reputationPoints
    ) {
        private RewardGrantPlan {
            items = List.copyOf(items);
            reputationPoints = Map.copyOf(reputationPoints);
        }
    }
}
