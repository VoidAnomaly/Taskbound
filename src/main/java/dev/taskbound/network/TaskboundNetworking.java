package dev.taskbound.network;

import dev.taskbound.TaskboundMod;
import dev.taskbound.block.TaskBoardBlockEntity;
import dev.taskbound.registry.TaskboundContent;
import dev.taskbound.runtime.TaskboundRuntime;
import dev.taskbound.ui.BoardAction;
import dev.taskbound.ui.BoardSnapshot;
import dev.taskbound.ui.JournalSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Play payloads for the presentation-only task-board screen. */
public final class TaskboundNetworking {
    private static final int MAX_SNAPSHOT_JSON = 262_144;
    private static final int MAX_ACTION_JSON = 4_096;
    private static final int MAX_REJECTION_TEXT = 2_048;
    private static final java.util.Map<java.util.UUID, BoardWatch> BOARD_WATCHES = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> JOURNAL_WATCHES = new java.util.HashMap<>();

    private TaskboundNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("7");
        registrar.playToServer(JournalRequestPayload.TYPE, JournalRequestPayload.STREAM_CODEC, TaskboundNetworking::handleJournalRequest);
        registrar.playToClient(JournalSnapshotPayload.TYPE, JournalSnapshotPayload.STREAM_CODEC, (payload, context) -> ClientHandler.handle(payload));
        registrar.playToClient(
                BoardSnapshotPayload.TYPE,
                BoardSnapshotPayload.STREAM_CODEC,
                TaskboundNetworking::handleSnapshot
        );
        registrar.playToServer(
                BoardActionPayload.TYPE,
                BoardActionPayload.STREAM_CODEC,
                TaskboundNetworking::handleAction
        );
        registrar.playToClient(
                BoardActionRejectedPayload.TYPE,
                BoardActionRejectedPayload.STREAM_CODEC,
                TaskboundNetworking::handleRejection
        );
    }

    public static void openBoard(ServerPlayer player, BoardSnapshot snapshot) {
        watchBoard(player, snapshot);
        PacketDistributor.sendToPlayer(player, new BoardSnapshotPayload(true, snapshot));
    }

    public static void sendAction(BoardAction action) {
        PacketDistributor.sendToServer(new BoardActionPayload(action));
    }

    public static void openJournal(ServerPlayer player, JournalSnapshot snapshot) {
        JOURNAL_WATCHES.put(player.getUUID(), player.server.getTickCount());
        PacketDistributor.sendToPlayer(player, new JournalSnapshotPayload(true, snapshot));
    }

    public static void refreshJournal() {
        PacketDistributor.sendToServer(new JournalRequestPayload(false));
    }

    public static void requestBoardUpdate(BoardSnapshot snapshot) {
        sendAction(BoardAction.forBoard(snapshot, BoardAction.Type.REFRESH_VIEW));
    }

    private static void handleJournalRequest(JournalRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            // Remote read requests cannot open a journal or substitute for owning the physical book.
            if (!payload.openScreen() && dev.taskbound.item.TaskJournalItem.hasJournal(player)) {
                JOURNAL_WATCHES.put(player.getUUID(), player.server.getTickCount());
                context.reply(new JournalSnapshotPayload(false, TaskboundRuntime.get().journalSnapshot(player)));
            }
        }
    }

    private static void handleSnapshot(BoardSnapshotPayload payload, IPayloadContext context) {
        ClientHandler.handle(payload);
    }

    private static void handleRejection(BoardActionRejectedPayload payload, IPayloadContext context) {
        ClientHandler.handle(payload);
    }

    private static void handleAction(BoardActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BoardAction action = payload.action();
        TaskBoardBlockEntity board;
        try {
            board = resolveBoard(player, action);
        } catch (RuntimeException exception) {
            context.reply(new BoardActionRejectedPayload(action.boardId(), message(exception)));
            return;
        }

        TaskboundRuntime runtime = TaskboundRuntime.get();
        String giverId = action.giverId().isBlank() ? board.giverId() : action.giverId();
        BoardSnapshot snapshot;
        try {
            if (action.type() == BoardAction.Type.CLAIM && runtime.isHandInTask(player, action.taskId())) {
                runtime.openHandIn(player, action.taskId(), board);
                return;
            }
            snapshot = switch (action.type()) {
                case ACCEPT -> {
                    runtime.accept(player, action.taskId(), board);
                    yield runtime.viewBoard(player, board, giverId).withNotice("Task accepted.");
                }
                case CLAIM -> {
                    runtime.claim(player, action.taskId(), board);
                    yield runtime.viewBoard(player, board, giverId).withNotice("Rewards claimed.");
                }
                case ABANDON -> {
                    runtime.abandon(player, action.taskId());
                    yield runtime.viewBoard(player, board, giverId).withNotice("Task abandoned.");
                }
                case CLAIM_LEVEL_REWARD -> {
                    runtime.claimLevelReward(player, giverId, action.rewardLevel());
                    yield runtime.viewBoard(player, board, giverId).withNotice("Level reward claimed.");
                }
                case REFRESH -> runtime.refreshBoardOffers(player, board, giverId);
                case REFRESH_VIEW -> runtime.viewBoard(player, board, giverId);
                case CYCLE_GIVER -> runtime.cycleBoardGiverAndOpen(player, board);
                case SELECT_GIVER -> runtime.selectBoardGiverAndOpen(player, board, giverId);
                case MARK_OFFERS_SEEN -> {
                    var saved = dev.taskbound.persistence.TaskboundSavedData.get(player.server);
                    if (saved.player(player.getUUID()).markOffersSeen(board.boardId().toString(), giverId, action.seenOfferIds())) saved.changed();
                    // A passive acknowledgement must not move the subscription back to an older guild.
                    BoardWatch watch = BOARD_WATCHES.get(player.getUUID());
                    String viewingGuild = watch != null && watch.action().boardId().equals(action.boardId()) ? watch.action().giverId() : giverId;
                    yield runtime.viewBoard(player, board, viewingGuild);
                }
            };
        } catch (RuntimeException exception) {
            context.reply(new BoardActionRejectedPayload(action.boardId(), message(exception)));
            return;
        }
        if (action.type() != BoardAction.Type.MARK_OFFERS_SEEN) watchBoard(player, snapshot);
        context.reply(new BoardSnapshotPayload(false, snapshot));
    }

    private static void watchBoard(ServerPlayer player, BoardSnapshot snapshot) {
        BOARD_WATCHES.put(player.getUUID(), new BoardWatch(BoardAction.forBoard(snapshot, BoardAction.Type.REFRESH_VIEW),
                player.serverLevel().dimension(), player.server.getTickCount()));
    }

    /** Only a recently polled, still-nearby physical board may receive pushed state. */
    public static java.util.Optional<TaskBoardBlockEntity> currentBoard(ServerPlayer player) {
        BoardWatch watch = BOARD_WATCHES.get(player.getUUID());
        if (watch == null) return java.util.Optional.empty();
        if (player.server.getTickCount() - watch.tick() > 100 || !player.serverLevel().dimension().equals(watch.dimension())) {
            BOARD_WATCHES.remove(player.getUUID());
            return java.util.Optional.empty();
        }
        try { return java.util.Optional.of(resolveBoard(player, watch.action())); }
        catch (RuntimeException invalid) { BOARD_WATCHES.remove(player.getUUID()); return java.util.Optional.empty(); }
    }

    /** Push mutations immediately; false/open updates never reopen a closed screen. */
    public static void syncPlayer(ServerPlayer player) {
        currentBoard(player).ifPresent(board -> {
            BoardWatch watch = BOARD_WATCHES.get(player.getUUID());
            try {
                BoardSnapshot snapshot = TaskboundRuntime.get().viewBoard(player, board, watch.action().giverId());
                PacketDistributor.sendToPlayer(player, new BoardSnapshotPayload(false, snapshot));
            } catch (RuntimeException invalid) { BOARD_WATCHES.remove(player.getUUID()); }
        });
        Integer tick = JOURNAL_WATCHES.get(player.getUUID());
        if (tick != null) {
            if (player.server.getTickCount() - tick > 100 || !dev.taskbound.item.TaskJournalItem.hasJournal(player)) JOURNAL_WATCHES.remove(player.getUUID());
            else PacketDistributor.sendToPlayer(player, new JournalSnapshotPayload(false, TaskboundRuntime.get().journalSnapshot(player)));
        }
    }

    public static void playerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        BOARD_WATCHES.remove(event.getEntity().getUUID());
        JOURNAL_WATCHES.remove(event.getEntity().getUUID());
    }

    public static void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        BOARD_WATCHES.clear();
        JOURNAL_WATCHES.clear();
    }

    private record BoardWatch(BoardAction action, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int tick) { }

    private static TaskBoardBlockEntity resolveBoard(ServerPlayer player, BoardAction action) {
        if (!player.isAlive() || player.isSpectator()) {
            throw new IllegalStateException("You cannot use a task board right now.");
        }
        BlockPos position = new BlockPos(action.boardX(), action.boardY(), action.boardZ());
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(position)) {
            throw new IllegalStateException("That task board is no longer loaded.");
        }
        double distanceSquared = player.distanceToSqr(
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D
        );
        if (distanceSquared > 64.0D) {
            throw new IllegalStateException("You are too far away from that task board.");
        }
        if (!level.getBlockState(position).is(TaskboundContent.TASK_BOARD.get())
                || !(level.getBlockEntity(position) instanceof TaskBoardBlockEntity board)) {
            throw new IllegalStateException("That task board no longer exists.");
        }
        if (!board.boardId().toString().equals(action.boardId())) {
            throw new IllegalStateException("That task board has changed.");
        }
        return board;
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null ? "Taskbound rejected that board action." : exception.getMessage();
    }

    public record BoardSnapshotPayload(boolean openScreen, BoardSnapshot snapshot)
            implements CustomPacketPayload {
        public static final Type<BoardSnapshotPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(TaskboundMod.MOD_ID, "board_snapshot"));
        public static final StreamCodec<ByteBuf, BoardSnapshotPayload> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(MAX_SNAPSHOT_JSON).map(
                        BoardSnapshotPayload::decode,
                        BoardSnapshotPayload::encode
                );

        private static BoardSnapshotPayload decode(String json) {
            int separator = json.indexOf('\n');
            if (separator < 0) {
                throw new IllegalArgumentException("Invalid Taskbound board snapshot payload");
            }
            return new BoardSnapshotPayload(
                    Boolean.parseBoolean(json.substring(0, separator)),
                    BoardSnapshot.fromJson(json.substring(separator + 1))
            );
        }

        private static String encode(BoardSnapshotPayload payload) {
            return payload.openScreen + "\n" + payload.snapshot.toJson();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BoardActionPayload(BoardAction action) implements CustomPacketPayload {
        public static final Type<BoardActionPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(TaskboundMod.MOD_ID, "board_action"));
        public static final StreamCodec<ByteBuf, BoardActionPayload> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(MAX_ACTION_JSON).map(
                        json -> new BoardActionPayload(BoardAction.fromJson(json)),
                        payload -> payload.action.toJson()
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BoardActionRejectedPayload(String boardId, String message, boolean closeScreen)
            implements CustomPacketPayload {
        public BoardActionRejectedPayload(String boardId, String message) {
            this(boardId, message, false);
        }
        public static final Type<BoardActionRejectedPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(TaskboundMod.MOD_ID, "board_action_rejected"));
        public static final StreamCodec<ByteBuf, BoardActionRejectedPayload> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(MAX_REJECTION_TEXT).map(
                        BoardActionRejectedPayload::decode,
                        BoardActionRejectedPayload::encode
                );

        public BoardActionRejectedPayload {
            java.util.UUID.fromString(boardId);
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("A rejected Taskbound board action requires a message");
            }
        }

        private static BoardActionRejectedPayload decode(String encoded) {
            int separator = encoded.indexOf('\n');
            int messageStart = encoded.indexOf('\n', separator + 1);
            if (separator < 0 || messageStart < 0) {
                throw new IllegalArgumentException("Invalid Taskbound board rejection payload");
            }
            return new BoardActionRejectedPayload(
                    encoded.substring(0, separator),
                    encoded.substring(messageStart + 1),
                    Boolean.parseBoolean(encoded.substring(separator + 1, messageStart))
            );
        }

        private static String encode(BoardActionRejectedPayload payload) {
            return payload.boardId + "\n" + payload.closeScreen + "\n" + payload.message;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JournalRequestPayload(boolean openScreen) implements CustomPacketPayload {
        public static final Type<JournalRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TaskboundMod.MOD_ID, "journal_request"));
        public static final StreamCodec<ByteBuf, JournalRequestPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(JournalRequestPayload::new, JournalRequestPayload::openScreen);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record JournalSnapshotPayload(boolean openScreen, JournalSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<JournalSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TaskboundMod.MOD_ID, "journal_snapshot"));
        public static final StreamCodec<ByteBuf, JournalSnapshotPayload> STREAM_CODEC = ByteBufCodecs.stringUtf8(MAX_SNAPSHOT_JSON).map(
                encoded -> {
                    int split = encoded.indexOf('\n');
                    if (split < 0) throw new IllegalArgumentException("Invalid journal payload");
                    return new JournalSnapshotPayload(Boolean.parseBoolean(encoded.substring(0, split)), JournalSnapshot.fromJson(encoded.substring(split + 1)));
                }, payload -> payload.openScreen() + "\n" + payload.snapshot().toJson());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Kept behind a nested class so dedicated servers never resolve client-only
     * Minecraft or Screen classes while loading the common networking class.
     */
    private static final class ClientHandler {
        private ClientHandler() {
        }

        static void handle(BoardSnapshotPayload payload) {
            if (payload.openScreen()) {
                dev.taskbound.client.GuildBoardScreen.openOrUpdate(payload.snapshot());
            } else {
                dev.taskbound.client.GuildBoardScreen.updateIfOpen(payload.snapshot());
            }
        }

        static void handle(JournalSnapshotPayload payload) {
            if (payload.openScreen()) dev.taskbound.client.TaskJournalScreen.openOrUpdate(payload.snapshot());
            else dev.taskbound.client.TaskJournalScreen.updateIfOpen(payload.snapshot());
        }

        static void handle(BoardActionRejectedPayload payload) {
            dev.taskbound.client.GuildBoardScreen.rejectIfOpen(payload.boardId(), payload.message(), payload.closeScreen());
        }
    }
}
