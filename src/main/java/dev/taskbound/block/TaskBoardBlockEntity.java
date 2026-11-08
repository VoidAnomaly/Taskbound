package dev.taskbound.block;

import dev.taskbound.registry.TaskboundContent;
import dev.taskbound.persistence.TaskboundSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TaskBoardBlockEntity extends BlockEntity {
    private static List<String> givers() {
        var definitions = dev.taskbound.data.TaskboundDefinitionManager.current();
        return definitions.enabledGivers().stream().map(dev.taskbound.core.TaskGiverDefinition::id).toList();
    }

    private UUID boardId = UUID.randomUUID();
    private String giverId = "taskbound:hunter";

    public TaskBoardBlockEntity(BlockPos pos, BlockState state) {
        super(TaskboundContent.TASK_BOARD_ENTITY.get(), pos, state);
    }

    public UUID boardId() {
        return boardId;
    }

    public String giverId() {
        return giverId;
    }

    public String cycleGiver() {
        List<String> choices = givers();
        if (choices.isEmpty()) return giverId;
        int current = choices.indexOf(giverId);
        giverId = choices.get((current + 1) % choices.size());
        setChanged();
        return giverId;
    }

    public String selectGiver(String selectedGiverId) {
        if (!givers().contains(selectedGiverId)) {
            throw new IllegalArgumentException("Unknown Taskbound guild: " + selectedGiverId);
        }
        giverId = selectedGiverId;
        setChanged();
        return giverId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            TaskboundSavedData.get(serverLevel.getServer()).registerBoard(
                    boardId, serverLevel, worldPosition);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("board_id")) {
            boardId = tag.getUUID("board_id");
        }
        if (tag.contains("giver_id")) {
            String loadedGiver = tag.getString("giver_id");
            if (net.minecraft.resources.ResourceLocation.tryParse(loadedGiver) != null) {
                giverId = loadedGiver;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("board_id", boardId);
        tag.putString("giver_id", giverId);
    }
}
