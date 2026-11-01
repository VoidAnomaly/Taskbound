package dev.taskbound.api.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public record FishingCatchContext(ServerLevel level, BlockPos hookPosition, ItemStack rod, String methodId) {
    public FishingCatchContext {
        rod = rod.copy();
    }
}
