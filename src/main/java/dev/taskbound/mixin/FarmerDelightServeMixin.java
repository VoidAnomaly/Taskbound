package dev.taskbound.mixin;

import dev.taskbound.compat.FarmersDelightCompat;
import dev.taskbound.event.FreshFoodLedger;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.CookingPotBlock", remap = false)
abstract class FarmerDelightServeMixin {
    @Unique private static final ThreadLocal<Deque<Integer>> taskbound$servings = ThreadLocal.withInitial(ArrayDeque::new);
    @Inject(method = "useItemOn", at = @At("HEAD"), remap = false)
    private void taskbound$before(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> callback) {
        taskbound$servings.get().push(player instanceof ServerPlayer ? FarmersDelightCompat.meal(level.getBlockEntity(pos)).getCount() : 0);
    }
    @Inject(method = "useItemOn", at = @At("RETURN"), remap = false)
    private void taskbound$after(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> callback) {
        int before = taskbound$servings.get().pop();
        if (taskbound$servings.get().isEmpty()) taskbound$servings.remove();
        BlockEntity pot = level.getBlockEntity(pos);
        ItemStack remaining = FarmersDelightCompat.meal(pot);
        int taken = before - remaining.getCount();
        if (pot != null && player instanceof ServerPlayer serverPlayer && taken == 1)
            FreshFoodLedger.collected(serverPlayer, pot, "meal", remaining, taken, "cooking_pot");
    }
}
