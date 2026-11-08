package dev.taskbound.item;

import dev.taskbound.registry.TaskboundContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A sealed delivery item whose task and package identifiers cannot be replaced by an ordinary item. */
public final class TaskPackageItem extends Item {
    public TaskPackageItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        deliveryTarget(stack).ifPresent(target -> tooltip.add(Component.translatable("taskbound.package.destination",
                target.pos().getX(), target.pos().getZ()).withStyle(ChatFormatting.GRAY)));
    }

    public static ItemStack seal(Item item, UUID packageId, UUID taskId, UUID originBoardId) {
        ItemStack stack = new ItemStack(item);
        stack.set(TaskboundContent.PACKAGE_UUID.get(), packageId);
        stack.set(TaskboundContent.PACKAGE_TASK_UUID.get(), taskId);
        stack.set(TaskboundContent.PACKAGE_ORIGIN_BOARD_UUID.get(), originBoardId);
        return stack;
    }

    public static Optional<UUID> packageId(ItemStack stack) {
        return Optional.ofNullable(stack.get(TaskboundContent.PACKAGE_UUID.get()));
    }

    public static Optional<UUID> taskId(ItemStack stack) {
        return Optional.ofNullable(stack.get(TaskboundContent.PACKAGE_TASK_UUID.get()));
    }

    public static Optional<UUID> originBoardId(ItemStack stack) {
        return Optional.ofNullable(stack.get(TaskboundContent.PACKAGE_ORIGIN_BOARD_UUID.get()));
    }

    /** Presentation is separate from the sealed identity, so existing parcels remain valid. */
    public static void stamp(ItemStack stack, ResourceLocation dimension, BlockPos position) {
        stack.set(TaskboundContent.PACKAGE_DESTINATION.get(), GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), position));
    }

    public static Optional<GlobalPos> deliveryTarget(ItemStack stack) {
        return Optional.ofNullable(stack.get(TaskboundContent.PACKAGE_DESTINATION.get()));
    }

    public static ResourceLocation destination(ItemStack stack) {
        return deliveryTarget(stack).map(target -> target.dimension().location()).orElse(Level.OVERWORLD.location());
    }

    /** Native item layers: the author's shaded box, then its top-face dimension stamp. */
    public static int color(ItemStack stack, int tintIndex) {
        if (tintIndex == 0) {
            int seed = packageId(stack).map(UUID::hashCode).orElse(0x805040);
            float hue = (24.0F + ((seed >>> 16) & 255) * (30.0F / 255.0F)) / 360.0F;
            float saturation = 0.32F + ((seed >>> 8) & 255) * (0.15F / 255.0F);
            float value = 0.82F + (seed & 255) * (0.15F / 255.0F);
            return 0xFF000000 | Mth.hsvToRgb(hue, saturation, value);
        }
        if (tintIndex == 1) {
            ResourceLocation dimension = destination(stack);
            if (dimension.equals(Level.NETHER.location())) return 0xFFD86757;
            if (dimension.equals(Level.END.location())) return 0xFFAE7CD8;
            return 0xFF67AD52;
        }
        return 0xFFFFFFFF;
    }
}
