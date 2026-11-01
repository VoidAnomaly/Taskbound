package dev.taskbound.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.taskbound.block.TaskBoardBlockEntity;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.core.GeneratedTask;
import dev.taskbound.event.TaskHandIns;
import dev.taskbound.event.TaskItemIdentity;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.registry.TaskboundContent;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Temporary native inventory: only stacks deliberately deposited here can be submitted. */
public final class TaskHandInMenu extends AbstractContainerMenu {
    public static final int WIDTH = 244;
    public static final int REQUIREMENTS_Y = 32;
    public static final int REQUIREMENT_ROW_HEIGHT = 11;
    public static final int OFFERED_Y = 45;
    public static final int INVENTORY_X = (WIDTH - 162) / 2;
    public static final int INVENTORY_Y = 91;
    public static final int HOTBAR_Y = 149;
    public static final int IMAGE_HEIGHT = 173;
    public static final int SLOT_PITCH = 18;
    public static final int MAX_OFFERED_SLOTS = 36;
    private static final int OFFERED_COLUMNS = 12;
    private static final int MAX_TASK_JSON = 65_536;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final GeneratedTask task;
    private final Player owner;
    @Nullable private final TaskBoardBlockEntity board;
    private final List<DepositRequirement> requirements;
    private final Container offered;
    private final DataSlot ready = DataSlot.standalone();
    private boolean closed;

    public TaskHandInMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, readOpeningData(data), null);
    }

    public TaskHandInMenu(int id, Inventory inventory, GeneratedTask task, @Nullable TaskBoardBlockEntity board) {
        super(TaskboundContent.TASK_HAND_IN_MENU.get(), id);
        this.task = task;
        this.owner = inventory.player;
        this.board = board;
        this.requirements = depositRequirements(task);
        this.offered = new SimpleContainer(requirements.size()) {
            @Override
            public void setChanged() {
                super.setChanged();
                TaskHandInMenu.this.slotsChanged(this);
            }
        };
        for (int index = 0; index < requirements.size(); index++) {
            final int offeredIndex = index;
            int row = index / OFFERED_COLUMNS;
            int columns = Math.min(OFFERED_COLUMNS, requirements.size() - row * OFFERED_COLUMNS);
            int x = (WIDTH - columns * SLOT_PITCH) / 2 + 1 + (index % OFFERED_COLUMNS) * SLOT_PITCH;
            addSlot(new Slot(offered, index, x, OFFERED_Y + requirementExtraHeight() + row * SLOT_PITCH) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    GeneratedObjective objective = requirementForSlot(offeredIndex);
                    if (!TaskHandIns.matches(owner, stack, objective.parameters())) return false;
                    Object stage = objective.parameters().get("provenance_stage");
                    return !(stage instanceof String value)
                            || TaskItemIdentity.qualifiedCount(stack, task.taskId(), value) > 0;
                }

                @Override
                public int getMaxStackSize() {
                    return Math.min(super.getMaxStackSize(), requiredCountForSlot(offeredIndex));
                }

                @Override
                public int getMaxStackSize(ItemStack stack) {
                    return Math.min(stack.getMaxStackSize(), getMaxStackSize());
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        INVENTORY_X + column * SLOT_PITCH, inventoryY() + row * SLOT_PITCH));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * SLOT_PITCH, hotbarY()));
        }
        addDataSlot(ready);
    }

    public static void writeOpeningData(RegistryFriendlyByteBuf data, GeneratedTask task) {
        data.writeUtf(GSON.toJson(task.toMap()), MAX_TASK_JSON);
    }

    private static GeneratedTask readOpeningData(RegistryFriendlyByteBuf data) {
        Map<String, Object> value = GSON.fromJson(data.readUtf(MAX_TASK_JSON), MAP_TYPE);
        if (value == null) throw new IllegalArgumentException("Missing hand-in task");
        return GeneratedTask.fromMap(value);
    }

    public GeneratedTask task() { return task; }
    public Container offered() { return offered; }
    @Nullable public TaskBoardBlockEntity board() { return board; }
    public boolean ready() { return ready.get() != 0; }
    public int requirementRows() { return Math.min(3, (int) task.objectives().stream().filter(objective -> objective.type().equals("hand_in_item")).count()); }
    private int requirementExtraHeight() { return (requirementRows() - 1) * REQUIREMENT_ROW_HEIGHT; }
    public int offeredRows() { return Math.max(1, (requirements.size() + OFFERED_COLUMNS - 1) / OFFERED_COLUMNS); }
    public int inventoryY() { return INVENTORY_Y + requirementExtraHeight() + (offeredRows() - 1) * SLOT_PITCH; }
    public int hotbarY() { return HOTBAR_Y + requirementExtraHeight() + (offeredRows() - 1) * SLOT_PITCH; }
    public int imageHeight() { return IMAGE_HEIGHT + requirementExtraHeight() + (offeredRows() - 1) * SLOT_PITCH; }
    public GeneratedObjective requirementForSlot(int slot) { return requirements.get(slot).objective(); }
    public int requiredCountForSlot(int slot) { return requirements.get(slot).count(); }

    @Override
    public void broadcastChanges() {
        if (!closed && owner instanceof ServerPlayer player) {
            ready.set(TaskboundRuntime.get().canConfirmHandIn(player, this) ? 1 : 0);
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button != 0 || !(player instanceof ServerPlayer serverPlayer)
                || player.containerMenu != this || !stillValid(player)) return false;
        var runtime = TaskboundRuntime.get();
        try {
            if (!runtime.canConfirmHandIn(serverPlayer, this)) {
                broadcastChanges();
                return false;
            }
            runtime.confirmHandIn(serverPlayer, this);
            return true;
        } catch (IllegalStateException | IllegalArgumentException | ArithmeticException exception) {
            serverPlayer.sendSystemMessage(Component.literal(exception.getMessage() == null
                    ? "This hand-in is no longer ready." : exception.getMessage()));
            broadcastChanges();
            return false;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (closed || !player.isAlive() || player.isSpectator() || !owner.getUUID().equals(player.getUUID())) return false;
        if (player.level().isClientSide) return true;
        return player instanceof ServerPlayer serverPlayer && board != null && !board.isRemoved()
                && board.getLevel() == player.level()
                && player.level().hasChunkAt(board.getBlockPos())
                && player.level().getBlockEntity(board.getBlockPos()) == board
                && player.distanceToSqr(board.getBlockPos().getX() + 0.5, board.getBlockPos().getY() + 0.5,
                        board.getBlockPos().getZ() + 0.5) <= 64.0
                && TaskboundSavedData.get(serverPlayer.server).player(player.getUUID()).activeTask(task.taskId()) != null;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (player.containerMenu == this && stillValid(player)) super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (player.containerMenu != this || !stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack current = slot.getItem();
        ItemStack before = current.copy();
        int deposits = offered.getContainerSize();
        if (index < deposits) {
            if (!moveItemStackTo(current, deposits, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(current, 0, deposits, false)) {
            // A nonmatching chosen stack keeps the native main-inventory/hotbar transfer behavior.
            if (index < deposits + 27) {
                if (!moveItemStackTo(current, deposits + 27, slots.size(), false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(current, deposits, deposits + 27, false)) return ItemStack.EMPTY;
        }
        if (current.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (current.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, current);
        return before;
    }

    @Override
    public void removed(Player player) {
        if (closed) return;
        closed = true;
        super.removed(player);
        if (player instanceof ServerPlayer) clearContainer(player, offered);
    }

    private static List<DepositRequirement> depositRequirements(GeneratedTask task) {
        var result = new ArrayList<DepositRequirement>();
        for (GeneratedObjective objective : task.objectives()) {
            if (!objective.type().equals("hand_in_item")) continue;
            int remaining = ((Number) objective.parameters().getOrDefault("required_count", 1)).intValue();
            ResourceLocation id = ResourceLocation.parse(objective.parameters().get("item").toString());
            int maximum = Math.max(1, BuiltInRegistries.ITEM.get(id).getDefaultMaxStackSize());
            while (remaining > 0) {
                if (result.size() == MAX_OFFERED_SLOTS) {
                    throw new IllegalArgumentException("This task requires more than 36 hand-in stacks; reduce its hand-in requirements.");
                }
                int count = Math.min(maximum, remaining);
                result.add(new DepositRequirement(objective, count));
                remaining -= count;
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("This task has no hand-in requirements");
        return List.copyOf(result);
    }

    private record DepositRequirement(GeneratedObjective objective, int count) { }
}
