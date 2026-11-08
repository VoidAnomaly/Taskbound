package dev.taskbound.registry;

import dev.taskbound.TaskboundMod;
import dev.taskbound.block.TaskBoardBlock;
import dev.taskbound.block.TaskBoardBlockEntity;
import dev.taskbound.item.TaskPackageItem;
import dev.taskbound.item.TaskJournalItem;
import dev.taskbound.menu.TaskHandInMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TaskboundContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TaskboundMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TaskboundMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, TaskboundMod.MOD_ID);
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TaskboundMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TaskboundMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TaskboundMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<TaskHandInMenu>> TASK_HAND_IN_MENU =
            MENUS.register("task_hand_in", () -> IMenuTypeExtension.create(TaskHandInMenu::new));

    public static final DeferredBlock<TaskBoardBlock> TASK_BOARD = BLOCKS.registerBlock(
            "task_board",
            TaskBoardBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
    );
    public static final DeferredItem<BlockItem> TASK_BOARD_ITEM =
            ITEMS.registerSimpleBlockItem(TASK_BOARD);
    public static final DeferredItem<TaskPackageItem> TASK_PACKAGE =
            ITEMS.registerItem("task_package", TaskPackageItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<TaskJournalItem> TASK_JOURNAL =
            ITEMS.registerItem("task_journal", TaskJournalItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<net.minecraft.world.item.CompassItem> DELIVERY_COMPASS =
            ITEMS.registerItem("delivery_compass", net.minecraft.world.item.CompassItem::new, new Item.Properties().stacksTo(1)
                    .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> PACKAGE_UUID =
            DATA_COMPONENTS.registerComponentType("package_uuid", builder ->
                    builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> PACKAGE_TASK_UUID =
            DATA_COMPONENTS.registerComponentType("package_task_uuid", builder ->
                    builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> PACKAGE_ORIGIN_BOARD_UUID =
            DATA_COMPONENTS.registerComponentType("package_origin_board_uuid", builder ->
                    builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> PACKAGE_DESTINATION =
            DATA_COMPONENTS.registerComponentType("package_destination", builder ->
                    builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TaskBoardBlockEntity>> TASK_BOARD_ENTITY =
            BLOCK_ENTITIES.register("task_board", () ->
                    BlockEntityType.Builder.of(TaskBoardBlockEntity::new, TASK_BOARD.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TASKBOUND_TAB = TABS.register(
            "taskbound",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.taskbound"))
                    .icon(() -> TASK_BOARD_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(TASK_BOARD_ITEM.get());
                        output.accept(TASK_PACKAGE.get());
                        output.accept(TASK_JOURNAL.get());
                    })
                    .build()
    );

    private TaskboundContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        DATA_COMPONENTS.register(modBus);
        TABS.register(modBus);
        MENUS.register(modBus);
    }
}
